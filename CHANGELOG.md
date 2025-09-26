# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-09-26

### Added
- Initial release of the Kubernetes migration demo
- Spring Boot 3.2.1 REST API with transaction endpoints
- Event-driven architecture with Spring Events (7 event types)
- PostgreSQL database integration with Flyway migrations
- Comprehensive input validation with Bean Validation (JSR 380)
- Idempotency support for transaction processing
- Audit logging with Spring AOP
- OpenAPI 3.0 documentation with Swagger UI
- JWT-based authentication configuration
- Kubernetes deployment manifests:
  - Zero-downtime deployment configuration
  - Pod Disruption Budget (PDB)
  - Horizontal Pod Autoscaler (HPA)
  - Multi-AZ anti-affinity rules
  - Network policies
  - Service account with RBAC
- Health checks (liveness, readiness, startup probes)
- Resilience4j circuit breaker configuration
- TestContainers for integration testing
- 32 tests with 100% pass rate
- CI/CD pipeline configurations (GitLab CI, Jenkins)
- Architecture Decision Records (ADRs)
- Comprehensive documentation:
  - Architecture overview
  - Zero-downtime deployment strategy
  - Database architecture
  - Security patterns
  - Troubleshooting guide
- Monitoring configurations (Prometheus, Grafana)
- Chaos engineering test scenarios

### Technical Stack
- Java 17
- Spring Boot 3.2.1
- PostgreSQL 15
- Docker
- Kubernetes
- Maven 3.8+

---

**Demo created for Nuageup's banking Kubernetes migration project**