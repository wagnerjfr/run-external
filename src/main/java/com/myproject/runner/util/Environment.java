/* Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved. */

package com.myproject.runner.util;

/**
 *
 */
public class Environment {

    public enum Os {

        /**
         *
         */
        OTHER,

        /**
         *
         */
        WINDOWS,

        /**
         *
         */
        LINUX,

        /**
         *
         */
        SOLARIS,

        /**
         *
         */
        OSX;
    }

    static public Os getOs() {
        String os = getOsName();
        if (os.equalsIgnoreCase("SunOS")) {
            return Os.SOLARIS;
        } else if (os.equalsIgnoreCase("Linux")) {
            return Os.LINUX;
        } else if (os.toLowerCase().startsWith("win")) {
            return Os.WINDOWS;
        } else if (os.contains("OS X")) {
            return Os.OSX;
        }
        return Os.OTHER;
    }

    static public String getOsName() {
        return System.getProperty("os.name");
    }

    static public boolean isUnix() {
        switch (getOs()) {
            case LINUX:
            case SOLARIS:
            case OSX:
                return true;
            default:
                return false;
        }
    }

    static public boolean isWindows() {
        switch (getOs()) {
            case WINDOWS:
                return true;
            default:
                return false;
        }
    }
    
    static public boolean isSolaris() {
        switch (getOs()) {
            case SOLARIS:
                return true;
            default:
                return false;
        }
    }

    static public boolean isOsX() {
        switch (getOs()) {
            case OSX:
                return true;
            default:
                return false;
        }
    }
    
    static public char classpathSeparator() {
        return isWindows() ? ';' : ':';
    }
    
    static public String dynamicLibraryExtension() {
        return isWindows() ? ".dll" : ".so";
    }
    
}
