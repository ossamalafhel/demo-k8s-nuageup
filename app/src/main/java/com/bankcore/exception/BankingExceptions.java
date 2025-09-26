package com.bankcore.exception;

import lombok.Getter;
import java.math.BigDecimal;

/**
 * Custom business exceptions for banking domain
 */
public class BankingExceptions {

    @Getter
    public static class InsufficientFundsException extends RuntimeException {
        private final String accountId;
        private final BigDecimal requestedAmount;
        private final BigDecimal availableBalance;

        public InsufficientFundsException(String accountId, BigDecimal requestedAmount, BigDecimal availableBalance) {
            super(String.format("Insufficient funds for account %s: requested=%.2f, available=%.2f", 
                accountId, requestedAmount, availableBalance));
            this.accountId = accountId;
            this.requestedAmount = requestedAmount;
            this.availableBalance = availableBalance;
        }
    }

    @Getter
    public static class TransactionNotFoundException extends RuntimeException {
        private final Long transactionId;

        public TransactionNotFoundException(Long transactionId) {
            super("Transaction not found: " + transactionId);
            this.transactionId = transactionId;
        }
    }

    @Getter
    public static class DuplicateTransactionException extends RuntimeException {
        private final String idempotencyKey;

        public DuplicateTransactionException(String idempotencyKey) {
            super("Transaction already processed with idempotency key: " + idempotencyKey);
            this.idempotencyKey = idempotencyKey;
        }
    }

    @Getter
    public static class AccountNotFoundException extends RuntimeException {
        private final String accountId;

        public AccountNotFoundException(String accountId) {
            super("Account not found: " + accountId);
            this.accountId = accountId;
        }
    }

    @Getter
    public static class TransactionLimitExceededException extends RuntimeException {
        private final BigDecimal requestedAmount;
        private final BigDecimal dailyLimit;

        public TransactionLimitExceededException(BigDecimal requestedAmount, BigDecimal dailyLimit) {
            super(String.format("Transaction limit exceeded: requested=%.2f, daily_limit=%.2f", 
                requestedAmount, dailyLimit));
            this.requestedAmount = requestedAmount;
            this.dailyLimit = dailyLimit;
        }
    }

    @Getter
    public static class InvalidTransactionStateException extends RuntimeException {
        private final Long transactionId;
        private final String currentState;
        private final String expectedState;

        public InvalidTransactionStateException(Long transactionId, String currentState, String expectedState) {
            super(String.format("Invalid transaction state for ID %d: current=%s, expected=%s", 
                transactionId, currentState, expectedState));
            this.transactionId = transactionId;
            this.currentState = currentState;
            this.expectedState = expectedState;
        }
    }
}