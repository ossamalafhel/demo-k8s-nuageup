package com.bankcore.service;

import com.bankcore.event.BankingEvents;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Compliance service for regulatory checks
 * Handles AML, KYC, FATCA and other regulatory requirements
 */
@Service
@Slf4j
public class ComplianceService {

    public void performAMLCheck(BankingEvents.ComplianceCheckRequiredEvent event) {
        log.info("COMPLIANCE: AML check initiated - txn: {}, account: {}", 
            event.getTransactionId(), event.getAccountId());
        
        // Simulate AML processing
        boolean passed = simulateComplianceCheck("AML");
        
        if (passed) {
            log.info("COMPLIANCE: AML check passed - txn: {}", event.getTransactionId());
        } else {
            log.warn("COMPLIANCE: AML check failed - txn: {}", event.getTransactionId());
        }
    }

    public void performKYCCheck(BankingEvents.ComplianceCheckRequiredEvent event) {
        log.info("COMPLIANCE: KYC check initiated - txn: {}, account: {}", 
            event.getTransactionId(), event.getAccountId());
        
        boolean passed = simulateComplianceCheck("KYC");
        
        if (passed) {
            log.info("COMPLIANCE: KYC check passed - txn: {}", event.getTransactionId());
        } else {
            log.warn("COMPLIANCE: KYC check failed - txn: {}", event.getTransactionId());
        }
    }

    public void performFATCACheck(BankingEvents.ComplianceCheckRequiredEvent event) {
        log.info("COMPLIANCE: FATCA check initiated - txn: {}, account: {}", 
            event.getTransactionId(), event.getAccountId());
        
        boolean passed = simulateComplianceCheck("FATCA");
        
        if (passed) {
            log.info("COMPLIANCE: FATCA check passed - txn: {}", event.getTransactionId());
        } else {
            log.warn("COMPLIANCE: FATCA check failed - txn: {}", event.getTransactionId());
        }
    }

    private boolean simulateComplianceCheck(String type) {
        // Simulate 95% pass rate
        return Math.random() > 0.05;
    }
}