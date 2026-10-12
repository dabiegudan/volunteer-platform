-- ---------------------------------------------------------------------------
-- V2: 签到凭据绑定表
--
-- 【修复的问题（P0）】
-- 人脸 / NFC / 蓝牙签到此前只做「非空 + 长度 + 正则」校验，从不与任何人脸底库、
-- 门禁卡或蓝牙信标做比对。因此任何已报名用户只要 POST
--   {"activityId":1,"faceData":"x"}
-- 就能签到成功 —— 等同于签到凭证完全不存在，考勤数据可被任意伪造。
--
-- 【修复思路】
-- 把「凭据 → 用户」的绑定关系落库，签到时必须同时满足：
--   1) 该凭据类型下存在与提交值一致（SHA-256 后比对）的有效记录；
--   2) 该记录的 user_id 等于当前登录用户。
-- 由于绑定关系只能由本人自助登记（或后续由管理员导入），提交他人凭据会被拒绝。
--
-- 【向后兼容】
-- 本表为空时，所有凭据类签到都会被拒绝并提示「请先登记」。这是刻意的
-- fail-closed 选择：宁可暂时禁用一条从未被真正校验过的通道，也不能继续
-- 接受任意伪造值。可通过 ATTENDANCE_CREDENTIAL_VERIFICATION_REQUIRED=false 临时降级
-- （降级后凭据类签到同样被拒绝，只是错误提示更明确），不会静默放行。
--
-- 【为什么是独立新表而不是加列到 attendance】
-- attendance 是「一次签到事实」，凭据是「用户的长期身份属性」，生命周期不同；
-- 且生产库的 V1 已作为基线固化，新增列需要改造既有基线校验脚本。
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `attendance_credential` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '所属用户',
  `credential_type` VARCHAR(32) NOT NULL COMMENT '凭据类型: FACE/NFC/BLUETOOTH',
  `credential_hash` CHAR(64) NOT NULL COMMENT '凭据规范化后的 SHA-256 十六进制值',
  `display_name` VARCHAR(64) DEFAULT NULL COMMENT '展示名（如「校园卡」），不含明文凭据',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
  `last_used_time` DATETIME DEFAULT NULL COMMENT '最近一次成功使用时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credential_type_hash` (`credential_type`, `credential_hash`),
  KEY `idx_credential_user` (`user_id`, `credential_type`, `deleted`),
  CONSTRAINT `fk_credential_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到凭据绑定表';
