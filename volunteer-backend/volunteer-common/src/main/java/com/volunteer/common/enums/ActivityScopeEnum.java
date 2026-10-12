package com.volunteer.common.enums;

import lombok.Getter;

@Getter
public enum ActivityScopeEnum {
    
    COLLEGE("COLLEGE", "院级活动"),
    UNIVERSITY("UNIVERSITY", "校级活动");
    
    private final String code;
    private final String desc;
    
    ActivityScopeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    public static ActivityScopeEnum fromCode(String code) {
        for (ActivityScopeEnum scope : values()) {
            if (scope.getCode().equals(code)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("未知的活动范围: " + code);
    }
}
