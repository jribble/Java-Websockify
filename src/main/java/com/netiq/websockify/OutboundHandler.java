package com.netiq.websockify;

import java.util.logging.Logger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class OutboundHandler extends SimpleChannelUpstreamHandler {

    private final Channel inboundChannel;
    private final Object trafficLock;

    OutboundHandler(Channel inboundChannel, Object trafficLock) {
        this.inboundChannel = inboundChannel;
        this.trafficLock = trafficLock;
    }
    
    protected Object processMessage ( ChannelBuffer buffer ) {
    	return buffer;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e)
            throws Exception {
        ChannelBuffer msg = (ChannelBuffer) e.getMessage();
    	Object outMsg = processMessage ( msg );
        synchronized (trafficLock) {
            inboundChannel.write(outMsg);
            // If inboundChannel is saturated, do not read until notified in
            // HexDumpProxyInboundHandler.channelInterestChanged().
            if (!inboundChannel.isWritable()) {
                e.getChannel().setReadable(false);
            }
        }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        // If outboundChannel is not saturated anymore, continue accepting
        // the incoming traffic from the inboundChannel.
        synchronized (trafficLock) {
            if (e.getChannel().isWritable()) {
                inboundChannel.setReadable(true);
            }
        }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
		Logger.getLogger(WebsockifyProxyHandler.class.getName()).info("Outbound proxy connection to " + ctx.getChannel().getRemoteAddress() + " closed.");
        WebsockifyProxyHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        e.getCause().printStackTrace();
		Logger.getLogger(WebsockifyProxyHandler.class.getName()).severe("Exception on outbound proxy connection to " + e.getChannel().getRemoteAddress() + ": " + e.getCause().getMessage());
        WebsockifyProxyHandler.closeOnFlush(e.getChannel());
    }
}
