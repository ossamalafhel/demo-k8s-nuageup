package com.bankcore.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", schema = "banking", indexes = {
    @Index(name = "idx_account_id", columnList = "account_id"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_by", columnList = "created_by"),
    @Index(name = "idx_idempotency_key", columnList = "idempotency_key")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Account ID is required")
    @Size(min = 10, max = 20)
    @Column(name = "account_id", nullable = false, length = 20)
    private String accountId;

    @NotBlank(message = "Transaction type is required")
    @Pattern(regexp = "DEPOSIT|WITHDRAWAL|TRANSFER", message = "Invalid transaction type")
    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "1000000.00", message = "Amount exceeds maximum limit")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "USD|EUR|GBP", message = "Invalid currency")
    @Column(nullable = false, length = 3)
    private String currency;

    @Size(max = 255)
    private String description;

    @Column(name = "reference_number", unique = true, length = 50)
    private String referenceNumber;

    @Column(name = "target_account", length = 20)
    private String targetAccount;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "message")
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // Audit fields for compliance and security
    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 50)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "last_modified_by", length = 50)
    private String lastModifiedBy;

    // Idempotency key for duplicate prevention
    @Column(name = "idempotency_key", unique = true, length = 36)
    private String idempotencyKey;

    // Client IP for security audit
    @Column(name = "client_ip", length = 45)
    private String clientIp;

    // User agent for fraud detection
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        if (referenceNumber == null) {
            referenceNumber = "TXN-" + System.currentTimeMillis() + "-" + Math.random();
        }
    }
}