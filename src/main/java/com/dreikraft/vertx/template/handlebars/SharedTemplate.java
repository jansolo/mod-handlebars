package com.dreikraft.vertx.template.handlebars;

import com.github.jknack.handlebars.Template;
import org.vertx.java.core.shareddata.Shareable;

import java.util.Date;

/**
 * A shareable wrapper for the compiled template that can be put into the shared map.
 */
public final class SharedTemplate implements Shareable {

    private final Template template;
    private final Date timestamp;

    /**
     * Creates a new immutable SharedTemplate instance.
     *
     * @param template a template
     * @param timestamp a timestamp
     */
    public SharedTemplate(final Template template, final Date timestamp) {
        this.template = template;
        this.timestamp = new Date(timestamp.getTime());
    }

    /**
     * Gets the compiled template stored in this object.
     * @return a compiled hanldebars template
     */
    public Template getTemplate() {
        return template;
    }

    /**
     * Gets the timestamp when the template was compiled last.
     * @return a timestamp
     */
    public Date getTimestamp() {
        return new Date(timestamp.getTime());
    }
}
