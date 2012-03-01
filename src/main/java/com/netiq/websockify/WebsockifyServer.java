package com.netiq.websockify;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WebsockifyServer {
	private Executor executor;
	private ServerBootstrap sb;
	private ClientSocketChannelFactory cf;
	private Channel serverChannel = null;
	
	public WebsockifyServer ( )
	{		
        // Configure the bootstrap.
        executor = Executors.newCachedThreadPool();
        sb = new ServerBootstrap(new NioServerSocketChannelFactory(executor, executor));

        // Set up the event pipeline factory.
        cf = new NioClientSocketChannelFactory(executor, executor);		
	}
	
	public void connect ( int localPort, String remoteHost, int remotePort, boolean useSSL, boolean enableWebServer )
	{
		connect ( localPort, new StaticTargetResolver ( remoteHost, remotePort ), useSSL, enableWebServer );		
	}
	
	public void connect ( int localPort, IProxyTargetResolver resolver, boolean useSSL, boolean enableWebServer )
	{
		if ( serverChannel != null )
		{
			close ( );
		}

        sb.setPipelineFactory(new WebsockifyProxyPipelineFactory(cf, resolver, useSSL, enableWebServer));

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
	
	public Channel getChannel ( )
	{
		return serverChannel;
	}

}
