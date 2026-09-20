package com.hyperlocal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class HyperLocalApplication {
    public static void main(String[] args){
        SpringApplication.run(HyperLocalApplication.class,args);
    }

}
