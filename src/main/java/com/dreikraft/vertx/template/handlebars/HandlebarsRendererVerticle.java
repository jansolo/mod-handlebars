package com.dreikraft.vertx.template.handlebars;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.file.FileProps;
import org.vertx.java.core.json.JsonObject;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;

/**
 * Applies data to Handlebar Templates. Will invoke template compilation, if the template is not already compiled. The
 * compilation is passed to the {@link HandlebarsCompilerVerticle} which blocks and therefore runs in the worker pool.
 */
public class HandlebarsRendererVerticle extends BusModBase {

    public static final String ADDRESS_BASE = HandlebarsRendererVerticle.class.getName();
    public static final String ADDRESS_RENDER_FILE = ADDRESS_BASE + "/renderFile";
    public static final String ADDRESS_FLUSH = ADDRESS_BASE + "/flush";
    public static final int ERR_CODE_BASE = 500;
    public static final String FIELD_TEMPLATE_LOCATION = "templateLocation";
    public static final String FIELD_DATA = "data";
    public static final String FIELD_RENDER_RESULT = "renderResult";
    public static final String CONFIG_AUTO_UPDATE = "autoUpdate";
    public static final String HANDLEBAR_TEMPLATES_CACHE = "handlebar.templates.cache";
    private static final String ERR_MSG_RENDER_FAILED = "failed to render template %1$s with data %2$s";
    private static final String ERR_MSG_TEMPLATE_NOT_FOUND = "template not found %1$s";
    private ConcurrentMap<String, SharedTemplate> templateCache;

    /**
     * Initialize the handlebar template handlers on the eventbus. Following handlers are registered:
     * <p/>
     * <ul>
     * <li>com.dreikraft.vertx.template.handlebars.HandlebarsRendererVerticle/render
     * <p>renders a template with the given data:
     * <code>{"templateLocation": "templates/hello.hbs", "data": {...}}</code></p>
     * </li>
     * <li>com.dreikraft.vertx.template.handlebars.HandlebarsRendererVerticle/flush
     * <p>Flushes the shared template cache</p>
     * </li>
     * <p/>
     * Compiled templates are stored in a shared template cache. The verticle will check, if the template in the cache
     * is up-to-date based on its last-modified date.
     */
    @Override
    public void start() {

        // initialize the busmod
        super.start();
        logger.info(String.format("starting %1$s ...", this.getClass().getSimpleName()));

        // initilialize members
        templateCache = vertx.sharedData().getMap(HANDLEBAR_TEMPLATES_CACHE);

        // register event handlers
        logger.info(String.format("registering handler %1$s", ADDRESS_RENDER_FILE));
        eb.registerHandler(ADDRESS_RENDER_FILE, new RenderFileMessageHandler());

        // register flush handler
        logger.info(String.format("registering handler %1$s", ADDRESS_FLUSH));
        eb.registerHandler(ADDRESS_FLUSH, new FlushMessageHandler());

        logger.info(String.format("successfully started %1$s", this.getClass().getSimpleName()));
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
            if (logger.isDebugEnabled())
                logger.debug(String.format("address %1$s received message: %2$s", renderMsg.address(),
                        renderMsg.body().encodePrettily()));
            final JsonObject renderCtx = renderMsg.body();
            final String templateLocation = renderCtx.getString(FIELD_TEMPLATE_LOCATION);
            final SharedTemplate sharedTemplate = templateCache.get(templateLocation);
            if (getOptionalBooleanConfig(CONFIG_AUTO_UPDATE, true)) {
                vertx.fileSystem().props(templateLocation, new TemplateUpToDateHandler(sharedTemplate, renderMsg,
                        templateLocation));
            } else {
                render(sharedTemplate, renderMsg);
            }
        }

        /**
         * Renders the template with the given Json data into a String
         *
         * @param sharedTemplate a shared template instance
         * @param renderMsg      a Json Message with the "data" JsonObject and the "templateLocation" as string
         */
        private void render(final SharedTemplate sharedTemplate, final Message<JsonObject> renderMsg) {
            final JsonObject data = renderMsg.body().getObject(FIELD_DATA);
            try {
                sendOK(renderMsg, new JsonObject().putString(FIELD_RENDER_RESULT,
                        sharedTemplate.getTemplate().apply(data.toMap())));
            } catch (IOException e) {
                final String msg = String.format(ERR_MSG_RENDER_FAILED,
                        renderMsg.body().getString(FIELD_TEMPLATE_LOCATION), data.encode());
                logger.error(msg, e);
                renderMsg.fail(ERR_CODE_BASE, msg);
            }
        }

        /**
         * Handles the result from template compilation.
         */
        private class CompileResultHandler implements AsyncResultHandler<Message<JsonObject>> {
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
            public void handle(AsyncResult<Message<JsonObject>> compileResultMsg) {
                if (compileResultMsg.succeeded()) {
                    render(templateCache.get(templateLocation), renderMsg);
                } else {
                    final ReplyException ex = (ReplyException) compileResultMsg.cause();
                    renderMsg.fail(ex.failureCode(), ex.getMessage());
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
                if (templatePropsResult.succeeded()) {
                    final FileProps templateProps = templatePropsResult.result();
                    if (sharedTemplate != null && !templateProps.lastModifiedTime().after(sharedTemplate
                            .getTimestamp())) {
                        render(sharedTemplate, renderMsg);
                    } else {
                        logger.info(String.format("template %1$s is out of date and will be compiled",
                                templateLocation));
                        eb.sendWithTimeout(HandlebarsCompilerVerticle.ADDRESS_COMPILE_FILE,
                                new JsonObject().putString(HandlebarsRendererVerticle.FIELD_TEMPLATE_LOCATION,
                                        templateLocation),
                                30 * 1000, new CompileResultHandler(templateLocation, renderMsg));

                    }
                } else {
                    renderMsg.fail(ERR_CODE_BASE, String.format(ERR_MSG_TEMPLATE_NOT_FOUND, templateLocation));
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
            if (logger.isDebugEnabled())
                logger.debug(String.format("address %1$s received message",
                        flushMessage.address()));
            templateCache.clear();
            sendOK(flushMessage);
        }
    }
}
