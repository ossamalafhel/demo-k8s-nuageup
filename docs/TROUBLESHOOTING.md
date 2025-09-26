# Troubleshooting Guide

## Table of Contents
1. [Common Issues](#common-issues)
2. [Kubernetes Issues](#kubernetes-issues)
3. [Application Issues](#application-issues)
4. [Database Issues](#database-issues)
5. [Network Issues](#network-issues)
6. [Performance Issues](#performance-issues)
7. [Deployment Issues](#deployment-issues)
8. [Debug Tools & Commands](#debug-tools--commands)

## Common Issues

### Issue: Application Won't Start

#### Symptoms
- Pods in CrashLoopBackOff
- Container exits immediately
- Health checks failing

#### Diagnosis
```bash
# Check pod status
kubectl get pods -n banking-prod

# Describe pod for events
kubectl describe pod <pod-name> -n banking-prod

# Check logs
kubectl logs <pod-name> -n banking-prod --previous
```

#### Common Causes & Solutions

1. **Missing Environment Variables**
```bash
# Check if all required env vars are set
kubectl exec -it <pod-name> -n banking-prod -- env | grep -E "DB_|SPRING_"

# Solution: Update ConfigMap/Secret
kubectl edit configmap app-config -n banking-prod
```

2. **Database Connection Failure**
```bash
# Test database connectivity
kubectl exec -it <pod-name> -n banking-prod -- nc -zv postgres-service 5432

# Check database credentials
kubectl get secret app-secrets -n banking-prod -o yaml
```

3. **Insufficient Resources**
```yaml
# Check resource limits
kubectl describe pod <pod-name> -n banking-prod | grep -A5 "Limits:"

# Solution: Increase resources
kubectl edit deployment bankcore -n banking-prod
```

### Issue: Out of Memory (OOMKilled)

#### Symptoms
- Pod restarts with OOMKilled status
- Application becomes unresponsive before crash
- Exit code 137

#### Diagnosis
```bash
# Check if OOMKilled
kubectl describe pod <pod-name> -n banking-prod | grep -i "OOMKilled"

# Check memory usage
kubectl top pod <pod-name> -n banking-prod

# Check JVM heap settings
kubectl exec -it <pod-name> -n banking-prod -- jcmd 1 VM.flags | grep -E "Xmx|Xms"
```

#### Solutions

1. **Increase Memory Limits**
```yaml
resources:
  limits:
    memory: "2Gi"  # Increase from 1Gi
  requests:
    memory: "1Gi"
```

2. **Optimize JVM Settings**
```yaml
env:
- name: JAVA_OPTS
  value: "-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"
```

3. **Add Memory Monitoring**
```java
@Component
public class MemoryMonitor {
    @Scheduled(fixedDelay = 60000)
    public void logMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        log.info("Memory stats - Used: {}MB, Free: {}MB, Max: {}MB",
            usedMemory / 1024 / 1024,
            freeMemory / 1024 / 1024,
            maxMemory / 1024 / 1024);
    }
}
```

## Kubernetes Issues

### Issue: Pods Not Scheduling

#### Symptoms
- Pods stuck in Pending state
- No nodes available
- Insufficient resources

#### Diagnosis
```bash
# Check pod events
kubectl describe pod <pod-name> -n banking-prod

# Check node resources
kubectl describe nodes
kubectl top nodes

# Check pod disruption budget
kubectl get pdb -n banking-prod
```

#### Solutions

1. **Node Selector Issues**
```bash
# Check node labels
kubectl get nodes --show-labels

# Remove node selector if needed
kubectl edit deployment bankcore -n banking-prod
```

2. **Resource Constraints**
```bash
# Check cluster capacity
kubectl get nodes -o json | jq '.items[] | {name:.metadata.name, allocatable:.status.allocatable}'

# Scale down other deployments or add nodes
```

3. **Anti-Affinity Conflicts**
```yaml
# Relax anti-affinity rules
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:  # Changed from required
    - weight: 100
      podAffinityTerm:
        topologyKey: kubernetes.io/hostname
```

### Issue: Service Discovery Not Working

#### Symptoms
- Cannot resolve service names
- Connection refused to services
- DNS timeouts

#### Diagnosis
```bash
# Test DNS resolution
kubectl exec -it <pod-name> -n banking-prod -- nslookup bankcore-service

# Check CoreDNS logs
kubectl logs -n kube-system -l k8s-app=kube-dns

# Verify service endpoints
kubectl get endpoints bankcore-service -n banking-prod
```

#### Solutions

1. **Fix Service Selector**
```bash
# Verify labels match
kubectl get pods -n banking-prod --show-labels
kubectl get service bankcore-service -n banking-prod -o yaml
```

2. **DNS Cache Issues**
```bash
# Restart CoreDNS
kubectl rollout restart deployment coredns -n kube-system

# Clear local DNS cache in pod
kubectl exec -it <pod-name> -n banking-prod -- kill -USR1 1
```

## Application Issues

### Issue: High Error Rate

#### Symptoms
- 5xx errors increasing
- Timeouts
- Circuit breaker opening

#### Diagnosis
```bash
# Check application logs
kubectl logs -f deployment/bankcore -n banking-prod

# Check metrics
curl -s http://localhost:8080/actuator/prometheus | grep http_server_requests_seconds_count

# Check circuit breaker status
curl -s http://localhost:8080/actuator/health | jq '.components.circuitBreakers'
```

#### Solutions

1. **Database Connection Pool Exhaustion**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30  # Increase from 20
      connection-timeout: 30000
      leak-detection-threshold: 60000
```

2. **Thread Pool Exhaustion**
```yaml
server:
  tomcat:
    threads:
      max: 400  # Increase from 200
      min-spare: 50
    accept-count: 200
```

3. **Circuit Breaker Tuning**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      backend:
        failure-rate-threshold: 50
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 20
        minimum-number-of-calls: 10
```

### Issue: Slow Response Times

#### Symptoms
- P95/P99 latency increasing
- User complaints about slowness
- Thread pool queuing

#### Diagnosis
```bash
# Generate thread dump
kubectl exec -it <pod-name> -n banking-prod -- jstack 1 > thread-dump.txt

# Check GC logs
kubectl exec -it <pod-name> -n banking-prod -- jstat -gcutil 1 1000 5

# Profile slow endpoints
curl -s http://localhost:8080/actuator/metrics/http.server.requests | jq '.measurements[] | select(.statistic=="MAX")'
```

#### Solutions

1. **Enable Caching**
```java
@Cacheable(value = "transactions", key = "#accountId")
public List<Transaction> getTransactionsByAccount(String accountId) {
    return transactionRepository.findByAccountId(accountId);
}
```

2. **Database Query Optimization**
```sql
-- Add missing indexes
CREATE INDEX CONCURRENTLY idx_transactions_account_created 
ON transactions(account_id, created_at DESC);

-- Analyze query performance
EXPLAIN ANALYZE SELECT * FROM transactions WHERE account_id = 'ACC001';
```

## Database Issues

### Issue: Connection Pool Exhausted

#### Symptoms
- "Connection is not available" errors
- Timeouts acquiring connections
- Slow response times

#### Diagnosis
```bash
# Check pool metrics
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active

# Check database connections
kubectl exec -it postgres-pod -- psql -U postgres -c "SELECT count(*) FROM pg_stat_activity;"
```

#### Solutions

1. **Increase Pool Size**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
```

2. **Find Connection Leaks**
```java
// Enable leak detection
spring.datasource.hikari.leak-detection-threshold=30000

// Add logging
logging.level.com.zaxxer.hikari=DEBUG
```

### Issue: Slow Queries

#### Symptoms
- Database CPU high
- Long query execution times
- Application timeouts

#### Diagnosis
```sql
-- Find slow queries
SELECT query, mean_exec_time, calls 
FROM pg_stat_statements 
ORDER BY mean_exec_time DESC 
LIMIT 10;

-- Check for missing indexes
SELECT schemaname, tablename, attname, n_distinct, correlation
FROM pg_stats
WHERE tablename = 'transactions'
AND n_distinct > 100;
```

#### Solutions

1. **Add Indexes**
```sql
-- Create index for common queries
CREATE INDEX CONCURRENTLY idx_transactions_status_date 
ON transactions(status, created_at DESC)
WHERE status IN ('PENDING', 'PROCESSING');
```

2. **Query Optimization**
```java
// Use pagination
@Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId")
Page<Transaction> findByAccountId(@Param("accountId") String accountId, Pageable pageable);
```

## Network Issues

### Issue: Intermittent Connection Failures

#### Symptoms
- Random connection refused errors
- DNS resolution failures
- Timeouts

#### Diagnosis
```bash
# Check network policies
kubectl describe networkpolicy -n banking-prod

# Test connectivity
kubectl exec -it <pod-name> -n banking-prod -- nc -zv postgres-service 5432

# Check DNS
kubectl exec -it <pod-name> -n banking-prod -- dig @10.96.0.10 postgres-service.banking-prod.svc.cluster.local
```

#### Solutions

1. **Fix Network Policies**
```yaml
# Allow DNS
egress:
- to:
  - namespaceSelector: {}
    podSelector:
      matchLabels:
        k8s-app: kube-dns
  ports:
  - protocol: UDP
    port: 53
```

2. **Increase Timeouts**
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 30000  # 30 seconds
      validation-timeout: 5000
```

### Issue: Cross-Zone Latency

#### Symptoms
- Higher latency for some requests
- Uneven response times
- Performance degradation

#### Diagnosis
```bash
# Check pod distribution
kubectl get pods -o wide -n banking-prod

# Trace network path
kubectl exec -it <pod-name> -n banking-prod -- traceroute postgres-service
```

#### Solutions

1. **Enable Topology-Aware Routing**
```yaml
apiVersion: v1
kind: Service
metadata:
  annotations:
    service.kubernetes.io/topology-aware-hints: "auto"
```

2. **Pod Affinity for Database Connections**
```yaml
affinity:
  podAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 100
      podAffinityTerm:
        labelSelector:
          matchExpressions:
          - key: app
            operator: In
            values: ["postgres"]
        topologyKey: topology.kubernetes.io/zone
```

## Performance Issues

### Issue: High CPU Usage

#### Symptoms
- CPU constantly above 80%
- Throttling occurring
- Slow response times

#### Diagnosis
```bash
# Check CPU usage
kubectl top pod <pod-name> -n banking-prod

# CPU profiling
kubectl exec -it <pod-name> -n banking-prod -- jcmd 1 JFR.start duration=60s filename=/tmp/profile.jfr

# Download and analyze
kubectl cp banking-prod/<pod-name>:/tmp/profile.jfr ./profile.jfr
```

#### Solutions

1. **Optimize Hot Code Paths**
```java
// Use efficient data structures
Map<String, Transaction> cache = new ConcurrentHashMap<>();

// Avoid unnecessary object creation
private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
```

2. **Increase CPU Limits**
```yaml
resources:
  requests:
    cpu: "1000m"
  limits:
    cpu: "2000m"
```

### Issue: Memory Leaks

#### Symptoms
- Gradually increasing memory usage
- Eventually OOMKilled
- GC time increasing

#### Diagnosis
```bash
# Heap dump
kubectl exec -it <pod-name> -n banking-prod -- jcmd 1 GC.heap_dump /tmp/heap.hprof

# Download for analysis
kubectl cp banking-prod/<pod-name>:/tmp/heap.hprof ./heap.hprof

# Analyze with MAT or similar tool
```

#### Solutions

1. **Fix Common Leaks**
```java
// Close resources properly
try (Connection conn = dataSource.getConnection()) {
    // Use connection
}

// Clear thread locals
@PreDestroy
public void cleanup() {
    threadLocal.remove();
}
```

2. **Monitor Memory Usage**
```java
@Component
public class MemoryLeakDetector {
    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::checkMemory, 0, 1, TimeUnit.MINUTES);
    }
    
    private void checkMemory() {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : pools) {
            if (pool.getName().contains("Old Gen")) {
                double usage = (double) pool.getUsage().getUsed() / pool.getUsage().getMax();
                if (usage > 0.9) {
                    log.warn("Old Gen usage high: {}%", usage * 100);
                }
            }
        }
    }
}
```

## Deployment Issues

### Issue: Deployment Stuck

#### Symptoms
- Deployment not progressing
- Pods not updating
- Rollout status hanging

#### Diagnosis
```bash
# Check rollout status
kubectl rollout status deployment/bankcore -n banking-prod

# Check deployment conditions
kubectl describe deployment bankcore -n banking-prod

# Check replica sets
kubectl get rs -n banking-prod -l app=bankcore
```

#### Solutions

1. **Fix Image Pull Issues**
```bash
# Check if image exists
docker pull your-registry/bankcore:tag

# Update image pull secrets
kubectl create secret docker-registry regcred \
  --docker-server=your-registry \
  --docker-username=user \
  --docker-password=pass \
  -n banking-prod
```

2. **Resolve PDB Conflicts**
```bash
# Check PDB
kubectl get pdb -n banking-prod

# Temporarily relax PDB
kubectl edit pdb bankcore-pdb -n banking-prod
```

### Issue: Rollback Failed

#### Symptoms
- Rollback command not working
- Old version not starting
- Deployment in bad state

#### Diagnosis
```bash
# Check rollout history
kubectl rollout history deployment/bankcore -n banking-prod

# Check previous replica set
kubectl get rs -n banking-prod
```

#### Solutions

1. **Manual Rollback**
```bash
# Find previous working revision
kubectl rollout history deployment/bankcore -n banking-prod

# Rollback to specific revision
kubectl rollout undo deployment/bankcore --to-revision=5 -n banking-prod
```

2. **Force Update**
```bash
# Edit deployment directly
kubectl edit deployment bankcore -n banking-prod

# Change image to previous version
# Force recreation of pods
kubectl delete pods -l app=bankcore -n banking-prod
```

## Debug Tools & Commands

### Essential Commands

```bash
# Logs
kubectl logs -f deployment/bankcore -n banking-prod --tail=100
kubectl logs -f <pod-name> -n banking-prod --previous

# Execute commands
kubectl exec -it <pod-name> -n banking-prod -- /bin/sh
kubectl exec -it <pod-name> -n banking-prod -- curl localhost:8080/actuator/health

# Port forwarding
kubectl port-forward deployment/bankcore 8080:8080 -n banking-prod

# Copy files
kubectl cp <pod-name>:/tmp/file.txt ./file.txt -n banking-prod

# Resource usage
kubectl top pods -n banking-prod
kubectl top nodes

# Events
kubectl get events -n banking-prod --sort-by='.lastTimestamp'
```

### Debugging Scripts

#### Health Check Script
```bash
#!/bin/bash
# health-check.sh

NAMESPACE=${1:-banking-prod}
DEPLOYMENT=${2:-bankcore}

echo "Checking health of $DEPLOYMENT in $NAMESPACE..."

# Check deployment
kubectl get deployment $DEPLOYMENT -n $NAMESPACE

# Check pods
kubectl get pods -l app=$DEPLOYMENT -n $NAMESPACE

# Check endpoints
kubectl get endpoints -l app=$DEPLOYMENT -n $NAMESPACE

# Check recent events
kubectl get events -n $NAMESPACE --field-selector involvedObject.name=$DEPLOYMENT

# Test service
POD=$(kubectl get pod -l app=$DEPLOYMENT -n $NAMESPACE -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $POD -n $NAMESPACE -- curl -s localhost:8080/actuator/health | jq .
```

#### Performance Analysis Script
```bash
#!/bin/bash
# perf-analysis.sh

POD=$1
NAMESPACE=${2:-banking-prod}

echo "Analyzing performance of $POD..."

# CPU and Memory
kubectl top pod $POD -n $NAMESPACE

# JVM stats
kubectl exec -it $POD -n $NAMESPACE -- jstat -gcutil 1 1000 5

# Thread dump
kubectl exec -it $POD -n $NAMESPACE -- jstack 1 > thread-dump-$(date +%s).txt

# Heap histogram
kubectl exec -it $POD -n $NAMESPACE -- jmap -histo 1 | head -20
```

### Monitoring Queries

```promql
# Error rate
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) 
/ 
sum(rate(http_server_requests_seconds_count[5m]))

# Response time (p99)
histogram_quantile(0.99, 
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le)
)

# Pod restarts
increase(kube_pod_container_status_restarts_total[1h])

# CPU throttling
rate(container_cpu_cfs_throttled_periods_total[5m])
```