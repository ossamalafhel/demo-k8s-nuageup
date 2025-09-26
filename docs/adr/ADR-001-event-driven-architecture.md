# ADR-001: Event-Driven Architecture for Banking Transactions

## Status
**ACCEPTED** ✅

**Date:** 2025-09-26  
**Decision:** Implement event-driven architecture for transaction processing  

## Context

Our banking application needs to handle:
- **Asynchronous transaction processing**
- **Fraud detection capabilities**
- **Audit trail requirements** for banking compliance
- **System integration** patterns
- **Scalability** for future growth

### Current Pain Points
- Monolithic transaction processing creates bottlenecks
- Synchronous operations block user experience
- Difficult to add new business rules without code changes
- Limited visibility into transaction lifecycle
- Audit data scattered across multiple systems

## Decision

We will implement an **Event-Driven Architecture** using:

### 1. Domain Events Pattern
```java
// Example: Transaction lifecycle events
TransactionCreatedEvent → FraudCheckEvent → ComplianceCheckEvent → TransactionApprovedEvent
```

### 2. Asynchronous Event Processing
- **Spring Application Events** for local processing
- **Spring Events** for event-driven communication
- **Event handlers** with retry and dead letter queues

### 3. Event Sourcing for Audit
- Complete transaction history through events
- Immutable audit trail for compliance
- Ability to reconstruct system state at any point in time

### 4. Saga Pattern for Distributed Transactions
- Multi-step transaction orchestration
- Compensation actions for failure scenarios
- Eventual consistency across services

## Architecture Overview

```
┌─────────────────┐    Events    ┌─────────────────┐
│   Transaction   │──────────────▶│  Spring Events  │
│   Controller    │               │   (In-Memory)   │
└─────────────────┘               └─────────────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
                    ▼                      ▼                      ▼
           ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
           │  Fraud Detection│    │ Compliance Check│    │  Audit Service  │
           │    Service      │    │    Service      │    │                 │
           └─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Implementation Details

### Event Types Implemented
1. **TransactionCreatedEvent** - New transaction initiated
2. **FraudSuspectedEvent** - Automated risk analysis triggered
3. **ComplianceCheckRequiredEvent** - Regulatory check needed
4. **TransactionApprovedEvent** - Transaction cleared for processing
5. **TransactionRejectedEvent** - Transaction declined with reason

### Event Handlers
- **Asynchronous processing** with `@Async` and `@EventListener`
- **Transactional safety** with `@Transactional(propagation = REQUIRES_NEW)`
- **Retry logic** with exponential backoff
- **Dead letter queue** for failed events

### Monitoring & Observability
- **Micrometer metrics** for event processing times
- **Distributed tracing** with Spring Cloud Sleuth
- **Event correlation IDs** for debugging

## Alternatives Considered

### Alternative 1: Synchronous REST APIs
❌ **Rejected**
- Blocking operations impact user experience
- Difficult to scale individual components
- Single point of failure
- Limited audit capabilities

### Alternative 2: Message Queues (RabbitMQ)
⚠️ **Partially Considered**
- Good for simple messaging patterns
- Less suitable for event sourcing
- Limited scalability for distributed systems
- Missing advanced stream processing capabilities

### Alternative 3: Database-based Event Store
⚠️ **Future Consideration**
- Good for strong consistency
- More complex to implement
- Limited distributed capabilities
- Could complement current solution

## Benefits

### 🚀 **Performance & Scalability**
- **Non-blocking operations** - UI remains responsive
- **Horizontal scaling** - Each service can scale independently
- **Load distribution** - Events processed across multiple instances

### 🔐 **Security & Compliance**
- **Complete audit trail** through event sourcing
- **Immutable logs** for regulatory compliance
- **Real-time fraud detection** without blocking transactions
- **Granular security events** for monitoring

### 🔧 **Development & Maintenance**
- **Loose coupling** between components
- **Easy to add new business rules** via new event handlers
- **Testable** - Each handler can be unit tested independently
- **Debuggable** - Event correlation for troubleshooting

### 🏦 **Business Value**
- **Real-time notifications** to customers
- **Faster transaction processing** perceived speed
- **Compliance reporting** automated through events
- **Business intelligence** from event streams

## Risks & Mitigation

### Risk 1: Eventual Consistency
**Mitigation:** 
- Use saga pattern for critical business processes
- Implement compensation actions
- Clear SLAs for consistency windows

### Risk 2: Event Ordering
**Mitigation:**
- Consider event partitioning for scalability
- Implement idempotent event handlers
- Version events for schema evolution

### Risk 3: Event Schema Evolution
**Mitigation:**
- Use Avro schema registry
- Backward/forward compatible event schemas
- Event versioning strategy

### Risk 4: Operational Complexity
**Mitigation:**
- Comprehensive monitoring and alerting
- Event replay capabilities for recovery
- Circuit breakers and bulkheads

## Success Metrics

### Performance Targets
- **Event processing latency** < 100ms (95th percentile)
- **Throughput** > 50,000 events/second
- **Availability** 99.9% event delivery guarantee

### Business Metrics  
- **Transaction approval time** reduced by 60%
- **Fraud detection speed** improved by 80%
- **Compliance reporting time** reduced from hours to minutes
- **System scalability** support 10x transaction growth

## Implementation Timeline

### Phase 1 (Weeks 1-2): Foundation ✅
- [x] Domain events definition
- [x] Spring event handlers
- [x] Basic metrics and monitoring

### Phase 2: Production Considerations
- [ ] Consider message broker for production
- [ ] Event sourcing implementation
- [ ] Dead letter queue handling

### Phase 3 (Weeks 5-6): Advanced Features
- [ ] Saga pattern implementation  
- [ ] Event replay functionality
- [ ] Advanced monitoring dashboards

## Conclusion

Event-Driven Architecture provides a solid foundation for scalable transaction processing. This demo implements the core patterns that could be extended for production use.

Key benefits demonstrated:
- **Asynchronous processing** with Spring Events
- **Audit trail** capabilities through event logging
- **Modular architecture** for independent scaling
- **Extensible design** for future enhancements

---
**Next ADR:** [ADR-002: Multi-AZ Kubernetes Deployment Strategy](ADR-002-multi-az-deployment.md)