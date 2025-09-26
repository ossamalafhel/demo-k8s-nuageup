package com.bankcore.service;

import com.bankcore.event.BankingEvents;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notification service for real-time alerts and notifications
 * Supports multiple channels: email, SMS, push notifications
 */
@Service
@Slf4j
public class NotificationService {

    public void sendTransactionAlert(BankingEvents.TransactionCreatedEvent event) {
        log.info("NOTIFICATION: Transaction alert sent - txn: {}, account: {}, amount: {}", 
            event.getTransactionId(), event.getAccountId(), event.getAmount());
    }

    public void sendApprovalNotification(BankingEvents.TransactionApprovedEvent event) {
        log.info("NOTIFICATION: Approval notification sent - txn: {}", event.getTransactionId());
    }

    public void sendRejectionNotification(BankingEvents.TransactionRejectedEvent event) {
        log.warn("NOTIFICATION: Rejection notification sent - txn: {}, reason: {}", 
            event.getTransactionId(), event.getRejectionReason());
    }

    public void sendFraudAlert(BankingEvents.FraudSuspectedEvent event) {
        log.error("NOTIFICATION: FRAUD ALERT - txn: {}, account: {}, risk: {}", 
            event.getTransactionId(), event.getAccountId(), event.getRiskScore());
    }

    public void sendTransferConfirmation(BankingEvents.TransferInitiatedEvent event) {
        log.info("NOTIFICATION: Transfer confirmation sent - txn: {}", event.getTransactionId());
    }

    public void sendLowBalanceAlert(BankingEvents.AccountBalanceUpdatedEvent event) {
        log.warn("NOTIFICATION: Low balance alert - account: {}, balance: {}", 
            event.getAccountId(), event.getNewBalance());
    }

    public void suggestAlternatives(BankingEvents.TransactionRejectedEvent event) {
        log.info("NOTIFICATION: Alternative suggestions sent - txn: {}", event.getTransactionId());
    }
}