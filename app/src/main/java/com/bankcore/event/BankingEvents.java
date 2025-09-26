package com.bankcore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Banking domain events for Event-Driven Architecture
 * Implements Event Sourcing pattern for complete audit trail
 */
public class BankingEvents {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionCreatedEvent {
        private Long transactionId;
        private String accountId;
        private String transactionType;
        private BigDecimal amount;
        private String currency;
        private String description;
        private String idempotencyKey;
        private String createdBy;
        private LocalDateTime createdAt;
        private String clientIp;
        private String userAgent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionApprovedEvent {
        private Long transactionId;
        private String accountId;
        private BigDecimal amount;
        private String approvedBy;
        private LocalDateTime approvedAt;
        private String approvalReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionRejectedEvent {
        private Long transactionId;
        private String accountId;
        private BigDecimal amount;
        private String rejectedBy;
        private LocalDateTime rejectedAt;
        private String rejectionReason;
        private String errorCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferInitiatedEvent {
        private Long transactionId;
        private String sourceAccount;
        private String targetAccount;
        private BigDecimal amount;
        private String currency;
        private String reference;
        private LocalDateTime initiatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudSuspectedEvent {
        private Long transactionId;
        private String accountId;
        private BigDecimal amount;
        private String suspicionReason;
        private Double riskScore;
        private LocalDateTime detectedAt;
        private String detectionSource;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBalanceUpdatedEvent {
        private String accountId;
        private BigDecimal previousBalance;
        private BigDecimal newBalance;
        private BigDecimal transactionAmount;
        private Long transactionId;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceCheckRequiredEvent {
        private Long transactionId;
        private String accountId;
        private BigDecimal amount;
        private String transactionType;
        private String regulationType; // AML, KYC, FATCA
        private LocalDateTime triggeredAt;
    }
}