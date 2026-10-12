package com.volunteer.common.constant;

public interface RedisConstant {
    
    String USER_TOKEN_PREFIX = "user:token:";
    
    String ACTIVITY_CACHE_PREFIX = "activity:";
    
    String QR_CODE_PREFIX = "qrcode:";
    
    Long TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60L;
    
    Long QR_CODE_EXPIRE_TIME = 5 * 60L;
}
