package com.jones.activation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.jones.activation.mapper")
public class ActivationCodeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivationCodeServerApplication.class, args);
    }
}
