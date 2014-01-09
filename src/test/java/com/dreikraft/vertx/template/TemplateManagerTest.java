package com.dreikraft.vertx.template;

import com.dreikraft.vertx.template.handlebars.HandlebarsTemplateManager;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;

/**
 * Created by jan_solo on 09.01.14.
 */
public class TemplateManagerTest extends TestVerticle {

    @Test
    public void simpleTemplateTest() {
        vertx.runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void event) {
                TemplateManager mgr = new HandlebarsTemplateManager(vertx);
                mgr.render("hello.hbs", new JsonObject("{\"text\": \"world\"}"), new Handler<AsyncResult<Buffer>>() {
                    @Override
                    public void handle(AsyncResult<Buffer> renderResult) {
                        if (renderResult.failed()) {
                            VertxAssert.fail(renderResult.cause().getMessage());
                        } else {
                            VertxAssert.assertEquals("hello world!", renderResult.result().toString("UTF-8"));
                        }
                        VertxAssert.testComplete();
                    }
                });
            }
        });
    }
}
