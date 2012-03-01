/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.netiq.websockify;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;

import com.netiq.websockify.WebsockifyServer.SSLSetting;

/**
 * Manipulates the current pipeline dynamically to switch protocols or enable
 * SSL or GZIP.
 */
public class PortUnificationHandler extends FrameDecoder {

	private final ClientSocketChannelFactory cf;
	private final IProxyTargetResolver resolver;
    private final SSLSetting sslSetting;
    private final String webDirectory;

    public PortUnificationHandler(ClientSocketChannelFactory cf, IProxyTargetResolver resolver, SSLSetting sslSetting, String webDirectory) {
    	this.cf = cf;
    	this.resolver = resolver;
        this.sslSetting = sslSetting;
        this.webDirectory = webDirectory;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {

        // Will use the first two bytes to detect a protocol.
        if (buffer.readableBytes() < 2) {
            return null;
        }

        final int magic1 = buffer.getUnsignedByte(buffer.readerIndex());
        final int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);

        if (isSsl(magic1)) {
            enableSsl(ctx);
        } else if ( isFlashPolicy ( magic1, magic2 ) ) {
        	switchToFlashPolicy(ctx);
        } else {
            switchToProxy(ctx);
        }

        // Forward the current read buffer as is to the new handlers.
        return buffer.readBytes(buffer.readableBytes());
    }

    private boolean isSsl(int magic1) {
        if (sslSetting != SSLSetting.OFF) {
            switch (magic1) {
            case 20: case 21: case 22: case 23: case 255:
                return true;
            default:
                return magic1 >= 128;
            }
        }
        return false;
    }
    
    private boolean isFlashPolicy(int magic1, int magic2 ) {
        return (magic1 == '<' && magic2 == 'p');
    }

    private void enableSsl(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.getPipeline();

        SSLEngine engine = WebsockifySslContext.getInstance().getServerContext().createSSLEngine();
        engine.setUseClientMode(false);

        p.addLast("ssl", new SslHandler(engine));
        p.addLast("unificationA", new PortUnificationHandler(cf, resolver, SSLSetting.OFF, webDirectory));
        p.remove(this);
    }

    private void switchToProxy(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.getPipeline();

        p.addLast("decoder", new HttpRequestDecoder());
        p.addLast("aggregator", new HttpChunkAggregator(65536));
        p.addLast("encoder", new HttpResponseEncoder());
        p.addLast("chunkedWriter", new ChunkedWriteHandler());
        p.addLast("handler", new WebsockifyInboundHandler(cf, resolver, webDirectory));
        p.remove(this);
    }

    private void switchToFlashPolicy(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.getPipeline();

        p.addLast("flash", new FlashPolicyHandler());

        p.remove(this);
    }
}
