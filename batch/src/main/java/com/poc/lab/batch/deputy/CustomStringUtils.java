package com.poc.lab.batch.deputy;

public class CustomStringUtils {

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String stringOrBlank(String v) {
        return v == null ? "" : v;
    }

}
