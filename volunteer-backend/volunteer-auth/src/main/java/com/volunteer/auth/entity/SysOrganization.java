package com.volunteer.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_organization")
public class SysOrganization extends BaseEntity {
    
    @TableField("organization_name")
    private String orgName;
    
    @TableField("organization_code")
    private String orgCode;
    
    private Long parentId;
    
    @TableField("organization_type")
    private String orgType;
    
    private Integer sortOrder;
    
    private Integer status;
    
    private String description;
}
