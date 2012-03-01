package com.netiq.websockify;

import static org.jboss.netty.channel.Channels.pipeline;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;

public class WebsockifyProxyPipelineFactory implements ChannelPipelineFactory {

    private final ClientSocketChannelFactory cf;
    private final IProxyTargetResolver resolver;
    private final boolean useSSL;
    private final boolean enableWebServer;

    public WebsockifyProxyPipelineFactory(ClientSocketChannelFactory cf, IProxyTargetResolver resolver, boolean useSSL, boolean enableWebServer) {
        this.cf = cf;
        this.resolver = resolver;
        this.useSSL = useSSL;
        this.enableWebServer = enableWebServer;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline p = pipeline(); // Note the static import.
        
        p.addLast("unification", new PortUnificationHandler(cf, resolver, useSSL, enableWebServer));
        return p;

    }

}
