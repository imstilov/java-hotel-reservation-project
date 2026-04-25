package com.stilov.springboot_practice_2503;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class SpringbootPractice2503Application {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootPractice2503Application.class, args);
    }

}
