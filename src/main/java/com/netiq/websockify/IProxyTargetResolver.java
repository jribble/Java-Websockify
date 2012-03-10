package com.netiq.websockify;

import java.net.InetSocketAddress;

import org.jboss.netty.channel.Channel;

public interface IProxyTargetResolver {
	
	public InetSocketAddress resolveTarget ( Channel channel );

}
