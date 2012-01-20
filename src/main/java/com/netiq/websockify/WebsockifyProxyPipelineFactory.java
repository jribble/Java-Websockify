package com.netiq.websockify;

import static org.jboss.netty.channel.Channels.pipeline;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;

public class WebsockifyProxyPipelineFactory implements ChannelPipelineFactory {

    private final ClientSocketChannelFactory cf;
    private final String remoteHost;
    private final int remotePort;
    private final boolean useSSL;

    public WebsockifyProxyPipelineFactory(ClientSocketChannelFactory cf, String remoteHost, int remotePort, boolean useSSL) {
        this.cf = cf;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.useSSL = useSSL;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline p = pipeline(); // Note the static import.
        
        if(useSSL) {
            SSLEngine engine = WebsockifySslContext.getInstance().getServerContext().createSSLEngine();
            engine.setUseClientMode(false);
            p.addLast("ssl", new SslHandler(engine));
        }

        p.addLast("decoder", new HttpRequestDecoder());
        p.addLast("aggregator", new HttpChunkAggregator(65536));
        p.addLast("encoder", new HttpResponseEncoder());
        p.addLast("handler", new WebsockifyInboundHandler(cf, remoteHost, remotePort));
        return p;
    }

}
