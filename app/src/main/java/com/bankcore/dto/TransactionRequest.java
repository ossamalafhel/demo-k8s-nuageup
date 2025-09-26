package com.bankcore.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transaction creation request")
public class TransactionRequest {

    @NotBlank(message = "Account ID is required")
    @Size(min = 10, max = 34, message = "Account ID must be between 10 and 34 characters")
    @Pattern(
        regexp = "^[A-Z0-9]+$", 
        message = "Account ID must contain only alphanumeric characters"
    )
    @Schema(
        description = "Account identifier (IBAN format recommended)", 
        example = "ACC0000000001",
        minLength = 10,
        maxLength = 34
    )
    private String accountId;

    @NotBlank(message = "Transaction type is required")
    @Pattern(
        regexp = "^(DEPOSIT|WITHDRAWAL|TRANSFER)$", 
        message = "Transaction type must be DEPOSIT, WITHDRAWAL, or TRANSFER"
    )
    @Schema(
        description = "Type of transaction", 
        example = "DEPOSIT",
        allowableValues = {"DEPOSIT", "WITHDRAWAL", "TRANSFER"}
    )
    private String transactionType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "1000000.00", message = "Amount cannot exceed 1,000,000.00")
    @Digits(integer = 7, fraction = 2, message = "Amount format invalid")
    @Schema(
        description = "Transaction amount", 
        example = "100.50",
        minimum = "0.01",
        maximum = "1000000.00"
    )
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Pattern(
        regexp = "^(USD|EUR|GBP)$", 
        message = "Currency must be USD, EUR, or GBP"
    )
    @Schema(
        description = "Currency code (ISO 4217)", 
        example = "USD",
        allowableValues = {"USD", "EUR", "GBP"}
    )
    private String currency;

    @Size(max = 140, message = "Description cannot exceed 140 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9\\s.,'()-]*$", 
        message = "Description contains invalid characters"
    )
    @Schema(
        description = "Transaction description", 
        example = "Monthly salary deposit",
        maxLength = 140
    )
    private String description;

    @Size(max = 34, message = "Target account cannot exceed 34 characters")
    @Pattern(
        regexp = "^[A-Z0-9]*$", 
        message = "Target account must contain only alphanumeric characters"
    )
    @Schema(
        description = "Target account for transfers", 
        example = "ACC0000000002",
        maxLength = 34
    )
    private String targetAccount;

    @Size(min = 36, max = 36, message = "Idempotency key must be a valid UUID")
    @Pattern(
        regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        message = "Idempotency key must be a valid UUID format"
    )
    @Schema(
        description = "Idempotency key to prevent duplicate processing",
        example = "123e4567-e89b-12d3-a456-426614174000",
        pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )
    private String idempotencyKey;
}