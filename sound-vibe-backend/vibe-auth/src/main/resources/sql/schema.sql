-- ========================================
-- SoundVibe 数据库表结构初始化脚本
-- ========================================

-- 切换到目标数据库
USE sound_vibe_db;

-- ========================================
-- 1. 用户表 (users)
-- ========================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户 ID（主键）',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名（唯一）',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希值（BCrypt 加密）',
    role VARCHAR(20) NOT NULL DEFAULT 'PRODUCER' COMMENT '用户角色：PRODUCER=制作人, ADMIN=管理员',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username) COMMENT '用户名索引（用于登录查询）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ========================================
-- 2. 插入测试数据（可选）
-- ========================================
-- 默认管理员账号: admin / admin123456
-- 密码 "admin123456" 的 BCrypt 哈希值（$2a$10$ 开头）
INSERT IGNORE INTO users (username, password_hash, role) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM1l.b9iMjYxn3f9h5FW', 'ADMIN');

-- 测试制作人账号建议通过注册接口生成，避免手写 BCrypt 导致密码不匹配
-- 示例:
-- 1) 调用 /auth/register 创建 producer01 / pass123456
-- 2) 再调用 /auth/login 登录
