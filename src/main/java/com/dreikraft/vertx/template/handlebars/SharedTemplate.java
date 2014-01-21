package com.dreikraft.vertx.template.handlebars;

import com.github.jknack.handlebars.Template;
import org.vertx.java.core.shareddata.Shareable;

import java.util.Date;

/**
 * Created by jan_solo on 20.01.14.
 */
public final class SharedTemplate implements Shareable {

    private final Template template;
    private final Date timestamp;

    public SharedTemplate(final Template template, final Date timestamp) {
        this.template = template;
        this.timestamp = new Date(timestamp.getTime());
    }

    public Template getTemplate() {
        return template;
    }

    public Date getTimestamp() {
        return new Date(timestamp.getTime());
    }
}
