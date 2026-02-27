-- ============================================
-- Migration v6: 添加搜索相关元数据字段
-- 为 tracks 表新增 bpm, musical_key, genre 字段
-- 支持 Phase 5 搜索服务的结构化过滤
-- ============================================

ALTER TABLE `tracks`
    ADD COLUMN `bpm`          INT           DEFAULT NULL COMMENT 'BPM 节拍速度（如 120, 140, 160）' AFTER `tags`,
    ADD COLUMN `musical_key`  VARCHAR(20)   DEFAULT NULL COMMENT '音乐调式（如 C minor, A major, F# minor）' AFTER `bpm`,
    ADD COLUMN `genre`        VARCHAR(50)   DEFAULT NULL COMMENT '音乐风格/流派（如 Trap, Lo-Fi, Drill, R&B）' AFTER `musical_key`;

-- 添加索引优化搜索性能
ALTER TABLE `tracks`
    ADD INDEX `idx_bpm` (`bpm`),
    ADD INDEX `idx_genre` (`genre`);
