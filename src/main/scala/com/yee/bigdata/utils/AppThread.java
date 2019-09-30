package com.yee.bigdata.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A wrapper for Thread that sets things up nicely
 */
public class AppThread extends Thread {

    private final Logger log = LogManager.getLogger(getClass());

    public static AppThread daemon(final String name, Runnable runnable) {
        return new AppThread(name, runnable, true);
    }

    public static AppThread nonDaemon(final String name, Runnable runnable) {
        return new AppThread(name, runnable, false);
    }

    public AppThread(final String name, boolean daemon) {
        super(name);
        configureThread(name, daemon);
    }

    public AppThread(final String name, Runnable runnable, boolean daemon) {
        super(runnable, name);
        configureThread(name, daemon);
    }

    private void configureThread(final String name, boolean daemon) {
        setDaemon(daemon);
        setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                log.error("Uncaught exception in thread '{}':", name, e);
            }
        });
    }

}