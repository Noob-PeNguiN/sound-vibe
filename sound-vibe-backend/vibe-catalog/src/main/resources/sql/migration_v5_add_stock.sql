-- ============================================
-- Migration V5: 为 tracks 表添加 sold_count 和 stock 字段
-- 功能说明：
--   sold_count: 记录已售数量，每次购买成功自动 +1
--   stock: 库存数量，null 表示不限库存，0 表示售罄
-- ============================================

USE sound_vibe_db;

-- 添加 sold_count 字段（已售数量）
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'sound_vibe_db'
      AND TABLE_NAME = 'tracks'
      AND COLUMN_NAME = 'sold_count'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `tracks` ADD COLUMN `sold_count` INT NOT NULL DEFAULT 0 COMMENT ''已售数量（每次购买成功 +1）'' AFTER `preview_duration`',
    'SELECT ''Column sold_count already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 stock 字段（库存数量）
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'sound_vibe_db'
      AND TABLE_NAME = 'tracks'
      AND COLUMN_NAME = 'stock'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `tracks` ADD COLUMN `stock` INT DEFAULT NULL COMMENT ''库存数量（null 表示不限库存，0 表示售罄）'' AFTER `sold_count`',
    'SELECT ''Column stock already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
