package com.koicoffee.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class PingController {
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Pong! Server is awake and running.");
        response.put("timestamp", LocalDateTime.now());

        // Trả về HTTP Status 200 OK
        return ResponseEntity.ok(response);
    }
}