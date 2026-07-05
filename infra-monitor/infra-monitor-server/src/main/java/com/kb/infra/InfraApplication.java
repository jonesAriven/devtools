package com.kb.infra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InfraApplication {
    public static void main(String[] args) {
        SpringApplication.run(InfraApplication.class, args);
    }
}
