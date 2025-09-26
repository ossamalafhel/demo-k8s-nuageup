# Zero Downtime Deployment Strategy

## Table of Contents
1. [Overview](#overview)
2. [Core Principles](#core-principles)
3. [Kubernetes Strategy](#kubernetes-strategy)
4. [VM Strategy](#vm-strategy)
5. [Database Considerations](#database-considerations)
6. [Testing Procedures](#testing-procedures)
7. [Monitoring & Validation](#monitoring--validation)
8. [Emergency Procedures](#emergency-procedures)

## Overview

Zero downtime deployment is critical for banking applications where availability directly impacts customer trust and regulatory compliance. This document outlines comprehensive strategies for achieving zero downtime deployments in both Kubernetes and VM environments.

## Core Principles

### 1. Redundancy at Every Layer
- Multiple application instances
- Load balancer distribution
- Database replication
- Cross-AZ deployment

### 2. Gradual Rollout
- Never update all instances simultaneously
- Validate each step
- Monitor key metrics
- Automated rollback triggers

### 3. Backwards Compatibility
- API versioning
- Database migration compatibility
- Configuration compatibility
- Protocol compatibility

### 4. Comprehensive Health Checks
- Application readiness
- Dependency availability
- Business logic validation
- End-to-end transaction tests

## Kubernetes Strategy

### Deployment Configuration

#### 1. Rolling Update Strategy
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bankcore
spec:
  replicas: 6  # Minimum for zero downtime
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 2        # Extra pods during update
      maxUnavailable: 0  # Critical: Never reduce capacity
  minReadySeconds: 30    # Wait before considering pod ready
```

#### 2. Pod Disruption Budget
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: bankcore-pdb
spec:
  minAvailable: 4  # Always keep 66% running
  selector:
    matchLabels:
      app: bankcore
  # Alternative: maxUnavailable: 1
```

### Graceful Shutdown Implementation

#### 1. Application Code
```java
@Component
public class GracefulShutdown implements ApplicationListener<ContextClosedEvent> {
    
    @Autowired
    private ServerProperties serverProperties;
    
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Starting graceful shutdown...");
        
        // Step 1: Stop accepting new requests
        setReadinessToFalse();
        
        // Step 2: Wait for load balancer to update
        Thread.sleep(15000);  // Match preStop hook
        
        // Step 3: Complete in-flight requests
        waitForRequestsToComplete();
        
        // Step 4: Close resources gracefully
        closeConnections();
        
        log.info("Graceful shutdown completed");
    }
}
```

#### 2. Spring Boot Configuration
```yaml
server:
  shutdown: graceful
  tomcat:
    connection-timeout: 20s
    keep-alive-timeout: 15s
    
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

#### 3. Kubernetes Lifecycle Hooks
```yaml
spec:
  containers:
  - name: app
    lifecycle:
      preStop:
        exec:
          command:
          - /bin/sh
          - -c
          - |
            # Notify app to start shutdown
            curl -X POST http://localhost:8080/actuator/shutdown/prepare || true
            
            # Wait for k8s to update endpoints
            sleep 15
            
            # Application continues shutdown via SIGTERM
```

### Health Probe Configuration

#### 1. Startup Probe (Initial Health)
```yaml
startupProbe:
  httpGet:
    path: /actuator/health/startup
    port: 8080
  initialDelaySeconds: 0
  periodSeconds: 5
  timeoutSeconds: 3
  successThreshold: 1
  failureThreshold: 30  # 150 seconds total
```

#### 2. Readiness Probe (Traffic Eligibility)
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
  timeoutSeconds: 3
  successThreshold: 1
  failureThreshold: 3
```

#### 3. Liveness Probe (Container Health)
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60  # After startup complete
  periodSeconds: 10
  timeoutSeconds: 5
  successThreshold: 1
  failureThreshold: 3
```

### Advanced Health Check Implementation

```java
@RestController
@RequestMapping("/actuator/health")
public class AdvancedHealthController {
    
    @Autowired
    private DatabaseHealthIndicator dbHealth;
    
    @Autowired
    private DependencyHealthChecker dependencyChecker;
    
    @GetMapping("/readiness")
    public ResponseEntity<HealthStatus> readiness() {
        HealthStatus status = HealthStatus.builder()
            .status("UP")
            .checks(Map.of(
                "database", dbHealth.check(),
                "dependencies", dependencyChecker.checkAll(),
                "initializationComplete", isInitialized()
            ))
            .build();
            
        return status.isHealthy() 
            ? ResponseEntity.ok(status)
            : ResponseEntity.status(503).body(status);
    }
    
    @GetMapping("/startup")
    public ResponseEntity<HealthStatus> startup() {
        // Lighter check during startup
        boolean schemaReady = dbHealth.isSchemaReady();
        boolean configLoaded = configManager.isLoaded();
        
        return (schemaReady && configLoaded)
            ? ResponseEntity.ok(new HealthStatus("UP"))
            : ResponseEntity.status(503).body(new HealthStatus("DOWN"));
    }
}
```

### Multi-AZ Deployment

```yaml
spec:
  affinity:
    podAntiAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchExpressions:
          - key: app
            operator: In
            values: ["bankcore"]
        topologyKey: topology.kubernetes.io/zone
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchExpressions:
            - key: app
              operator: In
              values: ["bankcore"]
          topologyKey: kubernetes.io/hostname
```

## VM Strategy

### Blue-Green Deployment

#### 1. Infrastructure Setup
```yaml
# docker-compose.blue.yml
version: '3.8'
services:
  app-blue:
    image: bankcore:current
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      start_period: 40s

# docker-compose.green.yml
services:
  app-green:
    image: bankcore:new
    ports:
      - "8081:8080"  # Different port initially
```

#### 2. Deployment Script
```bash
#!/bin/bash
# blue-green-deploy.sh

CURRENT_COLOR=$(cat .current-deployment || echo "blue")
NEW_COLOR=$([[ "$CURRENT_COLOR" == "blue" ]] && echo "green" || echo "blue")

echo "Deploying $NEW_COLOR (current: $CURRENT_COLOR)"

# Step 1: Start new version
docker-compose -f docker-compose.yml -f docker-compose.$NEW_COLOR.yml up -d

# Step 2: Health check loop
MAX_ATTEMPTS=30
for i in $(seq 1 $MAX_ATTEMPTS); do
    if curl -f http://localhost:8081/actuator/health > /dev/null 2>&1; then
        echo "✓ New version healthy"
        break
    fi
    echo "Waiting for health check ($i/$MAX_ATTEMPTS)..."
    sleep 2
done

# Step 3: Run smoke tests
./smoke-tests.sh $NEW_COLOR || {
    echo "Smoke tests failed, aborting"
    docker-compose -f docker-compose.$NEW_COLOR.yml down
    exit 1
}

# Step 4: Switch NGINX
cat > /etc/nginx/conf.d/upstream.conf << EOF
upstream backend {
    server 127.0.0.1:$([[ "$NEW_COLOR" == "green" ]] && echo "8081" || echo "8080");
}
EOF

nginx -s reload

# Step 5: Monitor for 60 seconds
sleep 60

# Step 6: Check metrics
ERROR_RATE=$(curl -s http://localhost:8081/metrics | grep error_rate | awk '{print $2}')
if (( $(echo "$ERROR_RATE > 0.01" | bc -l) )); then
    echo "Error rate too high, rolling back"
    ./rollback.sh
    exit 1
fi

# Step 7: Stop old version
docker-compose -f docker-compose.$CURRENT_COLOR.yml down

# Step 8: Update state
echo $NEW_COLOR > .current-deployment
```

### HAProxy Configuration for Zero Downtime

```
global
    maxconn 4096
    log stdout local0

defaults
    mode http
    timeout connect 5000ms
    timeout client 50000ms
    timeout server 50000ms
    option httplog

backend banking_backend
    balance roundrobin
    option httpchk GET /actuator/health
    
    # Enable seamless reload
    option redispatch
    option abortonclose
    
    # Blue server
    server blue 127.0.0.1:8080 check inter 2s fall 3 rise 2
    
    # Green server (initially disabled)
    server green 127.0.0.1:8081 check inter 2s fall 3 rise 2 disabled
```

### Session Affinity Considerations

```yaml
# For stateful applications
upstream backend {
    ip_hash;  # Session affinity
    server 127.0.0.1:8080 max_fails=3 fail_timeout=30s;
    server 127.0.0.1:8081 max_fails=3 fail_timeout=30s backup;
}
```

## Database Considerations

### Schema Migration Strategy

#### 1. Backward Compatible Migrations
```sql
-- Good: Additive change
ALTER TABLE transactions ADD COLUMN new_field VARCHAR(255);

-- Bad: Breaking change
ALTER TABLE transactions DROP COLUMN old_field;  -- Don't do this immediately

-- Better: Multi-step approach
-- Step 1: Add new column
ALTER TABLE transactions ADD COLUMN new_field VARCHAR(255);
-- Step 2: Dual write (application writes to both)
-- Step 3: Migrate data
UPDATE transactions SET new_field = old_field WHERE new_field IS NULL;
-- Step 4: Switch reads to new column
-- Step 5: (Later) Drop old column
```

#### 2. Flyway Configuration
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    out-of-order: true  # Allow hotfixes
    validate-on-migrate: true
    locations:
      - classpath:db/migration
      - classpath:db/hotfixes  # Emergency fixes
```

#### 3. Online Schema Changes
```bash
# Using pt-online-schema-change for large tables
pt-online-schema-change \
  --alter="ADD COLUMN new_field VARCHAR(255)" \
  --execute D=banking,t=transactions \
  --max-load="Threads_running=25" \
  --critical-load="Threads_running=50" \
  --set-vars="lock_wait_timeout=2" \
  --dry-run
```

### Connection Pool Management

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
      # Critical for zero downtime
      connection-test-query: SELECT 1
      validation-timeout: 3000
```

## Testing Procedures

### Load Test During Deployment

```javascript
// k6-zero-downtime-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '2m', target: 100 },  // Ramp up
    { duration: '10m', target: 100 }, // Stay at 100 users
    { duration: '2m', target: 0 },    // Ramp down
  ],
  thresholds: {
    errors: ['rate<0.001'],  // Less than 0.1% error rate
    http_req_duration: ['p(95)<500'],  // 95% of requests under 500ms
  },
};

export default function () {
  const res = http.get(`${__ENV.BASE_URL}/api/v1/health`);
  
  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  
  sleep(0.1);
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'zero-downtime-test-results.json': JSON.stringify(data),
  };
}
```

### Automated Deployment Validation

```bash
#!/bin/bash
# validate-deployment.sh

function validate_zero_downtime() {
    local DEPLOYMENT=$1
    local NAMESPACE=$2
    
    # Start continuous monitoring
    ./continuous-curl.sh &
    MONITOR_PID=$!
    
    # Trigger rolling update
    kubectl set image deployment/$DEPLOYMENT app=new-image:tag -n $NAMESPACE
    
    # Wait for rollout
    kubectl rollout status deployment/$DEPLOYMENT -n $NAMESPACE
    
    # Stop monitoring
    kill $MONITOR_PID
    
    # Analyze results
    TOTAL_REQUESTS=$(wc -l < curl-results.log)
    FAILED_REQUESTS=$(grep -c "error" curl-results.log)
    SUCCESS_RATE=$(echo "scale=4; ($TOTAL_REQUESTS - $FAILED_REQUESTS) / $TOTAL_REQUESTS * 100" | bc)
    
    echo "Success rate: ${SUCCESS_RATE}%"
    
    if (( $(echo "$SUCCESS_RATE < 99.9" | bc -l) )); then
        echo "❌ Zero downtime validation failed"
        exit 1
    fi
    
    echo "✅ Zero downtime validated"
}
```

## Monitoring & Validation

### Real-time Metrics

```yaml
# Prometheus queries for deployment monitoring

# Error rate during deployment
sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) 
/ 
sum(rate(http_server_requests_seconds_count[1m]))

# Response time percentiles
histogram_quantile(0.99, 
  sum(rate(http_server_requests_seconds_bucket[1m])) by (le)
)

# Pod readiness
kube_pod_status_ready{namespace="banking-prod"}

# Active connections
sum(http_server_connections_active) by (instance)
```

### Deployment Dashboard

Key metrics to monitor:
1. Request success rate (target: >99.9%)
2. Response time (p99 < baseline)
3. Error count (should be 0)
4. Pod status (all healthy)
5. Connection count (stable)
6. Business metrics (transactions/sec)

## Emergency Procedures

### Immediate Rollback

```bash
# Kubernetes
kubectl rollout undo deployment/bankcore -n banking-prod

# VM
./emergency-rollback.sh
```

### Circuit Breaker Activation

```java
@Component
public class DeploymentCircuitBreaker {
    
    @Value("${deployment.error.threshold:0.01}")
    private double errorThreshold;
    
    public void checkAndTriggerRollback() {
        double errorRate = metricsService.getErrorRate();
        
        if (errorRate > errorThreshold) {
            log.error("Error rate {} exceeds threshold {}, triggering rollback", 
                     errorRate, errorThreshold);
            
            // Log for monitoring
            alertingService.sendCriticalAlert("Automatic rollback triggered");
            
            // Trigger rollback
            deploymentService.rollback();
        }
    }
}
```

### Communication Template

```
Subject: [URGENT] Deployment Issue Detected

Zero-Downtime Strategy

Automated monitoring has detected issues with the current deployment:
- Error rate: {ERROR_RATE}%
- Affected service: {SERVICE_NAME}
- Start time: {TIMESTAMP}

Actions taken:
- Automatic rollback initiated
- Traffic rerouted to stable version
- Incidents logged for review

Next steps:
1. Join war room: {LINK}
2. Review deployment logs
3. Root cause analysis

Status page updated: {STATUS_PAGE_LINK}
```

## Best Practices Summary

### Do's
✅ Always maintain minimum capacity  
✅ Implement comprehensive health checks  
✅ Test rollback procedures  
✅ Monitor during deployment  
✅ Use gradual rollout  
✅ Maintain backward compatibility  
✅ Document emergency procedures  

### Don'ts
❌ Never set maxUnavailable > 0 for critical services  
❌ Don't skip health check configuration  
❌ Avoid big-bang deployments  
❌ Don't ignore monitoring alerts  
❌ Never deploy without rollback plan  
❌ Don't break API contracts  
❌ Avoid manual deployment steps