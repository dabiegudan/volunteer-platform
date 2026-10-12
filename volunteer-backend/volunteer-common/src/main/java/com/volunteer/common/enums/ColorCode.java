package com.volunteer.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ColorCode {

    RED("RED", "红色"),
    YELLOW("YELLOW", "黄色"),
    EMPTY("EMPTY", "空白");

    private final String code;
    private final String desc;

    public static ColorCode fromCode(String code) {
        for (ColorCode color : values()) {
            if (color.getCode().equals(code)) {
                return color;
            }
        }
        return EMPTY;
    }
}
