package com.college.attendance.model;

import jakarta.persistence.*;

/** A key/value application setting, e.g. threshold = 75. */
@Entity
@Table(name = "settings")
public class Setting {

    public static final String THRESHOLD = "threshold";
    public static final String COUNT_FREE_SLOTS_AS_PRESENT = "count_free_slots_as_present";
    public static final String SEMESTER_START = "semester_start";

    @Id
    @Column(name = "setting_key")
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    protected Setting() {
    }

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
