package com.volunteer.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.auth.entity.SysOrganization;
import com.volunteer.auth.mapper.SysOrganizationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 学院(组织)服务(R-06 实施)
 * <p>原接口返回空数据,导致前端下拉为空;现实现真正查询 sys_organization,并
 * 用 Redis 缓存 10 分钟,降低对数据库的访问压力。</p>
 *
 * <p><b>缓存值格式(修复说明):</b>历史实现把哨兵字符串 {@code "OK"}/{@code "EMPTY"}
 * 写入缓存,但命中分支<b>两个分支都返回空列表</b>,导致首次请求之后 10 分钟 TTL 内
 * {@code GET /api/college/list} 恒返回 {@code []}(学院下拉每 10 分钟只有第一次可用)。
 * 现在缓存值改为<b>真正的 JSON 数组</b>,命中时反序列化返回;解析失败(格式不兼容、
 * 历史脏值、字段变更)会记 WARN 并回落数据库查询,同时覆盖写入新值自愈,
 * 因此不会再有"脏缓存导致接口长期失效"的窗口。</p>
 *
 * <p>缓存键固定为带版本号的 {@code colleges:active:v1},与历史键名隔离,
 * 避免升级后仍读到旧格式的值。</p>
 */
@Service
@Slf4j
public class CollegeService {

    private final SysOrganizationMapper organizationMapper;
    private final StringRedisTemplate redisTemplate;
    /**
     * 可为 null(单元测试直接构造时不注入),为 null 时跳过缓存读写、直接查库。
     */
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY = "colleges:active:v1";
    private static final long CACHE_TTL_MINUTES = 10L;

    @Autowired
    public CollegeService(SysOrganizationMapper organizationMapper,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper) {
        this.organizationMapper = organizationMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 不带 Jackson 的构造器:缓存退化为"永远未命中",仅用于单元测试与极端降级场景。
     */
    public CollegeService(SysOrganizationMapper organizationMapper, StringRedisTemplate redisTemplate) {
        this(organizationMapper, redisTemplate, null);
    }

    public List<SysOrganization> listActiveColleges() {
        if (redisTemplate == null) {
            return queryFromDatabase();
        }
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached != null) {
                List<SysOrganization> hit = deserialize(cached);
                if (hit != null) {
                    return hit;
                }
                // 脏值/旧格式:容错并自愈,继续走下面的数据库查询
                log.warn("学院缓存值无法解析,忽略并重新查询数据库, cacheKey={}", CACHE_KEY);
            }
        } catch (Exception ex) {
            log.warn("读取学院缓存失败,降级到数据库", ex);
        }

        List<SysOrganization> result = queryFromDatabase();
        writeCache(result);
        return result;
    }

    private List<SysOrganization> queryFromDatabase() {
        List<SysOrganization> result = organizationMapper.selectList(
                new LambdaQueryWrapper<SysOrganization>()
                        .eq(SysOrganization::getStatus, 1)
                        .orderByAsc(SysOrganization::getSortOrder)
                        .orderByAsc(SysOrganization::getId)
        );
        return result == null ? Collections.emptyList() : result;
    }

    private void writeCache(List<SysOrganization> result) {
        if (objectMapper == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    CACHE_KEY,
                    objectMapper.writeValueAsString(result),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES);
        } catch (Exception ex) {
            log.warn("写入学院缓存失败,降级不影响主流程", ex);
        }
    }

    /**
     * @return 解析成功返回列表(可能为空集);解析失败返回 {@code null} 表示调用方应回落数据库
     */
    private List<SysOrganization> deserialize(String cached) {
        if (objectMapper == null) {
            return null;
        }
        try {
            List<SysOrganization> parsed =
                    objectMapper.readValue(cached, new TypeReference<List<SysOrganization>>() { });
            return parsed == null ? Collections.emptyList() : parsed;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 失效学院缓存,管理员新增/修改/禁用学院时调用
     */
    public void evictCache() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (Exception ex) {
            log.warn("清除学院缓存失败,降级不影响主流程", ex);
        }
    }
}
