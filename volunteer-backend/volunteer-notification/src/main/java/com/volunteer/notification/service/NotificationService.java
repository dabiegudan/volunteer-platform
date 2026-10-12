package com.volunteer.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.notification.entity.UserMessage;
import com.volunteer.notification.mapper.NotifyUserMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotifyUserMessageMapper messageMapper;
    private final WebSocketPushService webSocketPushService;
    private final SmsService smsService;
    
    public enum NotificationChannel {
        INBOX,
        WEBSOCKET,
        SMS,
        ALL
    }
    
    public enum NotificationPriority {
        HIGH,
        MEDIUM,
        LOW
    }
    
    @Transactional
    public UserMessage sendMessage(Long userId, String messageType, String title, String content, 
                                   Long relatedId, String relatedType) {
        return sendMessage(userId, messageType, title, content, relatedId, relatedType, null, null, NotificationChannel.INBOX);
    }
    
    @Transactional
    public UserMessage sendMessage(Long userId, String messageType, String title, String content, 
                                   Long relatedId, String relatedType, String phone,
                                   NotificationPriority priority, NotificationChannel channel) {
        UserMessage message = null;
        
        if (channel == NotificationChannel.INBOX || channel == NotificationChannel.ALL) {
            message = saveInboxMessage(userId, messageType, title, content, relatedId, relatedType);
        }
        
        if (channel == NotificationChannel.WEBSOCKET || channel == NotificationChannel.ALL) {
            pushWebSocket(userId, title, content, messageType);
        }
        
        if ((channel == NotificationChannel.SMS || channel == NotificationChannel.ALL) 
                && phone != null && priority == NotificationPriority.HIGH) {
            sendSmsNotification(phone, title, content);
        }
        
        return message;
    }
    
    private UserMessage saveInboxMessage(Long userId, String messageType, String title, 
                                         String content, Long relatedId, String relatedType) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setMessageType(messageType);
        message.setTitle(title);
        message.setContent(content);
        message.setRelatedId(relatedId);
        message.setRelatedType(relatedType);
        message.setIsRead(0);
        messageMapper.insert(message);
        return message;
    }
    
    private void pushWebSocket(Long userId, String title, String content, String messageType) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "notification");
        notification.put("messageType", messageType);
        notification.put("title", title);
        notification.put("content", content);
        notification.put("timestamp", System.currentTimeMillis());
        
        try {
            webSocketPushService.pushToUser(userId, notification);
        } catch (Exception e) {
            log.warn("WebSocket推送失败, userId: {}, error: {}", userId, e.getMessage());
        }
    }
    
    private void sendSmsNotification(String phone, String title, String content) {
        try {
            boolean sent = smsService.sendNotification(phone, "NOTIFICATION", title, content);
            if (!sent) {
                log.warn("SMS notification was not sent. phone: {}, title: {}", phone, title);
            }
        } catch (Exception e) {
            log.warn("短信发送失败, phone: {}, error: {}", phone, e.getMessage());
        }
    }
    
    @Transactional
    public void sendHighPriorityNotification(Long userId, String phone, String messageType, 
                                             String title, String content, Long relatedId, String relatedType) {
        sendMessage(userId, messageType, title, content, relatedId, relatedType, phone, 
                    NotificationPriority.HIGH, NotificationChannel.ALL);
    }
    
    public Page<UserMessage> getMessagePage(Long userId, Integer pageNum, Integer pageSize) {
        Page<UserMessage> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId)
                .orderByDesc(UserMessage::getCreateTime);
        return messageMapper.selectPage(page, wrapper);
    }
    
    @Transactional
    public void markAsRead(Long messageId, Long userId) {
        UserMessage message = messageMapper.selectById(messageId);
        // 双重判空：message.getUserId() 在数据异常时为 null，直接调用 equals 会 NPE，
        // 且该分支被 GlobalExceptionHandler 兜底成 500「系统繁忙」，掩盖真实原因
        if (message != null && message.getUserId() != null && message.getUserId().equals(userId)) {
            message.setIsRead(1);
            message.setReadTime(LocalDateTime.now());
            messageMapper.updateById(message);
        }
    }
    
    @Transactional
    public void markAllAsRead(Long userId) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0);
        
        UserMessage update = new UserMessage();
        update.setIsRead(1);
        update.setReadTime(LocalDateTime.now());
        messageMapper.update(update, wrapper);
    }

    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getId, messageId)
                .eq(UserMessage::getUserId, userId);
        int deleted = messageMapper.delete(wrapper);
        if (deleted == 0) {
            throw new BusinessException("消息不存在或无权限删除");
        }
    }
    
    public Long getUnreadCount(Long userId) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0);
        return messageMapper.selectCount(wrapper);
    }
}
