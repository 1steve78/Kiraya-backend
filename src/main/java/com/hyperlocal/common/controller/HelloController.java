package com.hyperlocal.common.controller;

import com.hyperlocal.common.service.GreetingService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HelloController {

    private final GreetingService greetingService ;

    public  HelloController(GreetingService greetingService){
        this.greetingService = greetingService;
    }

    @GetMapping("/")
    public ResponseEntity<String> home(){
        return  ResponseEntity.ok(greetingService.getWelcomeMessage());
    }
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok(greetingService.getHealthStatus());
    }

    @GetMapping("/version")
    public ResponseEntity<String> version() {
        return ResponseEntity.ok(greetingService.getVersion());
    }


    @GetMapping("/time")
    public ResponseEntity<String> time() {
        return ResponseEntity.ok(greetingService.getCurrentTime());
    }

    @GetMapping("/about")
    public ResponseEntity<Map<String, String>> about() {
        // Because this returns a Map, Spring Boot's built-in Jackson library
        // will automatically serialize this into the JSON format requested!
        return ResponseEntity.ok(greetingService.getAboutInfo());
    }
}
