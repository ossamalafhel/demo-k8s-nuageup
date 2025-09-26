package com.bankcore.event;

import com.bankcore.service.AuditService;
import com.bankcore.service.ComplianceService;
import com.bankcore.service.FraudDetectionService;
import com.bankcore.service.NotificationService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Event Handler implementing Event-Driven Architecture
 * Handles all banking domain events asynchronously
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BankingEventHandler {

    private final AuditService auditService;
    private final NotificationService notificationService;
    private final FraudDetectionService fraudDetectionService;
    private final ComplianceService complianceService;

    @EventListener
    @Async
    @Timed("banking.event.transaction.created")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTransactionCreated(BankingEvents.TransactionCreatedEvent event) {
        log.info("Processing transaction created event: transactionId={}, accountId={}, amount={}", 
            event.getTransactionId(), event.getAccountId(), event.getAmount());

        // Audit trail for compliance
        auditService.recordTransactionCreation(event);

        // Fraud detection analysis
        double riskScore = fraudDetectionService.calculateRiskScore(event);
        if (riskScore > 0.7) {
            publishFraudSuspectedEvent(event, riskScore);
        }

        // Large transaction compliance check
        if (event.getAmount().compareTo(new BigDecimal("10000")) >= 0) {
            publishComplianceCheckEvent(event);
        }

        // Real-time notifications
        notificationService.sendTransactionAlert(event);
    }

    @EventListener
    @Async
    @Timed("banking.event.transaction.approved")
    public void handleTransactionApproved(BankingEvents.TransactionApprovedEvent event) {
        log.info("Processing transaction approved event: transactionId={}", event.getTransactionId());
        
        auditService.recordApproval(event);
        notificationService.sendApprovalNotification(event);
    }

    @EventListener
    @Async
    @Timed("banking.event.transaction.rejected")
    public void handleTransactionRejected(BankingEvents.TransactionRejectedEvent event) {
        log.error("Processing transaction rejected event: transactionId={}, reason={}", 
            event.getTransactionId(), event.getRejectionReason());
        
        auditService.recordRejection(event);
        notificationService.sendRejectionNotification(event);
        
        // Trigger remediation workflow if needed
        if ("INSUFFICIENT_FUNDS".equals(event.getErrorCode())) {
            notificationService.suggestAlternatives(event);
        }
    }

    @EventListener
    @Async
    @Timed("banking.event.fraud.suspected")
    public void handleFraudSuspected(BankingEvents.FraudSuspectedEvent event) {
        log.warn("FRAUD ALERT: transactionId={}, accountId={}, riskScore={}", 
            event.getTransactionId(), event.getAccountId(), event.getRiskScore());
        
        // Immediate security response
        auditService.recordSecurityEvent(event);
        notificationService.sendFraudAlert(event);
        
        // Auto-freeze account if risk score is critical
        if (event.getRiskScore() > 0.9) {
            fraudDetectionService.freezeAccount(event.getAccountId(), "High fraud risk detected");
        }
    }

    @EventListener
    @Async
    @Timed("banking.event.compliance.check")
    public void handleComplianceCheckRequired(BankingEvents.ComplianceCheckRequiredEvent event) {
        log.info("Processing compliance check: transactionId={}, type={}", 
            event.getTransactionId(), event.getRegulationType());
        
        auditService.recordComplianceCheck(event);
        
        switch (event.getRegulationType()) {
            case "AML" -> complianceService.performAMLCheck(event);
            case "KYC" -> complianceService.performKYCCheck(event);
            case "FATCA" -> complianceService.performFATCACheck(event);
            default -> log.warn("Unknown regulation type: {}", event.getRegulationType());
        }
    }

    @EventListener
    @Async
    @Timed("banking.event.transfer.initiated")
    public void handleTransferInitiated(BankingEvents.TransferInitiatedEvent event) {
        log.info("Processing transfer initiated: from={} to={} amount={}", 
            event.getSourceAccount(), event.getTargetAccount(), event.getAmount());
        
        // Multi-step saga orchestration for distributed transactions
        auditService.recordTransferInitiation(event);
        
        // Step 1: Validate source account
        // Step 2: Reserve funds
        // Step 3: Validate target account
        // Step 4: Execute transfer
        // Step 5: Update balances
        
        notificationService.sendTransferConfirmation(event);
    }

    @EventListener
    @Async
    @Timed("banking.event.balance.updated")
    public void handleBalanceUpdated(BankingEvents.AccountBalanceUpdatedEvent event) {
        log.debug("Balance updated for account {}: {} -> {}", 
            event.getAccountId(), event.getPreviousBalance(), event.getNewBalance());
        
        auditService.recordBalanceChange(event);
        
        // Check for low balance alerts
        if (event.getNewBalance().compareTo(new BigDecimal("100")) < 0) {
            notificationService.sendLowBalanceAlert(event);
        }
    }

    // Helper methods to publish events
    private void publishFraudSuspectedEvent(BankingEvents.TransactionCreatedEvent source, double riskScore) {
        BankingEvents.FraudSuspectedEvent fraudEvent = BankingEvents.FraudSuspectedEvent.builder()
            .transactionId(source.getTransactionId())
            .accountId(source.getAccountId())
            .amount(source.getAmount())
            .riskScore(riskScore)
            .suspicionReason("Automated risk analysis")
            .detectionSource("ML_MODEL")
            .detectedAt(source.getCreatedAt())
            .build();
        
        // In production, this would be published to Kafka/RabbitMQ
        handleFraudSuspected(fraudEvent);
    }

    private void publishComplianceCheckEvent(BankingEvents.TransactionCreatedEvent source) {
        BankingEvents.ComplianceCheckRequiredEvent complianceEvent = BankingEvents.ComplianceCheckRequiredEvent.builder()
            .transactionId(source.getTransactionId())
            .accountId(source.getAccountId())
            .amount(source.getAmount())
            .transactionType(source.getTransactionType())
            .regulationType("AML")
            .triggeredAt(source.getCreatedAt())
            .build();
        
        // In production, this would be published to Kafka/RabbitMQ
        handleComplianceCheckRequired(complianceEvent);
    }
}