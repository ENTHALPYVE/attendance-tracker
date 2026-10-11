package com.college.attendance.util;

import com.college.attendance.model.Student;

import java.util.Comparator;

/**
 * Sorting by roll number the way people expect: "2" before "10", "SE-9" before "SE-10".
 * Plain string sorting would give 1, 10, 11, 2, ...
 */
public final class RollNumbers {

    private RollNumbers() {
    }

    public static final Comparator<String> NATURAL = RollNumbers::compare;

    public static final Comparator<Student> BY_ROLL_NO =
            Comparator.comparing(Student::getRollNo, NATURAL);

    /** Compares two strings, treating runs of digits as numbers. */
    public static int compare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                int cmp = na.length() != nb.length()
                        ? Integer.compare(na.length(), nb.length())
                        : na.compareTo(nb);
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.compare(Character.toUpperCase(ca), Character.toUpperCase(cb));
                if (cmp != 0) return cmp;
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
