package com.dreikraft.vertx.template.handlebars;

import com.dreikraft.vertx.AsyncResultWrapper;
import com.dreikraft.vertx.template.TemplateManager;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonObject;

import java.io.IOException;

/**
 * Created by jan_solo on 09.01.14.
 */
public class HandlebarsTemplateManager implements TemplateManager {

    private Vertx vertx;

    public HandlebarsTemplateManager(final Vertx vertx) {
        this.vertx = vertx;    }

    @Override
    public void render(final String template, final JsonObject data, final Handler<AsyncResult<Buffer>> renderHandler) {
        vertx.fileSystem().readFile(template, new AsyncResultHandler<Buffer>() {
            @Override
            public void handle(AsyncResult<Buffer> fileReadResult) {
                if (fileReadResult.failed()) {
                    renderHandler.handle(new AsyncResultWrapper(fileReadResult.cause()));
                } else {
                    try {
                        final Template template = new Handlebars().compileInline(fileReadResult.result().toString("UTF-8"));
                        renderHandler.handle(new AsyncResultWrapper<>(new Buffer(template.apply(data.toMap()))));
                    } catch (IOException ex) {
                        renderHandler.handle(new AsyncResultWrapper<>(ex));
                    }
                }
            }
        });
    }
}
