package com.onze.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OnzeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnzeApiApplication.class, args);
    }
}
