package com.gogoga.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 *
 * @author JackWei
 * @since 2026-01-21
 */
@SpringBootApplication
@MapperScan("com.gogoga.backend.mapper")
@EnableFeignClients(basePackages = "com.gogoga.backend.client")
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
