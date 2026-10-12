package com.volunteer.auth.enums;

public enum AccessLevelEnum {
    
    VISITOR(0, "访客", "仅浏览权限"),
    FULL_ACCESS(1, "完整访问", "完整系统访问权限");
    
    private final int code;
    private final String name;
    private final String description;
    
    AccessLevelEnum(int code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static AccessLevelEnum fromCode(int code) {
        for (AccessLevelEnum level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        return VISITOR;
    }
}
