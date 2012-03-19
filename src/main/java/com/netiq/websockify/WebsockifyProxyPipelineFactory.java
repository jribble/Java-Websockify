package com.netiq.websockify;

import static org.jboss.netty.channel.Channels.pipeline;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;

import com.netiq.websockify.WebsockifyServer.SSLSetting;

public class WebsockifyProxyPipelineFactory implements ChannelPipelineFactory {

    private final ClientSocketChannelFactory cf;
    private final IProxyTargetResolver resolver;
    private final SSLSetting sslSetting;
    private final String keystore;
    private final String keystorePassword;
    private final String keystoreKeyPassword;
    private final String webDirectory;

    public WebsockifyProxyPipelineFactory(ClientSocketChannelFactory cf, IProxyTargetResolver resolver, SSLSetting sslSetting, String keystore, String keystorePassword, String keystoreKeyPassword, String webDirectory) {
        this.cf = cf;
        this.resolver = resolver;
        this.sslSetting = sslSetting;
        this.keystore = keystore;
        this.keystorePassword = keystorePassword;
        this.keystoreKeyPassword = keystoreKeyPassword;
        this.webDirectory = webDirectory;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline p = pipeline(); // Note the static import.
        
        p.addLast("unification", new PortUnificationHandler(cf, resolver, sslSetting, keystore, keystorePassword, keystoreKeyPassword, webDirectory));
        return p;

    }

}
