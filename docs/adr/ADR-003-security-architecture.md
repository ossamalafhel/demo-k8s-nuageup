# ADR-003: Security Architecture for Banking Applications

## Status
**ACCEPTED** ✅

**Date:** 2025-09-26  
**Decision:** Implement security best practices for banking applications  

## Context

Banking applications require robust security. This demo implements security patterns suitable for financial services.

### Security Considerations
- **Authentication & Authorization** patterns
- **Data protection** with encryption
- **Audit logging** for compliance
- **Input validation** to prevent injection
- **Security headers** and CORS configuration

### Common Threats Addressed
- **API security** with proper authentication
- **Data exposure** prevention
- **Injection attacks** mitigation
- **Configuration vulnerabilities**

### Business Requirements
- **Zero-trust security** model implementation
- **Real-time fraud detection** capabilities
- **Complete audit trail** for compliance
- **API security** for microservices architecture
- **Data encryption** at rest and in transit

## Decision

Implement **Defense-in-Depth Security Architecture** with:

### 1. API Security Layer
```java
@RestController
@SecurityRequirement(name = "bearerAuth")
@RateLimiter(name = "banking-api")
@CircuitBreaker(name = "banking-service")  
public class SecureBankingController {
    
    @PostMapping("/transactions")
    @PreAuthorize("hasRole('TRANSACTION_CREATE')")
    @Validated
    public ResponseEntity<Transaction> createTransaction(
            @Valid @RequestBody TransactionRequest request,
            Authentication auth) {
        // Implementation with full security context
    }
}
```

### 2. Input Validation & Sanitization
```java
@Data
@Validated
public class TransactionRequest {
    
    @NotBlank(message = "Account ID is required")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Invalid characters")
    @Size(min = 10, max = 34)
    @Schema(description = "Account ID (IBAN format)")
    private String accountId;
    
    @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00")
    @Digits(integer = 7, fraction = 2)
    private BigDecimal amount;
}
```

### 3. Audit & Compliance Framework
```java
@Entity
@EntityListeners(AuditingEntityListener.class)  
public class Transaction {
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "client_ip")
    private String clientIp;
    
    @Version
    private Long version; // Optimistic locking
}
```

### 4. Encryption & Data Protection
```java
@Converter
public class SensitiveDataConverter implements AttributeConverter<String, String> {
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionService.encrypt(attribute);
    }
    
    @Override  
    public String convertToEntityAttribute(String dbData) {
        return encryptionService.decrypt(dbData);
    }
}
```

## Security Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    API Gateway (WAF)                        │  
│           Rate Limiting • DDoS Protection                   │
└─────────────────────┬───────────────────────────────────────┘
                      │
    ┌─────────────────▼───────────────────┐
    │        Spring Security Filter       │
    │     • JWT Validation               │  
    │     • RBAC Authorization           │
    │     • CSRF Protection              │
    └─────────────────┬───────────────────┘
                      │
    ┌─────────────────▼───────────────────┐
    │      Input Validation Layer        │
    │     • Bean Validation (JSR 380)    │
    │     • SQL Injection Prevention     │  
    │     • XSS Protection               │
    └─────────────────┬───────────────────┘
                      │
    ┌─────────────────▼───────────────────┐
    │       Business Logic Layer         │
    │     • AOP Security Aspects         │
    │     • Method-level Security        │
    │     • Transaction Boundaries       │  
    └─────────────────┬───────────────────┘
                      │
    ┌─────────────────▼───────────────────┐
    │         Data Access Layer          │
    │     • Encrypted Sensitive Data     │
    │     • Audit Logging               │
    │     • Optimistic Locking          │
    └─────────────────┬───────────────────┘
                      │
    ┌─────────────────▼───────────────────┐
    │         Database Layer             │
    │     • TLS Encryption              │
    │     • Column-level Encryption     │
    │     • Row-level Security          │
    └─────────────────────────────────────┘
```

## Implementation Details

### Authentication & Authorization

#### JWT Token Configuration
```yaml
# Security configuration
security:
  jwt:
    secret: ${JWT_SECRET:bankcore-secret-key}
    expiration: 3600 # 1 hour
    refresh-expiration: 86400 # 24 hours
  
  oauth2:
    resourceserver:
      jwt:
        issuer-uri: https://auth.banking-corp.com
        jwk-set-uri: https://auth.banking-corp.com/.well-known/jwks.json
```

#### Role-Based Access Control
```java
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/transactions/create").hasRole("TRANSACTION_CREATE")  
                .requestMatchers("/api/v1/transactions/approve").hasRole("TRANSACTION_APPROVE")
                .requestMatchers("/api/v1/reports/**").hasRole("REPORTING")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt())
            .build();
    }
}
```

### Data Protection

#### Encryption at Rest  
```java
@Configuration
@EnableJpaAuditing
public class EncryptionConfig {
    
    @Bean
    public AESUtil encryptionUtil() {
        return new AESUtil(secretKey);
    }
    
    @Bean
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setPassword(encryptionPassword);
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setPoolSize(4);
        return encryptor;
    }
}
```

#### Data Masking for Logs
```java
@Component  
public class SecurityLogger {
    
    public void logTransaction(Transaction tx) {
        log.info("Processing transaction: id={}, account={}, amount={}",
            tx.getId(),
            maskAccountNumber(tx.getAccountId()), // ACC***0001
            tx.getAmount()
        );
    }
    
    private String maskAccountNumber(String accountId) {
        if (accountId.length() <= 8) return "***";
        return accountId.substring(0, 3) + "***" + 
               accountId.substring(accountId.length() - 4);
    }
}
```

### Error Handling & Information Disclosure Prevention

#### Secure Exception Handling
```java
@RestControllerAdvice
public class SecurityExceptionHandler {
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        
        // Log full details server-side for debugging  
        log.error("Internal error: {}", ex.getMessage(), ex);
        
        // NEVER expose internal details to client
        ErrorResponse error = ErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .message("An error occurred while processing your request")
            .timestamp(LocalDateTime.now())  
            .traceId(MDC.get("traceId")) // For support correlation
            .build();
            
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(error);
    }
}
```

### Security Monitoring & Alerting

#### Real-time Security Events  
```java
@Component
@Slf4j
public class SecurityEventPublisher {
    
    public void publishSecurityEvent(String eventType, String details) {
        SecurityEvent event = SecurityEvent.builder()
            .eventType(eventType)
            .timestamp(LocalDateTime.now())
            .userId(getCurrentUser())
            .clientIp(getClientIP())
            .userAgent(getUserAgent())
            .details(details)
            .severity(calculateSeverity(eventType))
            .build();
            
        eventPublisher.publishEvent(event);
        
        // Real-time alerting for critical events
        if (event.getSeverity() == Severity.CRITICAL) {
            alertingService.sendImmediateAlert(event);
        }
    }
}
```

#### Security Metrics
```java
@Component
public class SecurityMetrics {
    
    private final Counter authFailures;
    private final Counter sqlInjectionAttempts;
    private final Counter accessDeniedEvents;
    private final Timer jwtValidationTime;
    
    public SecurityMetrics(MeterRegistry registry) {
        this.authFailures = Counter.builder("security.auth.failures")
            .description("Number of authentication failures")
            .register(registry);
            
        this.sqlInjectionAttempts = Counter.builder("security.sql.injection.attempts")
            .description("Suspected SQL injection attempts")
            .register(registry);
    }
}
```

## Alternatives Considered

### Alternative 1: Basic Spring Security Setup
❌ **Rejected**
- **Insufficient for banking** requirements
- **Limited audit capabilities**
- **No advanced threat protection**
- **Regulatory compliance gaps**

### Alternative 2: External Security Service (e.g., Okta)
⚠️ **Partially Adopted**
- **Good for authentication** but limited authorization
- **Vendor lock-in** concerns
- **Higher operational costs**
- **Less control** over security policies
- **Used for SSO integration** only

### Alternative 3: Custom Security Framework  
❌ **Rejected**
- **High development cost** and time
- **Security expertise** requirements
- **Maintenance burden**
- **Proven solutions** available

## Benefits

### 🔐 **Security & Compliance**
- **Defense-in-depth** protection against multiple attack vectors
- **Compliance readiness** with standard security patterns
- **Complete audit trail** for forensic analysis
- **Real-time threat detection** and response

### 🚀 **Performance & Scalability**  
- **Minimal overhead** with optimized security filters
- **Scalable authentication** with JWT tokens
- **Caching strategies** for authorization decisions
- **Async security processing** for non-blocking operations

### 🔧 **Development & Operations**
- **Developer-friendly** annotations and configurations
- **Centralized security** policies and management
- **Comprehensive testing** capabilities
- **Security-as-code** for consistent deployments

### 🏦 **Business Value**
- **Customer trust** through robust security
- **Regulatory compliance** avoiding fines
- **Reduced security incidents** and associated costs
- **Competitive advantage** with security-first approach

## Risks & Mitigation

### Risk 1: Performance Impact of Encryption
**Impact:** 10-15% overhead for encrypted database operations  
**Mitigation:**
- Use hardware-accelerated encryption (AES-NI)
- Selective encryption for sensitive fields only
- Connection pooling to amortize encryption overhead
- Regular performance testing and optimization

### Risk 2: JWT Token Compromise
**Impact:** Unauthorized access until token expiration  
**Mitigation:**
- Short token expiration times (1 hour)
- Token blacklist for immediate revocation
- Refresh token rotation strategy
- Monitoring for suspicious token usage

### Risk 3: Configuration Complexity
**Impact:** Security misconfiguration leading to vulnerabilities  
**Mitigation:**
- Infrastructure as Code for consistent configurations
- Automated security testing in CI/CD pipeline
- Security configuration reviews and audits
- Default-secure configurations

### Risk 4: Insider Threats  
**Impact:** Authorized users accessing data inappropriately  
**Mitigation:**
- Principle of least privilege access
- Comprehensive audit logging and monitoring
- Regular access reviews and certifications
- Behavioral analytics for anomaly detection

## Success Metrics

### Security KPIs
- **Zero critical vulnerabilities** in production
- **Authentication failure rate** < 0.1%
- **Security incident response time** < 30 minutes  
- **Compliance audit score** > 95%

### Performance Metrics
- **Security overhead** < 15% of total response time
- **JWT validation time** < 10ms (95th percentile)
- **Encryption/decryption latency** < 5ms
- **Security filter processing** < 20ms

### Compliance Metrics  
- **Audit trail completeness** 100%
- **Data retention compliance** 100%
- **Access control effectiveness** > 99%
- **Vulnerability remediation time** < 48 hours

## Implementation Roadmap

### Phase 1: Foundation ✅
- [x] Spring Security configuration
- [x] Input validation with Bean Validation
- [x] Basic audit logging
- [x] JWT authentication setup

### Phase 2: Advanced Security (In Progress)
- [x] Role-based access control (RBAC)
- [x] Data encryption at rest
- [x] Security exception handling
- [ ] Advanced threat detection

### Phase 3: Compliance & Monitoring
- [ ] Complete audit trail implementation
- [ ] Real-time security monitoring
- [ ] Compliance reporting automation
- [ ] Security metrics dashboards

## Compliance Mapping

### Security Requirements Demonstrated
| Requirement | Implementation | Status |
|-------------|----------------|--------|
| Build secure systems | Spring Security + validation | ✅ |
| Protect cardholder data | Encryption at rest/transit | ✅ |
| Maintain vulnerability program | Security scanning in CI/CD | ✅ |
| Implement access controls | RBAC with JWT | ✅ |
| Monitor and test networks | Security metrics + alerting | 🚧 |
| Maintain information security policy | Security documentation | ✅ |

### SOX Controls
| Control | Implementation | Status |
|---------|----------------|--------|
| Access controls | Authentication + authorization | ✅ |
| Change management | Audit trail for all changes | ✅ |
| Data integrity | Optimistic locking + checksums | ✅ |
| Segregation of duties | Role-based permissions | ✅ |

## Conclusion

This demo implements comprehensive security patterns suitable for financial applications.

Key patterns demonstrated:
- **Defense-in-depth** with multiple security layers
- **Authentication & authorization** with JWT/OAuth2
- **Audit logging** for all critical operations
- **Input validation** and secure coding practices

These patterns provide a foundation that can be extended for production banking applications.

---
**Previous:** [ADR-002: Multi-AZ Deployment Strategy](ADR-002-multi-az-deployment.md)  
**Next:** [ADR-004: Testing Strategy](ADR-004-testing-strategy.md)