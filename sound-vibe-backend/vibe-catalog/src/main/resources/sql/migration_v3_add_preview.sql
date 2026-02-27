-- ============================================
-- SoundVibe Catalog 模块 v3 迁移脚本
-- 为 track_files 表添加 allow_preview 字段
-- 数据库: sound_vibe_db
-- 适用: MySQL 8.0（不支持 ADD COLUMN IF NOT EXISTS）
-- ============================================

USE sound_vibe_db;

-- track_files 表新增 allow_preview 字段
-- 发布者可控制合集中哪些文件允许被试听
-- 注意：如果列已存在会报错，可忽略
ALTER TABLE `track_files`
    ADD COLUMN `allow_preview` TINYINT NOT NULL DEFAULT 1
    COMMENT '是否允许预览: 0=不允许, 1=允许（发布者可控制合集中哪些文件可试听）'
    AFTER `sort_order`;
