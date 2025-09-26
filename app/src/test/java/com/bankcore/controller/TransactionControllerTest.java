package com.bankcore.controller;

import com.bankcore.dto.TransactionRequest;
import com.bankcore.exception.BankingExceptions;
import com.bankcore.model.Transaction;
import com.bankcore.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = {"TRANSACTION_CREATE"})
    void shouldCreateTransaction() throws Exception {
        // Given
        TransactionRequest request = TransactionRequest.builder()
            .accountId("ACC0000000001")
            .transactionType("DEPOSIT")
            .amount(new BigDecimal("100.50"))
            .currency("USD")
            .description("Test deposit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        Transaction createdTransaction = createMockTransaction(1L, request);
        when(transactionService.create(any(Transaction.class))).thenReturn(createdTransaction);

        // When/Then
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.accountId").value("ACC0000000001"))
                .andExpect(jsonPath("$.amount").value(100.50))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(authorities = {"TRANSACTION_CREATE"})
    void shouldValidateRequiredFields() throws Exception {
        // Given - request with missing required fields
        TransactionRequest invalidRequest = TransactionRequest.builder().build();

        // When/Then
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @WithMockUser(authorities = {"TRANSACTION_CREATE"})
    void shouldValidateAmountRange() throws Exception {
        // Given - request with invalid amount
        TransactionRequest invalidRequest = TransactionRequest.builder()
            .accountId("ACC0000000001")
            .transactionType("DEPOSIT")
            .amount(new BigDecimal("-10.00")) // Negative amount
            .currency("USD")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When/Then
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.amount").exists());
    }

    @Test
    @WithMockUser(authorities = {"TRANSACTION_CREATE"})
    void shouldValidateTransactionType() throws Exception {
        // Given - request with invalid transaction type
        TransactionRequest invalidRequest = TransactionRequest.builder()
            .accountId("ACC0000000001")
            .transactionType("INVALID_TYPE")
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When/Then
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.transactionType").exists());
    }

    @Test
    @WithMockUser(authorities = {"TRANSACTION_READ"})
    void shouldGetTransactionById() throws Exception {
        // Given
        Transaction transaction = createMockTransaction(1L);
        when(transactionService.findById(1L)).thenReturn(Optional.of(transaction));

        // When/Then
        mockMvc.perform(get("/api/v1/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.accountId").value("ACC0000000001"));
    }

    @Test
    @WithMockUser(authorities = {"TRANSACTION_READ"})
    void shouldReturnNotFoundForNonExistentTransaction() throws Exception {
        // Given
        when(transactionService.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/v1/transactions/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = {"TRANSACTION_READ"})
    void shouldGetTransactionsWithPagination() throws Exception {
        // Given
        Transaction transaction = createMockTransaction(1L);
        Page<Transaction> page = new PageImpl<>(
            Collections.singletonList(transaction),
            PageRequest.of(0, 20),
            1
        );
        when(transactionService.findAll(any())).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/v1/transactions")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @WithMockUser(authorities = {"TRANSACTION_CREATE"})
    void shouldHandleInsufficientFundsException() throws Exception {
        // Given
        TransactionRequest request = TransactionRequest.builder()
            .accountId("ACC0000000001")
            .transactionType("WITHDRAWAL")
            .amount(new BigDecimal("1000.00"))
            .currency("USD")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        when(transactionService.create(any(Transaction.class)))
            .thenThrow(new BankingExceptions.InsufficientFundsException(
                "ACC0000000001", 
                new BigDecimal("1000.00"), 
                new BigDecimal("50.00")
            ));

        // When/Then
        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details").exists());
    }

    @Test
    @WithMockUser(authorities = {"TRANSACTION_READ"})
    void shouldHandleTransactionNotFoundException() throws Exception {
        // Given
        when(transactionService.findById(999L))
            .thenThrow(new BankingExceptions.TransactionNotFoundException(999L));

        // When/Then
        mockMvc.perform(get("/api/v1/transactions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(authorities = {"TRANSACTION_READ"})
    void shouldGetStatistics() throws Exception {
        // Given
        when(transactionService.getStatistics()).thenReturn(
            Collections.singletonMap("totalTransactions", 5)
        );

        // When/Then
        mockMvc.perform(get("/api/v1/transactions/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(5));
    }

    // Helper methods
    private Transaction createMockTransaction(Long id) {
        return createMockTransaction(id, null);
    }

    private Transaction createMockTransaction(Long id, TransactionRequest request) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        
        if (request != null) {
            transaction.setAccountId(request.getAccountId());
            transaction.setTransactionType(request.getTransactionType());
            transaction.setAmount(request.getAmount());
            transaction.setCurrency(request.getCurrency());
            transaction.setDescription(request.getDescription());
            transaction.setIdempotencyKey(request.getIdempotencyKey());
        } else {
            transaction.setAccountId("ACC0000000001");
            transaction.setTransactionType("DEPOSIT");
            transaction.setAmount(new BigDecimal("100.00"));
            transaction.setCurrency("USD");
            transaction.setDescription("Test transaction");
            transaction.setIdempotencyKey(UUID.randomUUID().toString());
        }
        
        transaction.setStatus("COMPLETED");
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction.setVersion(0L);
        transaction.setCreatedBy("test-user");
        
        return transaction;
    }
}