package com.dreikraft.vertx.template.handlebars;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.TemplateSource;
import com.github.jknack.handlebars.io.URLTemplateSource;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.file.FileProps;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.ConcurrentMap;

/**
 * Compiles and applies Handlebar Templates. The compile action blocks, and therefore should run in the worker event
 * loop.
 */
public class HandlebarsVerticle extends Verticle {

    public static final String ADDRESS_BASE = HandlebarsVerticle.class.getName();
    public static final String ADDRESS_COMPILE_FILE = ADDRESS_BASE + "/compileFile";
    public static final String ADDRESS_RENDER_FILE = ADDRESS_BASE + "/renderFile";
    public static final String ADDRESS_FLUSH = ADDRESS_BASE + "/flush";
    public static final long REPLY_TIMEOUT = 5 * 1000;
    public static final String HANDLEBAR_TEMPLATES_CACHE = "handlebar.templates.cache";
    private static final int ERR_CODE_BASE = 100;
    private static final int ERR_CODE_TMPL_COMPILE_FAILED = ERR_CODE_BASE;
    private static final String ERR_MSG_TMPL_COMPILE_FAILED = "failed to compile template: %1$s";
    private static final int ERR_CODE_RENDER_FAILED = ERR_CODE_BASE + 1;
    private static final String ERR_MSG_RENDER_FAILED = "failed to render template %1$s with data %2$s";
    private static final String FIELD_TEMPLATE_LOCATION = "templateLocation";
    private static final String FIELD_DATA = "data";
    private static final String ERR_MSG_TEMPLATE_NOT_FOUND = "template not found %1$s";
    private static final int ERR_CODE_TEMPLATE_NOT_FOUND = ERR_CODE_BASE + 2;
    private EventBus eb;
    private Handlebars handlebars;
    private ConcurrentMap<String, SharedTemplate> templateCache;
    private Logger logger;

    /**
     * Initialize the handlebar template handlers on the eventbus. Following handlers are registered:
     * <p/>
     * <ul>
     * <li>com.dreikraft.vertx.template.handlebars.HandlebarsVerticle/render
     * <p>renders a template with the given data:
     * <code>{"templateLocation": "templates/hello.hbs", "data": {...}}</code></p>
     * </li>
     * <li>com.dreikraft.vertx.template.handlebars.HandlebarsVerticle/compile
     * <p>compiles a template with the given location on the classpath: "templates/hello.hbs"</p>
     * </li>
     * <li>com.dreikraft.vertx.template.handlebars.HandlebarsVerticle/clean
     * <p>Flushes the shared template cache</p>
     * </li>
     * </ul>
     * <p/>
     * Compiled templates are stored in a shared template cache. The verticle will check, if the template in the cache
     * is up-to-date based on its last-modified date.
     */
    @Override
    public void start() {

        // initialize logger
        logger = container.logger();
        logger.info(String.format("starting %1$s ...", this.getClass().getSimpleName()));

        // initilialize members
        handlebars = new Handlebars();
        templateCache = vertx.sharedData().getMap(HANDLEBAR_TEMPLATES_CACHE);
        eb = vertx.eventBus();

        // register event handlers
        logger.info(String.format("registering handler %1$s", ADDRESS_COMPILE_FILE));
        eb.registerHandler(ADDRESS_COMPILE_FILE, new CompileFileMessageHandler());
        logger.info(String.format("registering handler %1$s", ADDRESS_RENDER_FILE));
        eb.registerHandler(ADDRESS_RENDER_FILE, new RenderFileMessageHandler());
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
            vertx.fileSystem().props(templateLocation, new TemplateUpToDateHandler(sharedTemplate, renderMsg,
                    templateLocation));
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
                renderMsg.reply(sharedTemplate.getTemplate().apply(data.toMap()));
            } catch (IOException e) {
                logger.error(ERR_CODE_RENDER_FAILED, e);
                renderMsg.fail(ERR_CODE_RENDER_FAILED, String.format(ERR_MSG_RENDER_FAILED,
                        renderMsg.body().getString(FIELD_TEMPLATE_LOCATION), data.encode()));
            }
        }

        /**
         * Handles the result from template compilation.
         */
        private class CompileResultHandler implements AsyncResultHandler<Message<Void>> {
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
            public void handle(AsyncResult<Message<Void>> compileResultMsg) {
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
                        eb.sendWithTimeout(ADDRESS_COMPILE_FILE, templateLocation, REPLY_TIMEOUT,
                                new CompileResultHandler(templateLocation, renderMsg));

                    }
                } else {
                    renderMsg.fail(ERR_CODE_TEMPLATE_NOT_FOUND,
                            String.format(ERR_MSG_TEMPLATE_NOT_FOUND, templateLocation));
                }
            }
        }
    }

    /**
     * Compiles a template from given file and stores it into a shared map.
     */
    private class CompileFileMessageHandler implements Handler<Message<String>> {

        /**
         * Handles compile messages. Puts the compiled template (SharedTemplate.class) into the shared map
         * "handlebar.templates.cache".
         *
         * @param compileMsg a string message with the location of the template in the classpath.
         */
        @Override
        public void handle(final Message<String> compileMsg) {
            if (logger.isDebugEnabled())
                logger.debug(String.format("address %1$s received message: %2$s", compileMsg.address(),
                        compileMsg.body()));
            final String templateLocation = compileMsg.body();
            final URL templateURL = Thread.currentThread().getContextClassLoader().getResource(templateLocation);
            try {
                final TemplateSource templateSource = new URLTemplateSource(templateLocation, templateURL);
                final Template template = handlebars.compile(new URLTemplateSource(templateLocation, templateURL));
                final SharedTemplate sharedTemplate = new SharedTemplate(template,
                        new Date(templateSource.lastModified()));
                templateCache.put(templateLocation, sharedTemplate);
                compileMsg.reply();
            } catch (IOException e) {
                compileMsg.fail(ERR_CODE_TMPL_COMPILE_FAILED, String.format(ERR_MSG_TMPL_COMPILE_FAILED,
                        templateLocation));
            }
        }
    }

    /**
     * A Handler for flush messages on the event bus.
     */
    private class FlushMessageHandler implements Handler<Message<Void>> {

        /**
         * Flushes the shared template cache.
         *
         * @param flushMessage the flush message
         */
        @Override
        public void handle(Message<Void> flushMessage) {
            if (logger.isDebugEnabled())
                logger.debug(String.format("address %1$s received message",
                        flushMessage.address()));
            templateCache.clear();
            flushMessage.reply();
        }
    }
}
