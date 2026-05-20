package com.github.carloc24.springboot.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloWorldController {

    public record HelloWorldResponse(String message) {} 

    @GetMapping("/hello")
    public HelloWorldResponse helloWorld() {
        return new HelloWorldResponse("Hello World");
    }
}
