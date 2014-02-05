mod-handlebars
==============

A simple vert.x module providing handlebar.js template compilation and rendering.

# Dependencies

The mod-handlebar vert.x module uses the [Handlebars.java](https://github.com/jknack/handlebars.java) library to compile
and render the mustache and handlebar templates.

# Installation

## Module

Deploy the module `3kraft~mod-handlebars~0.2` in the `start()` method of your verticle.

## Standalone

```
vertx runmod 3kraft~mod-handlebars~0.2
```

## Configuration

The module will search the classpath of the module and the current working directory for templates.

The module supports following configuration parameters:

 - `autoUpdate`: If enabled, the Handlebars verticle will check for outdated templates in the internal shared cache.
 Outdated templates will be automatically recompiled. The up-to-date check is performed by the `last-modified` date of
 the template in the filesystem.
 - `renderInstances`: The number of instances to render precompiled handlebar templates deployed in the event pool.
 - `compilerInstances`: The number of instances for compiling handlebar templates deployed in the worker pool.

# Usage

The module uses the vert.x event bus to request compiling and rendering of templates. The process of compiling and
rendering is split into two separate Verticles. The compiler Verticle uses blocking I/O and gets deployed to the worker
pool.

It is recommended to deploy a single instance of the mod-handlebars module. To scale the module configure the number
of instances (see configuration).

The verticle searches templates (*.hbs) on the classpath and the current working directory (the module parameter
`preserve-cwd` is enabled).

The mod-handlebar provides/supports following handler addresses and messages. All handlers return a success message
(a JsonObject, see BusModBase) or will call `message.fail(...)` in case of an error. Therefore its recommended to use
 `sendWithTimeout(..)` as it provides a AsyncResultHandler.

## Compile a template

Compiles a template and puts the compiled template into a shared cache `handlebar.templates.cache` for later usage.
This action is blocking and will run an the worker pool.

 - Address: `com.dreikraft.vertx.template.handlebars.HandlebarsCompilerVerticle/compile`
 - Message (JsonObject): `{'templateLocation': '<path-to-template>'}`
 - Reply:
    - success (JsonObject):  `{'status': 'ok'}`
    - failure (ReplyFailure)

```java
vertx.eventBus().sendWithTimeout("com.dreikraft.vertx.template.handlebars.HandlebarsCompilerVerticle/compile",
new JsonObject().putString("templateLocation": "templates/hello.hbs"), 5000,
        new AsyncResultHandler<Message<JsonObject>>() {

    @Override
    public void handle(AsyncResult<Message<JsonObject>> compileResult) {
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
can not be found in the shared template cache or if the template in the cache is outdated (does currently
not check partials included in the main template), the template will be compiled and put into the cache first.

 - Address: `com.dreikraft.vertx.template.handlebars.HandlebarsRendererVerticle/render`
 - Message (JSON): `{"templateLocation": "<path-to-template>", "data": {...}}`
 - Reply:
    - success (JsonObject):  `{'status': 'ok', 'renderResult': '<rendered template as string>'}`
    - failure (ReplyFailure)

```java
final JsonObject data = new JsonObject().putString("text", "world");
final JsonObject msg = new JsonObject().putString("templateLocation", "templates/hello.hbs").putObject("data", data);

vertx.eventBus().sendWithTimeout("com.dreikraft.vertx.template.handlebars.HandlebarsRendererVerticle/render",
                msg, 5000, new AsyncResultHandler<Message<JsonObject>>() {

            @Override
            public void handle(AsyncResult<Message<JsonObject>> renderResult) {
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

 - Address: `com.dreikraft.vertx.template.handlebars.HandlebarsRendererVerticle/flush`
 - Message (Void)
 - Reply:
    - success (JsonObject):  `{'status': 'ok'}`
    - failure (ReplyFailure)

 ```java
vertx.eventBus().sendWithTimeout(HandlebarsVerticle.ADDRESS_FLUSH,
         (Object) null, HandlebarsVerticle.REPLY_TIMEOUT, new AsyncResultHandler<Message<JsonObject>>() {

     @Override
     public void handle(AsyncResult<Message<JsonObject>> flushResult) {
         if (flushResult.succeeded()) {
            container.logger().info("flushing cache succeeded");
         } else {
            container.logger().error(renderResult.cause());
         }
 ```
