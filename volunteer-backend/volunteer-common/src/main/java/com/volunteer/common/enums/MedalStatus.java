package com.volunteer.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MedalStatus {

    LOCKED("LOCKED", "未解锁"),
    OBTAINED("OBTAINED", "已获得");

    private final String code;
    private final String desc;

    public static MedalStatus fromCode(String code) {
        for (MedalStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return LOCKED;
    }
}
