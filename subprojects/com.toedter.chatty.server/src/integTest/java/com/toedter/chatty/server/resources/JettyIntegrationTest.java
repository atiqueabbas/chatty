/**
 * Copyright (c) 2014 Kai Toedter
 * All rights reserved.
 * Licensed under MIT License, see http://toedter.mit-license.org/
 */

package com.toedter.chatty.server.resources;

import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.wasync.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyIntegrationTest extends AbstractIntegrationTest {

    private static Logger logger = LoggerFactory.getLogger(JettyIntegrationTest.class);
    private Server server;

    @Override
    public void startServer() throws Exception {
        server = new Server(freePort);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/chatty");
        server.setHandler(context);

        AtmosphereServlet atmoServlet = new AtmosphereServlet();
        context.addServlet(new ServletHolder(atmoServlet), "/atmos/*");

        ServletContainer jerseyContainer = new ServletContainer(resourceConfig);
        context.addServlet(new ServletHolder(jerseyContainer), "/api/*");

        atmosphereTransport = Request.TRANSPORT.WEBSOCKET;
        server.start();
    }

    @Override
    public void stopServer() throws Exception {
        server.stop();
    }
}
