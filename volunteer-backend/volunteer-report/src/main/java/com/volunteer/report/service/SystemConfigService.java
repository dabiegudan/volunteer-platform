package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.report.entity.SystemConfig;
import com.volunteer.report.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigMapper configMapper;

    public Page<SystemConfig> getConfigPage(Integer pageNum, Integer pageSize, String keyword, String configType) {
        Page<SystemConfig> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SystemConfig::getConfigKey, keyword)
                    .or()
                    .like(SystemConfig::getDescription, keyword));
        }
        if (configType != null && !configType.isEmpty()) {
            wrapper.eq(SystemConfig::getConfigType, configType);
        }
        wrapper.orderByAsc(SystemConfig::getSortOrder).orderByAsc(SystemConfig::getId);
        return configMapper.selectPage(page, wrapper);
    }

    @Transactional
    public SystemConfig updateConfig(String key, SystemConfig payload) {
        SystemConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigKey, key)
        );
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigType("string");
            config.setStatus(1);
            config.setSortOrder(0);
        }
        if (payload.getConfigValue() != null) {
            config.setConfigValue(payload.getConfigValue());
        }
        if (payload.getConfigType() != null) {
            config.setConfigType(payload.getConfigType());
        }
        if (payload.getDescription() != null) {
            config.setDescription(payload.getDescription());
        }
        if (payload.getSortOrder() != null) {
            config.setSortOrder(payload.getSortOrder());
        }
        if (payload.getStatus() != null) {
            config.setStatus(payload.getStatus());
        }
        if (config.getId() == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        return config;
    }
}
