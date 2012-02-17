package com.netiq.websockify;

import java.net.InetSocketAddress;
import org.jboss.netty.channel.MessageEvent;

public interface IProxyTargetResolver {
	
	public InetSocketAddress resolveTarget ( MessageEvent e );

}
