# Demo K8s Nuageup

> Démonstration technique pour migration bancaire Kubernetes
> Créée par Ossama Lafhel - Lead Java/Spring Boot

## 🎯 Objectif

Cette démonstration illustre les patterns et best practices 
pour migrer des applications bancaires vers Kubernetes avec 
une architecture zero-downtime.

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker 20.10+
- kubectl 1.26+ (optional)

### Run Locally
```bash
cd app
mvn clean install
mvn spring-boot:run
curl http://localhost:8080/actuator/health
```

### Run with Docker
```bash
cd app
docker build -t demo-nuageup .
docker run -p 8080:8080 demo-nuageup
```

### Deploy to Kubernetes
```bash
kubectl apply -f kubernetes/
kubectl port-forward svc/bankcore-service 8080:8080
```

## 🏗️ Architecture

**Technology Stack:**
- Java 17 + Spring Boot 3.2.1
- PostgreSQL 15
- Docker + Kubernetes
- Event-driven with Spring Events
- OpenAPI/Swagger documentation

**Patterns Demonstrated:**
- Event-driven architecture
- Zero-downtime deployment (PDB, graceful shutdown)
- Multi-AZ resilience patterns
- Health checks (liveness, readiness, startup)
- Configuration externalization
- Audit trail for compliance

## 📋 Features

### Core Application
✅ REST API with Spring Boot  
✅ Event-driven architecture (Spring Events)  
✅ Database integration (PostgreSQL)  
✅ Audit logging for compliance  
✅ OpenAPI/Swagger documentation  
✅ Input validation with Bean Validation  
✅ Idempotency support for transactions  

### Kubernetes Deployment
✅ Zero-downtime rolling updates  
✅ Pod Disruption Budget  
✅ Multi-AZ anti-affinity  
✅ Health probes configured  
✅ Resource limits & HPA  
✅ Network policies  
✅ Service account with RBAC  

### Testing
✅ Unit tests (JUnit 5)  
✅ Integration tests (Spring Boot Test)  
✅ Container tests (Testcontainers)  
✅ 32 tests - 100% passing  

## 📚 Documentation

- [Architecture Overview](docs/ARCHITECTURE.md)
- [Architecture Decision Records](docs/adr/)
- [Deployment Strategy](docs/DEPLOYMENT.md)
- [Zero-Downtime Strategy](docs/ZERO-DOWNTIME.md)
- [Database Architecture](docs/DATABASE_ARCHITECTURE.md)
- [Security Patterns](docs/SECURITY.md)

## 🎯 Production Considerations

This demo focuses on deployment patterns and architecture.

For production banking deployment, consider:
- Distributed cache (Redis Cluster)
- Message streaming (Kafka)
- Security hardening (encryption, MFA)
- Compliance (PCI DSS, audit trail)
- Monitoring (Prometheus, Grafana)
- Service mesh (Istio)

## 📁 Project Structure

```
demo-k8s-nuageup/
├── app/                    # Spring Boot application
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
├── kubernetes/             # K8s manifests
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   └── ...
├── ci-cd/                  # CI/CD pipelines
├── docs/                   # Documentation
│   └── adr/               # Architecture Decision Records
├── monitoring/             # Monitoring configs
└── tests/                  # Test scenarios
```

## 🔧 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/health` | Health check |
| GET | `/api/v1/transactions` | List transactions |
| POST | `/api/v1/transactions` | Create transaction |
| GET | `/actuator/health` | Spring Boot actuator |
| GET | `/actuator/prometheus` | Prometheus metrics |
| GET | `/swagger-ui.html` | API documentation |

## 👤 Author

**Ossama Lafhel**
- 24 years experience in Java/Spring Boot
- Tech Lead with banking sector expertise (Natixis, CNP Assurances, Crédit Agricole)
- Specialized in cloud-native migrations and Kubernetes deployments
- Contact: ossama.lafhel@gmail.com

## 📝 License

MIT License - Created for demonstration purposes

---

**Demo created for Nuageup's banking Kubernetes migration project**