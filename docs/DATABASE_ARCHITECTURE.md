# 🗄️ Database Architecture - Production-Grade Multi-AZ Setup

## 🎯 Overview

This document outlines the database architecture for the banking application, focusing on **high availability**, **zero-downtime deployments**, and **data consistency** across multiple availability zones.

## 🏗️ Current Demo Setup

### **Configuration**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bankingdb
    driver-class-name: org.postgresql.Driver
    
    # HikariCP - Production-grade connection pooling
    hikari:
      maximum-pool-size: 20          # Max connections per pod
      minimum-idle: 5                # Min idle connections
      connection-timeout: 30000      # 30s connection timeout
      idle-timeout: 600000           # 10min idle timeout
      max-lifetime: 1800000          # 30min max connection lifetime
      connection-test-query: "SELECT 1"
      validation-timeout: 5000       # 5s validation timeout
      leak-detection-threshold: 60000 # 1min leak detection
```

### **JPA Configuration**
```yaml
jpa:
  hibernate:
    ddl-auto: validate  # Never 'create' or 'update' in production
  properties:
    javax.persistence.query.timeout: 30000  # Global 30s timeout
    hibernate:
      query.timeout: 30000
      connection.provider_disables_autocommit: true
      connection.handling_mode: DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION
```

### **What's Implemented ✅**
- **Optimistic Locking**: `@Version` annotation on entities
- **Transaction Timeouts**: 30s global, specific per method
- **Connection Pooling**: HikariCP with leak detection
- **Read-Only Transactions**: Separate routing for queries
- **Retry Logic**: Automatic retry on transient failures
- **Recovery Methods**: Fallback for failed operations

## 🚀 Production Architecture

### **Multi-AZ PostgreSQL Cluster**
```
                    ┌─────────────────────────────────┐
                    │        Application Layer        │
                    │  Pod A    Pod B    Pod C        │
                    └────────┬─────────┬─────────────┬─┘
                             │         │             │
    ┌────────────────────────┼─────────┼─────────────┼────┐
    │                        │         │             │    │
    │        AZ-1            │   AZ-2  │       AZ-3  │    │
    │  ┌─────────────────┐   │  ┌──────▼──────┐  ┌──▼──┐ │
    │  │   PostgreSQL    │◄──┼──┤ PostgreSQL  │  │Post │ │
    │  │    Primary      │   │  │   Standby    │  │Stby │ │
    │  │  (Read/Write)   │───┼─►│ (Read Only)  │  │(R/O)│ │
    │  └─────────────────┘   │  └─────────────┘  └─────┘ │
    │                        │                           │
    └────────────────────────┴───────────────────────────┘
                             │
                    ┌────────▼─────────┐
                    │   Patroni/Consul │
                    │ (Leader Election) │
                    └──────────────────┘
```

## 🔧 Production Database Features

### **1. Automatic Failover with Patroni**
```yaml
# Example Patroni configuration
scope: banking-cluster
namespace: /banking/
name: postgres-primary

bootstrap:
  dcs:
    postgresql:
      use_pg_rewind: true
      parameters:
        max_connections: 200
        shared_buffers: 256MB
        effective_cache_size: 1GB
        maintenance_work_mem: 64MB
        checkpoint_completion_target: 0.9
        wal_buffers: 16MB
        default_statistics_target: 100
        random_page_cost: 1.1
        effective_io_concurrency: 200
        
postgresql:
  listen: 0.0.0.0:5432
  connect_address: postgres-primary:5432
  data_dir: /var/lib/postgresql/data
  pgpass: /tmp/pgpass
  authentication:
    replication:
      username: replicator
      password: repl_password
    superuser:
      username: postgres
      password: postgres_password
```

### **2. Multi-AZ Connection String**
```yaml
spring:
  datasource:
    # Primary connection for writes
    primary:
      url: jdbc:postgresql://postgres-primary.banking-ns:5432,postgres-standby-1.banking-ns:5432,postgres-standby-2.banking-ns:5432/bankingdb?targetServerType=primary&loadBalanceHosts=true&connectTimeout=30&socketTimeout=60&applicationName=bankcore
      
    # Read replicas for queries
    replica:
      url: jdbc:postgresql://postgres-standby-1.banking-ns:5432,postgres-standby-2.banking-ns:5432/bankingdb?targetServerType=preferSecondary&loadBalanceHosts=true&readOnly=true&applicationName=bankcore-readonly
```

### **3. Connection Pool Sizing**
```yaml
hikari:
  # Primary connection pool (writes)
  primary:
    maximum-pool-size: 10    # Conservative for writes
    minimum-idle: 2
    
  # Replica connection pool (reads)  
  replica:
    maximum-pool-size: 20    # More connections for reads
    minimum-idle: 5
    
  # Connection validation
  connection-test-query: "SELECT 1"
  validation-timeout: 3000
  
  # Failover settings
  connection-timeout: 15000  # 15s connection timeout
  initialization-fail-timeout: -1  # Retry indefinitely on startup
```

## 🛡️ Data Consistency & Locking

### **Optimistic Locking Implementation**
```java
@Entity
@Table(name = "transactions", schema = "banking")
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Version  // Critical for banking applications
    private Long version;
    
    // Other fields...
}
```

### **Service Layer with Conflict Resolution**
```java
@Service
public class TransactionService {
    
    @Transactional(timeout = 30)
    @Retryable(
        value = { OptimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 1.5)
    )
    public Transaction updateTransaction(Long id, Transaction update) {
        try {
            Transaction existing = findById(id);
            
            // Version check happens automatically via @Version
            existing.setAmount(update.getAmount());
            existing.setStatus(update.getStatus());
            existing.setUpdatedAt(LocalDateTime.now());
            
            return repository.save(existing);
            
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic locking conflict for transaction {}", id);
            throw e; // Will trigger retry
        }
    }
    
    @Recover
    public Transaction recoverOptimisticLockFailure(
            OptimisticLockingFailureException e, 
            Long id, 
            Transaction update) {
        log.error("Failed to update transaction {} after retries", id);
        throw new ConcurrentModificationException(
            "Transaction was modified by another process");
    }
}
```

## 📊 Database Monitoring & Metrics

### **Connection Pool Metrics**
```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
    enable:
      hikaricp: true
      
# Exposes metrics:
# - hikaricp_connections_active
# - hikaricp_connections_idle  
# - hikaricp_connections_pending
# - hikaricp_connections_timeout_total
# - hikaricp_connections_creation_seconds
```

### **Database Health Checks**
```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    @Autowired
    private DataSource dataSource;
    
    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT 1");
            
            if (rs.next()) {
                return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("connection_pool", getPoolStatus())
                    .build();
            }
        } catch (SQLException e) {
            return Health.down(e)
                .withDetail("error", e.getMessage())
                .build();
        }
        
        return Health.down().build();
    }
}
```

## 🔄 Read/Write Splitting

### **Dynamic Data Source Routing**
```java
@Configuration
public class DatabaseConfig {
    
    @Bean
    @Primary
    public DataSource routingDataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("PRIMARY", primaryDataSource());
        targetDataSources.put("REPLICA", replicaDataSource());
        
        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(primaryDataSource());
        
        return routingDataSource;
    }
    
    @Bean
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://primary:5432/bankingdb")
            .username("${DB_USER}")
            .password("${DB_PASSWORD}")
            .build();
    }
    
    @Bean
    public DataSource replicaDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://replica1:5432,replica2:5432/bankingdb")
            .username("${DB_REPLICA_USER}")
            .password("${DB_REPLICA_PASSWORD}")
            .build();
    }
}

// Usage in service layer
@Transactional(readOnly = true)  // → Routes to replica
public List<Transaction> findAll() {
    return repository.findAll();
}

@Transactional  // → Routes to primary
public Transaction save(Transaction tx) {
    return repository.save(tx);
}
```

## 🚦 Transaction Management Best Practices

### **1. Timeout Configuration**
```java
// Different timeouts for different operations
@Transactional(timeout = 10, readOnly = true)  // Fast reads
public Optional<Transaction> findById(Long id) { ... }

@Transactional(timeout = 30)  // Standard writes
public Transaction create(Transaction tx) { ... }

@Transactional(timeout = 60)  // Complex operations
public void processMonthlyReport() { ... }
```

### **2. Isolation Levels**
```java
// For critical financial operations
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transferFunds(String fromAccount, String toAccount, BigDecimal amount) {
    // Ensures no concurrent modifications affect this transaction
}

// For read-heavy operations
@Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
public List<Transaction> getAccountHistory(String accountId) {
    // Allows concurrent reads while preventing dirty reads
}
```

### **3. Retry Strategies**
```java
@Retryable(
    value = { TransientDataAccessException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 5000)
)
public Transaction processTransaction(Transaction tx) {
    // Will retry on temporary database issues
}

@Retryable(
    value = { OptimisticLockingFailureException.class },
    maxAttempts = 5,
    backoff = @Backoff(delay = 100, multiplier = 1.2)
)
public Transaction updateBalance(Long accountId, BigDecimal amount) {
    // Will retry on version conflicts
}
```

## 💾 Backup & Recovery Strategy

### **Continuous WAL Archiving**
```yaml
# PostgreSQL configuration
archive_mode: 'on'
archive_command: 'aws s3 cp %p s3://banking-backups/wal/%f'
archive_timeout: 300  # 5 minutes

# Point-in-time recovery capability
wal_level: replica
max_wal_senders: 3
wal_keep_segments: 32
```

### **Automated Backup Schedule**
```yaml
# CronJob for daily backups
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:15
            command:
            - /bin/bash
            - -c
            - |
              pg_dump -h postgres-primary -U postgres bankingdb | \
              gzip | \
              aws s3 cp - s3://banking-backups/daily/backup-$(date +%Y%m%d).sql.gz
```

## 📋 Migration Strategy (Legacy to Multi-AZ)

### **Phase 1: Single Instance → Primary/Standby**
1. Set up streaming replication
2. Configure application for failover URL
3. Test failover procedures
4. Switch DNS to point to Patroni VIP

### **Phase 2: Primary/Standby → Multi-AZ Cluster**
1. Add standby in third AZ
2. Configure load balancing for reads
3. Update application connection strings
4. Implement read/write splitting

### **Phase 3: Optimization**
1. Connection pool tuning
2. Query performance optimization
3. Monitoring and alerting setup
4. Disaster recovery testing

## ✅ Production Checklist

### **Before Go-Live**
- [ ] Multi-AZ PostgreSQL cluster deployed
- [ ] Patroni failover tested and verified
- [ ] Connection pooling optimized
- [ ] Read/write splitting implemented
- [ ] Backup strategy validated
- [ ] Monitoring dashboards configured
- [ ] Disaster recovery procedures documented
- [ ] Performance benchmarks completed

### **Monitoring Alerts**
- [ ] Connection pool exhaustion
- [ ] Long-running transactions (>30s)
- [ ] Replication lag >5 seconds
- [ ] Failed backup alerts
- [ ] Database CPU >80%
- [ ] Database disk space >85%

## 🎯 Summary

This database architecture provides:

- **99.99% Availability**: Multi-AZ with automatic failover
- **Zero Data Loss**: Synchronous replication to standby
- **Optimized Performance**: Read/write splitting, connection pooling
- **Data Consistency**: Optimistic locking with retry logic
- **Operational Excellence**: Automated backups, monitoring

The current demo shows the application-level patterns. In production, the PostgreSQL cluster would be managed by Patroni with full automation.

---
*Database architecture designed for mission-critical banking workloads*