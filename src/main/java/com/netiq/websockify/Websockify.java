package com.netiq.websockify;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Websockify {
	private Executor executor;
	private ServerBootstrap sb;
	private ClientSocketChannelFactory cf;
	private Channel serverChannel = null;
	
	public Websockify ( )
	{		
        // Configure the bootstrap.
        executor = Executors.newCachedThreadPool();
        sb = new ServerBootstrap(new NioServerSocketChannelFactory(executor, executor));

        // Set up the event pipeline factory.
        cf = new NioClientSocketChannelFactory(executor, executor);		
	}
	
	public void connect ( int localPort, String remoteHost, int remotePort, boolean useSSL )
	{
		connect ( localPort, new StaticTargetResolver ( remoteHost, remotePort ), useSSL );		
	}
	
	public void connect ( int localPort, IProxyTargetResolver resolver, boolean useSSL )
	{
		if ( serverChannel != null )
		{
			close ( );
		}

        sb.setPipelineFactory(new WebsockifyProxyPipelineFactory(cf, resolver, useSSL));

        // Start up the server.
        serverChannel = sb.bind(new InetSocketAddress(localPort));
		
	}
	
	public void close ( )
	{
		if ( serverChannel != null && serverChannel.isBound() )
		{
			serverChannel.close();
			serverChannel = null;
		}
	}
	
    public static void main(String[] args) throws Exception {
        // Validate command line options.
        if (args.length != 3 && args.length != 4) {
            System.err.println(
                    "Usage: " + Websockify.class.getSimpleName() +
                    " <local port> <remote host> <remote port> [encrypt]");
            return;
        }
        
        if (args.length == 4) {
            String keyStoreFilePath = System.getProperty("keystore.file.path");
            if (keyStoreFilePath == null || keyStoreFilePath.isEmpty()) {
                System.out.println("ERROR: System property keystore.file.path not set. Exiting now!");
                System.exit(1);
            }

            String keyStoreFilePassword = System.getProperty("keystore.file.password");
            if (keyStoreFilePassword == null || keyStoreFilePassword.isEmpty()) {
                System.out.println("ERROR: System property keystore.file.password not set. Exiting now!");
                System.exit(1);
            }
        }

        // Parse command line options.
        int localPort = Integer.parseInt(args[0]);
        String remoteHost = args[1];
        int remotePort = Integer.parseInt(args[2]);
        boolean useSSL = args.length < 4 ? false : true;

        System.out.println(
                "Websockify Proxying *:" + localPort + " to " +
                remoteHost + ':' + remotePort + " ...");
        if(useSSL) System.out.println("Websocket communications are SSL encrypted.");

        Websockify ws = new Websockify ( );
        ws.connect ( localPort, remoteHost, remotePort, useSSL );
        
    }

}
