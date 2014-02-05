package com.dreikraft.vertx.template.handlebars;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.TemplateSource;
import com.github.jknack.handlebars.io.URLTemplateSource;
import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.ConcurrentMap;

/**
 * Compiles Handlebar Templates. The compile action blocks, and therefore should run in the worker event loop.
 */
public class HandlebarsCompilerVerticle extends BusModBase {

    /**
     * The event bus base address of this verticle.
     */
    public static final String ADDRESS_BASE = HandlebarsCompilerVerticle.class.getName();

    /**
     * The event bus address to compile a template.
     */
    public static final String ADDRESS_COMPILE_FILE = ADDRESS_BASE + "/compile";

    /**
     * The error code returned by this verticle.
     */
    public static final int ERR_CODE_BASE = 500;

    private static final String ERR_MSG_TMPL_COMPILE_FAILED = "failed to compile template: %1$s";
    private static final String ERR_MSG_UNEXPECTED = "unexpected exception %1$s while processing message %2$s";

    private Handlebars handlebars;
    private ConcurrentMap<String, SharedTemplate> templateCache;

    /**
     * Initialize the handlebar template compilers on the eventbus. Following handlers are registered:
     * <p/>
     * <ul>
     * <li>com.dreikraft.vertx.template.handlebars.HandlebarsCompileVerticle/compile
     * <p>compiles a template with the given location on the classpath: "templates/hello.hbs"</p>
     * </li>
     * </ul>
     * <p/>
     * Compiled templates are stored in a shared template cache.
     */
    @Override
    public void start() {

        super.start();

        // initilialize members
        handlebars = new Handlebars();
        templateCache = vertx.sharedData().getMap(HandlebarsRendererVerticle.HANDLEBAR_TEMPLATES_CACHE);

        // register the compile handler
        logger.info(String.format("registering handler %1$s", ADDRESS_COMPILE_FILE));
        eb.registerHandler(ADDRESS_COMPILE_FILE, new CompileFileMessageHandler());
    }

    /**
     * Compiles a template from given file and stores it into a shared map.
     */
    private class CompileFileMessageHandler implements Handler<Message<JsonObject>> {

        /**
         * Handles compile messages. Puts the compiled template (SharedTemplate.class) into the shared map
         * "handlebar.templates.cache".
         *
         * @param compileMsg a string message with the location of the template in the classpath.
         */
        @Override
        public void handle(final Message<JsonObject> compileMsg) {
            if (logger.isDebugEnabled())
                logger.debug(String.format("address %1$s received message: %2$s", compileMsg.address(),
                        compileMsg.body()));
            try {
                final String templateLocation = compileMsg.body().getString(
                        HandlebarsRendererVerticle.FIELD_TEMPLATE_LOCATION);
                final URL templateURL = Thread.currentThread().getContextClassLoader().getResource(templateLocation);
                final TemplateSource templateSource = new URLTemplateSource(templateLocation, templateURL);
                final Template template = handlebars.compile(new URLTemplateSource(templateLocation, templateURL));
                final SharedTemplate sharedTemplate = new SharedTemplate(template,
                        new Date(templateSource.lastModified()));
                templateCache.put(templateLocation, sharedTemplate);
                sendOK(compileMsg, new JsonObject().putString("message", String.format("successfully compiled %1$s",
                        templateLocation)));
            } catch (IOException ex) {
                final String msg = String.format(ERR_MSG_TMPL_COMPILE_FAILED, compileMsg.body());
                logger.error(msg, ex);
                compileMsg.fail(ERR_CODE_BASE, msg);
            } catch (RuntimeException ex) {
                final String msg = String.format(ERR_MSG_UNEXPECTED, ex.getMessage(), compileMsg.body());
                logger.error(msg, ex);
                compileMsg.fail(ERR_CODE_BASE, msg);
            }
        }
    }
}
