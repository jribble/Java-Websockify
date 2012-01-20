package com.netiq.websockify;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Websockify {
    public static void main(String[] args) throws Exception {
        // Validate command line options.
        if (args.length != 3 && args.length != 4) {
            System.err.println(
                    "Usage: " + Websockify.class.getSimpleName() +
                    " <local port> <remote host> <remote port> [encrypt]");
            return;
        }
        
        if (args.length == 4) {
            String keyStoreFilePath = System.getProperty("keystore.file.path");
            if (keyStoreFilePath == null || keyStoreFilePath.isEmpty()) {
                System.out.println("ERROR: System property keystore.file.path not set. Exiting now!");
                System.exit(1);
            }

            String keyStoreFilePassword = System.getProperty("keystore.file.password");
            if (keyStoreFilePassword == null || keyStoreFilePassword.isEmpty()) {
                System.out.println("ERROR: System property keystore.file.password not set. Exiting now!");
                System.exit(1);
            }
        }

        // Parse command line options.
        int localPort = Integer.parseInt(args[0]);
        String remoteHost = args[1];
        int remotePort = Integer.parseInt(args[2]);
        boolean useSSL = args.length < 4 ? false : true;

        System.out.println(
                "Websockify Proxying *:" + localPort + " to " +
                remoteHost + ':' + remotePort + " ...");
        if(useSSL) System.out.println("Websocket communications are SSL encrypted.");

        // Configure the bootstrap.
        Executor executor = Executors.newCachedThreadPool();
        ServerBootstrap sb = new ServerBootstrap(new NioServerSocketChannelFactory(executor, executor));

        // Set up the event pipeline factory.
        ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(executor, executor);

        sb.setPipelineFactory(new WebsockifyProxyPipelineFactory(cf, remoteHost, remotePort, useSSL));

        // Start up the server.
        sb.bind(new InetSocketAddress(localPort));
    }

}
