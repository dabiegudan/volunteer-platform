package com.volunteer.activity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.common.constant.RedisConstant;
import com.volunteer.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 活动列表缓存服务
 * 使用 Redis 缓存分页查询结果，TTL 5分钟
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final long CACHE_TTL_SECONDS = 5 * 60; // 5分钟

    public String buildCacheKey(int pageNum, int pageSize, String category, String keyword,
                                String status, LocalDateTime startTime, LocalDateTime endTime,
                                String sortBy, String sortOrder) {
        return buildCacheKey(pageNum, pageSize, category, keyword, status, startTime, endTime, 
                           sortBy, sortOrder, null, null, null, null, null);
    }

    public String buildCacheKey(int pageNum, int pageSize, String category, String keyword,
                                String status, LocalDateTime startTime, LocalDateTime endTime,
                                String sortBy, String sortOrder,
                                String province, String city, String district, String street, String timeSlot) {
        StringBuilder sb = new StringBuilder(RedisConstant.ACTIVITY_CACHE_PREFIX + "page:");
        sb.append("pn=").append(pageNum);
        sb.append(":ps=").append(pageSize);
        if (category != null && !category.isEmpty()) {
            sb.append(":cat=").append(category);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sb.append(":kw=").append(keyword);
        }
        if (status != null && !status.isEmpty()) {
            sb.append(":st=").append(status);
        }
        if (startTime != null) {
            sb.append(":start=").append(startTime.toString());
        }
        if (endTime != null) {
            sb.append(":end=").append(endTime.toString());
        }
        if (sortBy != null && !sortBy.isEmpty()) {
            sb.append(":sort=").append(sortBy).append(",").append(sortOrder);
        }
        if (province != null && !province.isEmpty()) {
            sb.append(":province=").append(province);
        }
        if (city != null && !city.isEmpty()) {
            sb.append(":city=").append(city);
        }
        if (district != null && !district.isEmpty()) {
            sb.append(":district=").append(district);
        }
        if (street != null && !street.isEmpty()) {
            sb.append(":street=").append(street);
        }
        if (timeSlot != null && !timeSlot.isEmpty()) {
            sb.append(":timeSlot=").append(timeSlot);
        }
        return sb.toString();
    }

    // 兼容旧版本调用
    public String buildCacheKey(int pageNum, int pageSize, String category, String keyword) {
        return buildCacheKey(pageNum, pageSize, category, keyword, null, null, null, null, null);
    }

    public PageResult<?> getFromCache(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit: {}", cacheKey);
                return objectMapper.convertValue(cached, PageResult.class);
            }
        } catch (Exception e) {
            log.warn("Cache read error: {}", e.getMessage());
        }
        return null;
    }

    public void putToCache(String cacheKey, PageResult<?> result) {
        try {
            String finalCacheKey = cacheKey;
            redisTemplate.opsForValue().set(finalCacheKey, result, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Cache set: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Cache write error: {}", e.getMessage());
        }
    }

    public void clearActivityCache() {
        try {
            Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                Set<String> keySet = new HashSet<>();
                try (var cursor = connection.scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match(RedisConstant.ACTIVITY_CACHE_PREFIX + "page:*")
                                .count(100)
                                .build())) {
                    cursor.forEachRemaining(bytes -> keySet.add(new String(bytes, StandardCharsets.UTF_8)));
                }
                return keySet;
            });
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            log.info("Activity cache cleared, deleted {} keys", keys != null ? keys.size() : 0);
        } catch (Exception e) {
            log.warn("Cache clear error: {}", e.getMessage());
        }
    }

    public void clearActivityCache(Long activityId) {
        // 活动变更时清除所有缓存（简化处理）
        clearActivityCache();
    }
}
