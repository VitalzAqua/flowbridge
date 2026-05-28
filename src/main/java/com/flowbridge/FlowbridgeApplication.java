package com.flowbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FlowbridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowbridgeApplication.class, args);
    }

}
