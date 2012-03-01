package com.netiq.websockify;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class Websockify {
	
	@Option(name="--help",usage="show this help message")
	private boolean showHelp = false;
	
	@Option(name="--port",usage="(required) local port the websockify server will listen on",required=true)
	private int port;
	
	@Option(name="--remote-host",usage="(required) remote host the websockify server will proxy to",required=true)
	private String remoteHost;
	
	@Option(name="--remote-port",usage="(required) remote port the websockify server will proxy to",required=true)
	private int remotePort;
	
	@Option(name="--enable-ssl",usage="enable SSL")
	private boolean enableSSL = false;
	
    public static void main(String[] args) throws Exception {
    	new Websockify().doMain(args);
    }
    
    public void doMain(String[] args) throws Exception {
    	CmdLineParser parser = new CmdLineParser(this);
    	parser.setUsageWidth(80);
    	
    	try {
    		parser.parseArgument(args);
    	}
    	catch (CmdLineException e) {
    		System.err.println(e.getMessage());
    		System.err.println("java -jar websockify.jar [options...]");
    		parser.printUsage(System.err);
    		return;
    	}
    	
    	if ( showHelp ) {
    		System.err.println("java -jar websockify.jar [options...]");
    		parser.printUsage(System.out);
    		return;
    	}
        
        if (enableSSL) {
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

        System.out.println(
                "Websockify Proxying *:" + port + " to " +
                remoteHost + ':' + remotePort + " ...");
        if(enableSSL) System.out.println("Websocket communications are SSL encrypted.");

        WebsockifyServer wss = new WebsockifyServer ( );
        wss.connect ( port, remoteHost, remotePort, enableSSL, true );
        
    }

}
