package com.bankcore.service;

import com.bankcore.model.Transaction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TransactionService {

    private final Map<Long, Transaction> transactionRepository = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Counter transactionCounter;
    private final Counter errorCounter;
    private final Counter retryCounter;

    public TransactionService(MeterRegistry meterRegistry) {
        this.transactionCounter = Counter.builder("transactions.processed")
                .description("Number of transactions processed")
                .register(meterRegistry);
                
        this.errorCounter = Counter.builder("transactions.errors")
                .description("Number of transaction errors")
                .register(meterRegistry);
                
        this.retryCounter = Counter.builder("transactions.retries")
                .description("Number of transaction retries")
                .register(meterRegistry);

        initializeSampleData();
    }

    /**
     * Read-only transaction for queries - routes to read replicas in production
     */
    @Transactional(readOnly = true, timeout = 30)
    public Page<Transaction> findAll(Pageable pageable) {
        log.debug("Finding all transactions with pagination: {}", pageable);
        
        List<Transaction> allTransactions = new ArrayList<>(transactionRepository.values());
        allTransactions.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allTransactions.size());
        
        List<Transaction> pageContent = allTransactions.subList(start, end);
        return new PageImpl<>(pageContent, pageable, allTransactions.size());
    }

    /**
     * Read-only transaction with timeout
     */
    @Transactional(readOnly = true, timeout = 10)
    public Optional<Transaction> findById(Long id) {
        log.debug("Finding transaction by ID: {}", id);
        return Optional.ofNullable(transactionRepository.get(id));
    }

    /**
     * Write transaction with retry logic for transient failures
     * Includes optimistic locking handling
     */
    @Transactional(timeout = 30)
    @Retryable(
        value = { TransientDataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Transaction create(Transaction transaction) {
        try {
            log.info("Creating transaction for account: {}", transaction.getAccountId());
            
            Long id = idGenerator.getAndIncrement();
            transaction.setId(id);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());
            transaction.setVersion(0L); // Initial version for optimistic locking
            
            // Business logic processing
            processTransaction(transaction);
            
            // Simulate database save with version check
            transactionRepository.put(id, transaction);
            transactionCounter.increment();
            
            log.info("Transaction created successfully: {} with ID: {}", 
                transaction.getReferenceNumber(), id);
            return transaction;
            
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error creating transaction for account: {}", 
                transaction.getAccountId(), e);
            transaction.setStatus("FAILED");
            transaction.setMessage(e.getMessage());
            throw e;
        }
    }

    /**
     * Update with optimistic locking support
     */
    @Transactional(timeout = 30)
    @Retryable(
        value = { OptimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 1.5)
    )
    public Transaction update(Long id, Transaction updatedTransaction) {
        try {
            log.info("Updating transaction with ID: {}", id);
            
            Transaction existing = transactionRepository.get(id);
            if (existing == null) {
                throw new IllegalArgumentException("Transaction not found: " + id);
            }
            
            // Check version for optimistic locking (simulated)
            if (!existing.getVersion().equals(updatedTransaction.getVersion())) {
                throw new OptimisticLockingFailureException(
                    "Transaction was modified by another process");
            }
            
            // Update fields
            existing.setAmount(updatedTransaction.getAmount());
            existing.setDescription(updatedTransaction.getDescription());
            existing.setStatus(updatedTransaction.getStatus());
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setVersion(existing.getVersion() + 1); // Increment version
            
            log.info("Transaction updated successfully: {}", id);
            return existing;
            
        } catch (OptimisticLockingFailureException e) {
            retryCounter.increment();
            log.warn("Optimistic locking conflict for transaction {}, retrying...", id);
            throw e;
        }
    }

    /**
     * Delete transaction (soft delete in production)
     */
    @Transactional(timeout = 15)
    public boolean delete(Long id) {
        log.info("Deleting transaction with ID: {}", id);
        
        Transaction deleted = transactionRepository.remove(id);
        boolean success = deleted != null;
        
        if (success) {
            log.info("Transaction deleted successfully: {}", id);
        } else {
            log.warn("Transaction not found for deletion: {}", id);
        }
        
        return success;
    }

    /**
     * Query by account ID with read-only transaction
     */
    @Transactional(readOnly = true, timeout = 20)
    public Page<Transaction> findByAccountId(String accountId, Pageable pageable) {
        log.debug("Finding transactions for account: {}", accountId);
        
        List<Transaction> accountTransactions = transactionRepository.values().stream()
                .filter(t -> t.getAccountId().equals(accountId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
                
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), accountTransactions.size());
        
        List<Transaction> pageContent = accountTransactions.subList(start, end);
        return new PageImpl<>(pageContent, pageable, accountTransactions.size());
    }

    /**
     * Statistics with read-only transaction
     */
    @Transactional(readOnly = true, timeout = 30)
    public Map<String, Object> getStatistics() {
        log.debug("Generating transaction statistics");
        
        Map<String, Object> stats = new HashMap<>();
        List<Transaction> transactions = new ArrayList<>(transactionRepository.values());
        
        stats.put("totalTransactions", transactions.size());
        stats.put("totalAmount", transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
                
        Map<String, Long> byType = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getTransactionType, Collectors.counting()));
        stats.put("byType", byType);
        
        Map<String, Long> byStatus = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getStatus, Collectors.counting()));
        stats.put("byStatus", byStatus);
        
        stats.put("averageAmount", transactions.isEmpty() ? 0 : 
                transactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(transactions.size()), 2, BigDecimal.ROUND_HALF_UP));
                        
        return stats;
    }

    /**
     * Recovery method for failed retries
     */
    @Recover
    public Transaction recoverCreateTransaction(
            TransientDataAccessException e, 
            Transaction transaction) {
        log.error("Failed to create transaction after retries for account: {}", 
            transaction.getAccountId(), e);
        
        errorCounter.increment();
        transaction.setStatus("FAILED");
        transaction.setMessage("System temporarily unavailable, please try again later");
        
        // In production: send to dead letter queue, alert monitoring, etc.
        return transaction;
    }

    @Recover
    public Transaction recoverUpdateTransaction(
            OptimisticLockingFailureException e,
            Long id,
            Transaction transaction) {
        log.error("Optimistic locking failure after retries for transaction: {}", id, e);
        
        errorCounter.increment();
        throw new IllegalStateException(
            "Transaction was modified by another process. Please refresh and try again.");
    }

    /**
     * Business logic for transaction processing
     */
    private void processTransaction(Transaction transaction) {
        // Simulate processing delay (remove in real implementation)
        simulateProcessingDelay();
        
        // Business rules
        if (transaction.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            transaction.setStatus("PENDING_APPROVAL");
            log.info("Large transaction requires approval: {} for amount: {}", 
                transaction.getReferenceNumber(), transaction.getAmount());
        } else {
            transaction.setStatus("COMPLETED");
            transaction.setProcessedAt(LocalDateTime.now());
            log.debug("Transaction processed successfully: {}", transaction.getReferenceNumber());
        }
    }

    private void simulateProcessingDelay() {
        try {
            // Simulate variable processing time (remove in production)
            Thread.sleep((long) (Math.random() * 100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Processing interrupted", e);
        }
    }

    /**
     * Initialize sample data for testing
     */
    private void initializeSampleData() {
        log.info("Initializing sample transaction data");
        
        for (int i = 0; i < 20; i++) {
            Transaction transaction = new Transaction();
            transaction.setId(idGenerator.getAndIncrement());
            transaction.setAccountId("ACC" + String.format("%010d", i % 5));
            transaction.setTransactionType(getRandomType());
            transaction.setAmount(BigDecimal.valueOf(Math.random() * 5000 + 10));
            transaction.setCurrency("USD");
            transaction.setDescription("Sample transaction " + i);
            transaction.setStatus("COMPLETED");
            transaction.setCreatedAt(LocalDateTime.now().minusDays(i));
            transaction.setUpdatedAt(LocalDateTime.now().minusDays(i));
            transaction.setProcessedAt(LocalDateTime.now().minusDays(i));
            transaction.setVersion(0L); // Initial version
            
            transactionRepository.put(transaction.getId(), transaction);
        }
        
        log.info("Initialized {} sample transactions", transactionRepository.size());
    }

    private String getRandomType() {
        String[] types = {"DEPOSIT", "WITHDRAWAL", "TRANSFER"};
        return types[new Random().nextInt(types.length)];
    }
}