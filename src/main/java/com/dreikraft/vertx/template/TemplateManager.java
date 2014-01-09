package com.dreikraft.vertx.template;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonObject;

/**
 * Created by jan_solo on 09.01.14.
 */
public interface TemplateManager {
    void render(final String template, final JsonObject data, final Handler<AsyncResult<Buffer>> renderHandler);
}
