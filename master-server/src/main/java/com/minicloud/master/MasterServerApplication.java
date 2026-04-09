package com.minicloud.master;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MasterServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MasterServerApplication.class, args);
    }
}
