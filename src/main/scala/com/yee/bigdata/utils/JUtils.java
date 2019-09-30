package com.yee.bigdata.utils;

public class JUtils {
    /**
     * Look up a class by name.
     * @param klass class name
     * @param base super class of the class for verification
     * @param <T> the type of the base class
     * @return the new class
     */
    public static <T> Class<? extends T> loadClass(String klass, Class<T> base) throws ClassNotFoundException {
        return Class.forName(klass, true, getContextOrAppClassLoader()).asSubclass(base);
    }

    /**
     * Get the Context ClassLoader on this thread or, if not present, the ClassLoader that
     * loaded Kafka.
     *
     * This should be used whenever passing a ClassLoader to Class.forName
     */
    public static ClassLoader getContextOrAppClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null)
            return getAppClassLoader();
        else
            return cl;
    }

    /**
     * Get the ClassLoader which loaded Kafka.
     */
    public static ClassLoader getAppClassLoader() {
        return Utils.class.getClassLoader();
    }
}
