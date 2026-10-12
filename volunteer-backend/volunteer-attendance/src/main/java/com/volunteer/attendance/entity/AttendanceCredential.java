package com.volunteer.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 签到凭据绑定（人脸 / NFC / 蓝牙）。
 *
 * <p>本实体存在的唯一目的是让凭据类签到「可验证」：签到时必须能查到
 * {@code (credential_type, credential_hash)} 且 {@code user_id} 等于当前用户。
 * 在此之前，凭据类签到不比对任何底库，任意字符串都能签到成功。</p>
 *
 * <p><b>为什么不存明文：</b>{@code credential_hash} 存的是凭据规范化后的 SHA-256，
 * 既避免把生物特征/卡号明文落库，也让唯一索引能够在「同一凭据被重复登记到不同用户」
 * 时直接拦截。</p>
 *
 * <p>表结构见 {@code db/migration/V2__attendance_credential_binding.sql}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("attendance_credential")
public class AttendanceCredential extends BaseEntity {

    /** 凭据类型常量，与 {@code credential_type} 列取值一致。 */
    public static final String TYPE_FACE = "FACE";
    public static final String TYPE_NFC = "NFC";
    public static final String TYPE_BLUETOOTH = "BLUETOOTH";

    private Long userId;

    private String credentialType;

    private String credentialHash;

    private String displayName;

    private Integer status;

    private LocalDateTime lastUsedTime;
}
