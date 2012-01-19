package com.netiq.websockify;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class WebsockifyServer implements Runnable {
	/**
	 * The port number that this WebSocket server should listen on. Default is
	 * WebSocket.DEFAULT_PORT.
	 */
	protected int port;
	/**
	 * The socket channel for this WebSocket server.
	 */
	protected ServerSocketChannel server;
	/**
	 * The 'Selector' used to get event keys from the underlying socket.
	 */
	protected Selector selector;

	protected String targetHost;
	protected int targetPort;
	
	protected boolean isSSL = false;

	public WebsockifyServer(int port, String targetHost, int targetPort) {
		this ( port, targetHost, targetPort, false);
	}
	
	public WebsockifyServer(int port, String targetHost, int targetPort, boolean isSSL) {
		this.port = port;
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		this.isSSL = isSSL;
	}

	/**
	 * Starts the server thread that binds to the currently set port number and
	 * listeners for WebSocket connection requests.
	 */
	public void start() {
		(new Thread(this)).start();
	}

	/**
	 * Closes all connected clients sockets, then closes the underlying
	 * ServerSocketChannel, effectively killing the server socket thread and
	 * freeing the port the server was bound to.
	 * 
	 * @throws IOException
	 *             When socket related I/O errors occur.
	 */
	public void stop() throws IOException {
		// for (WebSocket ws : connections) {
		// ws.close();
		// }
		this.server.close();
	}

	public int getPort() {
		return port;
	}

	// Runnable IMPLEMENTATION /////////////////////////////////////////////////
	public void run() {
		try {
			server = ServerSocketChannel.open();
			server.configureBlocking(false);
			server.socket().bind(new java.net.InetSocketAddress(port));

			selector = Selector.open();
			server.register(selector, server.validOps());
		} catch (IOException ex) {
			// onError(ex);
			return;
		}

		while (true) {
			SelectionKey key = null;
			try {
				selector.select();
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> i = keys.iterator();

				while (i.hasNext()) {
					key = i.next();

					// Remove the current key
					i.remove();

					// if isAcceptable == true
					// then a client required a connection
					if (key.isAcceptable()) {
						SocketChannel client = server.accept();
						client.configureBlocking(false);
						new WebSocketProxy(client, targetHost, targetPort, isSSL);
					}
				}
			} catch (IOException ex) {
				if (key != null)
					key.cancel();
				// onError(ex);
			} catch (RuntimeException ex) {
				ex.printStackTrace();
			}
		}

		// System.err.println("WebSocketServer thread ended!");
	}

	public static void main(String[] args) {
		try {
			if(args.length != 3){
				System.out.println("Usage: websockify [port] [target host] [target port]");
			}
			int port = Integer.parseInt(args[0]);
			String targetHost = args[1];
			int targetPort = Integer.parseInt(args[2]);
			
			WebsockifyServer s = new WebsockifyServer(port, targetHost, targetPort, true);
			s.start();
			System.out.println("Websockify Server started on port: " + s.getPort());
		}
		catch(Exception e) {
			System.out.println( e.getMessage() );
		}
	}

}
