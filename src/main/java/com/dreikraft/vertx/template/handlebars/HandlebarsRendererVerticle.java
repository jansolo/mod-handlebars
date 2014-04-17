package com.dreikraft.vertx.template.handlebars;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.file.FileProps;
import org.vertx.java.core.json.JsonObject;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;

/**
 * Applies data to Handlebar Templates. Will invoke template compilation, if the template is not already compiled. The
 * compilation is passed to the {@link HandlebarsCompilerVerticle} which blocks and therefore runs in the worker pool.
 */
public class HandlebarsRendererVerticle extends BusModBase {

    /**
     * The event bus base address of this verticle.
     */
    public static final String ADDRESS_BASE = HandlebarsRendererVerticle.class.getName();
    /**
     * The event bus address to render a template.
     */
    public static final String ADDRESS_RENDER_FILE = ADDRESS_BASE + "/render";
    /**
     * The event bus address to flush the shared template cache.
     */
    public static final String ADDRESS_FLUSH = ADDRESS_BASE + "/flush";
    /**
     * JSON property name "templateLocation" (String).
     */
    public static final String FIELD_TEMPLATE_LOCATION = "templateLocation";
    /**
     * JSON property name "data" (JsonObject).
     */
    public static final String FIELD_DATA = "data";
    /**
     * JSON property name "renderResult" (String).
     */
    public static final String FIELD_RENDER_RESULT = "renderResult";
    /**
     * JSON property name "autoUpdate" (true/false)
     */
    public static final String CONFIG_AUTO_UPDATE = "autoUpdate";
    /**
     * The name of the shared cache.
     */
    public static final String HANDLEBAR_TEMPLATES_CACHE = "handlebar.templates.cache";

    private static final String ERR_MSG_RENDER_FAILED = "failed to render template %1$s with data %2$s";
    private static final String ERR_MSG_TEMPLATE_NOT_FOUND = "template not found %1$s";
    private ConcurrentMap<String, SharedTemplate> templateCache;

    /**
     * Initialize the handlebar template handlers on the eventbus. Following handlers are registered:
     * <ul>
     * <li><code>com.dreikraft.vertx.template.handlebars.HandlebarsRendererVerticle/render</code> ...
     * renders a template with the given data:
     * <code>{"templateLocation": "templates/hello.hbs", "data": {...}}</code>
     * </li>
     * <li><code>com.dreikraft.vertx.template.handlebars.HandlebarsRendererVerticle/flush</code> ...
     * Flushes the shared template cache
     * </li>
     * </ul>
     * <p>
     * Compiled templates are stored in a shared template cache. The verticle will check, if the template in the cache
     * is up-to-date based on its last-modified date.
     */
    @Override
    public void start() {

        // initialize the busmod
        super.start();

        // initilialize members
        templateCache = vertx.sharedData().getMap(HANDLEBAR_TEMPLATES_CACHE);

        // register event handlers
        logger.info(String.format("registering handler %1$s", ADDRESS_RENDER_FILE));
        eb.registerHandler(ADDRESS_RENDER_FILE, new RenderFileMessageHandler());

        // register flush handler
        logger.info(String.format("registering handler %1$s", ADDRESS_FLUSH));
        eb.registerHandler(ADDRESS_FLUSH, new FlushMessageHandler());
    }

    /**
     * Handler for applying data to a handlebar template.
     */
    private class RenderFileMessageHandler implements Handler<Message<JsonObject>> {

        /**
         * Takes a templateLocation and renders the applied JSON data into a string. The result is returned as reply
         * string.
         *
         * @param renderMsg a JSON message of the form "{"templateLocation": "templates/template.hbs", "data": "{...}"}"
         */
        @Override
        public void handle(final Message<JsonObject> renderMsg) {
            String templateLocation = null;
            try {
                if (logger.isDebugEnabled())
                    logger.debug(String.format("address %1$s received message: %2$s", renderMsg.address(),
                            renderMsg.body().encodePrettily()));
                final JsonObject renderCtx = renderMsg.body();
                templateLocation = renderCtx.getString(FIELD_TEMPLATE_LOCATION);
                final SharedTemplate sharedTemplate = templateCache.get(templateLocation);
                if (getOptionalBooleanConfig(CONFIG_AUTO_UPDATE, true)) {
                    vertx.fileSystem().props(templateLocation, new TemplateUpToDateHandler(sharedTemplate, renderMsg,
                            templateLocation));
                } else {
                    sendOK(renderMsg, render(sharedTemplate, renderMsg));
                }
            } catch (RuntimeException | IOException ex) {
                final String errMsg =
                        String.format(ERR_MSG_RENDER_FAILED, templateLocation, renderMsg.body().encode());
                sendError(renderMsg, errMsg, ex);
            }
        }

        /**
         * Renders the template with the given Json data into a String
         *
         * @param sharedTemplate a shared template instance
         * @param renderMsg      a Json Message with the "data" JsonObject and the "templateLocation" as string
         */
        private JsonObject render(final SharedTemplate sharedTemplate, final Message<JsonObject> renderMsg)
                throws IOException {
            final JsonObject data = renderMsg.body().getObject(FIELD_DATA);
            return new JsonObject().putString(FIELD_RENDER_RESULT, sharedTemplate.getTemplate().apply(data.toMap()));
        }

        /**
         * Handles the result from template compilation.
         */
        private class CompileResultHandler implements Handler<Message<JsonObject>> {
            private final String templateLocation;
            private final Message<JsonObject> renderMsg;

            /**
             * Initialize the CompileResultHandler.
             *
             * @param templateLocation the location of the template in the classpath
             * @param renderMsg        the render request received on the event bus
             */
            public CompileResultHandler(String templateLocation, Message<JsonObject> renderMsg) {
                this.templateLocation = templateLocation;
                this.renderMsg = renderMsg;
            }

            /**
             * If compilation was successful applies the data on the template.
             *
             * @param compileResultMsg returns the rendered page
             */
            @Override
            public void handle(final Message<JsonObject> compileResultMsg) {

                try {
                    if ("ok".equals(compileResultMsg.body().getString("status"))) {
                        sendOK(renderMsg, render(templateCache.get(templateLocation), renderMsg));
                    } else {
                        sendError(renderMsg, ERR_MSG_RENDER_FAILED);
                    }
                } catch (RuntimeException | IOException ex) {
                    final String errMsg =
                            String.format(ERR_MSG_RENDER_FAILED, templateLocation, renderMsg.body().encode());
                    sendError(renderMsg, errMsg, ex);
                }
            }
        }

        /**
         * Checks whether the requested template is up-to-date in the shared template cache by the last modified date.
         */
        private class TemplateUpToDateHandler implements AsyncResultHandler<FileProps> {
            private final SharedTemplate sharedTemplate;
            private final Message<JsonObject> renderMsg;
            private final String templateLocation;

            /**
             * Initializes the TemplateUpToDateHandler.
             *
             * @param sharedTemplate   a sharedTemplate instance or null
             * @param renderMsg        the render request received on the event bus
             * @param templateLocation the location of the template in the classpath
             */
            public TemplateUpToDateHandler(SharedTemplate sharedTemplate, Message<JsonObject> renderMsg,
                                           String templateLocation) {
                this.sharedTemplate = sharedTemplate;
                this.renderMsg = renderMsg;
                this.templateLocation = templateLocation;
            }

            /**
             * Handles the last modified date check. It invokes the template compilation if the template is outdated.
             *
             * @param templatePropsResult the template file properties
             */
            @Override
            public void handle(AsyncResult<FileProps> templatePropsResult) {
                try {
                    final FileProps templateProps = templatePropsResult.result();
                    if (sharedTemplate != null && !templateProps.lastModifiedTime().after(sharedTemplate
                            .getTimestamp())) {
                        sendOK(renderMsg, render(sharedTemplate, renderMsg));
                    } else {
                        logger.info(String.format("template %1$s is out of date and will be compiled",
                                templateLocation));
                        eb.send(HandlebarsCompilerVerticle.ADDRESS_COMPILE_FILE,
                                new JsonObject().putString(HandlebarsRendererVerticle.FIELD_TEMPLATE_LOCATION,
                                        templateLocation), new CompileResultHandler(templateLocation, renderMsg)
                        );
                    }
                } catch (RuntimeException | IOException ex) {
                    sendError(renderMsg, String.format(ERR_MSG_TEMPLATE_NOT_FOUND, templateLocation), ex);
                }
            }
        }
    }

    /**
     * A Handler for flush messages on the event bus.
     */
    private class FlushMessageHandler implements Handler<Message<JsonObject>> {

        /**
         * Flushes the shared template cache.
         *
         * @param flushMessage the flush message
         */
        @Override
        public void handle(Message<JsonObject> flushMessage) {
            try {
                logger.info("flushing handlebars template cache");
                templateCache.clear();
                sendOK(flushMessage);
            } catch (RuntimeException ex) {
                sendError(flushMessage, "failed to flush handlebars template cache", ex);
            }
        }
    }
}
