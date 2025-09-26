# Architecture Overview

## Table of Contents
1. [Introduction](#introduction)
2. [System Architecture](#system-architecture)
3. [Application Architecture](#application-architecture)
4. [Infrastructure Architecture](#infrastructure-architecture)
5. [Security Architecture](#security-architecture)
6. [Data Flow](#data-flow)
7. [Technology Stack](#technology-stack)

## Introduction

The demo application demonstrates a production-ready microservices architecture with hybrid deployment capabilities across Kubernetes clusters and traditional VMs. This document provides a comprehensive overview of the architectural decisions and patterns implemented.

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Load Balancer                         │
│                    (Multi-Region, SSL)                       │
└─────────────────────┬───────────────────────────────────────┘
                      │
        ┌─────────────┴─────────────┐
        │                           │
┌───────▼────────┐         ┌───────▼────────┐
│   Kubernetes   │         │      VM        │
│    Cluster     │◄────────►   Environment  │
│   (Primary)    │         │  (Transition)  │
└────────────────┘         └────────────────┘
        │                           │
        └───────────┬───────────────┘
                    │
            ┌───────▼────────┐
            │   PostgreSQL   │
            │   (Shared DB)  │
            └────────────────┘
```

### Component Overview

1. **Load Balancer Layer**
   - Multi-region traffic distribution
   - SSL/TLS termination
   - DDoS protection
   - Geographic routing

2. **Application Layer**
   - Spring Boot microservices
   - RESTful APIs
   - Async message processing
   - Circuit breakers

3. **Data Layer**
   - PostgreSQL with replication
   - Redis caching
   - Connection pooling
   - Read replicas

4. **Infrastructure Layer**
   - Kubernetes orchestration
   - Docker containers
   - Service mesh (optional)
   - Monitoring stack

## Application Architecture

### Microservices Design

```
┌────────────────────────────────────────┐
│         demo Service               │
├────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐   │
│  │  Controller  │  │   Service    │   │
│  │    Layer     │──►    Layer     │   │
│  └──────────────┘  └──────┬───────┘   │
│                            │           │
│  ┌──────────────┐  ┌──────▼───────┐   │
│  │ Repository   │◄─┤   Domain     │   │
│  │    Layer     │  │   Models     │   │
│  └──────────────┘  └──────────────┘   │
└────────────────────────────────────────┘
```

### Key Design Patterns

1. **Repository Pattern**
   - Abstracts data access
   - Enables testing with mocks
   - Supports multiple data sources

2. **Circuit Breaker Pattern**
   - Resilience4j implementation
   - Prevents cascade failures
   - Automatic recovery

3. **Event-Driven Architecture**
   - Async processing
   - Event sourcing ready
   - Audit trail

4. **API Gateway Pattern**
   - Centralized entry point
   - Rate limiting
   - Authentication/Authorization

### API Design

```yaml
/api/v1/
  /transactions
    GET    - List transactions (paginated)
    POST   - Create transaction
    /{id}
      GET    - Get transaction details
      PUT    - Update transaction
      DELETE - Delete transaction
  /health
    GET    - Application health
    /deep  - Deep health check
  /statistics
    GET    - Transaction statistics
```

## Infrastructure Architecture

### Kubernetes Deployment

```yaml
Namespace: banking-prod
├── Deployments
│   └── bankcore (3 replicas, multi-AZ)
├── Services
│   ├── bankcore-service (ClusterIP)
│   ├── bankcore-headless (Headless)
│   └── bankcore-nodeport (NodePort)
├── Ingress
│   └── bankcore-ingress (NGINX)
├── ConfigMaps & Secrets
├── HPA (3-10 replicas)
├── PDB (minAvailable: 2)
└── NetworkPolicies
```

### Multi-AZ Strategy

1. **Pod Anti-Affinity**
   - Required: Different availability zones
   - Preferred: Different nodes
   - Ensures high availability

2. **Zone-Aware Load Balancing**
   - Prefer local zone traffic
   - Cross-zone failover
   - Latency optimization

3. **Data Replication**
   - Multi-AZ database
   - Synchronous replication
   - Automated failover

### VM Deployment Architecture

```
VM Host
├── Docker Engine
├── Docker Compose Stack
│   ├── Application Container
│   ├── Database Container
│   ├── Redis Container
│   └── NGINX Container
├── Monitoring Agents
│   ├── Node Exporter
│   ├── Postgres Exporter
│   └── Custom Metrics
└── Log Aggregation
```

## Security Architecture

### Security Layers

1. **Network Security**
   - Network policies
   - Firewall rules
   - VPN access
   - Private subnets

2. **Application Security**
   - JWT authentication
   - Role-based access control
   - Input validation
   - SQL injection prevention

3. **Container Security**
   - Non-root containers
   - Read-only filesystems
   - Security scanning
   - Image signing

4. **Data Security**
   - Encryption at rest
   - Encryption in transit
   - Key rotation
   - Audit logging

### Security Best Practices

```yaml
SecurityContext:
  runAsNonRoot: true
  runAsUser: 1000
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop:
      - ALL
```

## Data Flow

### Transaction Processing Flow

```
Client Request
    │
    ▼
Load Balancer
    │
    ▼
Ingress Controller
    │
    ▼
Service (Load Balance)
    │
    ▼
Pod (Application)
    │
    ├──► Validation
    ├──► Business Logic
    ├──► Database Operation
    └──► Response
```

### Monitoring Data Flow

```
Application Metrics
    │
    ▼
Prometheus Scrape
    │
    ▼
Prometheus Storage
    │
    ▼
Grafana Queries
    │
    ▼
Dashboards & Alerts
```

## Technology Stack

### Core Technologies

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| Language | Java | 17 | Application runtime |
| Framework | Spring Boot | 3.2.1 | Application framework |
| Database | PostgreSQL | 15 | Primary data store |
| Cache | Redis | 7 | Caching layer |
| Container | Docker | 24 | Containerization |
| Orchestration | Kubernetes | 1.28+ | Container orchestration |
| CI/CD | GitLab CI/Jenkins | Latest | Automation pipeline |
| Monitoring | Prometheus/Grafana | Latest | Observability |

### Libraries & Dependencies

1. **Spring Ecosystem**
   - Spring Web
   - Spring Data JPA
   - Spring Security
   - Spring Actuator

2. **Resilience**
   - Resilience4j
   - Circuit breakers
   - Rate limiting
   - Retry mechanisms

3. **Observability**
   - Micrometer
   - Prometheus metrics
   - Distributed tracing
   - Structured logging

4. **Database**
   - HikariCP
   - Flyway migrations
   - JPA/Hibernate
   - Query optimization

## Architectural Decisions

### ADR-001: Hybrid Deployment Strategy
- **Status**: Accepted
- **Context**: Need to support both modern Kubernetes and legacy VM deployments
- **Decision**: Implement deployment scripts for both environments
- **Consequences**: Increased complexity but enables gradual migration

### ADR-002: Database Per Service vs Shared
- **Status**: Accepted
- **Context**: Starting with monolithic architecture
- **Decision**: Use shared database initially, design for future separation
- **Consequences**: Easier to start, requires careful schema management

### ADR-003: Synchronous vs Asynchronous Communication
- **Status**: Accepted
- **Context**: Need reliable transaction processing
- **Decision**: Synchronous for critical paths, async for notifications
- **Consequences**: Better reliability, potential latency for some operations

### ADR-004: Multi-AZ Deployment
- **Status**: Accepted
- **Context**: High availability requirement
- **Decision**: Enforce pod anti-affinity across availability zones
- **Consequences**: Higher infrastructure costs, better resilience

## Performance Considerations

1. **Connection Pooling**
   - HikariCP optimized settings
   - Connection reuse
   - Fast failover

2. **Caching Strategy**
   - Redis for session data
   - Application-level caching
   - CDN for static assets

3. **Database Optimization**
   - Proper indexing
   - Query optimization
   - Read replicas

4. **Resource Management**
   - JVM tuning
   - Container limits
   - Horizontal scaling

## Future Enhancements

1. **Service Mesh Integration**
   - Istio/Linkerd consideration
   - Advanced traffic management
   - Enhanced observability

2. **Event Streaming**
   - Kafka integration
   - Event sourcing
   - CQRS pattern

3. **Multi-Region Deployment**
   - Geographic distribution
   - Data sovereignty
   - Disaster recovery

4. **API Gateway**
   - Kong/Zuul integration
   - Advanced routing
   - API versioning