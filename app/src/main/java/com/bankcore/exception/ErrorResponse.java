package com.bankcore.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response")
public class ErrorResponse {

    @Schema(description = "Error code", example = "INSUFFICIENT_FUNDS")
    private String code;

    @Schema(description = "Human-readable error message", example = "Insufficient funds for this transaction")
    private String message;

    @Schema(description = "Additional error details", example = "Available balance: 100.00, Requested amount: 150.00")
    private String details;

    @Schema(description = "Error timestamp", example = "2025-01-15T10:30:00")
    private LocalDateTime timestamp;

    @Schema(description = "Request trace ID for debugging", example = "abc123-def456-ghi789")
    private String traceId;

    @Schema(description = "Request path", example = "/api/v1/transactions")
    private String path;

    @Schema(description = "Field validation errors")
    private Map<String, String> validationErrors;
}