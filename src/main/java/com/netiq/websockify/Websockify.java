package com.netiq.websockify;

import java.io.PrintStream;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.netiq.websockify.WebsockifyServer.SSLSetting;

public class Websockify {
	
	@Option(name="--help",usage="show this help message and quit")
	private boolean showHelp = false;
	
	@Option(name="--enable-ssl",usage="enable SSL")
	private boolean enableSSL = false;
	
	@Option(name="--ssl-only",usage="disallow non-encrypted connections")
	private boolean requireSSL = false;
	
	@Option(name="--dir",usage="run webserver on same port. Serve files from specified directory.")
	private String webDirectory = null;
	
	@Option(name="--keystore",usage="path to a java keystore file. Required for SSL.")
	private String keystore = null;
	
	@Option(name="--keystore-password",usage="password to the java keystore file. Required for SSL.")
	private String keystorePassword = null;
	
	@Argument(index=0,metaVar="source_port",usage="(required) local port the websockify server will listen on",required=true)
	private int sourcePort;
	
	@Argument(index=1,metaVar="target_host",usage="(required) host the websockify server will proxy to",required=true)
	private String targetHost;
	
	@Argument(index=2,metaVar="target_port",usage="(required) port the websockify server will proxy to",required=true)
	private int targetPort;
	
	private CmdLineParser parser;
	
	public Websockify ( ) {
    	parser = new CmdLineParser(this);
	}
	
	public void printUsage ( PrintStream out ) {
		out.println("Usage:");
		out.println(" java -jar websockify.jar [options] source_port target_addr target_port");
		out.println();
		out.println("Options:");
		parser.printUsage(out);
		out.println();
		out.println("Example:");
		out.println(" java -jar websockify.jar 5900 server.example.net 5900");
	}
	
    public static void main(String[] args) throws Exception {
    	new Websockify().doMain(args);
    }
    
    public void doMain(String[] args) throws Exception {
    	parser.setUsageWidth(80);
    	
    	try {
    		parser.parseArgument(args);
    	}
    	catch (CmdLineException e) {
    		System.err.println(e.getMessage());
    		printUsage(System.err);
    		return;
    	}
    	
    	if ( showHelp ) {
    		printUsage(System.out);
    		return;
    	}
    	
    	SSLSetting sslSetting = SSLSetting.OFF;
    	if ( requireSSL ) sslSetting = SSLSetting.REQUIRED;
    	else if ( enableSSL ) sslSetting = SSLSetting.ON;
        
        if ( sslSetting != SSLSetting.OFF ) {
            if (keystore == null || keystore.isEmpty()) {
                System.err.println("No keystore specified.");
        		printUsage(System.err);
                System.exit(1);
            }

            if (keystorePassword == null || keystorePassword.isEmpty()) {
                System.err.println("No keystore password specified.");
        		printUsage(System.err);
                System.exit(1);
            }
        }

        System.out.println(
                "Websockify Proxying *:" + sourcePort + " to " +
                targetHost + ':' + targetPort + " ...");
        if(sslSetting != SSLSetting.OFF) System.out.println("SSL is " + (sslSetting == SSLSetting.REQUIRED ? "required." : "enabled."));

        WebsockifyServer wss = new WebsockifyServer ( );
        wss.connect ( sourcePort, targetHost, targetPort, sslSetting, keystore, keystorePassword, webDirectory );
        
    }

}
