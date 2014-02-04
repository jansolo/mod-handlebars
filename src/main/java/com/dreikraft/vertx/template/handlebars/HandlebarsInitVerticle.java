package com.dreikraft.vertx.template.handlebars;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Future;

/**
 * Initializes mod-handlebar verticles.
 */
public class HandlebarsInitVerticle extends BusModBase {

    private static final int VERTICLES = 2;
    private int completeCount = 0;

    /**
     * Startup module Verticles.
     *
     * @param startedResult the start result.
     */
    @Override
    public void start(final Future<Void> startedResult) {
        super.start();

        container.logger().info(String.format("starting %1$s ...",
                HandlebarsInitVerticle.class.getSimpleName()));

        container.logger().info(String.format("starting %1$s instances ...", HandlebarsCompilerVerticle.class
                .getSimpleName()));
        final int compilerInstances = getOptionalIntConfig("compilerInstances",
                Runtime.getRuntime().availableProcessors());
        container.deployWorkerVerticle(HandlebarsCompilerVerticle.class.getName(), config, compilerInstances, false,
                new AsyncResultHandler<String>() {
                    @Override
                    public void handle(AsyncResult<String> deployResult) {
                        if (deployResult.succeeded()) {
                            logger.info(String.format("successfully started %1$s instances",
                                    HandlebarsCompilerVerticle.class.getSimpleName()));
                            if (completed()) {
                                startedResult.setResult(null);
                            }
                        } else {
                            logger.info(String.format("failed to start %1$s instances",
                                    HandlebarsCompilerVerticle.class.getSimpleName()
                            ));
                            startedResult.setFailure(deployResult.cause());
                        }
                    }
                });

        container.logger().info(String.format("starting %1$s instances ...", HandlebarsRendererVerticle.class.getSimpleName()));
        final int rendererInstances = getOptionalIntConfig("rendererInstances",
                Runtime.getRuntime().availableProcessors());
        container.deployWorkerVerticle(HandlebarsRendererVerticle.class.getName(), config, rendererInstances, false,
                new AsyncResultHandler<String>() {
                    @Override
                    public void handle(AsyncResult<String> deployResult) {
                        if (deployResult.succeeded()) {
                            logger.info(String.format("successfully started %1$s instances",
                                    HandlebarsRendererVerticle.class.getSimpleName()));
                            if (completed()) {
                                startedResult.setResult(null);
                            }
                        } else {
                            logger.info(String.format("failed to start %1$s instances", HandlebarsRendererVerticle.class
                                    .getSimpleName()
                            ));
                            startedResult.setFailure(deployResult.cause());
                        }
                    }
                });
    }

    private boolean completed() {
        synchronized (this) {
            completeCount++;
            return completeCount == VERTICLES;
        }
    }
}
