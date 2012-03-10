package com.netiq.websockify;

import java.net.InetSocketAddress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;

public class DirectProxyHandler extends SimpleChannelUpstreamHandler {
    
    private final ClientSocketChannelFactory cf;
    private final IProxyTargetResolver resolver;

    // This lock guards against the race condition that overrides the
    // OP_READ flag incorrectly.
    // See the related discussion: http://markmail.org/message/x7jc6mqx6ripynqf
    final Object trafficLock = new Object();

    private volatile Channel outboundChannel;

    public DirectProxyHandler(final Channel inboundChannel, ClientSocketChannelFactory cf, IProxyTargetResolver resolver) {
        this.cf = cf;
        this.resolver = resolver;
        this.outboundChannel = null;
        
        ensureTargetConnection ( inboundChannel, false, null );
    }

    private void ensureTargetConnection(final Channel inboundChannel, boolean websocket, final Object sendMsg) {
    	if(outboundChannel == null) {
	        // Suspend incoming traffic until connected to the remote host.
	        inboundChannel.setReadable(false);
	        
	        // resolve the target
	        InetSocketAddress target = resolver.resolveTarget(inboundChannel);
	        if ( target == null )
	        {
	        	// there is no target
	        	inboundChannel.close();
	        	return;
	        }
	
	        // Start the connection attempt.
	        ClientBootstrap cb = new ClientBootstrap(cf);
	        if ( websocket ) {
	        	cb.getPipeline().addLast("handler", new OutboundWebsocketHandler(inboundChannel, trafficLock));
	        }
	        else {
	        	cb.getPipeline().addLast("handler", new OutboundHandler(inboundChannel, trafficLock));	        	
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
	                    inboundChannel.setReadable(true);
	                } else {
	                    // Close the connection if the connection attempt has failed.
	                    inboundChannel.close();
	                }
	            }
	        });
    	} else {
	        if ( sendMsg != null ) outboundChannel.write(sendMsg);
    	}
    }
    
    // In cases where there will be a direct VNC proxy connection
    // The client won't send any message because VNC servers talk first
    // So we'll set a timer on the connection - if there's no message by the time
    // the timer fires we'll create the proxy connection to the target
    @Override
    public void channelOpen(final ChannelHandlerContext ctx, final ChannelStateEvent e)
            throws Exception {
		try {
	        // make the proxy connection
			ensureTargetConnection ( e.getChannel(), false, null );
		} catch (Exception ex) {
			// target connection failed, so close the client connection
			e.getChannel().close();
			ex.printStackTrace();
		}
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e)
            throws Exception {
        Object msg = e.getMessage();
        handleVncDirect(ctx, (ChannelBuffer) msg, e);
    }

    private void handleVncDirect(ChannelHandlerContext ctx, ChannelBuffer buffer, final MessageEvent e) throws Exception {
    	// ensure the target connection is open and send the data
    	ensureTargetConnection(e.getChannel(), false, buffer);
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
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        e.getCause().printStackTrace();
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
