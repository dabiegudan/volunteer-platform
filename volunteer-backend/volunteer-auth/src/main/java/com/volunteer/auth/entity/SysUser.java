package com.volunteer.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.volunteer.common.annotation.Encrypted;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    /**
     * BCrypt 口令哈希。
     *
     * <p><b>安全说明（必须保持只写）：</b>本实体直接作为 {@code GET /api/user/page}、
     * {@code GET /api/user/{id}} 的响应体返回。历史实现缺少该注解，导致管理员接口
     * 把 BCrypt 哈希批量吐给前端，可被离线爆破、且会在操作日志中进一步扩散。
     * {@code WRITE_ONLY} 保证「请求可写入、响应绝不输出」——若简单加
     * {@code @JsonIgnore} 会让 {@code POST /api/user} 无法接收初始口令。</p>
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    
    private String realName;
    
    private String phone;
    
    private String email;
    
    private String avatar;
    
    private Integer status;
    
    private Long createdBy;
    
    private Long updatedBy;
    
    @TableField("student_no")
    private String studentId;
    
    @TableField("access_level")
    private Integer accessLevel;
    
    /**
     * 身份证号。
     *
     * <p>{@link Encrypted} 由 {@code EncryptInterceptor} 在 MyBatis 参数/结果集两侧透明加解密：
     * 写库为 {@code enc:v1:Base64(GCM)} 密文，出库自动还原明文，历史明文数据可正常读取。
     * DDL 中该列为 {@code VARCHAR(256)}，足以容纳密文膨胀后的长度。</p>
     */
    @Encrypted
    private String idCard;
    
    private Long organizationId;
    
    private Long collegeId;
    
    private String collegeName;

    private String major;

    @TableField("class_name")
    private String className;

    private String grade;
    
    private String roleCode;
    
    private LocalDateTime lastLoginTime;
    
    private String lastLoginIp;
    
    private BigDecimal totalDuration;
    
    private Integer creditScore;

    // ===== #2 志愿者档案完整度门控 - 新增字段 =====
    private String gender;

    private LocalDate birthDate;

    private String politicalStatus;

    private String emergencyContact;

    private String emergencyPhone;

    private String dormArea;

    private String serviceCategoryTags;

    private Integer profileComplete;

    // ===== #13 星级志愿者认证 - 新增字段 =====
    private Integer volunteerStarLevel;
}
