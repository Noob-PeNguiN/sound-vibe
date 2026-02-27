-- ============================================
-- Phase 5.5 迁移脚本: 为 tracks 表添加 duration 字段
-- 用于存储音频时长（秒），由 vibe-asset 分析服务自动填充
-- ============================================

ALTER TABLE tracks ADD COLUMN duration INT DEFAULT NULL COMMENT '音频时长（秒），由分析服务自动填充' AFTER genre;
