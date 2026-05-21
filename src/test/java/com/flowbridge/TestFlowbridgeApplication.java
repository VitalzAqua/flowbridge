package com.flowbridge;

import org.springframework.boot.SpringApplication;

public class TestFlowbridgeApplication {

    public static void main(String[] args) {
        SpringApplication.from(FlowbridgeApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
