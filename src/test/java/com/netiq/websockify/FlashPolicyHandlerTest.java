package com.netiq.websockify;

import java.util.Random;

import static org.hamcrest.core.Is.is;
import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.junit.Assert.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;


public class FlashPolicyHandlerTest {

    private DecoderEmbedder<FlashPolicyRequest> embedder;

    @Before
    public void setUp() {
        embedder = new DecoderEmbedder<FlashPolicyRequest>(new FlashPolicyHandler());
    }
	
	@Test
	public void testDecode() {
		String request = "<policy-file-request/>";
        byte[] b = request.getBytes();
        ChannelBuffer buf = wrappedBuffer(b);
        embedder.offer(buf);
        // the first object on the list is not the FlashPolicyRequest object 
        Object first = embedder.poll();
        ChannelBuffer response = (ChannelBuffer) first;
        String resp = new String(response.array(), 0, response.readableBytes());
        assertTrue(FlashPolicyHandler.XML.equals(resp));
        FlashPolicyRequest fpr = embedder.poll(); 
        assertTrue( fpr instanceof FlashPolicyRequest);
	}
	
	@Test
	public void testDecodeCustomResponse() {
	    String XML = "<myresponse>";
	    ChannelBuffer policyResponse = ChannelBuffers.copiedBuffer(XML, CharsetUtil.UTF_8);
        embedder = new DecoderEmbedder<FlashPolicyRequest>(new FlashPolicyHandler(policyResponse));
	    
		String request = "<policy-file-request/>";
        byte[] b = request.getBytes();
        ChannelBuffer buf = wrappedBuffer(b);
        embedder.offer(buf);
        // the first object on the list is not the FlashPolicyRequest object 
        Object first = embedder.poll();
        ChannelBuffer response = (ChannelBuffer) first;
        String resp = new String(response.array(), 0, response.readableBytes());
        assertTrue(XML.equals(resp));
        FlashPolicyRequest fpr = embedder.poll(); 
        assertTrue( fpr instanceof FlashPolicyRequest);
	}
	
	@Test
	public void testDecodeEmpty() {
        byte[] b = new byte[2048];
        new Random().nextBytes(b);
        ChannelBuffer buf = wrappedBuffer(b);
        embedder.offer(buf);
        assertTrue(embedder.pollAll().length <= 0);
	}
	
	@Test
	public void testDecodeOtherType() {
        String str = "Meep!";
        embedder.offer(str);
        assertThat(embedder.poll(), is((Object) str));
	}
}
