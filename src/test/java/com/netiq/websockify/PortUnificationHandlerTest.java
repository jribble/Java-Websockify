package com.netiq.websockify;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.junit.Before;
import org.junit.Test;

import com.netiq.websockify.WebsockifyServer.SSLSetting;


public class PortUnificationHandlerTest {

    private DecoderEmbedder<FlashPolicyRequest> embedder;
    private ClientSocketChannelFactory cf = null;
    private IProxyTargetResolver resolver = null;

    @Before
    public void setUp() {
    	cf = mock ( ClientSocketChannelFactory.class );
    	resolver = mock ( IProxyTargetResolver.class );
        embedder = new DecoderEmbedder<FlashPolicyRequest>(new PortUnificationHandler(cf, resolver, SSLSetting.OFF, null, null, null, null));
    }
	
	@Test
	public void testFlashRequest() {
		String request = "<policy-file-request/>";
        byte[] b = request.getBytes();
        ChannelBuffer buf = wrappedBuffer(b);
        embedder.offer(buf);
        ChannelPipeline pipeline = embedder.getPipeline();
        assertNotNull(pipeline.get("flash"));
        assertTrue(pipeline.get("flash") instanceof FlashPolicyHandler);
	}
}
