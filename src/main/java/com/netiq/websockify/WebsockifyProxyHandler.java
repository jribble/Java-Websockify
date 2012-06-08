package com.netiq.websockify;

import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpMethod.GET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.TEMPORARY_REDIRECT;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.activation.MimetypesFileTypeMap;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelFutureProgressListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultFileRegion;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.FileRegion;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedFile;
import org.jboss.netty.util.CharsetUtil;

public class WebsockifyProxyHandler extends SimpleChannelUpstreamHandler {

    private static final String URL_PARAMETER = "url";
	public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;
    public static final String REDIRECT_PATH = "/redirect";
    
    private final ClientSocketChannelFactory cf;
    private final IProxyTargetResolver resolver;

    private WebSocketServerHandshaker handshaker = null;
    private String webDirectory;

    // This lock guards against the race condition that overrides the
    // OP_READ flag incorrectly.
    // See the related discussion: http://markmail.org/message/x7jc6mqx6ripynqf
    final Object trafficLock = new Object();

    private volatile Channel outboundChannel;

    public WebsockifyProxyHandler(ClientSocketChannelFactory cf, IProxyTargetResolver resolver, String webDirectory) {
        this.cf = cf;
        this.resolver = resolver;
        this.outboundChannel = null;
        this.webDirectory = webDirectory;
    }

    private void ensureTargetConnection(ChannelEvent e, boolean websocket, final Object sendMsg)
            throws Exception {
    	if(outboundChannel == null) {
	        // Suspend incoming traffic until connected to the remote host.
	        final Channel inboundChannel = e.getChannel();
	        inboundChannel.setReadable(false);
			Logger.getLogger(WebsockifyProxyHandler.class.getName()).info("Inbound proxy connection from " + inboundChannel.getRemoteAddress() + ".");
	        
	        // resolve the target
	        final InetSocketAddress target = resolver.resolveTarget(inboundChannel);
	        if ( target == null )
	        {
				Logger.getLogger(WebsockifyProxyHandler.class.getName()).severe("Connection from " + inboundChannel.getRemoteAddress() + " failed to resolve target.");
	        	// there is no target
	        	inboundChannel.close();
	        	return;
	        }
	
	        // Start the connection attempt.
	        ClientBootstrap cb = new ClientBootstrap(cf);
	        if ( websocket ) {
	        	cb.getPipeline().addLast("handler", new OutboundWebsocketHandler(e.getChannel(), trafficLock));
	        }
	        else {
	        	cb.getPipeline().addLast("handler", new OutboundHandler(e.getChannel(), trafficLock));	        	
	        }
	        ChannelFuture f = cb.connect(target);
	
	        outboundChannel = f.getChannel();
	        if ( sendMsg != null ) outboundChannel.write(sendMsg);
	        f.addListener(new ChannelFutureListener() {
	            @Override
	            public void operationComplete(ChannelFuture future) throws Exception {
	                if (future.isSuccess()) {
	                    // Connection attempt succeeded:
	                    // Begin to accept incoming traffic.
	    				Logger.getLogger(WebsockifyProxyHandler.class.getName()).info("Created outbound connection to " + target + ".");
	                    inboundChannel.setReadable(true);
	                } else {
	    				Logger.getLogger(WebsockifyProxyHandler.class.getName()).severe("Failed to create outbound connection to " + target + ".");
	                    // Close the connection if the connection attempt has failed.
	                    inboundChannel.close();
	                }
	            }
	        });
    	} else {
	        if ( sendMsg != null ) outboundChannel.write(sendMsg);
    	}
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e)
            throws Exception {
        Object msg = e.getMessage();
        // An HttpRequest means either an initial websocket connection
        // or a web server request
        if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, (HttpRequest) msg, e);
        // A WebSocketFrame means a continuation of an established websocket connection
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg, e);
            // A channel buffer we treat as a VNC protocol request
        } else if (msg instanceof ChannelBuffer) {
            handleVncDirect(ctx, (ChannelBuffer) msg, e);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req, final MessageEvent e) throws Exception {
        // Allow only GET methods.
        if (req.getMethod() != GET) {
            sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        String upgradeHeader = req.getHeader("Upgrade");
        if(upgradeHeader != null && upgradeHeader.toUpperCase().equals("WEBSOCKET")){
			Logger.getLogger(WebsockifyProxyHandler.class.getName()).fine("Websocket request from " + e.getRemoteAddress() + ".");
	        // Handshake
	        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
	                this.getWebSocketLocation(req), "base64", false);
	        this.handshaker = wsFactory.newHandshaker(req);
	        if (this.handshaker == null) {
	            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
	        } else {
	        	// deal with a bug in the flash websocket emulation
	        	// it specifies WebSocket-Protocol when it seems it should specify Sec-WebSocket-Protocol
	        	String protocol = req.getHeader("WebSocket-Protocol");
	        	String secProtocol = req.getHeader("Sec-WebSocket-Protocol");
	        	if(protocol != null && secProtocol == null )
	        	{
	        		req.addHeader("Sec-WebSocket-Protocol", protocol);
	        	}
	            this.handshaker.handshake(ctx.getChannel(), req);
	        }
	    	ensureTargetConnection (e, true, null);
        }
        else {
            HttpRequest request = (HttpRequest) e.getMessage();
            String redirectUrl = isRedirect(request.getUri());
		    if ( redirectUrl != null) {
				Logger.getLogger(WebsockifyProxyHandler.class.getName()).fine("Redirecting to " + redirectUrl + ".");
		        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, TEMPORARY_REDIRECT);
		        response.setHeader(HttpHeaders.Names.LOCATION, redirectUrl);
	            sendHttpResponse(ctx, req, response);
	            return;
		    }
		    else if ( webDirectory != null )/* not a websocket connection attempt */{
		    	handleWebRequest ( ctx, e );
		    }
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame, final MessageEvent e) {

        // Check for closing frame
        if (frame instanceof CloseWebSocketFrame) {
            this.handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
            return;
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
            return;
        } else if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass()
                    .getName()));
        }
        
        ChannelBuffer msg = ((TextWebSocketFrame) frame).getBinaryData();
        ChannelBuffer decodedMsg = Base64.decode(msg);
        synchronized (trafficLock) {
            outboundChannel.write(decodedMsg);
            // If outboundChannel is saturated, do not read until notified in
            // OutboundHandler.channelInterestChanged().
            if (!outboundChannel.isWritable()) {
                e.getChannel().setReadable(false);
            }
        }
    }

    private void handleVncDirect(ChannelHandlerContext ctx, ChannelBuffer buffer, final MessageEvent e) throws Exception {
    	// ensure the target connection is open and send the data
    	ensureTargetConnection(e, false, buffer);
    }
    
    private void handleWebRequest(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {

        HttpRequest request = (HttpRequest) e.getMessage();
        if (request.getMethod() != GET) {
            sendError(ctx, METHOD_NOT_ALLOWED);
            return;
        }
        
		Logger.getLogger(WebsockifyProxyHandler.class.getName()).info("Web request from " + e.getRemoteAddress() + " for " + request.getUri() + ".");

        final String path = sanitizeUri(request.getUri());
        if (path == null) {
            sendError(ctx, FORBIDDEN);
            return;
        }

        File file = new File(path);
        if (file.isHidden() || !file.exists()) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        if (!file.isFile()) {
            sendError(ctx, FORBIDDEN);
            return;
        }

        // Cache Validation
        String ifModifiedSince = request.getHeader(HttpHeaders.Names.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.equals("")) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

            // Only compare up to the second because the datetime format we send to the client does not have milliseconds 
            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = file.lastModified() / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                sendNotModified(ctx);
                return;
            }
        }
        
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException fnfe) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        long fileLength = raf.length();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentLength(response, fileLength);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);
        
        Channel ch = e.getChannel();

        // Write the initial line and the header.
        ch.write(response);

        // Write the content.
        ChannelFuture writeFuture;
        if (ch.getPipeline().get(SslHandler.class) != null) {
            // Cannot use zero-copy with HTTPS.
            writeFuture = ch.write(new ChunkedFile(raf, 0, fileLength, 8192));
        } else {
            // No encryption - use zero-copy.
            final FileRegion region =
                new DefaultFileRegion(raf.getChannel(), 0, fileLength);
            writeFuture = ch.write(region);
            writeFuture.addListener(new ChannelFutureProgressListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    region.releaseExternalResources();
                }

                @Override
                public void operationProgressed(
                        ChannelFuture future, long amount, long current, long total) {
                    System.out.printf("%s: %d / %d (+%d)%n", path, current, total, amount);
                }
            });
        }

        // Decide whether to close the connection or not.
        if (!isKeepAlive(request)) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }
    
    private static Map<String, String> getQueryMap(String query)
    {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<String, String>();
        for (String param : params)
        {
            String name = param.split("=")[0];
            String value = param.split("=")[1];
            map.put(name, value);
        }
        return map;
    }

    // checks to see if the uri is a redirect request
    // if it is, it returns the url parameter
    private String isRedirect(String uri) throws URISyntaxException, MalformedURLException {
        // Decode the path.        
        URI url = new URI (uri);
        
        if ( REDIRECT_PATH.equals(url.getPath()) ) {
        	String query = url.getRawQuery();
        	Map<String, String> params = getQueryMap(query);
        	
        	String urlParam = params.get(URL_PARAMETER);
        	if ( urlParam == null ) return null;
        	
        	try {
				return URLDecoder.decode(urlParam, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				Logger.getLogger(WebsockifyProxyHandler.class.getName()).severe(e.getMessage());
			}
        }

        return null;
    }

    private String sanitizeUri(String uri) throws URISyntaxException {
        // Decode the path.        
        URI url = new URI (uri);
        uri = url.getPath();

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + ".") ||
            uri.contains("." + File.separator) ||
            uri.startsWith(".") || uri.endsWith(".")) {
            return null;
        }

        // Convert to absolute path.
        return webDirectory + File.separator + uri;
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        // Generate an error page if response status code is not OK (200).
        if (res.getStatus().getCode() != 200) {
            res.setContent(ChannelBuffers.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8));
            setContentLength(res, res.getContent().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.getChannel().write(res);
        if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
        response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.setContent(ChannelBuffers.copiedBuffer(
                "Failure: " + status.toString() + "\r\n",
                CharsetUtil.UTF_8));

        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     * 
     * @param ctx
     *            Context
     */
    private void sendNotModified(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    /**
     * Sets the Date header for the HTTP response
     * 
     * @param response
     *            HTTP response
     */
    private void setDateHeader(HttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.setHeader(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));
    }
    
    /**
     * Sets the Date and Cache headers for the HTTP Response
     * 
     * @param response
     *            HTTP response
     * @param fileToCache
     *            file to extract content type
     */
    private void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.setHeader(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.setHeader(HttpHeaders.Names.EXPIRES, dateFormatter.format(time.getTime()));
        response.setHeader(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.setHeader(HttpHeaders.Names.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /**
     * Sets the content type header for the HTTP Response
     * 
     * @param response
     *            HTTP response
     * @param file
     *            file to extract content type
     */
    private void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }

    private String getWebSocketLocation(HttpRequest req) {
        String prefix = "ws";
        String origin = req.getHeader(HttpHeaders.Names.ORIGIN).toLowerCase();
        if(origin.contains("https")){
            prefix = "wss";
        }
        return prefix + "://" + req.getHeader(HttpHeaders.Names.HOST) + req.getUri();
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        // If inboundChannel is not saturated anymore, continue accepting
        // the incoming traffic from the outboundChannel.
        synchronized (trafficLock) {
            if (e.getChannel().isWritable() && outboundChannel != null) {
                outboundChannel.setReadable(true);
            }
        }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {

		Logger.getLogger(WebsockifyProxyHandler.class.getName()).info("Inbound proxy connection from " + ctx.getChannel().getRemoteAddress() + " closed.");
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        e.getCause().printStackTrace();
		Logger.getLogger(WebsockifyProxyHandler.class.getName()).severe("Exception on inbound proxy connection from " + e.getChannel().getRemoteAddress() + ": " + e.getCause().getMessage());
        closeOnFlush(e.getChannel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isConnected()) {
            ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
