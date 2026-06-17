package com.gogoga.backend.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码哈希生成工具
 * 
 * 使用方式：
 * cd backend
 * mvn exec:java -Dexec.mainClass="com.gogoga.backend.util.PasswordGenerator" -Dexec.args="Admin@123"
 * 
 * @author system
 */
public class PasswordGenerator {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("用法: PasswordGenerator <密码>");
            System.out.println("示例: PasswordGenerator Admin@123");
            System.exit(1);
        }

        String password = args[0];
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String hash = encoder.encode(password);

        System.out.println("========================================");
        System.out.println("原始密码: " + password);
        System.out.println("BCrypt哈希: " + hash);
        System.out.println("验证结果: " + encoder.matches(password, hash));
        System.out.println("========================================");
        System.out.println();
        System.out.println("SQL 更新语句:");
        System.out.println("UPDATE ai_user SET password = '" + hash + "' WHERE email = 'admin@example.com';");
    }
}
