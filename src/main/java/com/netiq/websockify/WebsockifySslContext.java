package com.netiq.websockify;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.Security;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

public class WebsockifySslContext {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebsockifySslContext.class);
    private static final String PROTOCOL = "TLS";
    private SSLContext _serverContext;

    /**
     * Returns the singleton instance for this class
     */
    public static WebsockifySslContext getInstance(String keystore, String keystorePassword) {
    	WebsockifySslContext context = SingletonHolder.INSTANCE_MAP.get(keystore);
    	if ( context == null )
    	{
    		context = new WebsockifySslContext ( keystore, keystorePassword );
    		SingletonHolder.INSTANCE_MAP.put(keystore, context);
    	}
    	return context;
    }

    /**
     * SingletonHolder is loaded on the first execution of Singleton.getInstance() or the first access to
     * SingletonHolder.INSTANCE, not before.
     * 
     * See http://en.wikipedia.org/wiki/Singleton_pattern
     */
    private static class SingletonHolder {
        public static final HashMap<String, WebsockifySslContext> INSTANCE_MAP = new HashMap<String, WebsockifySslContext>();
    }

    /**
     * Constructor for singleton
     */
    private WebsockifySslContext(String keystore, String keystorePassword) {
        try {
            // Key store (Server side certificate)
            String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
            if (algorithm == null) {
                algorithm = "SunX509";
            }

            SSLContext serverContext = null;
            try {
                String keyStoreFilePath = keystore;
                String keyStoreFilePassword = keystorePassword;

                KeyStore ks = KeyStore.getInstance("JKS");
                FileInputStream fin = new FileInputStream(keyStoreFilePath);
                ks.load(fin, keyStoreFilePassword.toCharArray());

                // Set up key manager factory to use our key store
                // Assume key password is the same as the key store file
                // password
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
                kmf.init(ks, keyStoreFilePassword.toCharArray());

                // Initialise the SSLContext to work with our key managers.
                serverContext = SSLContext.getInstance(PROTOCOL);
                serverContext.init(kmf.getKeyManagers(), null, null);
            } catch (Exception e) {
        		Logger.getLogger(WebsockifySslContext.class.getName()).severe("Error creating SSL context for keystore " + keystore + ": " + e.getMessage());
                throw new Error("Failed to initialize the server-side SSLContext", e);
            }
            _serverContext = serverContext;
        } catch (Exception ex) {
            logger.error("Error initializing SslContextManager. " + ex.getMessage(), ex);
            System.exit(1);

        }
    }

    /**
     * Returns the server context with server side key store
     */
    public SSLContext getServerContext() {
        return _serverContext;
    }
}
