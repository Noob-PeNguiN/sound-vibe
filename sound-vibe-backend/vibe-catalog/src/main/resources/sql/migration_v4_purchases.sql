-- ============================================
-- SoundVibe Catalog 模块 v4 迁移脚本
-- 新增 purchases 购买记录表
-- 数据库: sound_vibe_db
-- ============================================

USE sound_vibe_db;

CREATE TABLE IF NOT EXISTS `purchases` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `user_id`       BIGINT          NOT NULL                 COMMENT '购买者 ID（关联 users.id）',
    `track_id`      BIGINT          NOT NULL                 COMMENT '作品 ID（关联 tracks.id）',
    `price_paid`    DECIMAL(10, 2)  NOT NULL DEFAULT 0       COMMENT '实付金额（0=免费获取）',
    `deleted`       TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0=未删除, 1=已删除',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_track` (`user_id`, `track_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_track_id` (`track_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购买记录表';
