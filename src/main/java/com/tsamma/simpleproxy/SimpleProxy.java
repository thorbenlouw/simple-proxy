package com.tsamma.simpleproxy;

import org.apache.commons.cli.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;

import java.io.File;


public class SimpleProxy {

    private final int port;
    private final String keystore;
    private final String remoteHost;
    private final String remotePort;
    private final boolean verbose;
    private final String password;


    public SimpleProxy(int port, String keystore, String password, String remoteHost, String remotePort, boolean verbose) {
        this.port = port;
        this.keystore = keystore;
        this.password = password;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.verbose = verbose;
        setSystemProperties();
    }

    private void setSystemProperties() {
        System.setProperty("simpleproxy.remote_host", remoteHost);
        System.setProperty("simpleproxy.remote_port", remotePort);

        if (keystore != null) {
            checkKeystoreExists();
            System.setProperty("javax.net.ssl.keyStore", keystore);
            System.setProperty("javax.net.ssl.trustStore", keystore);
            System.setProperty("javax.net.ssl.keyStoreType", "jks");
            System.setProperty("javax.net.ssl.trustStoreType", "jks");

        }
        if (password != null) {
            System.setProperty("javax.net.ssl.keyStorePassword", password);
            System.setProperty("javax.net.ssl.trustStorePassword", password);
        }
        System.setProperty("javax.net.debug", verbose ? "all" : "");
        System.setProperty("org.eclipse.jetty.LEVEL", "INFO");


    }

    public void start() throws Exception {
        System.out.println(String.format("Starting HTTP Server at http://localhost:%s, and proxying all requests to https://%s:%s", port, remoteHost, remotePort));

        Server server = new Server(port);

        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping(ProxyServlet.class, "/*");

        server.setHandler(servletHandler);
        server.start();
        server.join();
    }

    private void checkKeystoreExists() {
        System.out.println(String.format("Using keys and certs from %s", keystore));

        if (!new File(keystore).isFile()) {
            System.err.println(String.format("The given file for the keystore (%s) does not exist. Maybe supplying a full path?", keystore));
            System.exit(-1);
        }
    }


    private static boolean allRequiredFieldsPresent(CommandLine line) {
        return line.hasOption("local-port") &&
                line.hasOption("remote-host") &&
                line.hasOption("remote-port");
    }


    public static void main(String... args) throws Exception {
        CommandLineParser parser = new BasicParser();

        Options options = new Options();
        options.addOption("k", "keystore", false, "[optional] fully-qualified path to file containing the JKS keystore to use");
        options.addOption("s", "passphrase", false, "[optional] Keystore passphrase");
        options.addOption("p", "local-port", true, "[required] port to bind this local webserver to");
        options.addOption("H", "remote-host", true, "[required] hostname of the remote server, e.g.");
        options.addOption("P", "remote-port", true, "[required] port to proxy to on the remote server");
        options.addOption("v", "verbose", false, "[optional] verbose mode");
        options.addOption("h", "help", false, "[optional] display this help message");

        CommandLine line = parser.parse(options, args);
        if (!allRequiredFieldsPresent(line) || line.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(SimpleProxy.class.getCanonicalName(), options);
            System.exit(line.hasOption("help") ? 0 : -1);
        }

        int port = Integer.valueOf(line.getOptionValue("local-port"));
        String keyStoreFile = line.getOptionValue("keystore");
        String keyStorePassword = line.getOptionValue("passphrase");
        String remoteHost = line.getOptionValue("remote-host");
        String remotePort = line.getOptionValue("remote-port");
        boolean verbose = line.hasOption("verbose");

        SimpleProxy simpleProxy = new SimpleProxy(port, keyStoreFile, keyStorePassword, remoteHost, remotePort, verbose);
        simpleProxy.start();
    }

}
