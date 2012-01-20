Java WebSockify
===============

This repository contains a basic Websockify server implementation
written using [Netty](http://netty.io).  It's designed to work with the websockify
client found at https://github.com/kanaka/websockify.  I specifically am using it as
a server to run [noVNC](http://kanaka.github.com/noVNC/) client.

Compiling and Running
---------------------

This project uses [Maven] (http://maven.apache.org/) for building.  Clone the repo, then compile
the project from the command line with:
    
    mvn compile
    
You can run the code from the command line with:

    mvn exec:java -Dexec.mainClass="com.netiq.websockify.Websockify" -Dexec.args="<port> <target host> <target port>"
    
where you replace `<port>` with the port number you want websockify to listen on and `<target host>` and `<target port>`
with the host and port that you want to websockify to proxy to.  For example:

    mvn exec:java -Dexec.mainClass="com.netiq.websockify.Websockify" -Dexec.args="10900 localhost 5900"


Using SSL Encryption
--------------------
Websockify can encrypt the websocket side of the traffic.  To use SSL encryption you first
need a keystore for your server key.  To create a self signed key run this on the command line:

    keytool -genkey -keyalg RSA -alias selfsigned -keystore keystore.jks -storepass password -validity 360 -keysize 2048

When it asks "What is your first and last name?" give it the host name that you will be using, for example `localhost`.

Run Websockify with `encrypt` as your 4th parameter, and specifying the keystore and password as JVM args, for example:

    mvn exec:java -Dexec.mainClass="com.netiq.websockify.Websockify" -Dexec.args="10900 localhost 5900 encrypt" -Dkeystore.file.path=keystore.jks -Dkeystore.file.password=password

License
-------

Everything found in this repo is licensed under an MIT license. See
the `LICENSE` file for specifics.
