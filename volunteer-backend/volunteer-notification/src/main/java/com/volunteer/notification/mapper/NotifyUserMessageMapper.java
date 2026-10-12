package com.volunteer.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.notification.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotifyUserMessageMapper extends BaseMapper<UserMessage> {
}
