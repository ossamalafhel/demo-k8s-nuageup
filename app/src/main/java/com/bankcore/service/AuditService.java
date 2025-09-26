package com.bankcore.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Audit Service for comprehensive compliance logging
 * Supports banking regulatory requirements (SOX, PCI DSS, GDPR)
 */
@Service
@Slf4j
public class AuditService {

    private final ConcurrentHashMap<String, AuditEntry> auditLog = new ConcurrentHashMap<>();
    private final AtomicLong entryIdGenerator = new AtomicLong(1);

    // Method execution audit
    public void recordMethodEntry(String methodName, String arguments, String userId, 
                                 String clientIp, String userAgent, String traceId) {
        AuditEntry entry = AuditEntry.builder()
            .id(entryIdGenerator.getAndIncrement())
            .eventType("METHOD_ENTRY")
            .methodName(methodName)
            .arguments(arguments)
            .userId(userId)
            .clientIp(clientIp)
            .userAgent(userAgent)
            .traceId(traceId)
            .timestamp(LocalDateTime.now())
            .build();
        
        auditLog.put(entry.getId().toString(), entry);
        log.debug("AUDIT: Method entry recorded - {}", entry);
    }

    public void recordMethodSuccess(String methodName, String result, long executionTime, 
                                   String userId, String traceId) {
        AuditEntry entry = AuditEntry.builder()
            .id(entryIdGenerator.getAndIncrement())
            .eventType("METHOD_SUCCESS")
            .methodName(methodName)
            .result(result)
            .executionTime(executionTime)
            .userId(userId)
            .traceId(traceId)
            .timestamp(LocalDateTime.now())
            .build();
        
        auditLog.put(entry.getId().toString(), entry);
        log.info("AUDIT: Method success recorded - method: {}, user: {}, duration: {}ms", 
            methodName, userId, executionTime);
    }

    public void recordMethodFailure(String methodName, String error, long executionTime, 
                                   String userId, String traceId) {
        AuditEntry entry = AuditEntry.builder()
            .id(entryIdGenerator.getAndIncrement())
            .eventType("METHOD_FAILURE")
            .methodName(methodName)
            .error(error)
            .executionTime(executionTime)
            .userId(userId)
            .traceId(traceId)
            .timestamp(LocalDateTime.now())
            .build();
        
        auditLog.put(entry.getId().toString(), entry);
        log.error("AUDIT: Method failure recorded - method: {}, user: {}, error: {}", 
            methodName, userId, error);
    }

    // Data modification audit
    public void recordDataModificationAttempt(String methodName, String arguments, 
                                            String userId, String clientIp, LocalDateTime timestamp) {
        AuditEntry entry = AuditEntry.builder()
            .id(entryIdGenerator.getAndIncrement())
            .eventType("DATA_MODIFICATION_ATTEMPT")
            .methodName(methodName)
            .arguments(arguments)
            .userId(userId)
            .clientIp(clientIp)
            .timestamp(timestamp)
            .build();
        
        auditLog.put(entry.getId().toString(), entry);
        log.info("AUDIT: Data modification attempt - method: {}, user: {}", methodName, userId);
    }

    public void recordDataModificationSuccess(String methodName, String result, 
                                            String userId, LocalDateTime timestamp) {
        AuditEntry entry = AuditEntry.builder()
            .id(entryIdGenerator.getAndIncrement())
            .eventType("DATA_MODIFICATION_SUCCESS")
            .methodName(methodName)
            .result(result)
            .userId(userId)
            .timestamp(timestamp)
            .build();
        
        auditLog.put(entry.getId().toString(), entry);
        log.info("AUDIT: Data modification successful - method: {}, user: {}", methodName, userId);
    }

    public void recordDataModificationFailure(String methodName, String error, String errorType, 
                                            String userId, LocalDateTime timestamp) {
        AuditEntry entry = AuditEntry.builder()
            .id(entryIdGenerator.getAndIncrement())
            .eventType("DATA_MODIFICATION_FAILURE")
            .methodName(methodName)
            .error(error)
            .errorType(errorType)
            .userId(userId)
            .timestamp(timestamp)
            .build();
        
        auditLog.put(entry.getId().toString(), entry);
        log.warn("AUDIT: Data modification failed - method: {}, user: {}, error: {}", 
            methodName, userId, error);
    }

    // Security audit
    public void recordSecurityCheck(String methodName, String userId, String clientIp, 
                                   String checkType, LocalDateTime timestamp) {
        AuditEntry entry = AuditEntry.builder()
            .id(entryIdGenerator.getAndIncrement())
            .eventType("SECURITY_CHECK")
            .methodName(methodName)
            .userId(userId)
            .clientIp(clientIp)
            .checkType(checkType)
            .timestamp(timestamp)
            .build();
        
        auditLog.put(entry.getId().toString(), entry);
        log.info("AUDIT: Security check - method: {}, user: {}, type: {}", 
            methodName, userId, checkType);
    }

    public void recordSecurityViolation(String violationType, String methodName, String userId, 
                                       String clientIp, String details) {
        AuditEntry entry = AuditEntry.builder()
            .id(entryIdGenerator.getAndIncrement())
            .eventType("SECURITY_VIOLATION")
            .violationType(violationType)
            .methodName(methodName)
            .userId(userId)
            .clientIp(clientIp)
            .details(details)
            .timestamp(LocalDateTime.now())
            .severity("HIGH")
            .build();
        
        auditLog.put(entry.getId().toString(), entry);
        log.error("AUDIT: SECURITY VIOLATION - type: {}, method: {}, user: {}, ip: {}, details: {}", 
            violationType, methodName, userId, clientIp, details);
    }

    public void recordSecurityAlert(String alertType, String message, String userId, 
                                   String clientIp, String severity) {
        AuditEntry entry = AuditEntry.builder()
            .id(entryIdGenerator.getAndIncrement())
            .eventType("SECURITY_ALERT")
            .alertType(alertType)
            .message(message)
            .userId(userId)
            .clientIp(clientIp)
            .severity(severity)
            .timestamp(LocalDateTime.now())
            .build();
        
        auditLog.put(entry.getId().toString(), entry);
        log.error("AUDIT: SECURITY ALERT - type: {}, severity: {}, user: {}, message: {}", 
            alertType, severity, userId, message);
    }

    // Event-specific audit methods (called from event handlers)
    public void recordTransactionCreation(Object event) {
        // Implementation for transaction creation audit
        log.info("AUDIT: Transaction created - event: {}", event);
    }

    public void recordApproval(Object event) {
        // Implementation for approval audit
        log.info("AUDIT: Transaction approved - event: {}", event);
    }

    public void recordRejection(Object event) {
        // Implementation for rejection audit
        log.info("AUDIT: Transaction rejected - event: {}", event);
    }

    public void recordSecurityEvent(Object event) {
        // Implementation for security event audit
        log.warn("AUDIT: Security event - event: {}", event);
    }

    public void recordComplianceCheck(Object event) {
        // Implementation for compliance check audit
        log.info("AUDIT: Compliance check - event: {}", event);
    }

    public void recordTransferInitiation(Object event) {
        // Implementation for transfer initiation audit
        log.info("AUDIT: Transfer initiated - event: {}", event);
    }

    public void recordBalanceChange(Object event) {
        // Implementation for balance change audit
        log.info("AUDIT: Balance updated - event: {}", event);
    }

    // Utility methods
    public long getTotalAuditEntries() {
        return auditLog.size();
    }

    public void clearAuditLog() {
        auditLog.clear();
        log.warn("AUDIT: Audit log cleared");
    }

    // Audit Entry data structure
    @lombok.Builder
    @lombok.Data
    private static class AuditEntry {
        private Long id;
        private String eventType;
        private String methodName;
        private String arguments;
        private String result;
        private String error;
        private String errorType;
        private Long executionTime;
        private String userId;
        private String clientIp;
        private String userAgent;
        private String traceId;
        private String checkType;
        private String violationType;
        private String alertType;
        private String message;
        private String details;
        private String severity;
        private LocalDateTime timestamp;
    }
}