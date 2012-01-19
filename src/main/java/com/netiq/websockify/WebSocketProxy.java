package com.netiq.websockify;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import com.netiq.websocket.Base64;
import com.netiq.websocket.Draft;
import com.netiq.websocket.Framedata;
import com.netiq.websocket.WebSocket;
import com.netiq.websocket.WebSocketAdapter;
import com.netiq.sslsocketchannel.*;

public class WebSocketProxy extends WebSocketAdapter implements Runnable {
	public static int BUFFER_SIZE = 65536;

	protected SocketChannel clientSocketChannel;
	protected boolean isSSL;
	protected boolean debug;
	protected String targetHost;
	protected int targetPort;
	
	protected WebSocket webSocket;
	protected SelectionKey webSocketKey;
	protected SocketChannel targetServer;
	protected SelectionKey targetServerKey;
	protected Selector selector;
	protected LinkedBlockingQueue<ByteBuffer> toTargetServerQueue = new LinkedBlockingQueue<ByteBuffer>();
	protected LinkedBlockingQueue<ByteBuffer> toWebSocketQueue = new LinkedBlockingQueue<ByteBuffer>();
	protected LinkedBlockingQueue<ByteBuffer> toWebSocketFrameQueue = new LinkedBlockingQueue<ByteBuffer>();

	public WebSocketProxy(SocketChannel client, String host, int port, boolean isSSL, boolean debug) throws IOException {
		this.debug = debug;
		this.isSSL = isSSL;
		this.targetHost = host;
		this.targetPort = port;
		this.clientSocketChannel = client;
		
		Thread thread = new Thread(this);
		thread.start();
	}
	
	public WebSocketProxy(SocketChannel client, String host, int port, boolean isSSL) throws IOException
	{
		this(client, host, port, isSSL, false);
	}

	@Override
	public void onMessage(WebSocket msgSocket, String message) {
		try {
			if(debug) System.out.print("}");
			byte[] buf = Base64.decode(message);
			ByteBuffer buffer = ByteBuffer.wrap(buf);
			//buffer.position(buffer.capacity());
			//System.out.println("Read from web socket " + buffer.position() + " bytes:" + message + "\n" + debugString(buffer));
			toTargetServerQueue.add(buffer);
			
		} catch (IOException e) {
			//System.out.println(e.getMessage());
		}

	}

	// Runnable IMPLEMENTATION /////////////////////////////////////////////////
	public void run() {
		try {
			SocketChannelWrapper clientSocketWrapper;
			if(isSSL){
				clientSocketWrapper = new SSLSocketChannelWrapper(clientSocketChannel);
			}
			else{
				clientSocketWrapper = new SocketChannelWrapper(clientSocketChannel);
			}
			WebSocket webSocket = new WebSocket (clientSocketWrapper, new LinkedBlockingQueue<ByteBuffer>(), this, Integer.MAX_VALUE);
			this.webSocket = webSocket;
			targetServer = SocketChannel.open(new InetSocketAddress(targetHost, targetPort));
			targetServer.configureBlocking(false);

			selector = Selector.open();
			targetServerKey = targetServer.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
			webSocketKey = webSocket.socketChannel().register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, webSocket);
		} catch (Exception ex) {
			ex.printStackTrace();
			return;
		}

		while (true) {
			try {
				selector.select();
				
				if(!targetServerKey.isValid() || !webSocketKey.isValid()){
					targetServer.close();
					webSocket.close();
					break;
				}

				if(!webSocket.isHandshakeComplete()){
					//System.out.println("Handling websocket handshake.");
					if (webSocketKey.isReadable()) {
						webSocket.handleRead();
					}
				}
				else{
					if (targetServerKey.isWritable() && !toTargetServerQueue.isEmpty()) {
						ByteBuffer buffer = toTargetServerQueue.element();
						//System.out.println("Writing to target server " + buffer.position() + " bytes:" + debugString(buffer));
						//buffer.flip();
						if(debug) System.out.print(">");
						targetServer.write(buffer);
						//System.out.println(written + " bytes written.");
						if(buffer.hasRemaining()) System.out.print(".");
						if (!buffer.hasRemaining())
							toTargetServerQueue.remove();
					}
	
					if (targetServerKey.isReadable()) {
						ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
						targetServer.read(buffer);
						if(debug) System.out.print("{");
						//System.out.println("Read from target server " + buffer.position() + " bytes:" + debugString(buffer));
						toWebSocketQueue.add(buffer);
					}
					
					while(!toWebSocketQueue.isEmpty()){
						//webSocket.handleWrite();
						ByteBuffer buffer = toWebSocketQueue.remove();
						String toSend = Base64.encodeBytes(buffer.array(), 0, buffer.position());
						//System.out.println("Writing to web socket " + buffer.position() + " bytes:" + debugString(buffer) + "\n" + toSend);
						//webSocket.send(toSend);
						
						Draft draft = webSocket.getDraft();
					    List<Framedata> frames = draft.createFrames ( toSend , false );
					    if( !frames.isEmpty () ){
						    for( Framedata f : frames ){
						    	ByteBuffer b = draft.createBinaryFrame ( f );
						    	toWebSocketFrameQueue.add(b);
						    }
					    }						
					}
	
					if (webSocketKey.isWritable() && !toWebSocketFrameQueue.isEmpty()) {
					    ByteBuffer buffer = toWebSocketFrameQueue.peek();
					    webSocket.socketChannel().write(buffer);
					    if(debug) System.out.print("<");
					    if(!buffer.hasRemaining()) toWebSocketFrameQueue.remove();
					    else if(debug) System.out.print(".");
					}
	
					if (webSocketKey.isReadable()) {
						webSocket.handleRead();
					}
				}

			} catch (IOException ex) {
				if (targetServerKey != null)
					targetServerKey.cancel();
				if (webSocketKey != null)
					webSocketKey.cancel();
				ex.printStackTrace();
			} catch (RuntimeException ex) {
				ex.printStackTrace();
			} catch (NoSuchAlgorithmException ex) {
				ex.printStackTrace();
			}
		}

		// System.err.println("WebSocketServer thread ended!");
	}

	static final byte[] HEX_CHAR_TABLE = { (byte) '0', (byte) '1', (byte) '2',
			(byte) '3', (byte) '4', (byte) '5', (byte) '6', (byte) '7',
			(byte) '8', (byte) '9', (byte) 'a', (byte) 'b', (byte) 'c',
			(byte) 'd', (byte) 'e', (byte) 'f' };

	public static String getHexString(byte[] raw)
			throws UnsupportedEncodingException {
		byte[] hex = new byte[2 * raw.length];
		int index = 0;

		for (byte b : raw) {
			int v = b & 0xFF;
			hex[index++] = HEX_CHAR_TABLE[v >>> 4];
			hex[index++] = HEX_CHAR_TABLE[v & 0xF];
		}
		return new String(hex, "ASCII");
	}

	String debugString(ByteBuffer buffer) {
		String fullstr = new String(buffer.array(), 0, buffer.position());
		String str = fullstr.replaceAll("\\p{Cntrl}", "");
		if(str.length() < fullstr.length()){
			try{
				String hexString = getHexString(Arrays.copyOfRange(buffer.array(), 0, buffer.position()));
				str = str + "\n" + hexString;
			}
			catch(Exception e){
				str = str + "\n" + e.getMessage();
			}
		}
		return str;
	}
}
