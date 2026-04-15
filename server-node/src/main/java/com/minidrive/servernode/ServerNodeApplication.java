package com.minidrive.servernode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServerNodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerNodeApplication.class, args);
    }
}