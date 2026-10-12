package com.volunteer.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WeekType {

    EVERY("EVERY", "每周"),
    SINGLE("SINGLE", "单周"),
    DOUBLE("DOUBLE", "双周");

    private final String code;
    private final String desc;

    public static WeekType fromCode(String code) {
        for (WeekType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return EVERY;
    }
}
