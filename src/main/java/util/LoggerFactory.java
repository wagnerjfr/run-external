/* Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved. */

package util;

import java.util.logging.Logger;

/**
 * Class that can create a logger for you with name set to the full
 * packagename of some objects class.
 */
public final class LoggerFactory {

    private LoggerFactory() {
    }

    /**
     * Get a Logger object for some instance of an Object. Will use the full
     * packagename of the objects class to generate name for the logger.
     *
     * @param owner The instance to return a Logger for
     * @return Logger
     */
    public static Logger getLogger(Object owner) {
        return Logger.getLogger(getPackageName(owner));
    }

    /**
     * Get a Logger object for a class. Will use the full
     * packagename of the class to generate name for the logger.
     *
     * @param c The class to return a Logger for
     * @return Logger
     */
    public static Logger getLogger(Class<?> c) {
        return Logger.getLogger(c.getName());
    }

    /**
     * Get package name for an Object.
     *
     * @param owner The object to get the classname for
     * @return String The classname without preceding "class"
     */
    public static String getPackageName(Object owner) {
        return owner.getClass().getName();
    }
}
