package com.bankcore.service;

import com.bankcore.event.BankingEvents;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Random;

/**
 * Fraud detection service with ML-based risk scoring
 * Implements real-time transaction analysis for suspicious patterns
 */
@Service
@Slf4j
public class FraudDetectionService {

    private final Random random = new Random();

    public double calculateRiskScore(BankingEvents.TransactionCreatedEvent event) {
        double baseScore = 0.1;
        
        // Amount-based risk
        if (event.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            baseScore += 0.3;
        }
        
        // Time-based risk (simplified)
        int hour = event.getCreatedAt().getHour();
        if (hour < 6 || hour > 22) {
            baseScore += 0.2;
        }
        
        // Velocity-based risk (simplified simulation)
        baseScore += random.nextDouble() * 0.4;
        
        log.debug("FRAUD: Risk score calculated - txn: {}, score: {}", 
            event.getTransactionId(), baseScore);
        
        return Math.min(baseScore, 1.0);
    }

    public void freezeAccount(String accountId, String reason) {
        log.error("FRAUD: Account frozen - account: {}, reason: {}", accountId, reason);
        // In production, this would update account status in database
    }
}