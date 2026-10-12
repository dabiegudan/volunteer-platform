package com.volunteer.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {
    
    private Long parentId;
    
    private String permissionName;
    
    private String permissionCode;
    
    private Integer permissionType;
    
    private String path;
    
    private String icon;
    
    private Integer sortOrder;
    
    private Integer status;
}
