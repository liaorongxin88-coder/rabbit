package com.rabbit.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RabbitBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(RabbitBackendApplication.class, args);
    }
}
