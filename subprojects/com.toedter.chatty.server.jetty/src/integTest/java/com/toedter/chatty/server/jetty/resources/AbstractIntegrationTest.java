/**
 * Copyright (c) 2016 Kai Toedter
 * All rights reserved.
 * Licensed under MIT License, see http://toedter.mit-license.org/
 */

package com.toedter.chatty.server.jetty.resources;

import com.theoryinpractise.halbuilder.api.RepresentationFactory;
import com.toedter.chatty.model.*;
import org.atmosphere.wasync.*;
import org.atmosphere.wasync.impl.AtmosphereClient;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public abstract class AbstractIntegrationTest {
    private static Logger logger = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    private WebTarget target;
    private UserRepository userRepository;
    protected static int freePort = findFreePort();
    protected static final String BASE_URI = "http://localhost:" + freePort;
    protected ResourceConfig resourceConfig = new ResourceConfig(UserResource.class, ChatMessageResource.class);
    protected Request.TRANSPORT atmosphereTransport = Request.TRANSPORT.LONG_POLLING;

    protected static int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            int port = socket.getLocalPort();
            logger.info("Using port {} for integration tests.", port);
            return port;
        } catch (IOException e) {
            logger.warn("Cannot find free port, will use 8080.");
            return 8080;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.error("Cannot close socket.");
                }
            }
        }
    }

    abstract public void startServer() throws Exception;

    abstract public void stopServer() throws Exception;

    @BeforeClass
    public  static  void beforeClass() {
        // Jersey uses java.util.logging - bridge to slf4
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }


    @Before
    public void before() throws Exception {
        startServer();
        Client c = ClientBuilder.newClient();
        target = c.target(BASE_URI + "/chatty");

        userRepository = ModelFactory.getInstance().getUserRepository();
        userRepository.saveUser(new SimpleUser("kai", "Kai Toedter", "kai@toedter.com"));
        userRepository.saveUser(new SimpleUser("john", "John Doe", "john@doe.com"));
        userRepository.saveUser(new SimpleUser("jane", "Jane Doe", "jane@doe.com"));
    }

    @After
    public void after() throws Exception {
        stopServer();
        userRepository.deleteAll();
    }

    @Test
    public void should_get_all_users_as_list() {
        String responseMsg = target.path("api/users").request().get(String.class);

        assertThat(responseMsg, containsString("\"email\":\"john@doe.com\""));
        assertThat(responseMsg, containsString("\"id\":\"john\""));
        assertThat(responseMsg, containsString("\"fullName\":\"John Doe\""));
        assertThat(responseMsg, containsString("\"email\":\"kai@toedter.com\""));
        assertThat(responseMsg, containsString("\"id\":\"kai\""));
        assertThat(responseMsg, containsString("\"fullName\":\"Kai Toedter\""));
        assertThat(responseMsg, containsString("\"email\":\"jane@doe.com\""));
        assertThat(responseMsg, containsString("\"id\":\"jane\""));
        assertThat(responseMsg, containsString("\"fullName\":\"Jane Doe\""));
    }

    @Test
    public void should_get_single_user_by_id() {
        String responseMsg = target.path("api/users/kai").request().get(String.class);

        assertThat(responseMsg, containsString("\"email\":\"kai@toedter.com\""));
        assertThat(responseMsg, containsString("\"id\":\"kai\""));
        assertThat(responseMsg, containsString("\"fullName\":\"Kai Toedter\""));
    }

    @Test
    public void should_push_and_receive_single_message() throws Exception {

        final StringBuilder receivedMessage = new StringBuilder();

        final CountDownLatch latch = new CountDownLatch(1);

        AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(BASE_URI + "/chatty/atmos/chat")
                .trackMessageLength(true)
                .transport(atmosphereTransport);

        Socket socket = client.create();
        socket.on(new Function<Request.TRANSPORT>() {
            @Override
            public void on(Request.TRANSPORT t) {
                logger.info("Using transport: " + t);
            }
        }).on(new Function<String>() {
            @Override
            public void on(String message) {
                receivedMessage.append(message);
                latch.countDown();
            }
        }).on(new Function<IOException>() {

            @Override
            public void on(IOException e) {
                fail(e.getMessage());
            }

        }).open(request.build()).fire("hello");


        latch.await(1, TimeUnit.SECONDS);
        socket.close();
        assertThat(receivedMessage.toString(), is("hello"));
    }

    @Test
    public void should_post_and_receive_single_message() throws Exception {

        final StringBuilder receivedMessage = new StringBuilder();

        final CountDownLatch latch = new CountDownLatch(1);

        AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(BASE_URI + "/chatty/atmos/messages")
                .trackMessageLength(true)
                .transport(atmosphereTransport);

        Socket socket = client.create();
        // TODO Check why Java 8 Lambdas don't work here
        // TODO Try to deserialize a ChatMessage from a JSON+HAL string
        socket.on(new Function<String>() {
            @Override
            public void on(String message) {
                receivedMessage.append(message);
                latch.countDown();
            }
        }).on(new Function<IOException>() {

            @Override
            public void on(IOException e) {
                fail(e.getMessage());
            }

        }).open(request.build());

        SimpleUser author = new SimpleUser("author-id", "The Author", "author@test.com");
        ChatMessage chatMessage = new SimpleChatMessage(author, "hello Jersey");

        target.path("/api/messages").request(RepresentationFactory.HAL_JSON).post(Entity
                .entity(chatMessage, MediaType.APPLICATION_JSON_TYPE), SimpleChatMessage.class);
        latch.await(1, TimeUnit.SECONDS);
        socket.close();

        assertThat(receivedMessage.toString(), containsString("hello Jersey"));
    }

}
