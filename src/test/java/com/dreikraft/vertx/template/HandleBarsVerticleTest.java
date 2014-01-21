package com.dreikraft.vertx.template;

import com.dreikraft.vertx.template.handlebars.HandlebarsVerticle;
import com.dreikraft.vertx.template.handlebars.SharedTemplate;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;

/**
 * Intergration tests for HandlebarVerticle.
 */
public class HandlebarsVerticleTest extends TestVerticle {

    private static final String HELLO_WORLD = "hello world!";

    /**
     * Initialize the vertx container for testing.
     */
    @Override
    public void start() {

        initialize();

        container.logger().info("starting HandlebarsVerticle tests ...");
        container.deployModule(System.getProperty("vertx.modulename"), new AsyncResultHandler<String>() {
            @Override
            public void handle(AsyncResult<String> asyncResult) {
                // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
                if (asyncResult.failed()) {
                    container.logger().error(asyncResult.cause());
                }
                VertxAssert.assertTrue(asyncResult.succeeded());
                VertxAssert.assertNotNull("deploymentID should not be null", asyncResult.result());
                // If deployed correctly then start the tests!
                startTests();
            }
        });
    }

    /**
     * Tests the rendering of a template.
     */
    @Test
    public void testRender() {

        final JsonObject data = new JsonObject();
        data.putString("text", "world");
        final JsonObject msg = new JsonObject();
        msg.putString("templateLocation", "templates/hello.hbs");
        msg.putObject("data", data);

        // needs to compile template
        vertx.eventBus().sendWithTimeout(HandlebarsVerticle.ADDRESS_RENDER_FILE, msg,
                HandlebarsVerticle.REPLY_TIMEOUT, new AsyncResultHandler<Message<String>>() {
            @Override
            public void handle(AsyncResult<Message<String>> renderResult) {
                if (renderResult.succeeded()) {
                    VertxAssert.assertEquals(HELLO_WORLD, renderResult.result().body());
                } else {
                    ReplyException ex = (ReplyException) renderResult.cause();
                    VertxAssert.fail(ex.getMessage());
                }
                VertxAssert.testComplete();
            }
        });
    }

    /**
     * Tests the compilation of a template
     */
    @Test
    public void testCompile() {

        final String templateLocation = "templates/hello.hbs";
        vertx.eventBus().sendWithTimeout(HandlebarsVerticle.ADDRESS_COMPILE_FILE, templateLocation, HandlebarsVerticle.REPLY_TIMEOUT, new AsyncResultHandler<Message<Void>>() {

            @Override
            public void handle(AsyncResult<Message<Void>> compileResult) {
                if (compileResult.succeeded()) {
                    VertxAssert.assertNotNull(vertx.sharedData().getMap(HandlebarsVerticle.HANDLEBAR_TEMPLATES_CACHE).get
                            (templateLocation));
                } else {
                    VertxAssert.fail(compileResult.cause().getMessage());
                }
                VertxAssert.testComplete();
            }
        });
    }

    /**
     * Test flushing of the template cache.
     */
    @Test
    public void testFlush() {

        final String templateLocation = "templates/hello.hbs";
        vertx.eventBus().sendWithTimeout(HandlebarsVerticle.ADDRESS_COMPILE_FILE, templateLocation, HandlebarsVerticle.REPLY_TIMEOUT, new AsyncResultHandler<Message<Void>>() {

            @Override
            public void handle(AsyncResult<Message<Void>> compileResult) {
                if (compileResult.succeeded()) {
                    VertxAssert.assertNotNull(vertx.sharedData().getMap(HandlebarsVerticle.HANDLEBAR_TEMPLATES_CACHE).get
                            (templateLocation));
                    vertx.eventBus().sendWithTimeout(HandlebarsVerticle.ADDRESS_FLUSH,
                            (Object) null, HandlebarsVerticle.REPLY_TIMEOUT, new AsyncResultHandler<Message<Void>>() {

                        @Override
                        public void handle(AsyncResult<Message<Void>> flushResult) {
                            if (flushResult.succeeded()) {
                                VertxAssert.assertNull(vertx.sharedData().getMap(HandlebarsVerticle.HANDLEBAR_TEMPLATES_CACHE).get
                                        (templateLocation));
                            } else {
                                VertxAssert.fail(flushResult.cause().getMessage());
                            }
                            VertxAssert.testComplete();
                        }
                    });

                } else {
                    VertxAssert.fail(compileResult.cause().getMessage());
                    VertxAssert.testComplete();
                }
            }
        });
    }

    /**
     * Tests the rendering of a template.
     */
    @Test
    public void testOutDated() {

        final String templateLocation = "templates/hello.hbs";
        vertx.eventBus().sendWithTimeout(HandlebarsVerticle.ADDRESS_COMPILE_FILE, templateLocation, HandlebarsVerticle.REPLY_TIMEOUT, new AsyncResultHandler<Message<Void>>() {

            @Override
            public void handle(AsyncResult<Message<Void>> compileResult) {
                if (compileResult.succeeded()) {
                    VertxAssert.assertNotNull(vertx.sharedData().getMap(HandlebarsVerticle.HANDLEBAR_TEMPLATES_CACHE).get
                            (templateLocation));
                    try {
                        final Path templateLocationPath = Paths.get(Thread.currentThread().getContextClassLoader().getResource(templateLocation).toURI());
                        Files.setLastModifiedTime(templateLocationPath, FileTime.fromMillis(System.currentTimeMillis()));
                        container.logger().info(Files.getLastModifiedTime(templateLocationPath));
                        final JsonObject data = new JsonObject();
                        data.putString("text", "world");
                        final JsonObject msg = new JsonObject();
                        msg.putString("templateLocation", templateLocation);
                        msg.putObject("data", data);

                        // needs to compile template
                        vertx.eventBus().sendWithTimeout(HandlebarsVerticle.ADDRESS_RENDER_FILE, msg,
                                HandlebarsVerticle.REPLY_TIMEOUT, new AsyncResultHandler<Message<String>>() {
                            @Override
                            public void handle(AsyncResult<Message<String>> renderResult) {
                                if (renderResult.succeeded()) {
                                    VertxAssert.assertEquals(HELLO_WORLD, renderResult.result().body());
                                    final SharedTemplate sharedTemplate = (SharedTemplate) vertx.sharedData().getMap
                                            (HandlebarsVerticle.HANDLEBAR_TEMPLATES_CACHE).get(templateLocation);
                                    try {
                                        VertxAssert.assertEquals(Files.getLastModifiedTime(templateLocationPath).toMillis
                                                (), sharedTemplate.getTimestamp().getTime());
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        VertxAssert.fail(e.getMessage());
                                    }
                                } else {
                                    ReplyException ex = (ReplyException) renderResult.cause();
                                    VertxAssert.fail(ex.getMessage());
                                }
                                VertxAssert.testComplete();
                            }
                        });

                    } catch (IOException | URISyntaxException e) {
                        e.printStackTrace();
                        VertxAssert.fail(e.getMessage());
                        VertxAssert.testComplete();
                    }
                } else {
                    VertxAssert.fail(compileResult.cause().getMessage());
                    VertxAssert.testComplete();
                }
            }
        });
    }
}
