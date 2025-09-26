# 🔄 Redis Distributed Cache - Production Architecture

## 🎯 Overview

This document outlines the Redis distributed cache architecture for production deployment of the banking application in a **Multi-AZ hybrid environment**.

## 🏗️ Architecture Decision

### **Current Demo**: Stateless Design
- ✅ No server-side sessions (JWT-based auth)
- ✅ No local cache dependencies  
- ✅ Database as single source of truth
- ✅ 12-factor app compliance

### **Production Extension**: Redis Cluster

```
┌─────────────────────────────────────────────────────────┐
│                   Application Layer                      │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐                 │
│  │ Pod A   │  │ Pod B   │  │ Pod C   │                 │
│  │ (AZ-1)  │  │ (AZ-2)  │  │ (AZ-3)  │                 │
│  └────┬────┘  └────┬────┘  └────┬────┘                 │
└───────┼───────────┼───────────┼─────────────────────────┘
        │           │           │
┌───────┼───────────┼───────────┼─────────────────────────┐
│       │    Redis Cluster      │                         │
│  ┌────▼────┐  ┌────▼────┐  ┌────▼────┐                 │
│  │Master-1 │  │Master-2 │  │Master-3 │                 │
│  │ (AZ-1)  │  │ (AZ-2)  │  │ (AZ-3)  │                 │
│  │         │  │         │  │         │                 │
│  │Replica-3│  │Replica-1│  │Replica-2│                 │
│  └─────────┘  └─────────┘  └─────────┘                 │
└─────────────────────────────────────────────────────────┘
```

## 🚀 Use Cases for Redis in Production

### 1. **Session Management**
```yaml
spring:
  session:
    store-type: redis
    redis:
      namespace: banking:sessions
      timeout: 1800  # 30 minutes
```

**Benefits:**
- Shared user sessions across all pods
- Seamless failover during rolling updates
- Horizontal scaling without session loss

### 2. **Application Caching**
```java
@Service
public class TransactionService {
    
    @Cacheable(value = "transactions", key = "#accountId")
    public List<Transaction> getTransactionsByAccount(String accountId) {
        // Expensive DB query cached in Redis
        return transactionRepository.findByAccountId(accountId);
    }
    
    @CacheEvict(value = "transactions", key = "#transaction.accountId")
    public Transaction createTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }
}
```

### 3. **Distributed Rate Limiting**
```java
@Component
public class RateLimitingFilter {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    public boolean isAllowed(String clientIp, int maxRequests) {
        String key = "rate_limit:" + clientIp;
        String count = redisTemplate.opsForValue().get(key);
        
        if (count == null) {
            redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(1));
            return true;
        }
        
        if (Integer.parseInt(count) >= maxRequests) {
            return false;
        }
        
        redisTemplate.opsForValue().increment(key);
        return true;
    }
}
```

### 4. **Real-time Data Sync**
```java
@Component
public class BalanceUpdatePublisher {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    public void publishBalanceUpdate(String accountId, BigDecimal newBalance) {
        BalanceUpdate update = new BalanceUpdate(accountId, newBalance, Instant.now());
        redisTemplate.convertAndSend("balance-updates", update);
    }
}
```

## 📊 Redis Cluster Configuration

### **Kubernetes StatefulSet**
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis-cluster
  namespace: banking-prod
spec:
  serviceName: redis-cluster
  replicas: 6  # 3 masters + 3 replicas
  selector:
    matchLabels:
      app: redis-cluster
  template:
    metadata:
      labels:
        app: redis-cluster
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchExpressions:
              - key: app
                operator: In
                values:
                - redis-cluster
            topologyKey: topology.kubernetes.io/zone
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        - containerPort: 16379
        command:
        - redis-server
        - /etc/redis/redis.conf
        - --cluster-enabled
        - "yes"
        - --cluster-config-file
        - nodes.conf
        - --cluster-node-timeout
        - "5000"
        - --appendonly
        - "yes"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        volumeMounts:
        - name: redis-data
          mountPath: /data
        - name: redis-config
          mountPath: /etc/redis
  volumeClaimTemplates:
  - metadata:
      name: redis-data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 10Gi
      storageClassName: gp3
```

### **High Availability Features**
- **3 Masters across 3 AZ**: Ensures data availability
- **3 Replicas**: Automatic failover capability
- **Cluster Mode**: Horizontal scaling and sharding
- **Persistent Storage**: Data survives pod restarts

## 🔧 Application Integration

### **Spring Boot Configuration**
```yaml
spring:
  redis:
    enabled: true
    cluster:
      nodes:
        - redis-0.redis-cluster:6379
        - redis-1.redis-cluster:6379
        - redis-2.redis-cluster:6379
      max-redirects: 3
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 2000ms
  
  cache:
    type: redis
    redis:
      time-to-live: 300s
      cache-null-values: false
```

### **Connection Management**
```java
@Configuration
@EnableRedisRepositories
@EnableCaching
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisClusterConfiguration clusterConfig = 
            new RedisClusterConfiguration(Arrays.asList(
                "redis-0.redis-cluster:6379",
                "redis-1.redis-cluster:6379", 
                "redis-2.redis-cluster:6379"
            ));
            
        clusterConfig.setMaxRedirects(3);
        
        LettucePoolingClientConfiguration poolConfig = 
            LettucePoolingClientConfiguration.builder()
                .poolConfig(jedisPoolConfig())
                .commandTimeout(Duration.ofSeconds(2))
                .build();
                
        return new LettuceConnectionFactory(clusterConfig, poolConfig);
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        template.setDefaultSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

## 📈 Performance Metrics

### **Expected Performance**
- **Latency**: < 1ms for cache hits
- **Throughput**: 100,000+ ops/sec per cluster
- **Memory**: 80% utilization max (auto-scaling trigger)
- **Availability**: 99.99% with proper clustering

### **Monitoring**
```yaml
# ServiceMonitor for Prometheus
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: redis-cluster
spec:
  selector:
    matchLabels:
      app: redis-cluster
  endpoints:
  - port: metrics
    interval: 30s
    path: /metrics
```

## 🛡️ Security Considerations

### **Network Security**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: redis-netpol
spec:
  podSelector:
    matchLabels:
      app: redis-cluster
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: bankcore
    ports:
    - protocol: TCP
      port: 6379
```

### **Authentication**
```yaml
# Redis AUTH with secret
apiVersion: v1
kind: Secret
metadata:
  name: redis-auth
type: Opaque
data:
  redis-password: <base64-encoded-password>
```

## 💡 Migration Strategy

### **Phase 1**: Deploy Redis Cluster
- Deploy Redis StatefulSet
- Configure monitoring
- Test connectivity

### **Phase 2**: Application Integration
- Enable Redis in application config
- Deploy with `REDIS_ENABLED=true`
- Monitor cache hit rates

### **Phase 3**: Optimization
- Tune cache TTL values
- Implement cache warming
- Monitor performance metrics

## 📚 Decision Rationale

### **Why Redis over alternatives?**

| Feature | Redis | Hazelcast | Memcached |
|---------|-------|-----------|-----------|
| **Cluster Mode** | ✅ Built-in | ✅ Native | ❌ Limited |
| **Persistence** | ✅ RDB + AOF | ✅ Memory + Disk | ❌ Memory only |
| **Data Types** | ✅ Rich types | ✅ Complex types | ❌ Simple K/V |
| **Pub/Sub** | ✅ Native | ✅ Built-in | ❌ None |
| **Memory Efficiency** | ✅ Good | ⚠️ Higher overhead | ✅ Excellent |
| **Operations Maturity** | ✅ Mature | ⚠️ Complex | ✅ Simple |

**Verdict**: Redis provides the best balance of features, performance, and operational simplicity for banking workloads.

## ✅ Production Checklist

### **Before Go-Live**
- [ ] Redis cluster deployed across 3 AZ
- [ ] Application integration tested
- [ ] Failover scenarios validated
- [ ] Monitoring and alerting configured
- [ ] Backup strategy implemented
- [ ] Performance benchmarks completed
- [ ] Security audit passed
- [ ] Runbooks created for operations

## 📞 Support

For Redis-related issues in production:
- **Monitoring**: Prometheus + Grafana dashboards
- **Alerting**: PagerDuty integration
- **Logs**: Centralized in ELK stack
- **Runbooks**: Available in wiki

---

## 🎯 Summary

This Redis architecture provides:
- **High Availability**: Multi-AZ clustering with automatic failover
- **Scalability**: Horizontal scaling through sharding  
- **Performance**: Sub-millisecond response times
- **Consistency**: Strong consistency within cluster
- **Monitoring**: Full observability stack

The current demo focuses on core deployment patterns. Redis would be added in production based on actual traffic patterns and caching requirements.

---
*Redis Architecture designed for 99.99% availability banking workloads*