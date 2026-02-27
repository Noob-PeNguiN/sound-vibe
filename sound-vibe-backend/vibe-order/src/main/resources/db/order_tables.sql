-- ============================================================
-- SoundVibe Order Module - 建表脚本
-- 数据库: sound_vibe_db
-- ============================================================

-- 订单主表
CREATE TABLE IF NOT EXISTS `orders` (
    `id`           VARCHAR(64)    NOT NULL COMMENT '订单号（雪花算法）',
    `user_id`      BIGINT         NOT NULL COMMENT '用户ID',
    `total_amount` DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT '订单总金额',
    `status`       TINYINT        NOT NULL DEFAULT 0 COMMENT '订单状态: 0=待支付, 1=已支付, 2=已取消',
    `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 订单项表
CREATE TABLE IF NOT EXISTS `order_items` (
    `id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id`     VARCHAR(64)    NOT NULL COMMENT '关联订单号',
    `track_id`     BIGINT         NOT NULL COMMENT '作品ID',
    `license_type` VARCHAR(20)    NOT NULL COMMENT '授权类型: LEASE / EXCLUSIVE',
    `price`        DECIMAL(10,2)  NOT NULL COMMENT '成交价格',
    `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_track_id` (`track_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';
