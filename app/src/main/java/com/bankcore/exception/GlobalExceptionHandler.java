package com.bankcore.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BankingExceptions.InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            BankingExceptions.InsufficientFundsException ex, WebRequest request) {
        
        log.error("Insufficient funds: account={}, requestedAmount={}, availableBalance={}", 
            ex.getAccountId(), ex.getRequestedAmount(), ex.getAvailableBalance());
            
        ErrorResponse error = ErrorResponse.builder()
            .code("INSUFFICIENT_FUNDS")
            .message("Insufficient funds for this transaction")
            .details("Available balance: " + ex.getAvailableBalance() + 
                    ", Requested amount: " + ex.getRequestedAmount())
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
            
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(error);
    }

    @ExceptionHandler(BankingExceptions.TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(
            BankingExceptions.TransactionNotFoundException ex, WebRequest request) {
        
        log.warn("Transaction not found: id={}", ex.getTransactionId());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("TRANSACTION_NOT_FOUND")
            .message("Transaction not found")
            .details("Transaction ID: " + ex.getTransactionId())
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
            
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(BankingExceptions.DuplicateTransactionException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTransaction(
            BankingExceptions.DuplicateTransactionException ex, WebRequest request) {
        
        log.info("Duplicate transaction detected: idempotencyKey={}", ex.getIdempotencyKey());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("DUPLICATE_TRANSACTION")
            .message("Transaction already processed")
            .details("Idempotency key: " + ex.getIdempotencyKey())
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
            
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(
            OptimisticLockingFailureException ex, WebRequest request) {
        
        log.warn("Optimistic locking conflict detected");
        
        ErrorResponse error = ErrorResponse.builder()
            .code("CONCURRENT_MODIFICATION")
            .message("Resource was modified by another process")
            .details("Please refresh the data and try again")
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
            
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(TransientDataAccessException.class)
    public ResponseEntity<ErrorResponse> handleTransientDataAccess(
            TransientDataAccessException ex, WebRequest request) {
        
        log.error("Transient database error", ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .code("TEMPORARY_UNAVAILABLE")
            .message("Service temporarily unavailable")
            .details("Please try again in a few moments")
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
            
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });
        
        log.warn("Validation failed: {}", validationErrors);
        
        ErrorResponse error = ErrorResponse.builder()
            .code("VALIDATION_FAILED")
            .message("Input validation failed")
            .details("Invalid fields: " + 
                validationErrors.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(Collectors.joining(", ")))
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .path(request.getDescription(false).replace("uri=", ""))
            .validationErrors(validationErrors)
            .build();
            
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        
        Map<String, String> validationErrors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            validationErrors.put(fieldName, errorMessage);
        }
        
        log.warn("Constraint violation: {}", validationErrors);
        
        ErrorResponse error = ErrorResponse.builder()
            .code("CONSTRAINT_VIOLATION")
            .message("Data constraint violation")
            .details(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .path(request.getDescription(false).replace("uri=", ""))
            .validationErrors(validationErrors)
            .build();
            
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        log.warn("Invalid argument: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("INVALID_ARGUMENT")
            .message("Invalid request parameter")
            .details(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
            
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, WebRequest request) {
        
        // Log complet côté serveur pour debugging
        log.error("Unexpected error occurred", ex);
        
        // JAMAIS exposer les détails internes au client (sécurité!)
        ErrorResponse error = ErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .message("An internal error occurred")
            .details("Please contact support if the issue persists")
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
            
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}