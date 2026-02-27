package com.soundvibe.auth.util;

import cn.hutool.crypto.digest.BCrypt;

/**
 * BCrypt 密码生成工具
 * 用于生成测试数据的加密密码
 * 
 * @author SoundVibe Team
 */
public class BCryptPasswordGenerator {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   BCrypt 密码生成工具");
        System.out.println("========================================\n");
        
        // 生成常用测试密码
        String[] passwords = {
            "test123456",
            "pass123456",
            "admin123456",
            "producer123"
        };
        
        for (String password : passwords) {
            String hash = BCrypt.hashpw(password);
            System.out.println("原始密码: " + password);
            System.out.println("BCrypt:   " + hash);
            System.out.println();
        }
        
        System.out.println("========================================");
        System.out.println("插入 SQL 示例:");
        System.out.println("========================================\n");
        
        System.out.println("INSERT INTO users (username, password_hash, role) VALUES");
        System.out.println("  ('testuser', '" + BCrypt.hashpw("test123456") + "', 'PRODUCER');");
        System.out.println();
        
        System.out.println("========================================");
        System.out.println("注意事项:");
        System.out.println("========================================");
        System.out.println("1. 数据库字段必须是 password_hash（不是 password）");
        System.out.println("2. 密码必须是 BCrypt 加密后的（以 $2a$10$ 开头）");
        System.out.println("3. 每次运行生成的哈希值都不同（这是正常的）");
        System.out.println("4. 推荐使用 /auth/register 接口而不是手动插入");
    }
}
