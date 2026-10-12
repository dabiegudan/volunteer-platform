package com.volunteer.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * #12 系统公告/全员广播 - 系统公告实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_announcement")
public class SystemAnnouncement extends BaseEntity {

    private String title;

    private String content;

    private String type;

    private Integer priority;

    private String coverImage;

    private Long publisherId;

    private String publisherName;

    private LocalDateTime publishTime;

    private LocalDateTime expireTime;

    private Integer status;

    private Integer viewCount;

    private Integer isTop;
}