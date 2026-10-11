package com.college.attendance.util;

public final class Text {

    private Text() {
    }

    /** Trims and collapses repeated spaces; returns "" for null. */
    public static String clean(String text) {
        return text == null ? "" : text.trim().replaceAll("\s+", " ");
    }
}
