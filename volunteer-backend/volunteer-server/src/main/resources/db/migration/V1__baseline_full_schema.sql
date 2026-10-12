-- ============================================================================
-- V1 基线迁移：全量数据库结构 + 种子数据
-- ============================================================================
-- 【脚本定位】
--   本脚本是 Flyway 迁移链的**基线（Baseline）**，内容等同于项目的全量结构定义。
--   它同时服务于两种场景：
--     场景一（全新空库）：Flyway 直接执行本脚本，一次性建立全部 70 张表、
--                         索引、外键、CHECK 约束与种子数据。
--     场景二（已有数据库）：Flyway 检测到库非空且无 flyway_schema_history，
--                         执行 baseline 后把版本记为 1 并**跳过本脚本**，
--                         只执行 V2 及之后的增量迁移。
--   因此本脚本**只会在空库上执行**，不会破坏已有数据。
--
-- 【为什么没有 DROP/CREATE DATABASE、USE】
--   源脚本 db/init.sql 面向手工执行，含 DROP DATABASE / CREATE DATABASE / USE。
--   这三类语句在 Flyway 迁移中必须剔除，原因：
--     1. Flyway 已经连接到目标库（由 spring.datasource.url 决定），无需再 USE；
--     2. DROP DATABASE 与 Flyway 自身持有的元数据表冲突，且会误删其他库；
--     3. CREATE DATABASE 需要更高权限，且与 Flyway 的库选择逻辑冲突。
--   库的创建请使用：Flyway 的 createSchemas（见 yml 的 flyway.create-schemas）
--   或手工 `CREATE DATABASE volunteer_db ...`。
--
-- 【为什么保留 DROP TABLE IF EXISTS】
--   在全新空库上这些语句为空操作。保留的原因是可重复执行（幂等），
--   便于在开发过程中手工重跑本脚本而不必先清库。
--
-- 【维护约定】
--   本脚本内容必须与 db/init.sql 的结构部分保持一致。
--   修改结构时的正确做法：**新增一个 V{n}__*.sql 增量脚本**，不要修改本文件。
--   修改已执行过的迁移脚本会导致 checksum 校验失败（详见文档「避坑要点」）。
-- ============================================================================
-- ====================================================
-- 一体式志愿者服务平台 - MySQL 数据库完整初始化脚本
-- 数据库版本: MySQL 8.0+
-- 说明: 全量初始化脚本，一次执行即可建库建表并写入种子数据
-- 包含: DDL建表、外键约束、索引、CHECK约束、种子数据
-- 表数量: 70 张（含 ShedLock 分布式定时任务锁表）
-- 整理日期: 2026-06-07
-- 密码策略: BCrypt 加密，工作因子=12
-- 引擎: InnoDB  字符集: utf8mb4  排序规则: utf8mb4_unicode_ci
-- ====================================================
-- 【主键策略说明 — 请勿改为 NOT NULL PRIMARY KEY】
--   1) 数据库侧：所有业务表主键为 `id BIGINT AUTO_INCREMENT PRIMARY KEY`；
--   2) 应用侧：实体统一继承 BaseEntity，主键声明为 @TableId(type = IdType.AUTO)；
--   3) 两者必须保持一致。本脚本的种子数据依赖自增生成稳定的连续主键：
--        - sys_permission 种子不含 id 列，由自增生成 1..15，
--          下游用 `SELECT 2, id FROM sys_permission WHERE id IN (4, 5, 7)` 引用；
--        - sys_user_role / sys_role_permission 直接硬编码 (user_id, role_id) = (1,1)..(8,5)。
--      因此若去掉 AUTO_INCREMENT，脚本会立即失败：
--        ERROR 1364 (HY000): Field 'id' doesn't have a default value
--   4) 同理，不可把 mybatis-plus 全局 id-type 配为 ASSIGN_ID，
--      否则应用写入雪花 ID，下游硬编码整数 ID 的种子数据将无法对应。
-- ====================================================
-- 危险提示: 脚本开头包含 DROP DATABASE，执行为全库重建，请勿在有数据的库上运行
-- ====================================================
-- 默认口令清单(部署前请通过 BCryptPasswordEncoder 重新生成):
--   admin          : Admin@2026Safe
--   organizer      : Organizer@2026
--   volunteer1~3   : Volunteer@2026
--   college_admin1 : College@Admin2026
--   college_admin2 : College@Admin2026
--   auditor        : Auditor@2026
-- ====================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 创建数据库


-- ============================================
-- 2. 系统管理模块 (6张表)
-- ============================================

-- 2.1 组织信息表（必须在 sys_user 之前创建，因为 sys_user 引用其外键）
DROP TABLE IF EXISTS sys_organization;
CREATE TABLE sys_organization (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    organization_name VARCHAR(128) NOT NULL COMMENT '组织名称',
    organization_code VARCHAR(64) UNIQUE COMMENT '组织代码',
    organization_type VARCHAR(32) DEFAULT 'COLLEGE' COMMENT '组织类型: COLLEGE-学院, ASSOCIATION-协会, DEPARTMENT-部门',
    parent_id BIGINT COMMENT '父组织ID',
    leader_id BIGINT COMMENT '负责人ID',
    contact_phone VARCHAR(32) COMMENT '联系电话',
    description TEXT COMMENT '组织描述',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常',
    total_volunteers INT DEFAULT 0 COMMENT '志愿者总数',
    total_duration DECIMAL(12,2) DEFAULT 0 COMMENT '总服务时长(小时)',
    sort_order TINYINT DEFAULT 0 COMMENT '排序号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_organization_parent (parent_id),
    INDEX idx_organization_status (status),
    INDEX idx_org_type (organization_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织信息表';

-- 2.2 用户信息表（整合V2.4.0/V2.5.0/V2.5.2所有字段）
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(256) NOT NULL COMMENT '密码(BCrypt加密)',
    real_name VARCHAR(64) NOT NULL COMMENT '真实姓名',
    id_card VARCHAR(256) COMMENT '身份证号(AES-256加密)',
    phone VARCHAR(32) COMMENT '手机号',
    email VARCHAR(128) COMMENT '邮箱',
    avatar VARCHAR(512) COMMENT '头像URL',
    organization_id BIGINT COMMENT '组织ID',
    college_id BIGINT COMMENT '学院ID（对组织者必填）',
    college_name VARCHAR(128) COMMENT '学院名称',
    major VARCHAR(64) COMMENT '专业',
    class_name VARCHAR(64) COMMENT '班级',
    grade VARCHAR(20) COMMENT '年级',
    student_no VARCHAR(32) COMMENT '学号',
    role_code VARCHAR(64) COMMENT '角色代码',
    access_level TINYINT DEFAULT 0 COMMENT '访问级别: 0-管理员, 1-组织者, 2-完整用户, 3-访客',
    -- V2.4.0 志愿者档案完整度字段
    gender CHAR(1) COMMENT '性别 M/F',
    birth_date DATE COMMENT '出生日期',
    political_status VARCHAR(20) COMMENT '政治面貌',
    emergency_contact VARCHAR(64) COMMENT '紧急联系人',
    emergency_phone VARCHAR(32) COMMENT '紧急联系电话',
    dorm_area VARCHAR(128) COMMENT '宿舍区域',
    service_category_tags VARCHAR(255) COMMENT '服务类别标签 JSON数组',
    profile_complete TINYINT DEFAULT 0 COMMENT '档案完整度 0-未完成 1-已完成',
    volunteer_star_level TINYINT DEFAULT 0 COMMENT '星级志愿者 1-5星',
    -- 基础状态字段
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常, 2-锁定',
    last_login_time DATETIME COMMENT '最后登录时间',
    last_login_ip VARCHAR(64) COMMENT '最后登录IP',
    total_duration DECIMAL(10,2) DEFAULT 0 COMMENT '总服务时长(小时)',
    credit_score TINYINT UNSIGNED DEFAULT 100 COMMENT '信用分 0-100',
    created_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_user_status (status, deleted),
    INDEX idx_user_organization (organization_id),
    INDEX idx_user_college (college_name),
    INDEX idx_user_phone (phone),
    INDEX idx_user_student_no (student_no),
    INDEX idx_user_create_time (create_time),
    INDEX idx_user_college_id (college_id),
    INDEX idx_user_access_level (access_level),
    CONSTRAINT chk_user_status CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_credit_score CHECK (credit_score BETWEEN 0 AND 100),
    CONSTRAINT fk_user_college FOREIGN KEY (college_id) REFERENCES sys_organization(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- 2.3 角色表
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色代码',
    description VARCHAR(512) COMMENT '角色描述',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常',
    sort_order TINYINT DEFAULT 0 COMMENT '排序号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_role_status (status),
    INDEX idx_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 2.4 用户角色关联表
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_sys_user_role_user (user_id),
    INDEX idx_sys_user_role_role (role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 2.5 权限表
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    parent_id BIGINT DEFAULT 0 COMMENT '父权限ID',
    permission_name VARCHAR(64) NOT NULL COMMENT '权限名称',
    permission_code VARCHAR(128) NOT NULL UNIQUE COMMENT '权限代码',
    permission_type TINYINT NOT NULL COMMENT '权限类型: 1-菜单, 2-按钮, 3-接口',
    path VARCHAR(256) COMMENT '路由路径',
    component VARCHAR(256) COMMENT '组件路径',
    icon VARCHAR(64) COMMENT '图标',
    sort_order TINYINT DEFAULT 0 COMMENT '排序号',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_permission_parent (parent_id),
    INDEX idx_permission_code (permission_code),
    INDEX idx_permission_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 2.6 角色权限关联表
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_permission_role (role_id),
    INDEX idx_role_permission_permission (permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ============================================
-- 3. 活动管理模块 (8张表)
-- ============================================

-- 3.1 活动分类表（必须在 activity 表之前创建）
DROP TABLE IF EXISTS activity_category;
CREATE TABLE activity_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    category_name VARCHAR(50) NOT NULL COMMENT '分类名称',
    category_code VARCHAR(50) NOT NULL UNIQUE COMMENT '分类代码',
    icon VARCHAR(100) COMMENT '图标',
    description TEXT COMMENT '分类描述',
    sort_order TINYINT DEFAULT 0 COMMENT '排序号',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category_status (status),
    INDEX idx_category_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动分类表';

-- 3.2 活动信息表（整合V2.4.0/V2.5.0/V2.5.2所有字段）
DROP TABLE IF EXISTS activity;
CREATE TABLE activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    title VARCHAR(255) NOT NULL COMMENT '活动标题',
    description TEXT COMMENT '活动描述',
    category VARCHAR(64) COMMENT '活动分类(关联activity_category.category_code)',
    tags VARCHAR(255) COMMENT '活动标签',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '结束时间',
    registration_deadline DATETIME COMMENT '报名截止时间',
    location VARCHAR(512) COMMENT '活动地点',
    province VARCHAR(32) NOT NULL DEFAULT '' COMMENT '省份',
    city VARCHAR(32) NOT NULL DEFAULT '' COMMENT '城市',
    district VARCHAR(32) NOT NULL DEFAULT '' COMMENT '区县',
    street VARCHAR(64) NOT NULL DEFAULT '' COMMENT '街道',
    location_gps_lat DECIMAL(10,8) COMMENT 'GPS纬度',
    location_gps_lng DECIMAL(11,8) COMMENT 'GPS经度',
    max_participants INT COMMENT '最大参与人数',
    current_participants INT DEFAULT 0 COMMENT '当前参与人数',
    min_participants INT DEFAULT 1 COMMENT '最少参与人数',
    organizer_id BIGINT NOT NULL COMMENT '组织者ID',
    organization_id BIGINT COMMENT '所属组织ID',
    activity_scope VARCHAR(20) NOT NULL DEFAULT 'COLLEGE' COMMENT '活动范围: COLLEGE-院级, UNIVERSITY-校级',
    college_id BIGINT COMMENT '所属学院ID',
    need_classify TINYINT(1) DEFAULT 0 COMMENT '是否需要分类：0-否，1-是',
    -- V2.4.0 活动负责人字段
    leader_name VARCHAR(64) COMMENT '负责人姓名',
    leader_phone VARCHAR(32) COMMENT '负责人电话',
    leader_org_unit VARCHAR(128) COMMENT '负责人组织单位',
    -- 状态与审核
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT/AUDITING/APPROVED/REJECTED/PUBLISHED/RECRUITING/ONGOING/CANCELLED/COMPLETED/ARCHIVED',
    cover_image VARCHAR(512) COMMENT '封面图片',
    attachments TEXT COMMENT '附件JSON数组',
    credit_reward INT DEFAULT 0 COMMENT '学分奖励',
    duration_hours DECIMAL(5,2) COMMENT '活动时长(小时)',
    check_in_radius SMALLINT DEFAULT 500 COMMENT '签到半径(米)',
    is_urgent TINYINT(1) DEFAULT 0 COMMENT '是否紧急活动: 0-否, 1-是',
    urgent_level VARCHAR(20) COMMENT '紧急等级: LOW-低, MEDIUM-中, HIGH-高',
    requires_approval TINYINT(1) DEFAULT 0 COMMENT '是否需要审核: 0-否, 1-是',
    created_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    published_time DATETIME COMMENT '发布时间',
    submit_time DATETIME COMMENT '提交审核时间',
    audit_comment VARCHAR(500) COMMENT '审核意见',
    audit_time DATETIME COMMENT '审核时间',
    cancel_reason VARCHAR(500) COMMENT '取消原因',
    cancel_time DATETIME COMMENT '取消时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_activity_organizer (organizer_id),
    INDEX idx_activity_org_status (organization_id, status),
    INDEX idx_activity_status_deleted (status, deleted),
    INDEX idx_activity_category (category),
    INDEX idx_activity_start_time (start_time),
    INDEX idx_activity_registration_deadline (registration_deadline),
    INDEX idx_activity_create_time (create_time),
    INDEX idx_activity_organizer_status_time (organizer_id, status, start_time DESC),
    INDEX idx_activity_region (province, city, district, street),
    INDEX idx_activity_urgent (is_urgent, urgent_level, start_time),
    INDEX idx_activity_scope (activity_scope),
    INDEX idx_activity_college (college_id),
    INDEX idx_activity_scope_college (activity_scope, college_id),
    INDEX idx_activity_status_time (status, start_time DESC),
    FULLTEXT INDEX ft_activity_search (title, description, tags),
    CONSTRAINT chk_activity_time CHECK (end_time > start_time),
    CONSTRAINT chk_activity_status CHECK (status IN ('DRAFT', 'AUDITING', 'APPROVED', 'REJECTED', 'PUBLISHED', 'RECRUITING', 'ONGOING', 'CANCELLED', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT chk_activity_participants CHECK (max_participants >= min_participants AND current_participants <= max_participants),
    CONSTRAINT chk_activity_scope CHECK ((activity_scope = 'UNIVERSITY' AND college_id IS NULL) OR (activity_scope = 'COLLEGE' AND college_id IS NOT NULL)),
    CONSTRAINT fk_activity_organizer FOREIGN KEY (organizer_id) REFERENCES sys_user(id),
    CONSTRAINT fk_activity_organization FOREIGN KEY (organization_id) REFERENCES sys_organization(id) ON DELETE SET NULL,
    CONSTRAINT fk_activity_category FOREIGN KEY (category) REFERENCES activity_category(category_code) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_activity_college FOREIGN KEY (college_id) REFERENCES sys_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动信息表';

-- 3.3 活动报名表（整合V2.5.1批量导入字段）
DROP TABLE IF EXISTS activity_registration;
CREATE TABLE activity_registration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    team_id BIGINT COMMENT '组队ID',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '报名状态: PENDING-待确认, APPROVED-已录取, REJECTED-已拒绝, CANCELLED-已取消, WAITLIST-候补, CHECKED_IN-已签到, COMPLETED-已完成',
    registration_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    approval_time DATETIME COMMENT '审批时间',
    approval_user_id BIGINT COMMENT '审批人ID',
    cancel_time DATETIME COMMENT '取消时间',
    cancel_reason VARCHAR(500) COMMENT '取消原因',
    reject_reason VARCHAR(500) COMMENT '拒绝原因',
    remark VARCHAR(1000) COMMENT '备注',
    credit_earned DECIMAL(5,2) DEFAULT 0 COMMENT '获得学分',
    -- V2.5.1 批量导入字段
    join_method VARCHAR(32) DEFAULT 'self_signup' COMMENT '报名方式: self_signup-自主报名, import-批量导入',
    operator_id BIGINT COMMENT '操作人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY uk_registration_activity_user (activity_id, user_id),
    INDEX idx_registration_activity (activity_id),
    INDEX idx_registration_user (user_id),
    INDEX idx_registration_team (team_id),
    INDEX idx_registration_act_status (activity_id, status),
    INDEX idx_registration_user_status (user_id, status),
    INDEX idx_reg_user_status_time (user_id, status, registration_time DESC),
    INDEX idx_registration_join_method (join_method),
    CONSTRAINT chk_registration_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'WAITLIST', 'CHECKED_IN', 'COMPLETED')),
    CONSTRAINT fk_registration_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_registration_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_registration_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动报名表';

-- 3.4 组队信息表
DROP TABLE IF EXISTS team;
CREATE TABLE team (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    team_name VARCHAR(128) COMMENT '队伍名称',
    leader_id BIGINT NOT NULL COMMENT '队长ID',
    max_members TINYINT DEFAULT 5 COMMENT '最大成员数',
    current_members TINYINT DEFAULT 1 COMMENT '当前成员数',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/FULL/DISBANDED',
    description VARCHAR(500) COMMENT '队伍描述',
    invitation_code VARCHAR(32) UNIQUE COMMENT '邀请码',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    closed_time DATETIME COMMENT '关闭时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_team_activity (activity_id),
    INDEX idx_team_leader (leader_id),
    INDEX idx_team_status (status),
    INDEX idx_team_invitation_code (invitation_code),
    CONSTRAINT fk_team_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_leader FOREIGN KEY (leader_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组队信息表';

-- 3.5 组队成员表
DROP TABLE IF EXISTS team_member;
CREATE TABLE team_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    team_id BIGINT NOT NULL COMMENT '队伍ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role VARCHAR(20) DEFAULT 'MEMBER' COMMENT '成员角色: LEADER-队长, MEMBER-队员',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING-待确认, JOINED-已加入, REJECTED-已拒绝, LEFT-已离开',
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    approved_time DATETIME COMMENT '审批时间',
    leave_time DATETIME COMMENT '离开时间',
    remark VARCHAR(255) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY uk_team_member (team_id, user_id),
    INDEX idx_team_member_team (team_id),
    INDEX idx_team_member_user (user_id),
    INDEX idx_team_member_status (status),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_member_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组队成员表';

-- 3.6 活动变更日志表
DROP TABLE IF EXISTS activity_change_log;
CREATE TABLE activity_change_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    change_type VARCHAR(64) NOT NULL COMMENT '变更类型',
    change_field VARCHAR(64) COMMENT '变更字段',
    old_value TEXT COMMENT '旧值',
    new_value TEXT COMMENT '新值',
    change_content TEXT COMMENT '变更内容',
    operator_id BIGINT COMMENT '操作人ID',
    operator_type VARCHAR(32) COMMENT '操作人类型',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_change_log_activity (activity_id),
    INDEX idx_change_log_time (create_time),
    INDEX idx_change_log_operator (operator_id, create_time),
    CONSTRAINT fk_change_log_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动变更日志表';

-- 3.7 活动模板表
DROP TABLE IF EXISTS activity_template;
CREATE TABLE activity_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    category VARCHAR(64) COMMENT '活动分类',
    default_title VARCHAR(255) COMMENT '默认标题',
    default_description TEXT COMMENT '默认描述',
    default_location VARCHAR(512) COMMENT '默认地点',
    location_gps_lat DECIMAL(10,8) COMMENT 'GPS纬度',
    location_gps_lng DECIMAL(11,8) COMMENT 'GPS经度',
    province VARCHAR(32) COMMENT '省份',
    city VARCHAR(32) COMMENT '城市',
    district VARCHAR(32) COMMENT '区县',
    street VARCHAR(64) COMMENT '街道',
    default_duration_hours DECIMAL(5,2) COMMENT '默认时长(小时)',
    default_max_participants INT COMMENT '默认最大人数',
    min_participants INT DEFAULT 1 COMMENT '最少参与人数',
    default_credit_reward INT DEFAULT 0 COMMENT '默认学分',
    default_check_in_radius SMALLINT DEFAULT 500 COMMENT '默认签到半径(米)',
    default_requires_approval TINYINT(1) DEFAULT 0 COMMENT '默认是否需要审核',
    tags VARCHAR(255) COMMENT '默认标签',
    usage_count INT DEFAULT 0 COMMENT '使用次数',
    owner_id BIGINT COMMENT '模板所有者ID',
    organization_id BIGINT COMMENT '所属组织ID',
    is_public TINYINT(1) DEFAULT 0 COMMENT '是否公开: 0-私有, 1-公开',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_template_category (category),
    INDEX idx_template_owner (owner_id),
    INDEX idx_template_status (status),
    INDEX idx_template_public (is_public, status),
    CONSTRAINT fk_template_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动模板表';

-- 3.8 活动批量导入日志表（V2.5.1）
DROP TABLE IF EXISTS activity_import_log;
CREATE TABLE activity_import_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    operator_id BIGINT COMMENT '操作人ID',
    file_name VARCHAR(255) COMMENT '导入文件名',
    total_count INT DEFAULT 0 COMMENT '总行数',
    success_count INT DEFAULT 0 COMMENT '成功数',
    skip_count INT DEFAULT 0 COMMENT '跳过数（已存在）',
    fail_count INT DEFAULT 0 COMMENT '失败数',
    fail_details TEXT COMMENT '失败详情JSON',
    import_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_import_activity (activity_id),
    INDEX idx_import_operator (operator_id),
    INDEX idx_import_time (import_time),
    CONSTRAINT fk_import_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_import_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动导入日志表';

-- ============================================
-- 4. 考勤管理模块 (2张表)
-- ============================================

-- 4.1 考勤记录表（整合V2.5.2新增字段）
DROP TABLE IF EXISTS attendance;
CREATE TABLE attendance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    registration_id BIGINT COMMENT '报名ID',
    check_in_time DATETIME COMMENT '签到时间',
    check_out_time DATETIME COMMENT '签退时间',
    check_in_location VARCHAR(500) COMMENT '签到地点',
    check_out_location VARCHAR(500) COMMENT '签退地点',
    check_in_gps_lat DECIMAL(10,8) COMMENT '签到GPS纬度',
    check_in_gps_lng DECIMAL(11,8) COMMENT '签到GPS经度',
    check_out_gps_lat DECIMAL(10,8) COMMENT '签退GPS纬度',
    check_out_gps_lng DECIMAL(11,8) COMMENT '签退GPS经度',
    check_in_mode VARCHAR(32) COMMENT '签到方式: QR_CODE-二维码, GPS-定位, MANUAL-手动, FACE-人脸识别, NFC-NFC, BLUETOOTH-蓝牙',
    check_out_mode VARCHAR(32) COMMENT '签退方式',
    check_in_distance DECIMAL(10,2) COMMENT '签到距离(米)',
    check_out_distance DECIMAL(10,2) COMMENT '签退距离(米)',
    is_supplementary TINYINT(1) DEFAULT 0 COMMENT '是否补签: 0-正常, 1-补签',
    is_late TINYINT(1) DEFAULT 0 COMMENT '是否迟到',
    is_early TINYINT(1) DEFAULT 0 COMMENT '是否早退',
    supplementary_reason VARCHAR(1000) COMMENT '补签原因',
    supplementary_proof VARCHAR(512) COMMENT '补签凭证(文件URL)',
    supplementary_ai_score DECIMAL(5,2) COMMENT 'AI合理性评分',
    supplementary_status VARCHAR(20) COMMENT '补签状态: PENDING-待审核, APPROVED-已通过, REJECTED-已驳回',
    status VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'NORMAL/LATE/EARLY/ABSENT/SUPPLEMENTARY/PENDING_AUDIT/PENDING/APPROVED/REJECTED',
    approval_time DATETIME COMMENT '审批时间',
    reject_reason VARCHAR(500) COMMENT '驳回原因',
    duration_hours DECIMAL(6,2) COMMENT '时长(小时)',
    actual_duration_minutes SMALLINT COMMENT '实际时长(分钟)',
    credit_earned DECIMAL(5,2) DEFAULT 0 COMMENT '获得学分',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    UNIQUE KEY uk_attendance_user_activity (user_id, activity_id),
    INDEX idx_attendance_activity (activity_id),
    INDEX idx_attendance_user (user_id),
    INDEX idx_attendance_registration (registration_id),
    INDEX idx_attendance_act_status (activity_id, status),
    INDEX idx_attendance_user_time (user_id, create_time),
    INDEX idx_attendance_supplementary (is_supplementary, supplementary_status),
    INDEX idx_attendance_checkin_time (check_in_time),
    INDEX idx_att_act_time (activity_id, check_in_time DESC),
    INDEX idx_attendance_user_activity (user_id, activity_id),
    CONSTRAINT chk_attendance_status CHECK (status IN ('NORMAL', 'LATE', 'EARLY', 'ABSENT', 'SUPPLEMENTARY', 'PENDING_AUDIT', 'PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_attendance_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_registration FOREIGN KEY (registration_id) REFERENCES activity_registration(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考勤记录表';

-- 4.2 失信记录表
DROP TABLE IF EXISTS violation_record;
CREATE TABLE violation_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    activity_id BIGINT COMMENT '活动ID',
    registration_id BIGINT COMMENT '报名ID',
    violation_type VARCHAR(64) NOT NULL COMMENT '违规类型: ABSENT-无故缺席, LATE-迟到, EARLY-早退, FRAUD-虚假签到, OTHER-其他',
    violation_reason TEXT COMMENT '违规原因',
    evidence_urls TEXT COMMENT '证据URLs(JSON数组)',
    restriction_type VARCHAR(32) COMMENT '限制类型: SHORT_TERM-短期(1月), LONG_TERM-长期(1学期)',
    restriction_scope VARCHAR(64) COMMENT '限制范围',
    restriction_days SMALLINT DEFAULT 0 COMMENT '限制天数',
    restriction_end_time DATETIME COMMENT '限制结束时间',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-生效中, RELEASED-已解除, EXPIRED-已过期',
    release_reason VARCHAR(500) COMMENT '解除原因',
    released_by BIGINT COMMENT '解除人ID',
    released_time DATETIME COMMENT '解除时间',
    created_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_violation_user (user_id),
    INDEX idx_violation_activity (activity_id),
    INDEX idx_violation_user_status (user_id, status),
    INDEX idx_violation_status_time (status, restriction_end_time),
    INDEX idx_violation_type (violation_type),
    INDEX idx_violation_create_time (create_time),
    INDEX idx_violation_user_time (user_id, create_time DESC),
    CONSTRAINT fk_violation_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_violation_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE SET NULL,
    CONSTRAINT fk_violation_registration FOREIGN KEY (registration_id) REFERENCES activity_registration(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='失信记录表';

-- ============================================
-- 5. 时长管理模块 (2张表)
-- ============================================

-- 5.1 用户时长明细表
DROP TABLE IF EXISTS user_duration;
CREATE TABLE user_duration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    activity_id BIGINT COMMENT '活动ID',
    attendance_id BIGINT COMMENT '考勤ID',
    duration_type VARCHAR(32) DEFAULT 'ACTIVITY' COMMENT '时长类型: ACTIVITY-活动时长, TRAINING-培训时长, OTHER-其他',
    original_duration DECIMAL(6,2) NOT NULL COMMENT '原始时长(小时)',
    adjusted_duration DECIMAL(6,2) COMMENT '调整后时长(小时)',
    duration_minutes SMALLINT COMMENT '时长(分钟)',
    adjustment_reason VARCHAR(1000) COMMENT '调整原因',
    adjustment_type VARCHAR(32) COMMENT '调整类型: AUTO-自动, MANUAL-手动修正',
    status VARCHAR(20) DEFAULT 'APPROVED' COMMENT '审核状态: PENDING-待审核, APPROVED-已通过, REJECTED-已驳回',
    approver_id BIGINT COMMENT '审批人ID',
    approval_time DATETIME COMMENT '审批时间',
    approval_comment VARCHAR(500) COMMENT '审批意见',
    certificate_no VARCHAR(64) COMMENT '证书编号',
    remarks VARCHAR(1000) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_duration_user (user_id),
    INDEX idx_duration_activity (activity_id),
    INDEX idx_duration_status (status),
    INDEX idx_duration_user_time (user_id, create_time),
    INDEX idx_duration_user_type (user_id, duration_type),
    INDEX idx_duration_certificate (certificate_no),
    INDEX idx_duration_user_time_status (user_id, create_time DESC, status),
    INDEX idx_dur_user_type_time (user_id, duration_type, create_time DESC),
    CONSTRAINT chk_duration CHECK (original_duration >= 0 AND adjusted_duration >= 0),
    CONSTRAINT fk_duration_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_duration_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE SET NULL,
    CONSTRAINT fk_duration_attendance FOREIGN KEY (attendance_id) REFERENCES attendance(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户时长明细表';

-- 5.2 时长异议/修正表（V2.4.0）
DROP TABLE IF EXISTS duration_dispute;
CREATE TABLE duration_dispute (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '申请人ID',
    attendance_id BIGINT COMMENT '关联考勤记录ID',
    activity_id BIGINT COMMENT '关联活动ID',
    dispute_no VARCHAR(32) NOT NULL UNIQUE COMMENT '异议编号',
    original_duration DECIMAL(10,2) COMMENT '原始时长',
    requested_duration DECIMAL(10,2) COMMENT '申请时长',
    reason TEXT COMMENT '申请理由',
    evidence VARCHAR(512) COMMENT '证明材料URL',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    reviewer_id BIGINT COMMENT '审核人ID',
    review_comment TEXT COMMENT '审核意见',
    review_time DATETIME COMMENT '审核时间',
    approved_duration DECIMAL(10,2) COMMENT '批准时长',
    audit_trail TEXT COMMENT '审计留痕',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_dispute_user_id (user_id),
    INDEX idx_dispute_status (status),
    CONSTRAINT fk_dispute_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时长异议/修正表';

-- ============================================
-- 6. 消息中心模块 (1张表)
-- ============================================

-- 6.1 用户消息表
DROP TABLE IF EXISTS user_message;
CREATE TABLE user_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    sender_id BIGINT COMMENT '发送者ID',
    message_type VARCHAR(32) NOT NULL COMMENT '消息类型: SYSTEM-系统消息, ACTIVITY-活动通知, AUDIT-审核通知, ATTENDANCE-考勤通知, DURATION-时长通知',
    title VARCHAR(255) COMMENT '消息标题',
    content TEXT COMMENT '消息内容',
    channel VARCHAR(32) COMMENT '发送渠道',
    status VARCHAR(20) DEFAULT 'UNREAD' COMMENT 'UNREAD/READ',
    template_code VARCHAR(100) COMMENT '消息模板编码',
    related_id BIGINT COMMENT '关联ID',
    related_type VARCHAR(64) COMMENT '关联类型',
    send_time DATETIME COMMENT '发送时间',
    priority TINYINT DEFAULT 0 COMMENT '优先级: 0-普通, 1-重要, 2-紧急',
    is_read TINYINT(1) DEFAULT 0 COMMENT '是否已读: 0-未读, 1-已读',
    read_time DATETIME COMMENT '阅读时间',
    expire_time DATETIME COMMENT '过期时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_message_user (user_id),
    INDEX idx_message_user_read (user_id, is_read),
    INDEX idx_message_type (message_type),
    INDEX idx_message_related (related_id, related_type),
    INDEX idx_message_create_time (create_time),
    INDEX idx_user_message_unread (user_id, is_read, create_time DESC),
    CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户消息表';

-- ============================================
-- 7. 评价管理模块 (2张表)
-- ============================================

-- 7.1 活动评价表
DROP TABLE IF EXISTS evaluation_activity;
CREATE TABLE evaluation_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    user_id BIGINT NOT NULL COMMENT '用户ID(评价人)',
    rating TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '评分1-5',
    content TEXT COMMENT '评价内容',
    sentiment VARCHAR(32) COMMENT '情感分析: POSITIVE-正面, NEUTRAL-中性, NEGATIVE-负面',
    sentiment_score DECIMAL(3,2) COMMENT '情感得分(-1.00 ~ 1.00)',
    is_anonymous TINYINT(1) DEFAULT 0 COMMENT '是否匿名: 0-实名, 1-匿名',
    status VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT '状态: PUBLISHED-已发布, HIDDEN-已隐藏',
    reply_content TEXT COMMENT '回复内容',
    reply_time DATETIME COMMENT '回复时间',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_evaluation_activity_user (activity_id, user_id),
    INDEX idx_evaluation_activity (activity_id),
    INDEX idx_evaluation_user (user_id),
    INDEX idx_evaluation_status (status),
    INDEX idx_evaluation_sentiment (sentiment),
    INDEX idx_evaluation_create_time (create_time),
    CONSTRAINT chk_evaluation_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_evaluation_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_evaluation_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动评价表';

-- 7.2 志愿者评价表
DROP TABLE IF EXISTS evaluation_volunteer;
CREATE TABLE evaluation_volunteer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    user_id BIGINT NOT NULL COMMENT '被评价用户ID',
    reviewer_id BIGINT NOT NULL COMMENT '评价人ID',
    rating TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '评分1-5',
    content TEXT COMMENT '评价内容',
    tags VARCHAR(255) COMMENT '评价标签(逗号分隔)',
    status VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT '状态: PUBLISHED-已发布, HIDDEN-已隐藏',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_eval_vol_activity (activity_id),
    INDEX idx_eval_vol_user (user_id),
    INDEX idx_eval_vol_reviewer (reviewer_id),
    INDEX idx_eval_vol_create_time (create_time),
    CONSTRAINT chk_volunteer_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_eval_vol_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_vol_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_vol_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='志愿者评价表';

-- ============================================
-- 8. 表彰奖励管理模块 (1张表, V2.4.0)
-- ============================================

DROP TABLE IF EXISTS commendation;
CREATE TABLE commendation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    commendation_no VARCHAR(32) NOT NULL UNIQUE COMMENT '表彰编号',
    title VARCHAR(255) NOT NULL COMMENT '表彰标题',
    level VARCHAR(32) COMMENT '表彰级别 国家级/省级/市级/校级/院级',
    issuing_unit VARCHAR(128) COMMENT '颁发单位',
    issue_date DATE COMMENT '颁发日期',
    description TEXT COMMENT '表彰描述',
    certificate_url VARCHAR(512) COMMENT '证书图片URL',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    approved_by BIGINT COMMENT '审核人ID',
    approved_at DATETIME COMMENT '审核时间',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_commendation_user_id (user_id),
    INDEX idx_commendation_status (status),
    CONSTRAINT fk_commendation_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表彰奖励表';

-- ============================================
-- 9. AI模块 (2张表)
-- ============================================

-- 9.1 AI对话记录表
DROP TABLE IF EXISTS ai_conversation;
CREATE TABLE ai_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    message_type VARCHAR(32) NOT NULL COMMENT '消息类型: USER-用户消息, ASSISTANT-AI回复, SYSTEM-系统消息',
    content TEXT COMMENT '消息内容',
    model VARCHAR(64) COMMENT '使用的模型',
    token_count INT COMMENT 'Token数量',
    context_used TEXT COMMENT '使用的上下文',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation_user (user_id),
    INDEX idx_conversation_session (conversation_id),
    INDEX idx_conversation_time (create_time),
    INDEX idx_conversation_user_time (user_id, create_time),
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话记录表';

-- 9.2 知识库表
DROP TABLE IF EXISTS knowledge_base;
CREATE TABLE knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    content TEXT NOT NULL COMMENT '知识内容',
    content_hash VARCHAR(64) UNIQUE COMMENT '内容SHA-256哈希(用于去重)',
    category VARCHAR(64) COMMENT '知识分类',
    source VARCHAR(256) COMMENT '来源',
    metadata JSON COMMENT '元数据(JSON格式)',
    usage_count INT DEFAULT 0 COMMENT '使用次数',
    last_used_time DATETIME COMMENT '最后使用时间',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    created_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FULLTEXT INDEX ft_knowledge_content (content),
    INDEX idx_knowledge_category (category),
    INDEX idx_knowledge_source (source),
    INDEX idx_knowledge_status (status),
    INDEX idx_knowledge_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- ============================================
-- 10. 系统配置模块 (3张表)
-- ============================================

-- 10.1 系统配置表
DROP TABLE IF EXISTS system_config;
CREATE TABLE system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    config_type VARCHAR(50) DEFAULT 'string' COMMENT '配置类型: string-字符串, number-数字, boolean-布尔, json-JSON',
    description VARCHAR(200) COMMENT '配置描述',
    sort_order TINYINT DEFAULT 0 COMMENT '排序号',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_config_key (config_key),
    INDEX idx_config_type (config_type),
    INDEX idx_config_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 10.2 证书序列号表
DROP TABLE IF EXISTS certificate_sequence;
CREATE TABLE certificate_sequence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    prefix VARCHAR(16) NOT NULL COMMENT '前缀',
    current_value BIGINT NOT NULL COMMENT '当前值',
    step INT DEFAULT 1 COMMENT '步长',
    year INT COMMENT '年份',
    description VARCHAR(255) COMMENT '描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_certificate_prefix_year (prefix, year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='证书序列号表';

-- 10.3 系统公告表（V2.4.0）
DROP TABLE IF EXISTS system_announcement;
CREATE TABLE system_announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    title VARCHAR(255) NOT NULL COMMENT '公告标题',
    content TEXT COMMENT '公告内容',
    type VARCHAR(32) DEFAULT 'NOTICE' COMMENT 'NOTICE/WARNING/EVENT/SYSTEM',
    priority INT DEFAULT 0 COMMENT '优先级 0-普通 1-重要 2-紧急',
    cover_image VARCHAR(512) COMMENT '封面图片',
    publisher_id BIGINT COMMENT '发布人ID',
    publisher_name VARCHAR(64) COMMENT '发布人姓名',
    publish_time DATETIME COMMENT '发布时间',
    expire_time DATETIME COMMENT '过期时间',
    status TINYINT DEFAULT 1 COMMENT '1-发布 0-下架',
    view_count INT DEFAULT 0 COMMENT '浏览次数',
    is_top TINYINT DEFAULT 0 COMMENT '是否置顶',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_announcement_status_expire (status, expire_time),
    CONSTRAINT fk_announcement_publisher FOREIGN KEY (publisher_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统公告表';

-- ============================================
-- 11. GIS志愿地图模块 (1张表, V2.4.0)
-- ============================================

DROP TABLE IF EXISTS service_station;
CREATE TABLE service_station (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    station_name VARCHAR(128) NOT NULL COMMENT '服务站名称',
    address VARCHAR(512) NOT NULL COMMENT '地址',
    gps_lat DECIMAL(10,8) NOT NULL COMMENT '纬度',
    gps_lng DECIMAL(11,8) NOT NULL COMMENT '经度',
    service_hours VARCHAR(255) COMMENT '服务时间',
    contact_phone VARCHAR(32) COMMENT '联系电话',
    services TEXT COMMENT '服务清单 JSON',
    status TINYINT DEFAULT 1 COMMENT '1-启用 0-停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='志愿服务站表';

-- ============================================
-- 12. 操作审计日志模块 (1张表, V2.4.0)
-- ============================================

DROP TABLE IF EXISTS sys_operation_log;
CREATE TABLE sys_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(64) COMMENT '操作人姓名',
    op_type VARCHAR(32) COMMENT '操作类型',
    op_module VARCHAR(32) COMMENT '操作模块',
    op_content TEXT COMMENT '操作内容',
    op_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    ip_address VARCHAR(64) COMMENT 'IP地址',
    result VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '操作结果 SUCCESS/FAILURE',
    error_msg VARCHAR(500) COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_oplog_operator (operator_id),
    INDEX idx_oplog_op_time (op_time),
    INDEX idx_oplog_op_type (op_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';

-- ============================================
-- 13. 信用成长银行模块 (9张表)
-- ============================================

-- 13.1 用户积分表
DROP TABLE IF EXISTS user_credit;
CREATE TABLE user_credit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    total_credit INT DEFAULT 0 COMMENT '总积分',
    available_credit INT DEFAULT 0 COMMENT '可用积分',
    frozen_credit INT DEFAULT 0 COMMENT '冻结积分',
    growth_value INT DEFAULT 0 COMMENT '成长值',
    level VARCHAR(32) DEFAULT 'L1' COMMENT '等级',
    star_level TINYINT DEFAULT 0 COMMENT '星级志愿者等级',
    total_service_hours INT DEFAULT 0 COMMENT '累计服务时长',
    consecutive_volunteer_months INT DEFAULT 0 COMMENT '连续服务月数',
    completed_activity_count INT DEFAULT 0 COMMENT '完成活动数',
    certificate_count INT DEFAULT 0 COMMENT '证书数量',
    star_certification_status VARCHAR(32) DEFAULT 'UNVERIFIED' COMMENT '星级认证状态',
    last_star_certification_time DATETIME COMMENT '上次星级认证时间',
    next_star_certification_time DATETIME COMMENT '下次星级认证时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_credit_user_id (user_id),
    CONSTRAINT chk_credit CHECK (total_credit >= 0 AND available_credit >= 0 AND frozen_credit >= 0),
    CONSTRAINT fk_user_credit_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户积分表';

-- 13.2 积分流水表
DROP TABLE IF EXISTS credit_transaction;
CREATE TABLE credit_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    amount INT NOT NULL COMMENT '变动金额（正数为获得，负数为消费）',
    type VARCHAR(32) NOT NULL COMMENT '交易类型：EARN-获得，CONSUME-消费，FREEZE-冻结，UNFREEZE-解冻',
    source VARCHAR(64) COMMENT '来源：CHECKIN-签到，ACTIVITY-活动，DAILY_TASK-每日任务等',
    source_id BIGINT COMMENT '来源ID',
    description VARCHAR(500) COMMENT '交易描述',
    balance_before INT COMMENT '变动前余额',
    balance_after INT COMMENT '变动后余额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_ct_user_id (user_id),
    INDEX idx_ct_type (type),
    INDEX idx_ct_create_time (create_time),
    INDEX idx_ct_user_time (user_id, create_time DESC),
    CONSTRAINT chk_transaction_type CHECK (type IN ('EARN', 'CONSUME', 'FREEZE', 'UNFREEZE')),
    CONSTRAINT fk_credit_transaction_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表';

-- 13.3 勋章表
DROP TABLE IF EXISTS medal;
CREATE TABLE medal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(128) NOT NULL COMMENT '勋章名称',
    icon VARCHAR(128) COMMENT '勋章图标',
    description VARCHAR(500) COMMENT '勋章描述',
    category VARCHAR(32) COMMENT '勋章类别：PARTICIPATION-参与类，SKILL-技能类，PERSEVERANCE-坚持类，CONTRIBUTION-贡献类，FESTIVAL-节日类',
    rarity VARCHAR(32) DEFAULT 'COMMON' COMMENT '稀有度：COMMON-普通，RARE-稀有，EPIC-史诗，LEGENDARY-传说',
    required_condition VARCHAR(500) COMMENT '获取条件描述',
    required_value INT DEFAULT 0 COMMENT '获取条件数值',
    condition_type VARCHAR(32) COMMENT '条件类型：HOURS-时长，COUNT-次数，LEVEL-等级',
    reward_credit INT DEFAULT 0 COMMENT '获得勋章奖励积分',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    status TINYINT(1) DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_medal_category (category),
    INDEX idx_medal_rarity (rarity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='勋章表';

-- 13.4 用户勋章表
DROP TABLE IF EXISTS user_medal;
CREATE TABLE user_medal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    medal_id BIGINT NOT NULL COMMENT '勋章ID',
    obtain_time DATETIME COMMENT '获取时间',
    status VARCHAR(20) DEFAULT 'LOCKED' COMMENT '状态：LOCKED-未解锁，OBTAINED-已获得，DISPLAYED-已展示',
    progress DECIMAL(5,2) DEFAULT 0 COMMENT '解锁进度(0-1)',
    current_value INT DEFAULT 0 COMMENT '当前数值',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_um_user_id (user_id),
    INDEX idx_um_medal_id (medal_id),
    UNIQUE KEY uk_user_medal (user_id, medal_id),
    CONSTRAINT fk_user_medal_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_medal_medal FOREIGN KEY (medal_id) REFERENCES medal(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户勋章表';

-- 13.5 每日任务表
DROP TABLE IF EXISTS daily_task;
CREATE TABLE daily_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(128) NOT NULL COMMENT '任务名称',
    description VARCHAR(500) COMMENT '任务描述',
    reward INT NOT NULL COMMENT '奖励积分',
    target INT DEFAULT 1 COMMENT '目标数量',
    type VARCHAR(32) COMMENT '任务类型：VIEW-浏览，SHARE-分享，CHECKIN-签到，APPLY-报名',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    status TINYINT(1) DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_dt_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日任务表';

-- 13.6 用户任务完成记录表
DROP TABLE IF EXISTS user_task_record;
CREATE TABLE user_task_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    progress INT DEFAULT 0 COMMENT '当前进度',
    target INT DEFAULT 1 COMMENT '目标进度',
    status VARCHAR(20) DEFAULT 'NOT_STARTED' COMMENT '状态：NOT_STARTED-未开始，IN_PROGRESS-进行中，COMPLETED-已完成，REWARDED-已领奖',
    completed_time DATETIME COMMENT '完成时间',
    reward_time DATETIME COMMENT '领奖时间',
    date VARCHAR(10) NOT NULL COMMENT '任务日期(YYYY-MM-DD)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_utr_user_id (user_id),
    INDEX idx_utr_task_id (task_id),
    INDEX idx_utr_date (date),
    UNIQUE KEY uk_user_task_date (user_id, task_id, date),
    CONSTRAINT fk_user_task_record_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_task_record_task FOREIGN KEY (task_id) REFERENCES daily_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户任务完成记录表';

-- 13.7 用户待办事项表
DROP TABLE IF EXISTS user_todo;
CREATE TABLE user_todo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(255) NOT NULL COMMENT '待办标题',
    content VARCHAR(2000) COMMENT '待办详情',
    priority TINYINT DEFAULT 0 COMMENT '优先级: 0-普通, 1-重要, 2-紧急',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-未完成, 1-已完成, 2-已取消',
    due_time DATETIME COMMENT '截止时间',
    completed_time DATETIME COMMENT '完成时间',
    related_type VARCHAR(64) COMMENT '关联类型: ACTIVITY-活动, ATTENDANCE-考勤, TRAINING-培训',
    related_id BIGINT COMMENT '关联ID',
    reminder_sent TINYINT(1) DEFAULT 0 COMMENT '是否已发送提醒: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    INDEX idx_todo_user (user_id),
    INDEX idx_todo_user_status (user_id, status),
    INDEX idx_todo_user_due (user_id, due_time),
    INDEX idx_todo_related (related_id, related_type),
    CONSTRAINT fk_todo_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户待办事项表';

-- 13.8 积分商城商品表
DROP TABLE IF EXISTS credit_product;
CREATE TABLE credit_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '商品名称',
    description VARCHAR(1000) COMMENT '商品描述',
    category VARCHAR(32) COMMENT '商品类别：VIRTUAL-虚拟物品，PHYSICAL-实物，TRAINING-培训课程',
    credit_cost INT NOT NULL COMMENT '所需积分',
    stock INT DEFAULT 0 COMMENT '库存数量',
    image_url VARCHAR(512) COMMENT '商品图片',
    is_limited TINYINT(1) DEFAULT 0 COMMENT '是否限量',
    limit_per_user INT DEFAULT 0 COMMENT '每人限购数量(0为不限购)',
    exchange_count INT DEFAULT 0 COMMENT '兑换次数',
    status TINYINT(1) DEFAULT 1 COMMENT '状态：0-下架，1-上架',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_cp_category (category),
    INDEX idx_cp_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分商城商品表';

-- 13.9 积分订单表
DROP TABLE IF EXISTS credit_order;
CREATE TABLE credit_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    product_name VARCHAR(255) COMMENT '商品名称',
    quantity INT DEFAULT 1 COMMENT '数量',
    credit_cost INT NOT NULL COMMENT '消耗积分',
    shipping_address VARCHAR(1000) COMMENT '收货地址',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING-待处理，PROCESSING-处理中，SHIPPED-已发货，COMPLETED-已完成，CANCELLED-已取消',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_co_order_no (order_no),
    INDEX idx_co_user_id (user_id),
    INDEX idx_co_status (status),
    CONSTRAINT fk_credit_order_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_order_product FOREIGN KEY (product_id) REFERENCES credit_product(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分订单表';

-- ============================================
-- 14. 技能认证模块 (5张表)
-- ============================================

-- 14.1 技能分类表
DROP TABLE IF EXISTS skill_category;
CREATE TABLE skill_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(64) NOT NULL COMMENT '技能分类名称',
    description VARCHAR(500) COMMENT '分类描述',
    icon VARCHAR(255) COMMENT '图标路径',
    sort_order INT DEFAULT 0 COMMENT '排序序号',
    status TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_sc_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能分类表';

-- 14.2 技能表
DROP TABLE IF EXISTS skill;
CREATE TABLE skill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    category_id BIGINT NOT NULL COMMENT '所属分类ID',
    name VARCHAR(64) NOT NULL COMMENT '技能名称',
    description VARCHAR(500) COMMENT '技能描述',
    icon VARCHAR(255) COMMENT '图标路径',
    level VARCHAR(16) COMMENT '技能等级要求',
    required_hours INT DEFAULT 0 COMMENT '需要志愿服务时长(小时)',
    required_activities INT DEFAULT 0 COMMENT '需要服务活动次数',
    status TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_skill_category_id (category_id),
    UNIQUE INDEX uk_skill_category_name (category_id, name),
    CONSTRAINT fk_skill_category FOREIGN KEY (category_id) REFERENCES skill_category(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能表';

-- 14.3 用户技能表
DROP TABLE IF EXISTS user_skill;
CREATE TABLE user_skill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    skill_id BIGINT NOT NULL COMMENT '技能ID',
    level VARCHAR(16) DEFAULT '初级' COMMENT '技能等级',
    proficiency INT DEFAULT 0 COMMENT '熟练度 0-100',
    verified_hours INT DEFAULT 0 COMMENT '已验证时长(小时)',
    verified_activities INT DEFAULT 0 COMMENT '已验证活动次数',
    status VARCHAR(32) DEFAULT 'PENDING' COMMENT '状态 PENDING-待审核 APPROVED-已认证 REJECTED-已拒绝',
    certificate_no VARCHAR(64) COMMENT '证书编号',
    certificate_url VARCHAR(500) COMMENT '证书链接',
    apply_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    approve_time DATETIME COMMENT '审核时间',
    approver_id BIGINT COMMENT '审核人ID',
    reject_reason VARCHAR(500) COMMENT '拒绝理由',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_us_user_id (user_id),
    INDEX idx_us_skill_id (skill_id),
    UNIQUE INDEX uk_user_skill (user_id, skill_id),
    CONSTRAINT fk_user_skill_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_skill_skill FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户技能表';

-- 14.4 技能认证申请表
DROP TABLE IF EXISTS certification_application;
CREATE TABLE certification_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    skill_id BIGINT NOT NULL COMMENT '技能ID',
    target_level VARCHAR(16) NOT NULL COMMENT '目标等级',
    proof_files TEXT COMMENT '证明材料(JSON格式)',
    description TEXT COMMENT '申请说明',
    status VARCHAR(32) DEFAULT 'PENDING' COMMENT '状态 PENDING-待审核 APPROVED-已通过 REJECTED-已拒绝',
    apply_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    review_time DATETIME COMMENT '审核时间',
    reviewer_id BIGINT COMMENT '审核人ID',
    review_comments VARCHAR(500) COMMENT '审核意见',
    certificate_no VARCHAR(64) COMMENT '证书编号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ca_user_id (user_id),
    INDEX idx_ca_skill_id (skill_id),
    INDEX idx_ca_status (status),
    CONSTRAINT fk_cert_app_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_cert_app_skill FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能认证申请表';

-- 14.5 活动技能匹配表
DROP TABLE IF EXISTS skill_matching;
CREATE TABLE skill_matching (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    skill_id BIGINT NOT NULL COMMENT '技能ID',
    required_level VARCHAR(16) COMMENT '要求等级',
    importance INT DEFAULT 1 COMMENT '重要程度 1-5',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_sm_activity_id (activity_id),
    INDEX idx_sm_skill_id (skill_id),
    UNIQUE INDEX uk_activity_skill (activity_id, skill_id),
    CONSTRAINT fk_skill_matching_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_skill_matching_skill FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动技能匹配表';

-- ============================================
-- 15. 智能推荐模块 (5张表)
-- ============================================

-- 15.1 用户画像表
DROP TABLE IF EXISTS user_profile;
CREATE TABLE user_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    interests TEXT COMMENT '兴趣爱好(JSON数组)',
    preferred_areas VARCHAR(500) COMMENT '偏好服务区域',
    preferred_time_slots VARCHAR(200) COMMENT '偏好服务时段',
    activity_history TEXT COMMENT '参与活动历史(JSON数组)',
    skill_tags VARCHAR(500) COMMENT '技能标签',
    personality_type VARCHAR(32) COMMENT '性格类型',
    availability_score INT DEFAULT 0 COMMENT '活跃度评分',
    reliability_score INT DEFAULT 0 COMMENT '可靠性评分',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_up_user_id (user_id),
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户画像表';

-- 15.2 活动偏好表
DROP TABLE IF EXISTS activity_preference;
CREATE TABLE activity_preference (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    activity_type VARCHAR(64) COMMENT '偏好活动类型',
    category VARCHAR(64) COMMENT '活动分类',
    priority INT DEFAULT 1 COMMENT '优先级 1-5',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ap_user_id (user_id),
    UNIQUE INDEX uk_ap_user_type (user_id, activity_type),
    CONSTRAINT fk_activity_preference_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动偏好表';

-- 15.3 推荐记录表
DROP TABLE IF EXISTS recommendation;
CREATE TABLE recommendation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    score DECIMAL(5,2) DEFAULT 0 COMMENT '推荐分数 0-100',
    reason VARCHAR(500) COMMENT '推荐理由',
    source VARCHAR(32) COMMENT '推荐来源',
    expires_at DATETIME COMMENT '过期时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_rec_user_id (user_id),
    INDEX idx_rec_activity_id (activity_id),
    UNIQUE INDEX uk_rec_user_activity (user_id, activity_id),
    CONSTRAINT fk_recommendation_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐记录表';

-- 15.4 推荐行为日志表
DROP TABLE IF EXISTS recommendation_log;
CREATE TABLE recommendation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    action VARCHAR(32) COMMENT '操作类型 VIEW/CLICK/APPLY/SKIP',
    action_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    session_id VARCHAR(64) COMMENT '会话ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_rl_user_id (user_id),
    INDEX idx_rl_activity_id (activity_id),
    INDEX idx_rl_action_time (action_time),
    CONSTRAINT fk_recommendation_log_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_log_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐行为日志表';

-- 15.5 活动热度统计表
DROP TABLE IF EXISTS popular_activity;
CREATE TABLE popular_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    view_count INT DEFAULT 0 COMMENT '浏览次数',
    apply_count INT DEFAULT 0 COMMENT '报名次数',
    share_count INT DEFAULT 0 COMMENT '分享次数',
    hot_score DECIMAL(10,2) DEFAULT 0 COMMENT '热度分数',
    date DATE NOT NULL COMMENT '统计日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_pa_activity_date (activity_id, date),
    CONSTRAINT fk_popular_activity_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动热度统计表';

-- ============================================
-- 16. 活动健康诊断模块 (4张表)
-- ============================================

-- 16.1 活动健康度表
DROP TABLE IF EXISTS activity_health;
CREATE TABLE activity_health (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    overall_score DECIMAL(5,2) DEFAULT 0 COMMENT '综合健康度评分 0-100',
    participation_score DECIMAL(5,2) DEFAULT 0 COMMENT '参与度评分',
    engagement_score DECIMAL(5,2) DEFAULT 0 COMMENT '互动度评分',
    quality_score DECIMAL(5,2) DEFAULT 0 COMMENT '质量评分',
    health_status VARCHAR(32) DEFAULT 'HEALTHY' COMMENT '健康状态 HEALTHY/CAUTION/DANGER',
    diagnosis_date DATE NOT NULL COMMENT '诊断日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_ah_activity_date (activity_id, diagnosis_date),
    INDEX idx_ah_activity_id (activity_id),
    CONSTRAINT fk_activity_health_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动健康度表';

-- 16.2 活动指标表
DROP TABLE IF EXISTS activity_metric;
CREATE TABLE activity_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    metric_type VARCHAR(64) COMMENT '指标类型',
    metric_value DECIMAL(10,2) COMMENT '指标值',
    metric_date DATE NOT NULL COMMENT '统计日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_am_activity_id (activity_id),
    INDEX idx_am_metric_type (metric_type),
    UNIQUE INDEX uk_am_activity_metric_date (activity_id, metric_type, metric_date),
    CONSTRAINT fk_activity_metric_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动指标表';

-- 16.3 诊断报告表
DROP TABLE IF EXISTS diagnosis_report;
CREATE TABLE diagnosis_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    report_title VARCHAR(200) COMMENT '报告标题',
    report_content TEXT COMMENT '报告内容(JSON格式)',
    report_type VARCHAR(32) COMMENT '报告类型 WEEKLY/MONTHLY/CUSTOM',
    period_start DATE COMMENT '报告周期开始',
    period_end DATE COMMENT '报告周期结束',
    generated_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_dr_activity_id (activity_id),
    INDEX idx_dr_report_type (report_type),
    CONSTRAINT fk_diagnosis_report_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='诊断报告表';

-- 16.4 改进建议表
DROP TABLE IF EXISTS improvement_suggestion;
CREATE TABLE improvement_suggestion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    suggestion_type VARCHAR(64) COMMENT '建议类型',
    severity VARCHAR(32) DEFAULT 'LOW' COMMENT '严重程度 HIGH/MEDIUM/LOW',
    title VARCHAR(200) COMMENT '建议标题',
    description TEXT COMMENT '建议描述',
    suggested_action TEXT COMMENT '建议操作',
    priority INT DEFAULT 1 COMMENT '优先级 1-5',
    status VARCHAR(32) DEFAULT 'PENDING' COMMENT '状态 PENDING/IMPLEMENTED/DISCARDED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_is_activity_id (activity_id),
    INDEX idx_is_severity (severity),
    INDEX idx_is_status (status),
    CONSTRAINT fk_improvement_suggestion_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='改进建议表';

-- ============================================
-- 17. 课程表系统模块 (5张表)
-- ============================================

-- 17.1 课程表
DROP TABLE IF EXISTS course_schedule;
CREATE TABLE course_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    course_name VARCHAR(100) NOT NULL COMMENT '课程名称',
    course_code VARCHAR(50) COMMENT '课程代码',
    teacher_name VARCHAR(50) COMMENT '教师姓名',
    classroom VARCHAR(50) COMMENT '教室位置',
    day_of_week INT NOT NULL COMMENT '星期几 1-7对应周一至周日',
    period INT NOT NULL COMMENT '第几节课 1-5对应每天五节课',
    start_week INT DEFAULT 1 COMMENT '起始周',
    end_week INT DEFAULT 16 COMMENT '结束周',
    week_type VARCHAR(20) DEFAULT 'EVERY' COMMENT '周次类型 EVERY-每周 SINGLE-单周 DOUBLE-双周',
    color_code VARCHAR(20) DEFAULT 'RED' COMMENT '颜色编码 RED-每周 YELLOW-单双周',
    notes VARCHAR(200) COMMENT '备注信息',
    status TINYINT DEFAULT 1 COMMENT '状态 0-已删除 1-有效',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_cs_user_id (user_id),
    INDEX idx_cs_day_period (day_of_week, period),
    INDEX idx_cs_week_range (start_week, end_week),
    UNIQUE INDEX uk_cs_user_day_period_week (user_id, day_of_week, period, start_week, end_week, week_type),
    CONSTRAINT fk_course_schedule_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

-- 17.2 时间段配置表
DROP TABLE IF EXISTS time_slot_config;
CREATE TABLE time_slot_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    period INT NOT NULL COMMENT '第几节课 1-5',
    start_time VARCHAR(10) NOT NULL COMMENT '开始时间 HH:mm格式',
    end_time VARCHAR(10) NOT NULL COMMENT '结束时间 HH:mm格式',
    duration_minutes INT DEFAULT 100 COMMENT '时长(分钟)',
    sort_order INT DEFAULT 0 COMMENT '排序序号',
    status TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_tsc_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时间段配置表';

-- 17.3 学期周次配置表
DROP TABLE IF EXISTS week_config;
CREATE TABLE week_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    semester_id VARCHAR(50) NOT NULL COMMENT '学期ID',
    total_weeks INT DEFAULT 16 COMMENT '总周数',
    current_week INT DEFAULT 1 COMMENT '当前周次',
    semester_start_date DATE COMMENT '学期开始日期',
    semester_end_date DATE COMMENT '学期结束日期',
    status TINYINT DEFAULT 1 COMMENT '状态 0-已结束 1-进行中',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_wc_semester (semester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学期周次配置表';

-- 17.4 课程导入日志表
DROP TABLE IF EXISTS course_import_log;
CREATE TABLE course_import_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    import_type VARCHAR(20) COMMENT '导入类型 EXCEL/CSV/MANUAL',
    file_name VARCHAR(200) COMMENT '文件名',
    total_records INT COMMENT '总记录数',
    success_records INT COMMENT '成功记录数',
    failed_records INT COMMENT '失败记录数',
    error_details TEXT COMMENT '错误详情(JSON)',
    import_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_cil_user_id (user_id),
    INDEX idx_cil_import_time (import_time),
    CONSTRAINT fk_course_import_log_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程导入日志表';

-- 17.5 活动冲突检测记录表
DROP TABLE IF EXISTS activity_conflict_check;
CREATE TABLE activity_conflict_check (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    activity_start_time DATETIME NOT NULL COMMENT '活动开始时间',
    activity_end_time DATETIME NOT NULL COMMENT '活动结束时间',
    has_conflict TINYINT DEFAULT 0 COMMENT '是否有冲突 0-无冲突 1-有冲突',
    conflict_course_ids VARCHAR(500) COMMENT '冲突课程ID列表',
    conflict_details TEXT COMMENT '冲突详情(JSON)',
    check_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '检测时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_acc_user_id (user_id),
    INDEX idx_acc_activity_id (activity_id),
    INDEX idx_acc_has_conflict (has_conflict),
    CONSTRAINT fk_activity_conflict_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_conflict_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动冲突检测记录表';

-- ============================================
-- 18. 数据导出模块 (1张表)
-- ============================================

-- ============================================
-- 18. Extension business tables
-- ============================================

DROP TABLE IF EXISTS activity_review;
CREATE TABLE activity_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    activity_id BIGINT NOT NULL COMMENT 'Activity ID',
    user_id BIGINT NOT NULL COMMENT 'Reviewer user ID',
    rating INT NOT NULL COMMENT 'Rating 1-5',
    content TEXT COMMENT 'Review content',
    images TEXT COMMENT 'Image JSON',
    tags VARCHAR(500) COMMENT 'Tags',
    status VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/HIDDEN/DELETED',
    reply_id BIGINT COMMENT 'Reply ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    UNIQUE KEY uk_activity_review_user (activity_id, user_id),
    INDEX idx_activity_review_activity (activity_id),
    INDEX idx_activity_review_user (user_id),
    INDEX idx_activity_review_status (status),
    CONSTRAINT chk_activity_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_activity_review_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_review_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Activity review';

DROP TABLE IF EXISTS volunteer_review;
CREATE TABLE volunteer_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    reviewed_user_id BIGINT NOT NULL COMMENT 'Reviewed user ID',
    reviewer_user_id BIGINT NOT NULL COMMENT 'Reviewer user ID',
    activity_id BIGINT NOT NULL COMMENT 'Activity ID',
    rating INT NOT NULL COMMENT 'Rating 1-5',
    content TEXT COMMENT 'Review content',
    tags VARCHAR(500) COMMENT 'Tags',
    status VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/HIDDEN/DELETED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    UNIQUE KEY uk_volunteer_review_pair (reviewed_user_id, reviewer_user_id, activity_id),
    INDEX idx_volunteer_review_reviewed (reviewed_user_id),
    INDEX idx_volunteer_review_reviewer (reviewer_user_id),
    INDEX idx_volunteer_review_activity (activity_id),
    INDEX idx_volunteer_review_status (status),
    CONSTRAINT chk_volunteer_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_volunteer_review_reviewed FOREIGN KEY (reviewed_user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_volunteer_review_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_volunteer_review_activity FOREIGN KEY (activity_id) REFERENCES activity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Volunteer peer review';

DROP TABLE IF EXISTS feedback;
CREATE TABLE feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    user_id BIGINT NOT NULL COMMENT 'User ID',
    type VARCHAR(50) NOT NULL COMMENT 'Feedback type',
    title VARCHAR(200) NOT NULL COMMENT 'Title',
    content TEXT NOT NULL COMMENT 'Content',
    images TEXT COMMENT 'Image JSON',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/REPLIED/CLOSED',
    reply TEXT COMMENT 'Reply content',
    reply_time DATETIME COMMENT 'Reply time',
    reply_by BIGINT COMMENT 'Reply user ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    INDEX idx_feedback_user (user_id),
    INDEX idx_feedback_status (status),
    INDEX idx_feedback_type (type),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_reply_by FOREIGN KEY (reply_by) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User feedback';

DROP TABLE IF EXISTS training_course;
CREATE TABLE training_course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    title VARCHAR(200) NOT NULL COMMENT 'Course title',
    description TEXT COMMENT 'Course description',
    category VARCHAR(64) COMMENT 'Category',
    level VARCHAR(32) COMMENT 'Level',
    duration_minutes INT DEFAULT 0 COMMENT 'Duration minutes',
    credit_reward DECIMAL(10,2) DEFAULT 0 COMMENT 'Credit reward',
    cover_image VARCHAR(512) COMMENT 'Cover image',
    video_url VARCHAR(512) COMMENT 'Video URL',
    materials TEXT COMMENT 'Materials',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    view_count INT DEFAULT 0 COMMENT 'View count',
    completion_count INT DEFAULT 0 COMMENT 'Completion count',
    created_by BIGINT COMMENT 'Creator ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    INDEX idx_training_course_category (category),
    INDEX idx_training_course_level (level),
    INDEX idx_training_course_status (status),
    CONSTRAINT fk_training_course_creator FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Training course';

DROP TABLE IF EXISTS training_record;
CREATE TABLE training_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    user_id BIGINT NOT NULL COMMENT 'User ID',
    course_id BIGINT NOT NULL COMMENT 'Course ID',
    status VARCHAR(20) DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/COMPLETED',
    progress INT DEFAULT 0 COMMENT 'Progress 0-100',
    start_time DATETIME COMMENT 'Start time',
    completion_time DATETIME COMMENT 'Completion time',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    UNIQUE KEY uk_training_record_user_course (user_id, course_id),
    INDEX idx_training_record_user (user_id),
    INDEX idx_training_record_course (course_id),
    INDEX idx_training_record_status (status),
    CONSTRAINT chk_training_record_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT fk_training_record_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_training_record_course FOREIGN KEY (course_id) REFERENCES training_course(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Training record';

DROP TABLE IF EXISTS certification;
CREATE TABLE certification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    user_id BIGINT NOT NULL COMMENT 'User ID',
    name VARCHAR(200) NOT NULL COMMENT 'Certificate name',
    code VARCHAR(100) NOT NULL UNIQUE COMMENT 'Certificate code',
    type VARCHAR(64) COMMENT 'Certificate type',
    issuer VARCHAR(200) COMMENT 'Issuer',
    description TEXT COMMENT 'Description',
    issue_date DATETIME COMMENT 'Issue date',
    expire_date DATETIME COMMENT 'Expire date',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/REVOKED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    INDEX idx_certification_user (user_id),
    INDEX idx_certification_status (status),
    CONSTRAINT fk_certification_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Training certification';

DROP TABLE IF EXISTS external_platform_duration;
CREATE TABLE external_platform_duration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    user_id BIGINT NOT NULL COMMENT 'User ID',
    platform_code VARCHAR(64) NOT NULL COMMENT 'Platform code',
    platform_name VARCHAR(100) COMMENT 'Platform name',
    duration_hours DECIMAL(10,2) NOT NULL COMMENT 'Duration hours',
    activity_name VARCHAR(200) COMMENT 'Activity name',
    activity_date VARCHAR(32) COMMENT 'Activity date',
    certificate_number VARCHAR(100) COMMENT 'External certificate number',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    reject_reason VARCHAR(500) COMMENT 'Reject reason',
    import_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Import time',
    audit_time DATETIME COMMENT 'Audit time',
    auditor_id BIGINT COMMENT 'Auditor ID',
    INDEX idx_external_duration_user (user_id),
    INDEX idx_external_duration_status (status),
    INDEX idx_external_duration_platform (platform_code),
    CONSTRAINT fk_external_duration_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_external_duration_auditor FOREIGN KEY (auditor_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='External platform duration';

DROP TABLE IF EXISTS time_bank_account;
CREATE TABLE time_bank_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    user_id BIGINT NOT NULL UNIQUE COMMENT 'User ID',
    balance DECIMAL(10,2) DEFAULT 0 COMMENT 'Balance',
    total_earned DECIMAL(10,2) DEFAULT 0 COMMENT 'Total earned',
    total_spent DECIMAL(10,2) DEFAULT 0 COMMENT 'Total spent',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    CONSTRAINT chk_time_bank_account_non_negative CHECK (balance >= 0 AND total_earned >= 0 AND total_spent >= 0),
    CONSTRAINT fk_time_bank_account_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Time bank account';

DROP TABLE IF EXISTS time_bank_service;
CREATE TABLE time_bank_service (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    provider_id BIGINT NOT NULL COMMENT 'Provider ID',
    title VARCHAR(200) NOT NULL COMMENT 'Service title',
    description TEXT COMMENT 'Description',
    category VARCHAR(64) COMMENT 'Category',
    tbc_cost DECIMAL(10,2) NOT NULL COMMENT 'TBC cost',
    available_count INT DEFAULT 0 COMMENT 'Available count',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    service_type VARCHAR(64) COMMENT 'Service type',
    location VARCHAR(255) COMMENT 'Location',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    INDEX idx_time_bank_service_provider (provider_id),
    INDEX idx_time_bank_service_category (category),
    INDEX idx_time_bank_service_status (status),
    CONSTRAINT chk_time_bank_service_non_negative CHECK (tbc_cost >= 0 AND available_count >= 0),
    CONSTRAINT fk_time_bank_service_provider FOREIGN KEY (provider_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Time bank service';

DROP TABLE IF EXISTS time_bank_transaction;
CREATE TABLE time_bank_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    user_id BIGINT NOT NULL COMMENT 'User ID',
    type VARCHAR(32) NOT NULL COMMENT 'DEPOSIT/WITHDRAW/TRANSFER_IN/TRANSFER_OUT',
    amount DECIMAL(10,2) NOT NULL COMMENT 'Amount',
    balance_before DECIMAL(10,2) NOT NULL COMMENT 'Balance before',
    balance_after DECIMAL(10,2) NOT NULL COMMENT 'Balance after',
    source_id BIGINT COMMENT 'Source ID',
    source_type VARCHAR(64) COMMENT 'Source type',
    description VARCHAR(500) COMMENT 'Description',
    target_user_id BIGINT COMMENT 'Target user ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    INDEX idx_time_bank_tx_user (user_id),
    INDEX idx_time_bank_tx_type (type),
    INDEX idx_time_bank_tx_create_time (create_time),
    CONSTRAINT chk_time_bank_tx_amount CHECK (amount > 0),
    CONSTRAINT fk_time_bank_tx_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_time_bank_tx_target FOREIGN KEY (target_user_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Time bank transaction';

DROP TABLE IF EXISTS message_template;
CREATE TABLE message_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    template_code VARCHAR(100) NOT NULL UNIQUE COMMENT 'Template code',
    template_name VARCHAR(100) NOT NULL COMMENT 'Template name',
    title VARCHAR(200) COMMENT 'Title',
    content TEXT NOT NULL COMMENT 'Content',
    channel VARCHAR(32) DEFAULT 'SYSTEM' COMMENT 'SYSTEM/SMS/EMAIL',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    description VARCHAR(500) COMMENT 'Description',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    INDEX idx_message_template_channel (channel),
    INDEX idx_message_template_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Message template';

DROP TABLE IF EXISTS export_task;
CREATE TABLE export_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_id VARCHAR(64) NOT NULL UNIQUE COMMENT '任务ID（UUID）',
    export_type VARCHAR(64) NOT NULL COMMENT '导出类型: ACTIVITY-活动, ATTENDANCE-考勤, DURATION-时长, CREDIT-积分等',
    export_format VARCHAR(16) DEFAULT 'XLSX' COMMENT '导出格式: XLSX/CSV',
    file_name VARCHAR(255) COMMENT '生成文件名',
    file_url VARCHAR(512) COMMENT '文件存储URL（MinIO）',
    file_size BIGINT COMMENT '文件大小(字节)',
    total_rows INT DEFAULT 0 COMMENT '总行数',
    processed_rows INT DEFAULT 0 COMMENT '已处理行数',
    progress INT DEFAULT 0 COMMENT '进度(0-100)',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING-待处理, PROCESSING-处理中, SUCCESS-成功, FAILED-失败, EXPIRED-已过期',
    error_message VARCHAR(2000) COMMENT '错误信息',
    query_params TEXT COMMENT '查询参数(JSON)',
    columns_config TEXT COMMENT '列配置(JSON)',
    creator_id BIGINT COMMENT '创建人ID',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    expire_time DATETIME COMMENT '文件过期时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_export_type (export_type),
    INDEX idx_export_status (status),
    INDEX idx_export_creator (creator_id),
    INDEX idx_export_create_time (create_time),
    INDEX idx_export_status_creator (status, creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步导出任务表';

-- ============================================
-- 19. ShedLock 分布式定时任务锁表
-- 由 ShedLock 运行时读写，保证多副本部署时 @Scheduled 只在一个实例执行
-- ============================================
DROP TABLE IF EXISTS shedlock;
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL COMMENT '任务名称（锁名称）',
    lock_until TIMESTAMP(3) NOT NULL COMMENT '锁到期时间',
    locked_at  TIMESTAMP(3) NOT NULL COMMENT '加锁时间',
    locked_by  VARCHAR(255) NOT NULL COMMENT '持锁实例标识',
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ShedLock 分布式定时任务锁表';

-- ============================================
-- 以下为种子数据（Seed Data）
-- ============================================

-- ============================================
-- 初始化活动分类（必须在activity种子数据之前）
-- ============================================
INSERT INTO activity_category (category_name, category_code, icon, description, sort_order, status) VALUES
('公益服务', 'PUBLIC_WELFARE', 'heart', '关爱弱势群体，提供志愿服务', 1, 1),
('社区服务', 'COMMUNITY', 'home', '社区环境整治、便民服务', 2, 1),
('环境保护', 'ENVIRONMENT', 'leaf', '环保宣传、垃圾分类等', 3, 1),
('教育支持', 'EDUCATION', 'book', '支教、辅导、培训等', 4, 1),
('文化传承', 'CULTURE', 'culture', '传统文化宣传与保护', 5, 1),
('应急救援', 'EMERGENCY', 'alert', '应急响应与救援支持', 6, 1),
('健康医疗', 'HEALTH', 'medicine', '健康宣传、医疗辅助', 7, 1),
('其他', 'OTHER', 'ellipsis', '其他类型志愿活动', 8, 1),
-- 以下 3 项原由历史迁移 V9 / V10 写入，此前未并入本脚本，
-- 导致前端下拉框可选但提交时 ActivityService 校验报「活动分类不存在」
('校园服务', 'CAMPUS', 'school', '校园引导、迎新、赛事保障等校内服务', 9, 1),
('助老服务', 'ELDERLY', 'users', '面向老年群体的陪伴、帮扶与便民服务', 10, 1),
('医疗辅助', 'MEDICAL', 'first-aid', '医疗秩序维护、导诊与健康支持服务', 11, 1);

-- ============================================
-- 初始化角色数据
-- ============================================
INSERT INTO sys_role (role_name, role_code, description, status, sort_order) VALUES
('超级管理员', 'ADMIN', '系统超级管理员，拥有所有权限', 1, 1),
('活动组织者', 'ORGANIZER', '活动组织者，仅可管理本院活动', 1, 2),
('志愿者', 'VOLUNTEER', '普通志愿者用户，可以报名参加活动', 1, 3),
('学院管理员', 'COLLEGE_ADMIN', '学院管理员，管理本学院的志愿者活动', 1, 4),
('系统审核员', 'AUDITOR', '系统审核员，负责审核活动和用户', 1, 5);

-- ============================================
-- 初始化权限数据
-- ============================================
INSERT INTO sys_permission (parent_id, permission_name, permission_code, permission_type, path, icon, sort_order, status) VALUES
(0, '系统管理', 'system', 1, '/system', 'setting', 1, 1),
(1, '用户管理', 'system:user', 2, '/system/user', 'user', 1, 1),
(1, '角色管理', 'system:role', 2, '/system/role', 'team', 2, 1),
(0, '活动管理', 'activity', 1, '/activity', 'calendar', 2, 1),
(4, '活动列表', 'activity:list', 2, '/activity/list', 'list', 1, 1),
(4, '活动审核', 'activity:audit', 2, '/activity/audit', 'check', 2, 1),
(4, '活动创建', 'activity:create', 2, '/activity/create', 'plus', 3, 1),
(0, '考勤管理', 'attendance', 1, '/attendance', 'clock', 3, 1),
(8, '签到管理', 'attendance:checkin', 2, '/attendance/checkin', 'check-circle', 1, 1),
(8, '补签审核', 'attendance:supplement', 2, '/attendance/supplement', 'file-text', 2, 1),
(0, '报表统计', 'report', 1, '/report', 'chart', 4, 1),
(10, '个人报表', 'report:personal', 2, '/report/personal', 'user', 1, 1),
(10, '组织报表', 'report:organization', 2, '/report/organization', 'team', 2, 1),
(0, '消息中心', 'notification', 1, '/notification', 'message', 5, 1),
(0, 'AI智能助手', 'ai', 1, '/ai', 'robot', 6, 1);

-- ============================================
-- 初始化测试用户 (BCrypt加密密码, 工作因子=12)
-- 密码说明: 见脚本顶部"密码策略"段,部署前需重新 BCrypt 编码
-- ============================================
INSERT INTO sys_user (username, password, real_name, phone, email, college_name, student_no, status) VALUES
('admin', '$2b$12$rhxBi9rTkrA/iwcMSoY0Ye8McdhZxulkhd9xI6NmkQSc0Ie6/ND1i', '系统管理员', '13800138000', 'admin@volunteer.com', '系统管理部', '', 1),
('organizer', '$2b$12$u0zaKCHU.JMaY5/cfXrTfuP5BpP5/qlmTtEJSIV4OhkfrTcstsFzW', '组织者小李', '13800138001', 'organizer@volunteer.com', '志愿者协会', '', 1),
('volunteer1', '$2b$12$Kr63Hdd92XcX84r95s9M9ei8rvwXW2Yfacmt86MpE4s1lP/hxVjSG', '张三', '13800138002', 'zhangsan@volunteer.com', '计算机学院', '2024001', 1),
('volunteer2', '$2b$12$Kr63Hdd92XcX84r95s9M9ei8rvwXW2Yfacmt86MpE4s1lP/hxVjSG', '李四', '13800138003', 'lisi@volunteer.com', '软件学院', '2024002', 1),
('volunteer3', '$2b$12$Kr63Hdd92XcX84r95s9M9ei8rvwXW2Yfacmt86MpE4s1lP/hxVjSG', '王五', '13800138004', 'wangwu@volunteer.com', '信息学院', '2024003', 1),
('college_admin1', '$2b$12$Kr63Hdd92XcX84r95s9M9ei8rvwXW2Yfacmt86MpE4s1lP/hxVjSG', '计算机学院管理员', '13800138005', 'admin1@cs.volunteer.com', '计算机学院', '', 1),
('college_admin2', '$2b$12$Kr63Hdd92XcX84r95s9M9ei8rvwXW2Yfacmt86MpE4s1lP/hxVjSG', '软件学院管理员', '13800138006', 'admin2@se.volunteer.com', '软件学院', '', 1),
('auditor', '$2b$12$Kr63Hdd92XcX84r95s9M9ei8rvwXW2Yfacmt86MpE4s1lP/hxVjSG', '系统审核员', '13800138007', 'auditor@volunteer.com', '系统管理部', '', 1);

-- ============================================
-- 关联用户角色
-- ============================================
UPDATE sys_user SET password = '$2a$12$qk.S/mQIxsYYjnFGhbBZe.2IEebqqQzxHuVVEFj3Fy9QLl88BPeF6' WHERE username = 'admin';
UPDATE sys_user SET password = '$2a$12$ws5XMgzjPqx3yibM2o4eguCIDBz7cfV9xlXC8ScaejmN.cNtj3R1y' WHERE username = 'organizer';
UPDATE sys_user SET password = '$2a$12$Fd2HKiH1IJasfr7omFwnoeEleUI7CY4kUR2CGjpD2PVvfdF0vNMS2' WHERE username IN ('volunteer1', 'volunteer2', 'volunteer3');
UPDATE sys_user SET password = '$2a$12$BiUIFGSrrRw01gl4i8rp8.QBqKPrJKGehUjYdkZiw2SBKszbryxwu' WHERE username IN ('college_admin1', 'college_admin2');
UPDATE sys_user SET password = '$2a$12$uOyJ4WYBQFxLIkJMm2z3OOVe8sEjABG7nWe0O13j.aLDRWbLl/cFu' WHERE username = 'auditor';

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1),  -- admin -> 超级管理员
(2, 2),  -- organizer -> 活动组织者
(3, 3),  -- volunteer1 -> 志愿者
(4, 3),  -- volunteer2 -> 志愿者
(5, 3),  -- volunteer3 -> 志愿者
(6, 4),  -- college_admin1 -> 学院管理员
(7, 4),  -- college_admin2 -> 学院管理员
(8, 5);  -- auditor -> 系统审核员

-- ============================================
-- 给超级管理员分配所有权限
-- ============================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- ============================================
-- 给活动组织者分配相关权限
-- ============================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE id IN (4, 5, 7);

-- ============================================
-- 给志愿者分配基本权限
-- ============================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, id FROM sys_permission WHERE id IN (10, 12);

-- ============================================
-- 给学院管理员分配活动相关权限
-- ============================================
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(4, 5),  -- 活动列表
(4, 6),  -- 活动审核(本院)
(4, 7),  -- 活动创建
(4, 9),  -- 签到管理
(4, 11), -- 报表
(4, 12); -- 个人报表

-- ============================================
-- 给系统审核员分配活动审核与组织报表权限
-- ============================================
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(5, 6),  -- 活动审核
(5, 13); -- 组织报表

-- ============================================
-- 初始化组织数据（学院类型默认COLLEGE）
-- ============================================
INSERT INTO sys_organization (organization_name, organization_code, organization_type, description, status) VALUES
('志愿者协会', 'VOLUNTEER_ASSOC', 'ASSOCIATION', '学校官方志愿者组织', 1),
('计算机学院志愿服务队', 'CS_VOLUNTEER', 'COLLEGE', '计算机学院学生志愿服务队', 1),
('软件学院志愿服务队', 'SOFTWARE_VOLUNTEER', 'COLLEGE', '软件学院学生志愿服务队', 1),
('信息学院志愿服务队', 'INFO_VOLUNTEER', 'COLLEGE', '信息学院学生志愿服务队', 1);

-- ============================================
-- 更新组织者的college_id（关联到志愿者协会）
-- ============================================
UPDATE sys_user SET college_id = (SELECT id FROM sys_organization WHERE organization_code = 'VOLUNTEER_ASSOC') WHERE username = 'organizer';

-- ============================================
-- 更新志愿者的college_id
-- ============================================
UPDATE sys_user SET college_id = (SELECT id FROM sys_organization WHERE organization_code = 'CS_VOLUNTEER') WHERE username IN ('volunteer1', 'college_admin1');
UPDATE sys_user SET college_id = (SELECT id FROM sys_organization WHERE organization_code = 'SOFTWARE_VOLUNTEER') WHERE username IN ('volunteer2', 'college_admin2');
UPDATE sys_user SET college_id = (SELECT id FROM sys_organization WHERE organization_code = 'INFO_VOLUNTEER') WHERE username = 'volunteer3';

-- ============================================
-- 更新角色码
-- ============================================
UPDATE sys_user SET role_code = 'COLLEGE_ADMIN' WHERE username IN ('college_admin1', 'college_admin2');
UPDATE sys_user SET role_code = 'AUDITOR' WHERE username = 'auditor';
UPDATE sys_user SET role_code = 'ORGANIZER' WHERE username = 'organizer';
UPDATE sys_user SET role_code = 'VOLUNTEER' WHERE username IN ('volunteer1', 'volunteer2', 'volunteer3');

-- ============================================
-- 初始化系统配置
-- ============================================
INSERT INTO system_config (config_key, config_value, config_type, description, sort_order, status) VALUES
('activity.max_participants', '100', 'number', '活动最大参与人数', 1, 1),
('activity.min_participants', '5', 'number', '活动最小参与人数', 2, 1),
('attendance.check_in_radius', '500', 'number', '签到有效半径(米)', 3, 1),
('attendance.check_in_advance', '30', 'number', '允许提前签到时间(分钟)', 4, 1),
('attendance.check_in_delay', '60', 'number', '允许延迟签退时间(分钟)', 5, 1),
('violation.trust_limit_days', '30', 'number', '失信限制天数', 6, 1),
('violation.trust_warning_threshold', '3', 'number', '失信警告阈值', 7, 1),
('report.certificate_template', 'default', 'string', '证书模板', 8, 1),
('ai.enabled', 'true', 'boolean', '是否启用AI功能', 9, 1),
('ai.model', 'gpt-3.5-turbo', 'string', 'AI模型', 10, 1);

-- ============================================
-- 初始化测试活动数据（涵盖全状态）
-- ============================================
INSERT INTO activity (title, description, category, tags, start_time, end_time, registration_deadline,
                      location, province, city, district, street, location_gps_lat, location_gps_lng,
                      max_participants, current_participants, min_participants,
                      organizer_id, organization_id, activity_scope, college_id, status, cover_image, credit_reward, duration_hours,
                      check_in_radius, requires_approval, created_by, updated_by)
VALUES
('社区环境清洁志愿活动', '组织志愿者对社区公共区域进行环境清洁，清理垃圾，美化环境',
 'ENVIRONMENT', '环保,清洁,社区',
 DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY) + INTERVAL 4 HOUR, DATE_ADD(NOW(), INTERVAL 5 DAY),
 '北京市朝阳区某社区', '北京市', '朝阳区', '某街道', '某社区',
 39.9042, 116.4074, 50, 0, 10,
 2, 1, 'COLLEGE', 2, 'PUBLISHED', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=volunteer+community+cleaning+activity&image_size=landscape_16_9', 4, 4.0,
 500, 0, 2, 2),

('爱心图书馆志愿服务', '在社区图书馆整理书籍、引导读者，维护阅读环境',
 'PUBLIC_WELFARE', '公益,图书馆,服务',
 DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY) + INTERVAL 3 HOUR, DATE_ADD(NOW(), INTERVAL 10 DAY),
 '北京市海淀区某图书馆', '北京市', '海淀区', '某街道', '某图书馆',
 39.9890, 116.3063, 30, 0, 5,
 2, 1, 'COLLEGE', 2, 'PUBLISHED', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=library+volunteer+service&image_size=landscape_16_9', 3, 3.0,
 500, 0, 2, 2),

('老年人数字技能培训', '教授老年人使用智能手机和基本APP操作',
 'EDUCATION', '教育,老年,数字',
 DATE_ADD(NOW(), INTERVAL 21 DAY), DATE_ADD(NOW(), INTERVAL 21 DAY) + INTERVAL 2 HOUR, DATE_ADD(NOW(), INTERVAL 15 DAY),
 '北京市昌平区某养老院', '北京市', '昌平区', '某街道', '某养老院',
 40.2174, 116.2337, 20, 0, 5,
 2, 1, 'UNIVERSITY', NULL, 'PUBLISHED', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=elderly+digital+skills+training&image_size=landscape_16_9', 2, 2.0,
 300, 1, 2, 2),

('校园文明引导志愿活动', '校园内文明引导、秩序维护，新生入学季特别活动',
 'CAMPUS', '校园,引导,新生',
 DATE_ADD(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY) + INTERVAL 6 HOUR, DATE_ADD(NOW(), INTERVAL 25 DAY),
 '校园正门广场', '北京市', '海淀区', '校园', '正门',
 39.9900, 116.3100, 40, 0, 8,
 2, 1, 'COLLEGE', 2, 'DRAFT', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=campus+civilization+guidance+volunteering&image_size=landscape_16_9', 3, 6.0,
 500, 0, 2, 2),

('网络安全宣传周志愿服务', '在校园内开展网络安全知识宣传',
 'PUBLIC_WELFARE', '网络,安全,宣传',
 DATE_ADD(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 12 DAY) + INTERVAL 5 HOUR, DATE_ADD(NOW(), INTERVAL 8 DAY),
 '校园食堂门口', '北京市', '海淀区', '校园', '食堂',
 39.9880, 116.3080, 25, 0, 5,
 2, 1, 'COLLEGE', 2, 'AUDITING', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cybersecurity+awareness+week+volunteering&image_size=landscape_16_9', 2, 5.0,
 500, 0, 2, 2),

('街道垃圾分类督导', '在社区街道督导居民正确分类投放垃圾',
 'ENVIRONMENT', '环保,垃圾,分类',
 DATE_ADD(NOW(), INTERVAL 18 DAY), DATE_ADD(NOW(), INTERVAL 18 DAY) + INTERVAL 3 HOUR, DATE_ADD(NOW(), INTERVAL 14 DAY),
 '北京市朝阳区某街道', '北京市', '朝阳区', '某街道', '某小区',
 39.9050, 116.4080, 15, 0, 5,
 2, 1, 'COLLEGE', 3, 'REJECTED', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=waste+sorting+supervision+volunteering&image_size=landscape_16_9', 1, 3.0,
 500, 0, 2, 2),

('高校招生咨询志愿服务', '协助招生办进行志愿填报咨询',
 'EDUCATION', '教育,招生,咨询',
 DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY) + INTERVAL 8 HOUR, DATE_ADD(NOW(), INTERVAL 3 DAY),
 '校园体育馆', '北京市', '海淀区', '校园', '体育馆',
 39.9870, 116.3070, 60, 0, 10,
 2, 1, 'UNIVERSITY', NULL, 'APPROVED', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=university+admissions+consultation+volunteering&image_size=landscape_16_9', 5, 8.0,
 500, 1, 2, 2),

('校园运动会志愿服务', '协助校运会现场秩序维护、成绩统计',
 'CAMPUS', '校园,体育,服务',
 DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY) + INTERVAL 10 HOUR, DATE_ADD(NOW(), INTERVAL 1 DAY),
 '校园田径场', '北京市', '海淀区', '校园', '田径场',
 39.9860, 116.3060, 80, 0, 15,
 2, 1, 'UNIVERSITY', NULL, 'ONGOING', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=sports+day+volunteering+service&image_size=landscape_16_9', 6, 10.0,
 500, 0, 2, 2),

('防艾宣传志愿活动', '在世界艾滋病日开展校园防艾宣传',
 'PUBLIC_WELFARE', '公益,健康,宣传',
 DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 4 HOUR, DATE_SUB(NOW(), INTERVAL 10 DAY),
 '校园学生活动中心', '北京市', '海淀区', '校园', '学生活动中心',
 39.9850, 116.3050, 35, 35, 8,
 2, 1, 'COLLEGE', 2, 'COMPLETED', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=AIDS+prevention+awareness+volunteering&image_size=landscape_16_9', 3, 4.0,
 500, 0, 2, 2),

('校园迎新志愿活动(已取消)', '因场地原因取消的迎新志愿活动',
 'CAMPUS', '校园,迎新',
 DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 6 HOUR, DATE_ADD(NOW(), INTERVAL 2 DAY),
 '校园食堂门口', '北京市', '海淀区', '校园', '食堂',
 39.9880, 116.3080, 30, 0, 5,
 2, 1, 'COLLEGE', 2, 'CANCELLED', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=campus+welcome+volunteering+activity&image_size=landscape_16_9', 2, 6.0,
 500, 0, 2, 2);

-- ============================================
-- 初始化证书序列号数据
-- ============================================
INSERT INTO certificate_sequence (prefix, current_value, step, year, description) VALUES
('VC', 0, 1, YEAR(CURRENT_DATE), '志愿者证书序列号'),
('VC', 0, 1, YEAR(CURRENT_DATE) + 1, '志愿者证书序列号'),
('VC', 0, 1, YEAR(CURRENT_DATE) + 2, '志愿者证书序列号');

-- ============================================
-- 初始化用户积分
-- ============================================
INSERT INTO user_credit (user_id) SELECT id FROM sys_user;

-- ============================================
-- 初始化勋章数据
-- ============================================
INSERT INTO medal (name, icon, description, category, rarity, required_condition, required_value, condition_type, reward_credit) VALUES
('初次参与', '🎯', '完成第一次志愿服务', 'PARTICIPATION', 'COMMON', '完成第一次志愿服务', 1, 'COUNT', 10),
('百时达人', '⏰', '累计服务时长达到100小时', 'PARTICIPATION', 'RARE', '累计服务时长达到100小时', 100, 'HOURS', 50),
('千时楷模', '⭐', '累计服务时长达到1000小时', 'PARTICIPATION', 'LEGENDARY', '累计服务时长达到1000小时', 1000, 'HOURS', 200),
('全勤王', '👑', '连续参加活动无缺席记录', 'PERSEVERANCE', 'EPIC', '连续参与10次活动无缺席', 10, 'COUNT', 100),
('连续三月', '🌙', '连续三个月参与活动', 'PERSEVERANCE', 'RARE', '连续三个月每月至少参与1次', 3, 'COUNT', 30),
('年度之星', '🏆', '年度服务时长排名前10%', 'CONTRIBUTION', 'LEGENDARY', '年度服务时长排名前10%', 10, 'LEVEL', 500),
('雷锋日纪念', '💌', '在雷锋日参与志愿服务', 'FESTIVAL', 'COMMON', '3月5日参与活动', 1, 'COUNT', 15),
('植树节卫士', '🌳', '在植树节参与环保活动', 'FESTIVAL', 'COMMON', '3月12日参与环保活动', 1, 'COUNT', 15),
('急救能手', '🏥', '获得急救技能认证', 'SKILL', 'RARE', '通过急救技能认证', 1, 'COUNT', 30),
('环保先锋', '🌿', '参与环保活动次数最多', 'SKILL', 'EPIC', '参与环保活动达到20次', 20, 'COUNT', 80),
('金牌导师', '🎓', '指导新志愿者超过10人', 'CONTRIBUTION', 'EPIC', '指导新志愿者超过10人', 10, 'COUNT', 150),
('最佳队长', '🚀', '成功组织5次以上活动', 'CONTRIBUTION', 'RARE', '成功组织5次以上活动', 5, 'COUNT', 100);

-- ============================================
-- 初始化每日任务数据
-- ============================================
INSERT INTO daily_task (name, description, reward, target, type, sort_order) VALUES
('每日签到', '每日登录签到', 10, 1, 'CHECKIN', 1),
('浏览活动', '查看至少3个推荐活动', 5, 3, 'VIEW', 2),
('分享活动', '分享活动到社交平台', 10, 1, 'SHARE', 3),
('报名活动', '报名一个活动', 15, 1, 'APPLY', 4),
('评价活动', '对已完成的活动进行评价', 10, 1, 'RATE', 5);

-- ============================================
-- 初始化技能分类和技能数据
-- ============================================
INSERT INTO skill_category (name, description, icon, sort_order) VALUES
('语言能力', '外语沟通能力', '🌐', 1),
('专业技术', '计算机、设计等专业技能', '💻', 2),
('艺术特长', '音乐、舞蹈、美术等艺术技能', '🎨', 3),
('运动健康', '体育、急救等健康相关技能', '⚽', 4),
('教育教学', '教学、辅导等教育技能', '📚', 5),
('其他技能', '其他各类特长技能', '✨', 99);

INSERT INTO skill (category_id, name, description, icon, level, required_hours, required_activities) VALUES
(1, '英语', '英语听说读写能力', '🇬🇧', '初级', 10, 3),
(1, '日语', '日语听说读写能力', '🇯🇵', '初级', 10, 3),
(1, '韩语', '韩语听说读写能力', '🇰🇷', '初级', 10, 3),
(1, '法语', '法语听说读写能力', '🇫🇷', '初级', 10, 3),
(2, '计算机基础', '计算机基本操作能力', '🖥️', '初级', 8, 2),
(2, '网页设计', 'HTML/CSS/JavaScript网页开发', '🌐', '初级', 15, 3),
(2, '平面设计', 'Photoshop/Illustrator设计', '🎨', '初级', 15, 3),
(2, '摄影摄像', '摄影摄像及后期制作', '📷', '初级', 12, 3),
(3, '歌唱表演', '歌唱表演能力', '🎤', '初级', 8, 2),
(3, '舞蹈表演', '舞蹈表演能力', '💃', '初级', 10, 2),
(3, '器乐演奏', '乐器演奏能力', '🎹', '初级', 12, 2),
(4, '体育指导', '体育项目指导能力', '🏀', '初级', 10, 3),
(4, '急救技能', 'CPR及急救知识', '🚑', '初级', 8, 2),
(5, '课业辅导', '中小学课业辅导', '📖', '初级', 10, 3),
(5, '科普讲解', '科学知识普及讲解', '🔬', '初级', 8, 2),
(6, '组织协调', '活动组织协调能力', '👥', '初级', 10, 3),
(6, '礼仪服务', '礼仪接待服务能力', '🤵', '初级', 8, 2),
(6, '心理咨询', '基础心理咨询能力', '💡', '初级', 15, 2);

-- ============================================
-- 初始化时间段配置和课程表数据
-- ============================================
INSERT INTO time_slot_config (period, start_time, end_time, duration_minutes, sort_order) VALUES
(1, '08:30', '10:10', 100, 1),
(2, '10:30', '12:10', 100, 2),
(3, '14:30', '16:10', 100, 3),
(4, '16:30', '18:10', 100, 4),
(5, '19:30', '21:10', 100, 5);

INSERT INTO week_config (semester_id, total_weeks, current_week, semester_start_date, semester_end_date) VALUES
('2024-2025-1', 16, 1, '2024-09-02', '2025-01-12'),
('2024-2025-2', 16, 1, '2025-02-17', '2025-06-29');

INSERT INTO course_schedule (user_id, course_name, course_code, teacher_name, classroom, day_of_week, period, start_week, end_week, week_type, color_code, notes) VALUES
(3, '高等数学', 'MATH101', '张教授', 'A101', 1, 1, 1, 16, 'EVERY', 'RED', '必修课'),
(3, '大学英语', 'ENG101', '李老师', 'B202', 2, 2, 1, 16, 'EVERY', 'RED', '必修课'),
(3, '计算机基础', 'CS101', '王教授', 'C303', 3, 3, 1, 16, 'SINGLE', 'YELLOW', '选修课-单周'),
(3, '体育课', 'PE101', '陈老师', '体育馆', 4, 4, 1, 16, 'DOUBLE', 'YELLOW', '必修课-双周'),
(4, '线性代数', 'MATH102', '赵教授', 'A102', 1, 2, 1, 16, 'EVERY', 'RED', '必修课'),
(4, '程序设计', 'CS102', '刘老师', 'D404', 3, 1, 1, 16, 'EVERY', 'RED', '必修课'),
(5, '数据结构', 'CS201', '孙教授', 'E505', 5, 3, 1, 16, 'EVERY', 'RED', '核心课程');

-- ============================================
-- 初始化志愿服务站数据（V2.4.0）
-- ============================================
INSERT INTO service_station (station_name, address, gps_lat, gps_lng, service_hours, contact_phone, services, status) VALUES
('大学生活动中心服务站', 'XX大学大学生活动中心一楼', 39.9042, 116.4074, '周一至周五 9:00-17:00', '010-12345678', '["志愿者注册","活动报名","时长查询","证书打印"]', 1),
('图书馆志愿服务站', 'XX大学图书馆二楼', 39.9050, 116.4080, '周一至周日 8:00-22:00', '010-12345679', '["图书整理","读者引导","自习管理"]', 1),
('社区服务中心', 'XX社区服务中心', 39.9060, 116.4090, '周一至周五 8:30-17:30', '010-12345680', '["社区服务","便民咨询","活动组织"]', 1);

-- ============================================
-- 初始化活动指标数据
-- ============================================
INSERT INTO activity_metric (activity_id, metric_type, metric_value, metric_date) VALUES
(1, 'VIEW_COUNT', 150.00, CURDATE()),
(1, 'APPLY_COUNT', 45.00, CURDATE()),
(1, 'PARTICIPATION_RATE', 68.00, CURDATE()),
(1, 'SHARE_COUNT', 25.00, CURDATE()),
(1, 'FEEDBACK_SCORE', 4.50, CURDATE()),
(2, 'VIEW_COUNT', 120.00, CURDATE()),
(2, 'APPLY_COUNT', 38.00, CURDATE()),
(2, 'PARTICIPATION_RATE', 72.00, CURDATE()),
(2, 'SHARE_COUNT', 18.00, CURDATE()),
(2, 'FEEDBACK_SCORE', 4.20, CURDATE());

-- ============================================
-- 初始化活动偏好数据
-- ============================================
INSERT INTO activity_preference (user_id, activity_type, category, priority) VALUES
(1, '环保公益', '环保', 5),
(1, '社区服务', '社区', 4),
(2, '文化艺术', '文化', 5),
(2, '教育助学', '教育', 4),
(3, '体育赛事', '体育', 5),
(3, '医疗健康', '健康', 4);

-- ============================================
-- 初始化用户画像数据
-- ============================================
INSERT INTO user_profile (user_id, interests, preferred_areas, skill_tags) VALUES
(3, '["环保","阅读","运动"]', '北京市', '英语,计算机基础'),
(4, '["教育","音乐","公益"]', '北京市', '英语,课业辅导'),
(5, '["体育","户外活动"]', '北京市', '体育指导');

-- ============================================
-- 初始化用户待办事项数据
-- ============================================
INSERT INTO user_todo (user_id, title, priority, due_time, related_id, related_type) VALUES
(3, '完成课程表导入', 0, DATE_ADD(NOW(), INTERVAL 3 DAY), NULL, 'SYSTEM'),
(3, '报名社区环境清洁活动', 1, DATE_ADD(NOW(), INTERVAL 5 DAY), 1, 'ACTIVITY'),
(4, '查看活动评价', 0, DATE_ADD(NOW(), INTERVAL 1 DAY), NULL, 'SYSTEM');

-- ============================================
-- 初始化用户勋章数据（未解锁状态）
-- ============================================
INSERT INTO user_medal (user_id, medal_id) SELECT u.id, m.id FROM sys_user u, medal m;

-- ============================================
-- 初始化完成
-- 总计: 58张表 (含DDL+DML)
-- 所有表使用 InnoDB 引擎，支持事务
-- 所有表使用 utf8mb4 字符集，支持 emoji
-- 所有密码使用 BCrypt 加密存储，工作因子=12
-- 所有外键约束已补充完整
-- 所有 update_time 字段均使用 DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
-- ============================================

SET FOREIGN_KEY_CHECKS = 1;
