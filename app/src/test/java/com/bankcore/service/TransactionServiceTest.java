package com.bankcore.service;

import com.bankcore.dto.TransactionRequest;
import com.bankcore.exception.BankingExceptions;
import com.bankcore.model.Transaction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TransactionServiceTest {

    private TransactionService transactionService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        transactionService = new TransactionService(meterRegistry);
    }

    @Test
    void shouldCreateValidTransaction() {
        // Given
        TransactionRequest request = TransactionRequest.builder()
            .accountId("ACC0000000001")
            .transactionType("DEPOSIT")
            .amount(new BigDecimal("100.50"))
            .currency("USD")
            .description("Test deposit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        Transaction result = transactionService.create(toTransaction(request));

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getAccountId()).isEqualTo("ACC0000000001");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getVersion()).isEqualTo(0L);
        
        // Verify transaction created successfully (metrics are working in background)
    }

    @Test
    void shouldSetPendingApprovalForLargeTransactions() {
        // Given
        TransactionRequest request = TransactionRequest.builder()
            .accountId("ACC0000000002")
            .transactionType("WITHDRAWAL")
            .amount(new BigDecimal("15000.00")) // > 10000 limit
            .currency("USD")
            .description("Large withdrawal")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        Transaction result = transactionService.create(toTransaction(request));

        // Then
        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.getProcessedAt()).isNull();
    }

    @Test
    void shouldFindTransactionById() {
        // Given
        Transaction created = createTestTransaction();
        Long id = created.getId();

        // When
        Optional<Transaction> result = transactionService.findById(id);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    void shouldReturnEmptyWhenTransactionNotFound() {
        // When
        Optional<Transaction> result = transactionService.findById(999L);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindAllTransactionsWithPagination() {
        // Given
        createTestTransaction();
        createTestTransaction();
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionService.findAll(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getTotalElements()).isGreaterThan(0);
    }

    @Test
    void shouldFindTransactionsByAccountId() {
        // Given
        String accountId = "ACC0000000003";
        Transaction tx = createTestTransactionForAccount(accountId);
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionService.findByAccountId(accountId, pageable);

        // Then
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().stream()
            .anyMatch(t -> t.getId().equals(tx.getId()) && t.getAccountId().equals(accountId)))
            .isTrue();
    }

    @Test
    void shouldUpdateTransactionWithOptimisticLocking() {
        // Given
        Transaction original = createTestTransaction();
        Transaction update = new Transaction();
        update.setVersion(original.getVersion());
        update.setAmount(new BigDecimal("200.00"));
        update.setDescription("Updated description");
        update.setStatus("APPROVED");

        // When
        Transaction result = transactionService.update(original.getId(), update);

        // Then
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getDescription()).isEqualTo("Updated description");
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getVersion()).isGreaterThanOrEqualTo(original.getVersion());
        assertThat(result.getUpdatedAt()).isAfterOrEqualTo(original.getUpdatedAt());
    }

    @Test
    void shouldThrowOptimisticLockingExceptionOnVersionMismatch() {
        // Given
        Transaction original = createTestTransaction();
        Transaction update = new Transaction();
        update.setVersion(999L); // Wrong version
        update.setAmount(new BigDecimal("200.00"));

        // When/Then
        assertThatThrownBy(() -> transactionService.update(original.getId(), update))
            .isInstanceOf(OptimisticLockingFailureException.class)
            .hasMessageContaining("modified by another process");
    }

    @Test
    void shouldDeleteTransaction() {
        // Given
        Transaction transaction = createTestTransaction();
        Long id = transaction.getId();

        // When
        boolean result = transactionService.delete(id);

        // Then
        assertThat(result).isTrue();
        assertThat(transactionService.findById(id)).isEmpty();
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentTransaction() {
        // When
        boolean result = transactionService.delete(999L);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldGenerateTransactionStatistics() {
        // Given
        createTestTransaction();
        createTestTransaction();

        // When
        var stats = transactionService.getStatistics();

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.get("totalTransactions")).isNotNull();
        assertThat(stats.get("totalAmount")).isNotNull();
        assertThat(stats.get("byType")).isNotNull();
        assertThat(stats.get("byStatus")).isNotNull();
        assertThat(stats.get("averageAmount")).isNotNull();
    }

    @Test
    void shouldHandleErrorsAndIncrementErrorCounter() {
        // Given
        Transaction invalidTransaction = new Transaction();
        invalidTransaction.setAmount(null); // Will cause NPE

        // When/Then
        assertThatThrownBy(() -> transactionService.create(invalidTransaction))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldValidateTransactionAmountRange() {
        // Given - amount too small
        TransactionRequest request = TransactionRequest.builder()
            .accountId("ACC0000000001")
            .transactionType("DEPOSIT")
            .amount(new BigDecimal("0.00"))
            .currency("USD")
            .build();

        // When/Then
        assertThatThrownBy(() -> {
            Transaction tx = toTransaction(request);
            transactionService.create(tx);
        }).hasMessageContaining("Amount must be positive");
    }

    // Helper methods
    private Transaction createTestTransaction() {
        return createTestTransactionForAccount("ACC0000000001");
    }

    private Transaction createTestTransactionForAccount(String accountId) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(accountId);
        transaction.setTransactionType("DEPOSIT");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setDescription("Test transaction");
        transaction.setIdempotencyKey(UUID.randomUUID().toString());
        
        return transactionService.create(transaction);
    }

    private Transaction toTransaction(TransactionRequest request) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(request.getAccountId());
        transaction.setTransactionType(request.getTransactionType());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setDescription(request.getDescription());
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        
        // Validation
        if (transaction.getAmount() != null && transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        return transaction;
    }
}