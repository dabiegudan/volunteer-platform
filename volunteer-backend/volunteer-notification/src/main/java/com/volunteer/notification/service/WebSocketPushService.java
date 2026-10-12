package com.volunteer.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    public void pushToUser(Long userId, String message) {
        String destination = "/queue/user/" + userId;
        messagingTemplate.convertAndSend(destination, message);
        log.info("Pushed message to user {}: {}", userId, message);
    }
    
    public void pushToUser(Long userId, Map<String, Object> data) {
        String destination = "/queue/user/" + userId;
        messagingTemplate.convertAndSend(destination, data);
        log.info("Pushed data to user {}", userId);
    }
    
    public void broadcast(String topic, String message) {
        String destination = "/topic/" + topic;
        messagingTemplate.convertAndSend(destination, message);
        log.info("Broadcasted message to topic {}: {}", topic, message);
    }
    
    public void broadcast(String topic, Map<String, Object> data) {
        String destination = "/topic/" + topic;
        messagingTemplate.convertAndSend(destination, data);
        log.info("Broadcasted data to topic {}", topic);
    }
    
    public void sendNotification(Long userId, String title, String content) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "notification");
        notification.put("title", title);
        notification.put("content", content);
        notification.put("timestamp", System.currentTimeMillis());
        
        pushToUser(userId, notification);
    }
    
    public void sendActivityReminder(Long userId, String activityName, String activityTime) {
        Map<String, Object> reminder = new HashMap<>();
        reminder.put("type", "reminder");
        reminder.put("activityName", activityName);
        reminder.put("activityTime", activityTime);
        reminder.put("timestamp", System.currentTimeMillis());
        
        pushToUser(userId, reminder);
    }
}
