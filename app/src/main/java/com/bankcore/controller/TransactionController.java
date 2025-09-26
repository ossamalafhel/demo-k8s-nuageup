package com.bankcore.controller;

import com.bankcore.model.Transaction;
import com.bankcore.service.TransactionService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Banking Transactions", description = "Banking transaction operations with enterprise security")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @Timed(value = "transactions.get.all", description = "Time taken to get all transactions")
    @Operation(
        summary = "Get all transactions with pagination",
        description = "Retrieves a paginated list of all banking transactions with sorting and filtering capabilities"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions"),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded")
    })
    public ResponseEntity<Page<Transaction>> getAllTransactions(
            @Parameter(description = "Pagination and sorting parameters") Pageable pageable) {
        log.info("Getting all transactions with pagination: {}", pageable);
        return ResponseEntity.ok(transactionService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Timed(value = "transactions.get.by.id", description = "Time taken to get transaction by ID")
    public ResponseEntity<Transaction> getTransaction(@PathVariable Long id) {
        log.info("Getting transaction with ID: {}", id);
        return transactionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @CircuitBreaker(name = "backend", fallbackMethod = "createTransactionFallback")
    @RateLimiter(name = "backend")
    @Timed(value = "transactions.create", description = "Time taken to create transaction")
    public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody Transaction transaction) {
        log.info("Creating new transaction: {}", transaction);
        Transaction created = transactionService.create(transaction);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Timed(value = "transactions.update", description = "Time taken to update transaction")
    public ResponseEntity<Transaction> updateTransaction(
            @PathVariable Long id, 
            @Valid @RequestBody Transaction transaction) {
        log.info("Updating transaction with ID: {}", id);
        Transaction updated = transactionService.update(id, transaction);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Timed(value = "transactions.delete", description = "Time taken to delete transaction")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        log.info("Deleting transaction with ID: {}", id);
        if (transactionService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/statistics")
    @Timed(value = "transactions.statistics", description = "Time taken to get statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.info("Getting transaction statistics");
        return ResponseEntity.ok(transactionService.getStatistics());
    }

    @GetMapping("/account/{accountId}")
    @Timed(value = "transactions.by.account", description = "Time taken to get transactions by account")
    public ResponseEntity<Page<Transaction>> getTransactionsByAccount(
            @PathVariable String accountId, 
            Pageable pageable) {
        log.info("Getting transactions for account: {}", accountId);
        return ResponseEntity.ok(transactionService.findByAccountId(accountId, pageable));
    }

    public ResponseEntity<Transaction> createTransactionFallback(Transaction transaction, Exception ex) {
        log.error("Circuit breaker opened. Fallback method called due to: {}", ex.getMessage());
        Transaction fallbackTransaction = new Transaction();
        fallbackTransaction.setStatus("PENDING");
        fallbackTransaction.setMessage("Service temporarily unavailable. Transaction queued.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(fallbackTransaction);
    }
}