package com.volunteer.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 所有业务实体的公共基类。
 *
 * <h2>主键策略（重要）</h2>
 * <p>主键使用 {@link IdType#AUTO}，即由数据库 {@code AUTO_INCREMENT} 生成。
 * 这与建表脚本 {@code db/init.sql} 中的
 * {@code id BIGINT AUTO_INCREMENT PRIMARY KEY} <b>必须保持一致</b>。
 *
 * <p><b>为什么不能用 ASSIGN_ID（雪花 ID）：</b>
 * {@code init.sql} 的种子数据依赖自增生成稳定的连续主键：
 * <ul>
 *   <li>{@code sys_permission} 种子不含 {@code id} 列，由自增生成 1..15，
 *       其下游语句通过 {@code SELECT 2, id FROM sys_permission WHERE id IN (4, 5, 7)}
 *       以及硬编码的 {@code role_id/permission_id} 引用这些整数 ID；</li>
 *   <li>{@code sys_user_role}、{@code sys_role_permission} 直接硬编码
 *       {@code (user_id, role_id) = (1,1) ... (8,5)}。</li>
 * </ul>
 * <p>若改为 {@code ASSIGN_ID}，应用会写入 19 位雪花 ID，上述种子数据将无法与新数据对应；
 * 同理，若把建表语句的 {@code AUTO_INCREMENT} 去掉，初始化脚本会立即报
 * {@code ERROR 1364 (HY000): Field 'id' doesn't have a default value}。
 *
 * <h2>公共字段自动填充</h2>
 * <p>{@code createTime} / {@code updateTime} / {@code deleted} 由
 * {@code MyMetaObjectHandler} 在插入/更新时自动填充，业务代码无需手工赋值。
 */
@Data
public class BaseEntity {

    /** 主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间，插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入与更新时自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除，1-已删除；由 MyBatis-Plus 自动追加查询条件。 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
