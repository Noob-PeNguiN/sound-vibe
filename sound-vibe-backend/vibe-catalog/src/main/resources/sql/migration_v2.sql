-- ============================================
-- SoundVibe Catalog 模块 v2 迁移脚本
-- 从旧 schema 迁移到新的 SINGLE/PACK 模型
-- 数据库: sound_vibe_db
-- ============================================

USE sound_vibe_db;

-- ===================== Step 1: tracks 表变更 =====================

-- 1.1 删除旧字段（如果存在）
ALTER TABLE `tracks` DROP COLUMN IF EXISTS `bpm`;
ALTER TABLE `tracks` DROP COLUMN IF EXISTS `musical_key`;
ALTER TABLE `tracks` DROP COLUMN IF EXISTS `genre`;

-- 1.2 新增 description 字段
ALTER TABLE `tracks` ADD COLUMN IF NOT EXISTS `description` TEXT DEFAULT NULL
    COMMENT '作品描述（建议用户在此说明 BPM、调式、风格等信息）' AFTER `title`;

-- 1.3 新增 track_type 字段（作品类型: SINGLE / PACK）
ALTER TABLE `tracks` ADD COLUMN IF NOT EXISTS `track_type` VARCHAR(10) NOT NULL DEFAULT 'SINGLE'
    COMMENT '作品类型: SINGLE=单曲, PACK=合集/采样包' AFTER `description`;

-- 1.4 audio_id → file_id（如果旧列名存在）
-- 注意：如果已经改过则跳过此步
-- ALTER TABLE `tracks` CHANGE COLUMN `audio_id` `file_id` BIGINT DEFAULT NULL COMMENT '文件资产 ID';

-- 1.5 file_type 允许 NULL（PACK 类型不需要此字段）
ALTER TABLE `tracks` MODIFY COLUMN `file_type` VARCHAR(20) DEFAULT NULL
    COMMENT '文件类型（仅 SINGLE 类型）: AUDIO, MIDI';

-- 1.6 新增 file_count 字段
ALTER TABLE `tracks` ADD COLUMN IF NOT EXISTS `file_count` INT NOT NULL DEFAULT 1
    COMMENT '文件数量（SINGLE=1, PACK=合集中的实际文件数量）' AFTER `file_type`;

-- 1.7 删除旧索引（如果存在），创建新索引
-- DROP INDEX IF EXISTS `idx_genre_status` ON `tracks`;
-- DROP INDEX IF EXISTS `idx_bpm` ON `tracks`;
CREATE INDEX IF NOT EXISTS `idx_track_type` ON `tracks` (`track_type`);
CREATE INDEX IF NOT EXISTS `idx_file_type` ON `tracks` (`file_type`);

-- ===================== Step 2: 新建 track_files 表 =====================

CREATE TABLE IF NOT EXISTS `track_files` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `track_id`      BIGINT          NOT NULL                 COMMENT '所属作品 ID（关联 tracks.id）',
    `asset_id`      BIGINT          NOT NULL                 COMMENT '文件资产 ID（关联 assets.id）',
    `file_type`     VARCHAR(20)     NOT NULL                 COMMENT '文件类型: AUDIO, MIDI',
    `original_name` VARCHAR(255)    DEFAULT NULL             COMMENT '原始文件名（冗余存储）',
    `sort_order`    INT             NOT NULL DEFAULT 0       COMMENT '排序序号（从 0 开始，越小越靠前）',
    `deleted`       TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0=未删除, 1=已删除',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_track_id` (`track_id`),
    INDEX `idx_asset_id` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品文件表（PACK 合集文件列表）';

-- ===================== 完成 =====================
-- 所有已有数据的 track_type 默认为 'SINGLE'，file_count 默认为 1
-- 这与现有 SINGLE 逻辑完全兼容，无需修改已有数据
