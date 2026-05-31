package com.skybooker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkyBookerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyBookerApplication.class, args);
    }
}
