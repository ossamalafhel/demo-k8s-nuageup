package com.bankcore.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Externalized configuration properties for banking application
 * Supports environment-specific configurations with validation
 */
@ConfigurationProperties(prefix = "banking")
@Component
@Data
@Validated
public class BankingConfigProperties {

    @NotNull
    private TransactionConfig transaction = new TransactionConfig();
    
    @NotNull
    private SecurityConfig security = new SecurityConfig();
    
    @NotNull
    private AuditConfig audit = new AuditConfig();
    
    @NotNull
    private MonitoringConfig monitoring = new MonitoringConfig();
    
    @NotNull
    private ComplianceConfig compliance = new ComplianceConfig();

    @Data
    public static class TransactionConfig {
        
        @NotNull
        private BigDecimal maxTransactionAmount = new BigDecimal("1000000.00");
        
        @NotNull
        private BigDecimal largeTransactionThreshold = new BigDecimal("10000.00");
        
        @NotNull
        private BigDecimal dailyTransactionLimit = new BigDecimal("50000.00");
        
        @NotEmpty
        private List<String> supportedCurrencies = List.of("USD", "EUR", "GBP");
        
        @NotEmpty
        private List<String> supportedTransactionTypes = List.of("DEPOSIT", "WITHDRAWAL", "TRANSFER");
        
        @Min(1)
        @Max(3600)
        private int timeoutSeconds = 30;
        
        @Min(1)
        @Max(10)
        private int maxRetryAttempts = 3;
        
        private Duration retryDelay = Duration.ofSeconds(1);
        
        private boolean enableIdempotencyCheck = true;
        
        private boolean enableFraudDetection = true;
        
        private double fraudThreshold = 0.7;
        
        private Duration transactionExpirationTime = Duration.ofHours(24);
    }

    @Data
    public static class SecurityConfig {
        
        @NotEmpty
        private String jwtSecret = "bankcore-secret-key-change-in-production";
        
        @Min(300)
        @Max(86400)
        private int jwtExpirationSeconds = 3600;
        
        @Min(1800)
        @Max(604800)
        private int refreshTokenExpirationSeconds = 86400;
        
        @Min(1)
        @Max(100)
        private int maxLoginAttempts = 5;
        
        private Duration loginAttemptWindow = Duration.ofMinutes(15);
        
        private Duration accountLockoutDuration = Duration.ofMinutes(30);
        
        @NotEmpty
        private List<String> allowedOrigins = List.of("http://localhost:3000", "https://bankcore.com");
        
        @Min(1)
        @Max(10000)
        private int rateLimitRequestsPerMinute = 100;
        
        private boolean enableCsrfProtection = true;
        
        private boolean enableHttpsOnly = true;
        
        @NotEmpty
        private String encryptionAlgorithm = "AES/GCM/NoPadding";
        
        @NotEmpty
        private String hashingAlgorithm = "PBKDF2WithHmacSHA256";
        
        @Min(10000)
        @Max(1000000)
        private int hashingIterations = 100000;
    }

    @Data
    public static class AuditConfig {
        
        private boolean enableMethodAudit = true;
        
        private boolean enableDataModificationAudit = true;
        
        private boolean enableSecurityAudit = true;
        
        private boolean enablePerformanceAudit = true;
        
        @Min(1)
        @Max(365)
        private int auditRetentionDays = 90;
        
        @NotEmpty
        private List<String> sensitiveFields = List.of("password", "ssn", "accountNumber", "cardNumber");
        
        private boolean maskSensitiveData = true;
        
        private boolean enableRealTimeAlerting = true;
        
        @NotEmpty
        private List<String> criticalEvents = List.of(
            "SECURITY_VIOLATION", 
            "FRAUD_DETECTED", 
            "LARGE_TRANSACTION", 
            "COMPLIANCE_FAILURE"
        );
        
        @NotEmpty
        private String auditLogLevel = "INFO";
        
        private Duration auditBatchProcessingInterval = Duration.ofSeconds(30);
    }

    @Data
    public static class MonitoringConfig {
        
        private boolean enableMetrics = true;
        
        private boolean enableTracing = true;
        
        private boolean enableHealthChecks = true;
        
        @Min(1)
        @Max(300)
        private int metricsIntervalSeconds = 10;
        
        @Min(1)
        @Max(3600)
        private int healthCheckIntervalSeconds = 30;
        
        private Duration healthCheckTimeout = Duration.ofSeconds(5);
        
        @NotEmpty
        private List<String> customMetrics = List.of(
            "transaction.processing.time",
            "fraud.detection.score",
            "compliance.check.duration",
            "database.connection.pool.active"
        );
        
        @Min(1)
        @Max(99)
        private int alertThresholdPercentile = 95;
        
        @Min(100)
        @Max(10000)
        private int performanceThresholdMs = 500;
        
        @Min(1)
        @Max(100)
        private int errorRateThresholdPercent = 5;
        
        @NotEmpty
        private Map<String, String> alertChannels = Map.of(
            "email", "alerts@bankcore.com",
            "slack", "#banking-alerts",
            "pagerduty", "banking-service"
        );
    }

    @Data
    public static class ComplianceConfig {
        
        private boolean enablePciDssCompliance = true;
        
        private boolean enableSoxCompliance = true;
        
        private boolean enableGdprCompliance = true;
        
        private boolean enableKycChecks = true;
        
        private boolean enableAmlScreening = true;
        
        @NotNull
        private BigDecimal amlThreshold = new BigDecimal("10000.00");
        
        @NotEmpty
        private List<String> watchlistCountries = List.of("", "IR", "KP", "SY");
        
        @NotEmpty
        private List<String> restrictedCountries = List.of("", "AF", "BY", "MM", "ZW");
        
        @Min(1)
        @Max(365)
        private int dataRetentionDays = 2555; // 7 years for banking
        
        @Min(1)
        @Max(24)
        private int complianceReportingIntervalHours = 24;
        
        private boolean enableAutomaticReporting = true;
        
        @NotEmpty
        private String regulatoryRegion = "US";
        
        @NotEmpty
        private List<String> requiredDocuments = List.of(
            "identity_verification",
            "address_proof", 
            "income_verification"
        );
        
        private Duration documentExpirationPeriod = Duration.ofDays(365);
        
        @Min(1)
        @Max(100)
        private int riskToleranceLevel = 70;
    }

    // Utility methods for feature flags
    public boolean isFeatureEnabled(String featureName) {
        return switch (featureName.toLowerCase()) {
            case "fraud_detection" -> transaction.isEnableFraudDetection();
            case "idempotency_check" -> transaction.isEnableIdempotencyCheck();
            case "csrf_protection" -> security.isEnableCsrfProtection();
            case "https_only" -> security.isEnableHttpsOnly();
            case "real_time_alerts" -> audit.isEnableRealTimeAlerting();
            case "automatic_reporting" -> compliance.isEnableAutomaticReporting();
            default -> false;
        };
    }
    
    public boolean isComplianceRequired(String type) {
        return switch (type.toLowerCase()) {
            case "pci" -> compliance.isEnablePciDssCompliance();
            case "sox" -> compliance.isEnableSoxCompliance();
            case "gdpr" -> compliance.isEnableGdprCompliance();
            case "kyc" -> compliance.isEnableKycChecks();
            case "aml" -> compliance.isEnableAmlScreening();
            default -> false;
        };
    }
    
    public boolean isSensitiveField(String fieldName) {
        return audit.getSensitiveFields().contains(fieldName.toLowerCase());
    }
    
    public boolean isRestrictedCountry(String countryCode) {
        return compliance.getRestrictedCountries().contains(countryCode.toUpperCase());
    }
    
    public boolean isWatchlistCountry(String countryCode) {
        return compliance.getWatchlistCountries().contains(countryCode.toUpperCase());
    }
}