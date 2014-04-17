package com.dreikraft.vertx.template;

import com.dreikraft.vertx.template.handlebars.HandlebarsCompilerVerticle;
import com.dreikraft.vertx.template.handlebars.HandlebarsRendererVerticle;
import com.dreikraft.vertx.template.handlebars.SharedTemplate;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
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
public class HandlebarsModuleTest extends TestVerticle {

    private static final String HELLO_WORLD = "hello world!";

    /**
     * Initialize the vertx container for testing.
     */
    @Override
    public void start() {

        initialize();

        container.logger().info("starting HandlebarsRendererVerticle tests ...");
        container.deployModule(System.getProperty("vertx.modulename"), new AsyncResultHandler<String>() {
            @Override
            public void handle(AsyncResult<String> asyncResult) {
                container.logger().info("started HandlebarsRendererVerticle tests");
                // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
                if (asyncResult.failed()) {
                    container.logger().error(asyncResult.cause().getMessage(), asyncResult.cause());
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

        final JsonObject data = new JsonObject().putString("text", "world");
        final JsonObject msg = new JsonObject().putString("templateLocation", "templates/hello.hbs")
                .putObject("data", data);

        // needs to compile template
        vertx.eventBus().send(HandlebarsRendererVerticle.ADDRESS_RENDER_FILE, msg,
                new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(final Message<JsonObject> renderResult) {
                        try {
                            VertxAssert.assertEquals("ok", renderResult.body().getString("status"));
                            VertxAssert.assertEquals(HELLO_WORLD, renderResult.body().getString(
                                    HandlebarsRendererVerticle.FIELD_RENDER_RESULT));
                        } catch (RuntimeException ex) {
                            VertxAssert.fail(ex.getMessage());
                        }
                        VertxAssert.testComplete();
                    }
                }
        );
    }

    /**
     * Tests the compilation of a template
     */
    @Test
    public void testCompile() {

        final String templateLocation = "templates/hello.hbs";

        vertx.eventBus().send(HandlebarsCompilerVerticle.ADDRESS_COMPILE_FILE,
                new JsonObject().putString(HandlebarsRendererVerticle.FIELD_TEMPLATE_LOCATION, templateLocation),
                new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(final Message<JsonObject> compileResult) {
                        try {
                            VertxAssert.assertEquals("ok", compileResult.body().getString("status"));
                            VertxAssert.assertNotNull(vertx.sharedData().getMap(
                                    HandlebarsRendererVerticle.HANDLEBAR_TEMPLATES_CACHE).get(templateLocation));
                        } catch (RuntimeException ex) {
                            VertxAssert.fail(ex.getMessage());
                        }
                        VertxAssert.testComplete();
                    }
                }
        );
    }

    /**
     * Test flushing of the template cache.
     */
    @Test
    public void testFlush() {

        final String templateLocation = "templates/hello.hbs";
        vertx.eventBus().send(HandlebarsCompilerVerticle.ADDRESS_COMPILE_FILE,
                new JsonObject().putString("templateLocation", "templates/hello.hbs"),
                new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(final Message<JsonObject> compileResult) {
                        try {
                            VertxAssert.assertEquals("ok", compileResult.body().getString("status"));
                            VertxAssert.assertNotNull(vertx.sharedData().getMap(HandlebarsRendererVerticle
                                    .HANDLEBAR_TEMPLATES_CACHE).get(templateLocation));
                            vertx.eventBus().send(HandlebarsRendererVerticle.ADDRESS_FLUSH, new JsonObject(),
                                    new Handler<Message<JsonObject>>() {

                                        @Override
                                        public void handle(final Message<JsonObject> flushResult) {
                                            try {
                                                VertxAssert.assertEquals("ok", flushResult.body().getString("status"));
                                                VertxAssert.assertNull(vertx.sharedData().getMap(
                                                        HandlebarsRendererVerticle.HANDLEBAR_TEMPLATES_CACHE).get
                                                        (templateLocation));
                                            } catch (RuntimeException ex) {
                                                VertxAssert.fail(ex.getMessage());
                                            }
                                            VertxAssert.testComplete();
                                        }
                                    }
                            );
                        } catch (RuntimeException ex) {
                            VertxAssert.fail(ex.getMessage());
                            VertxAssert.testComplete();
                        }
                    }
                }
        );
    }

    /**
     * Tests the rendering of a template.
     */
    @Test
    public void testOutDated() {

        final String templateLocation = "templates/hello.hbs";
        vertx.eventBus().send(HandlebarsCompilerVerticle.ADDRESS_COMPILE_FILE,
                new JsonObject().putString("templateLocation", templateLocation), new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(final Message<JsonObject> compileResult) {
                        try {
                            VertxAssert.assertEquals("ok", compileResult.body().getString("status"));
                            VertxAssert.assertNotNull(vertx.sharedData().getMap(HandlebarsRendererVerticle.HANDLEBAR_TEMPLATES_CACHE)
                                    .get(templateLocation));
                            final Path templateLocationPath = Paths.get(Thread.currentThread().getContextClassLoader()
                                    .getResource(templateLocation).toURI());
                            Files.setLastModifiedTime(templateLocationPath, FileTime.fromMillis(
                                    System.currentTimeMillis()));
                            final JsonObject data = new JsonObject().putString("text", "world");
                            final JsonObject msg = new JsonObject().putString("templateLocation", templateLocation)
                                    .putObject("data", data);

                            // needs to compile template
                            vertx.eventBus().send(HandlebarsRendererVerticle.ADDRESS_RENDER_FILE, msg,
                                    new Handler<Message<JsonObject>>() {
                                        @Override
                                        public void handle(final Message<JsonObject> renderResult) {
                                            try {
                                                VertxAssert.assertEquals("ok", renderResult.body().getString("status"));
                                                VertxAssert.assertEquals(HELLO_WORLD, renderResult.body().getString
                                                        (HandlebarsRendererVerticle.FIELD_RENDER_RESULT));
                                                final SharedTemplate sharedTemplate = (SharedTemplate) vertx.sharedData().getMap
                                                        (HandlebarsRendererVerticle.HANDLEBAR_TEMPLATES_CACHE).get(templateLocation);
                                                VertxAssert.assertEquals(Files.getLastModifiedTime(templateLocationPath)
                                                        .toMillis(), sharedTemplate.getTimestamp().getTime());
                                            } catch (IOException | RuntimeException ex) {
                                                VertxAssert.fail(ex.getMessage());
                                            }
                                            VertxAssert.testComplete();
                                        }
                                    }
                            );
                        } catch (RuntimeException | IOException | URISyntaxException ex) {
                            VertxAssert.fail(ex.getMessage());
                            VertxAssert.testComplete();
                        }
                    }
                }
        );
    }
}
