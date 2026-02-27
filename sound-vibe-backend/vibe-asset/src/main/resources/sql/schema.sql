-- ============================================
-- SoundVibe Asset 模块数据库表结构
-- 数据库: sound_vibe_db
-- ============================================

-- 数字资产表（文件存储元数据）
-- 设计说明：
--   Asset 是 MinIO 中存储的原始文件的元数据记录
--   类型分为：AUDIO（音频文件）、IMAGE（封面图片）、MIDI（MIDI 序列）
--   IMAGE 仅作为作品封面使用，不作为独立的可交易商品
CREATE TABLE IF NOT EXISTS `assets` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键 ID（内部使用，不暴露给前端）',
    `asset_code`    VARCHAR(32)     NOT NULL                 COMMENT '资产编码（对外唯一标识，UUID，替代主键暴露）',
    `user_id`       BIGINT          NOT NULL                 COMMENT '上传者 ID (关联 users.id)',
    `original_name` VARCHAR(255)    NOT NULL                 COMMENT '原始文件名',
    `storage_name`  VARCHAR(500)    NOT NULL                 COMMENT 'MinIO 存储对象名 (含路径)',
    `url`           VARCHAR(1000)   DEFAULT NULL             COMMENT '文件访问 URL',
    `size`          BIGINT          NOT NULL DEFAULT 0       COMMENT '文件大小 (字节)',
    `extension`     VARCHAR(20)     NOT NULL                 COMMENT '文件扩展名',
    `type`          VARCHAR(20)     NOT NULL                 COMMENT '资产类型: AUDIO, IMAGE, MIDI',
    `status`        TINYINT         NOT NULL DEFAULT 0       COMMENT '状态: 0=上传中, 1=正常, 2=已删除, 3=分析中, 4=分析失败',
    `bpm`           INT             DEFAULT NULL             COMMENT '节拍速度 (Beats Per Minute)',
    `musical_key`   VARCHAR(20)     DEFAULT NULL             COMMENT '音乐调性 (如 C Major, A Minor)',
    `duration`      INT             DEFAULT NULL             COMMENT '音频时长 (秒)',
    `auto_tags`     VARCHAR(500)    DEFAULT NULL             COMMENT '自动标注标签 (逗号分隔, 由 CLAP Zero-Shot 生成)',
    `deleted`       TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0=未删除, 1=已删除',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_asset_code` (`asset_code`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_type_status` (`type`, `status`),
    INDEX `idx_user_deleted` (`user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数字资产表';

-- ============================================
-- 迁移脚本（从旧 schema 迁移）
-- ============================================
-- 将 STEM 类型统一为 AUDIO:
-- UPDATE `assets` SET `type` = 'AUDIO' WHERE `type` = 'STEM';

-- ============================================
-- Phase 6: Audio Intelligence 字段扩展
-- 添加音频分析结果字段: bpm, musical_key, duration
-- ============================================
ALTER TABLE `assets`
    ADD COLUMN `bpm`          INT           DEFAULT NULL COMMENT '节拍速度 (Beats Per Minute)' AFTER `status`,
    ADD COLUMN `musical_key`  VARCHAR(20)   DEFAULT NULL COMMENT '音乐调性 (如 C Major, A Minor)' AFTER `bpm`,
    ADD COLUMN `duration`     INT           DEFAULT NULL COMMENT '音频时长 (秒)' AFTER `musical_key`;

-- ============================================
-- Phase 7: Zero-Shot Audio Tagging 字段扩展
-- 添加 CLAP 自动标注结果
-- ============================================
ALTER TABLE `assets`
    ADD COLUMN `auto_tags`    VARCHAR(500)  DEFAULT NULL COMMENT '自动标注标签 (逗号分隔, 由 CLAP Zero-Shot 生成)' AFTER `duration`;
