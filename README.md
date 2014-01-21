mod-handlebars
==============

A simple vert.x module providing handlebar.js template compilation and rendering.

# Dependencies

The mod-handlebar vert.x module uses the [Handlebars.java](https://github.com/jknack/handlebars.java) library to compile
and render the mustache and handlebar templates.

# Installation

## Module

Deploy the module `3kraft~mod-handlebars~0.1` in the `start()` method of your verticle.

## Standalone

```
vertx runmod 3kraft~mod-handlebars~0.
```

## Configuration

Currently there is no configuration possible/required. The module will search the classpath of the module and the
current working directory for templates.

# Usage

## Compile a template


## Render a template with data

Applies the data onto a template and sends back the rendered template as string in the reply handler. If the template
can not be found in the shared compiled template cache or if the template in the cache is outdated (does currently
not check partials), the template will be compiled and put into the cache first.

- Address: `com.dreikraft.vertx.template.handlebars.HandlebarsVerticle/render`
- Message: `{"templateLocation": "<path-to-template>", "data": {...}}`

```java
final JsonObject data = new JsonObject();
data.putString("text", "world");
final JsonObject msg = new JsonObject();
msg.putString("templateLocation", "templates/hello.hbs");
msg.putObject("data", data);

vertx.eventBus().sendWithTimeout("com.dreikraft.vertx.template.handlebars.HandlebarsVerticle/render",
                msg, 5000, new AsyncResultHandler<Message<String>>() {
            @Override
            public void handle(AsyncResult<Message<String>> renderResult) {
                if (renderResult.succeeded()) {
                    container.logger().info(renderResult.result().body());
                } else {
                    container.logger().error(renderResult.cause());
                }
            }
        });
```


## Flush the compiled template cache
