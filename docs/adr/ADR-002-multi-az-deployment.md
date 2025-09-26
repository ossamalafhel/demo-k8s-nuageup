# ADR-002: Multi-AZ Kubernetes Deployment Strategy

## Status
**ACCEPTED** ✅

**Date:** 2025-09-26  
**Decision:** Implement Multi-AZ deployment for high availability  

## Context

High availability is critical for banking applications. This demo implements Multi-AZ deployment patterns to ensure resilience against zone failures.

### Requirements Demonstrated
- **Zero-downtime deployments** using rolling updates
- **Zone failure resilience** with pod anti-affinity
- **Auto-scaling** capabilities with HPA
- **Health monitoring** with proper probes
- **Resource optimization** with proper limits

### Risk Assessment
- **Zone failures** occur 2-3 times per year (AWS/Azure statistics)
- **Network partitions** can isolate entire availability zones
- **Planned maintenance** requires seamless failover
- **Traffic spikes** during market events need rapid scaling

## Decision

Implement **Multi-AZ Kubernetes deployment** with:

### 1. Pod Anti-Affinity Rules
```yaml
affinity:
  podAntiAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
    - labelSelector:
        matchExpressions:
        - key: app
          operator: In
          values: [bankcore]
      topologyKey: topology.kubernetes.io/zone
```

### 2. Zero-Downtime Rolling Updates
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1
    maxUnavailable: 0  # Critical: No downtime allowed
```

### 3. Multi-AZ Database Configuration
```yaml
# PostgreSQL with Multi-AZ failover
datasource:
  url: jdbc:postgresql://db-az1:5432,db-az2:5432,db-az3:5432/bankingdb?targetServerType=primary&loadBalanceHosts=true
```

### 4. Intelligent Load Balancing
```yaml
# Application Load Balancer with health checks
service:
  type: LoadBalancer
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-cross-zone-load-balancing-enabled: "true"
```

## Architecture Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    Application Load Balancer                 │
│              (Cross-Zone Load Balancing)                     │
└────────────────┬─────────────────┬─────────────────┬─────────┘
                 │                 │                 │
         ┌───────▼────────┐ ┌──────▼────────┐ ┌─────▼────────┐
         │  Availability  │ │ Availability  │ │ Availability │
         │    Zone A      │ │    Zone B     │ │    Zone C    │
         │                │ │               │ │              │
         │ ┌────────────┐ │ │┌────────────┐ │ │┌────────────┐│
         │ │    Pod     │ │ ││    Pod     │ │ ││    Pod     ││
         │ │ (Primary)  │ │ ││ (Replica)  │ │ ││ (Replica)  ││
         │ └────────────┘ │ │└────────────┘ │ │└────────────┘│
         │                │ │               │ │              │
         │ ┌────────────┐ │ │┌────────────┐ │ │┌────────────┐│
         │ │ PostgreSQL │ │ ││ PostgreSQL │ │ ││ PostgreSQL ││
         │ │ (Primary)  │◄┼─┼┤ (Standby)  │ │ ││ (Standby)  ││
         │ └────────────┘ │ │└────────────┘ │ │└────────────┘│
         └────────────────┘ └───────────────┘ └──────────────┘
```

## Implementation Details

### Cluster Configuration

#### Node Groups per AZ
```yaml
# Minimum 1 node per AZ, auto-scale up to 10 nodes
nodeGroups:
  - name: banking-nodes-az1
    availabilityZones: [us-east-1a]
    minSize: 1
    maxSize: 10
    instanceTypes: [m5.xlarge, m5.2xlarge]
    
  - name: banking-nodes-az2  
    availabilityZones: [us-east-1b]
    minSize: 1
    maxSize: 10
    instanceTypes: [m5.xlarge, m5.2xlarge]
    
  - name: banking-nodes-az3
    availabilityZones: [us-east-1c]  
    minSize: 1
    maxSize: 10
    instanceTypes: [m5.xlarge, m5.2xlarge]
```

#### Pod Distribution Strategy
```yaml
# Ensure pods are distributed across zones
spec:
  replicas: 3  # Minimum for Multi-AZ
  selector:
    matchLabels:
      app: bankcore
  template:
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchLabels:
                app: bankcore
            topologyKey: topology.kubernetes.io/zone
        nodeAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            preference:
              matchExpressions:
              - key: node.kubernetes.io/instance-type
                operator: In
                values: [m5.xlarge, m5.2xlarge]
```

### Database High Availability

#### Connection Pool Configuration
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000
      validation-timeout: 5000
      connection-test-query: "SELECT 1"
      # Failover handling
      connection-init-sql: "SET application_name='bankcore'"
```

#### Failover Detection
```java
@Component
@Slf4j
public class DatabaseHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            // Test connection to all AZs
            testConnection();
            return Health.up()
                .withDetail("database", "All AZs accessible")
                .withDetail("primary-az", getCurrentPrimaryAZ())
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("failover-status", checkFailoverStatus())
                .build();
        }
    }
}
```

### Auto-Scaling Configuration

#### Horizontal Pod Autoscaler
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: bankcore-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: bankcore
  minReplicas: 3    # One per AZ minimum
  maxReplicas: 30   # 10 per AZ maximum
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource  
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
```

## Alternatives Considered

### Alternative 1: Single-AZ with Regular Backups
❌ **Rejected**
- **Unacceptable downtime** during zone failures
- **Manual failover** takes 30+ minutes
- **Data loss risk** during disasters
- **Does not meet** 99.99% SLA requirements

### Alternative 2: Multi-Region Active-Active
⚠️ **Future Consideration**
- **Higher complexity** and operational overhead
- **Data consistency** challenges across regions
- **Significantly higher costs** (3x infrastructure)
- **Regulatory complexity** for cross-border data

### Alternative 3: VM-based Multi-AZ
⚠️ **Partially Implemented**
- **Slower scaling** compared to Kubernetes
- **Resource inefficiency** 
- **Complex orchestration** without K8s benefits
- **Used for hybrid transition** strategy

## Benefits

### 🚀 **Availability & Resilience**
- **99.99% uptime** target achievable
- **Zero-downtime deployments** for continuous delivery
- **Automatic failover** in case of AZ failure
- **No single point of failure** in application layer

### 💰 **Cost Optimization**
- **Right-sizing** with HPA based on actual load
- **Spot instances** for non-critical workloads
- **Resource sharing** across applications
- **Pay for usage** model with auto-scaling

### ⚡ **Performance**
- **Low latency** with zone-local processing
- **Load distribution** across multiple AZs
- **Burst capacity** during peak loads
- **Geographic optimization** for user proximity

### 🔧 **Operational Excellence**
- **Automated operations** with Kubernetes operators
- **Consistent deployments** across environments
- **Easy monitoring** with centralized logging
- **Standard tooling** for developers

## Risks & Mitigation

### Risk 1: Cross-AZ Network Latency
**Impact:** 1-2ms additional latency between zones  
**Mitigation:**
- Optimize database queries for minimal cross-AZ calls
- Use read replicas in each AZ for read operations
- Cache frequently accessed data locally

### Risk 2: Uneven Load Distribution
**Impact:** Some AZs may be overloaded while others are underutilized  
**Mitigation:**
- Implement intelligent load balancing with health checks
- Monitor per-AZ metrics and adjust routing weights
- Use Kubernetes scheduler to balance pod placement

### Risk 3: Data Consistency During Failover
**Impact:** Potential data loss during primary database failover  
**Mitigation:**
- Use synchronous replication for critical data
- Implement circuit breakers to handle temporary inconsistencies
- Database-level failover automation with minimal RPO

### Risk 4: Increased Infrastructure Costs
**Impact:** 2-3x infrastructure costs compared to single-AZ  
**Mitigation:**
- Use auto-scaling to minimize idle resources
- Implement cost monitoring and optimization
- Reserved instances for baseline capacity

## Success Metrics

### Availability Targets
- **Uptime:** 99.99% (52.56 minutes downtime/year)
- **Failover time:** < 30 seconds automated
- **Recovery time:** < 15 minutes for major incidents
- **Zero-downtime deployments:** 100% success rate

### Performance Targets  
- **Response time:** < 200ms (95th percentile)
- **Throughput:** 10,000+ requests/second
- **Auto-scaling time:** < 60 seconds for scale-up
- **Cross-AZ latency:** < 5ms additional overhead

### Cost Targets
- **Infrastructure efficiency:** > 70% resource utilization
- **Cost per transaction:** Reduce by 30% through optimization
- **Spot instance usage:** > 50% for non-critical workloads

## Implementation Phases

### Phase 1: Foundation ✅
- [x] Multi-AZ cluster setup
- [x] Pod anti-affinity configuration  
- [x] Basic load balancing
- [x] Health checks implementation

### Phase 2: Database HA (In Progress)
- [ ] Multi-AZ PostgreSQL setup
- [ ] Automated failover configuration
- [ ] Connection pooling optimization
- [ ] Backup and recovery procedures

### Phase 3: Advanced Features
- [ ] Cross-region disaster recovery
- [ ] Advanced monitoring and alerting
- [ ] Cost optimization automation
- [ ] Chaos engineering validation

## Monitoring & Alerting

### Key Metrics to Monitor
```yaml
# Prometheus alerts
- alert: PodNotDistributedAcrossAZs
  expr: |
    count by (app) (
      count by (app, topology_kubernetes_io_zone) (
        kube_pod_info{app="bankcore"}
      )
    ) < 3
  for: 5m
  
- alert: AZFailureDetected  
  expr: |
    up{job="bankcore"} == 0
    and on (availability_zone) 
    count by (availability_zone) (up{job="bankcore"}) == 0
  for: 1m
```

## Conclusion

Multi-AZ Kubernetes deployment provides the **foundation for banking-grade availability** while maintaining **operational efficiency** and **cost effectiveness**.

Key benefits:
- **Meeting SLA requirements** with 99.99% availability
- **Zero-downtime operations** for competitive advantage  
- **Automatic scaling** for business growth
- **Disaster recovery** capabilities for business continuity

This strategy supports our **600+ application migration** by providing a **proven, scalable platform** that meets **banking industry standards**.

---
**Previous:** [ADR-001: Event-Driven Architecture](ADR-001-event-driven-architecture.md)  
**Next:** [ADR-003: Security Architecture for Banking](ADR-003-security-architecture.md)