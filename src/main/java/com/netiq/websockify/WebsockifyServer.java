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
	
	public enum SSLSetting { OFF, ON, REQUIRED };
	
	public WebsockifyServer ( )
	{		
        // Configure the bootstrap.
        executor = Executors.newCachedThreadPool();
        sb = new ServerBootstrap(new NioServerSocketChannelFactory(executor, executor));

        // Set up the event pipeline factory.
        cf = new NioClientSocketChannelFactory(executor, executor);		
	}
	
	public void connect ( int localPort, String remoteHost, int remotePort )
	{
		connect ( localPort, remoteHost, remotePort, SSLSetting.OFF, null );
	}
	
	public void connect ( int localPort, String remoteHost, int remotePort, SSLSetting sslSetting )
	{
		connect ( localPort, remoteHost, remotePort, sslSetting, null );
	}
	
	public void connect ( int localPort, String remoteHost, int remotePort, SSLSetting sslSetting, String webDirectory )
	{
		connect ( localPort, new StaticTargetResolver ( remoteHost, remotePort ), sslSetting, webDirectory );		
	}
	
	public void connect ( int localPort, IProxyTargetResolver resolver, SSLSetting sslSetting )
	{
		connect ( localPort, resolver, sslSetting, null );
	}
	
	public void connect ( int localPort, IProxyTargetResolver resolver, SSLSetting sslSetting, String webDirectory )
	{
		if ( serverChannel != null )
		{
			close ( );
		}

        sb.setPipelineFactory(new WebsockifyProxyPipelineFactory(cf, resolver, sslSetting, webDirectory));

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
