package com.bankcore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final ApplicationEventPublisher eventPublisher;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "bankcore");
        health.put("timestamp", System.currentTimeMillis());
        health.put("version", "1.0.0");
        
        return ResponseEntity.ok(health);
    }

    @GetMapping("/deep")
    public ResponseEntity<Map<String, Object>> deepHealthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("database", checkDatabase());
        health.put("memory", checkMemory());
        health.put("cpu", checkCpu());
        
        return ResponseEntity.ok(health);
    }

    @PostMapping("/ready/{state}")
    public ResponseEntity<String> setReadiness(@PathVariable boolean state) {
        log.info("Setting readiness state to: {}", state);
        AvailabilityChangeEvent.publish(
            eventPublisher, 
            this, 
            state ? ReadinessState.ACCEPTING_TRAFFIC : ReadinessState.REFUSING_TRAFFIC
        );
        return ResponseEntity.ok("Readiness state changed to: " + state);
    }

    @PostMapping("/live/{state}")
    public ResponseEntity<String> setLiveness(@PathVariable boolean state) {
        log.info("Setting liveness state to: {}", state);
        AvailabilityChangeEvent.publish(
            eventPublisher, 
            this, 
            state ? LivenessState.CORRECT : LivenessState.BROKEN
        );
        return ResponseEntity.ok("Liveness state changed to: " + state);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> dbHealth = new HashMap<>();
        dbHealth.put("status", "UP");
        dbHealth.put("type", "PostgreSQL");
        return dbHealth;
    }

    private Map<String, Object> checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memory = new HashMap<>();
        memory.put("total", runtime.totalMemory());
        memory.put("free", runtime.freeMemory());
        memory.put("used", runtime.totalMemory() - runtime.freeMemory());
        memory.put("max", runtime.maxMemory());
        return memory;
    }

    private Map<String, Object> checkCpu() {
        Map<String, Object> cpu = new HashMap<>();
        cpu.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        return cpu;
    }
}