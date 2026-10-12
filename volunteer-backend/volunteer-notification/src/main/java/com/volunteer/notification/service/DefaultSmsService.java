package com.volunteer.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Slf4j
@Service
public class DefaultSmsService implements SmsService {

    @Value("${notification.sms.console-enabled:false}")
    private boolean consoleEnabled;

    @Value("${notification.sms.provider-name:}")
    private String providerName;

    @Override
    public boolean sendVerificationCode(String phone, String code) {
        if (!isConsoleModeEnabled()) {
            log.warn("SMS provider is not configured; verification code was not sent. phone={}", phone);
            return false;
        }

        log.info("SMS console mode: verification code sent to {}, code={}", phone, code);
        return true;
    }

    @Override
    public boolean sendNotification(String phone, String templateCode, String... params) {
        if (!isConsoleModeEnabled()) {
            log.warn("SMS provider is not configured; notification was not sent. phone={}, templateCode={}", phone, templateCode);
            return false;
        }

        log.info("SMS console mode: notification sent to {}, templateCode={}, params={}", phone, templateCode, Arrays.toString(params));
        return true;
    }

    private boolean isConsoleModeEnabled() {
        return consoleEnabled || "console".equalsIgnoreCase(providerName);
    }
}
