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

The module uses the vert.x event bus to request compiling and rendering of templates. mod-handlebar provides/supports
following handler addresses and messages.

## Compile a template

Compiles a template and puts the compiled template into a shared cache `handlebar.templates.cache` for later usage.

- Address: `com.dreikraft.vertx.template.handlebars.HandlebarsVerticle/render`
- Message (String): `<path-to-template>`
- Reply (Void)

```java
vertx.eventBus().sendWithTimeout(HandlebarsVerticle.ADDRESS_COMPILE_FILE, "templates/hello.hbs", 5000,
        new AsyncResultHandler<Message<Void>>() {

    @Override
    public void handle(AsyncResult<Message<Void>> compileResult) {
        if (compileResult.succeeded()) {
            container.logger().info("compilation succeeded");
            Template compiledTemplate = vertx.sharedData().getMap("handlebar.templates.cache").get(templateLocation);
            ...
        } else {
            container.logger().error(renderResult.cause());
        }
```

## Render a template with data

Applies the data onto a template and sends back the rendered template as string in the reply handler. If the template
can not be found in the shared compiled template cache or if the template in the cache is outdated (does currently
not check partials), the template will be compiled and put into the cache first.

 - Address: `com.dreikraft.vertx.template.handlebars.HandlebarsVerticle/render`
 - Message (JSON): `{"templateLocation": "<path-to-template>", "data": {...}}`
 - Reply (String): the rendered template

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

Removes all compiled templates from the shared cache `handlebar.templates.cache`.

 - Address: `com.dreikraft.vertx.template.handlebars.HandlebarsVerticle/flush`
 - Message (Void)
 - Reply (Void)

 ```java
vertx.eventBus().sendWithTimeout(HandlebarsVerticle.ADDRESS_FLUSH,
         (Object) null, HandlebarsVerticle.REPLY_TIMEOUT, new AsyncResultHandler<Message<Void>>() {

     @Override
     public void handle(AsyncResult<Message<Void>> flushResult) {
         if (flushResult.succeeded()) {
            container.logger().info("flushing cache succeeded");
         } else {
            container.logger().error(renderResult.cause());
          }
 ```
