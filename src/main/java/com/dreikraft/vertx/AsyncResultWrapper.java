package com.dreikraft.vertx;

import org.vertx.java.core.AsyncResult;

/**
 * Created by jan_solo on 09.01.14.
 */
public class AsyncResultWrapper<T>
        implements AsyncResult {

    private T result;
    private Throwable cause;

    public AsyncResultWrapper(T result) {
        this.result = result;
    }

    public AsyncResultWrapper(Throwable cause) {
        this.cause = cause;
    }

    @Override
    public T result() {
        return result;
    }

    @Override
    public Throwable cause() {
        return cause;
    }

    @Override
    public boolean succeeded() {
        return cause == null;
    }

    @Override
    public boolean failed() {
        return cause != null;
    }
}
