package com.volunteer.notification.service;

/**
 * 短信服务接口
 * 可对接阿里云短信、腾讯云短信等第三方服务商
 */
public interface SmsService {
    
    /**
     * 发送短信验证码
     * @param phone 手机号
     * @param code 验证码
     * @return 是否发送成功
     */
    boolean sendVerificationCode(String phone, String code);
    
    /**
     * 发送通知短信
     * @param phone 手机号
     * @param templateCode 短信模板编码
     * @param params 模板参数
     * @return 是否发送成功
     */
    boolean sendNotification(String phone, String templateCode, String... params);
}