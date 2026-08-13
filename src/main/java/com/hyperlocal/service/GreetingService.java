package com.hyperlocal.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class GreetingService {

    public  String getWelcomeMessage(){
        return "Welcome to Hyperlocal  Delivery Platform";
    }

    public String getHealthStatus() {
        return "Application Running";
    }

    public String getVersion() {
        return "Version 1.0";
    }

    public String getCurrentTime() {
        return "Current Server Time: " + LocalDateTime.now().toString();
    }

    public Map<String,String> getAboutInfo(){
        return Map.of(
                "project","Hyperlocal Delivery Platform",
                "backend","Spring Boot",
                "frontend","Next.js",
                "Version","1.0"
        );
    }

}
