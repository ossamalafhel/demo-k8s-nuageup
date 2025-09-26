# Deployment Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Environment Setup](#environment-setup)
3. [Building the Application](#building-the-application)
4. [Kubernetes Deployment](#kubernetes-deployment)
5. [VM Deployment](#vm-deployment)
6. [Database Setup](#database-setup)
7. [Monitoring Setup](#monitoring-setup)
8. [Verification](#verification)
9. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Tools
- Java 17+
- Maven 3.9+
- Docker 24+
- Kubernetes 1.28+
- kubectl CLI
- Helm 3+ (optional)
- Git

### Access Requirements
- Kubernetes cluster access
- Container registry access
- VM SSH access (for VM deployment)
- Database credentials

## Environment Setup

### 1. Clone Repository
```bash
git clone https://github.com/your-org/demo-k8s-nuageup.git
cd demo-k8s-nuageup
```

### 2. Configure Environment Variables
```bash
# Create .env file
cp .env.example .env

# Edit with your values
export DOCKER_REGISTRY=your-registry.com
export DB_PASSWORD=secure-password
export K8S_CONTEXT=your-k8s-context
```

### 3. Verify Connectivity
```bash
# Verify Kubernetes access
kubectl cluster-info
kubectl get nodes

# Verify Docker registry
docker login ${DOCKER_REGISTRY}
```

## Building the Application

### Local Build
```bash
cd app
./scripts/build.sh

# Or manually
mvn clean package
docker build -t ${DOCKER_REGISTRY}/bankcore:latest .
```

### CI/CD Build
```bash
# GitLab CI
git push origin main
# Pipeline triggers automatically

# Jenkins
# Build triggers via webhook or manually
```

## Kubernetes Deployment

### 1. Prepare Namespace
```bash
kubectl create namespace banking-prod
kubectl label namespace banking-prod name=banking-prod environment=production
```

### 2. Create Secrets
```bash
# Database credentials
kubectl create secret generic app-secrets \
  --from-literal=db-username=postgres \
  --from-literal=db-password=${DB_PASSWORD} \
  -n banking-prod

# Registry credentials
kubectl create secret docker-registry registry-credentials \
  --docker-server=${DOCKER_REGISTRY} \
  --docker-username=${DOCKER_USER} \
  --docker-password=${DOCKER_PASSWORD} \
  -n banking-prod
```

### 3. Deploy Application
```bash
# Update image in deployment.yaml
sed -i "s|your-registry|${DOCKER_REGISTRY}|g" kubernetes/deployment.yaml

# Apply all manifests
kubectl apply -f kubernetes/ -n banking-prod

# Or use the deployment script
./ci-cd/scripts/deploy-k8s.sh latest
```

### 4. Verify Deployment
```bash
# Check deployment status
kubectl rollout status deployment/bankcore -n banking-prod

# Check pods
kubectl get pods -n banking-prod -l app=bankcore

# Check services
kubectl get svc -n banking-prod

# Check ingress
kubectl get ingress -n banking-prod
```

### 5. Configure Ingress
```bash
# Update hostname in ingress.yaml
kubectl edit ingress bankcore-ingress -n banking-prod

# Add DNS record pointing to ingress IP
INGRESS_IP=$(kubectl get ingress bankcore-ingress -n banking-prod -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "Add DNS A record: bankcore.yourdomain.com -> ${INGRESS_IP}"
```

## VM Deployment

### 1. Prepare VM
```bash
# SSH to VM
ssh user@vm-host

# Run setup script
curl -sSL https://raw.githubusercontent.com/your-org/demo-k8s-nuageup/main/vm-deployment/scripts/setup.sh | bash
```

### 2. Configure Environment
```bash
# Copy deployment files
scp -r vm-deployment/* user@vm-host:/opt/bankcore/

# Update .env file on VM
ssh user@vm-host
cd /opt/bankcore
vim .env  # Update with your values
```

### 3. Deploy Application
```bash
# Option 1: Using systemd service
sudo systemctl start bankcore

# Option 2: Using deployment script
cd /opt/bankcore
./scripts/deploy.sh latest

# Option 3: Manual docker-compose
docker-compose up -d
```

### 4. Configure NGINX
```bash
# Update nginx configuration
sudo vim /etc/nginx/sites-available/bankcore

# Add SSL certificates
sudo certbot --nginx -d bankcore.yourdomain.com

# Reload nginx
sudo nginx -s reload
```

## Database Setup

### 1. PostgreSQL Installation (if needed)
```bash
# Kubernetes
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install postgres bitnami/postgresql \
  --set auth.postgresPassword=${DB_PASSWORD} \
  --set auth.database=bankingdb \
  -n banking-prod

# VM
docker run -d \
  --name postgres \
  -e POSTGRES_PASSWORD=${DB_PASSWORD} \
  -e POSTGRES_DB=bankingdb \
  -p 5432:5432 \
  postgres:15-alpine
```

### 2. Initialize Database
```bash
# Run init script
psql -h localhost -U postgres -d bankingdb -f database/init.sql

# Run migrations (automatic with Flyway)
# Migrations run on application startup
```

### 3. Verify Database
```bash
# Connect to database
psql -h localhost -U postgres -d bankingdb

# Check tables
\dt banking.*

# Check sample data
SELECT COUNT(*) FROM banking.transactions;
```

## Monitoring Setup

### 1. Deploy Prometheus
```bash
# Add Prometheus repository
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts

# Install Prometheus
helm install prometheus prometheus-community/kube-prometheus-stack \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  -n monitoring --create-namespace
```

### 2. Configure Prometheus
```bash
# Apply ServiceMonitor
kubectl apply -f kubernetes/monitoring/servicemonitor.yaml

# Verify targets
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090
# Visit http://localhost:9090/targets
```

### 3. Deploy Grafana Dashboard
```bash
# Import dashboard
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80

# Login (default: admin/prom-operator)
# Import dashboard from kubernetes/monitoring/dashboard.json
```

### 4. Configure Alerts
```bash
# Apply PrometheusRule
kubectl apply -f monitoring/alerts/application-alerts.yml

# Verify in Prometheus
# http://localhost:9090/alerts
```

## Verification

### 1. Health Checks
```bash
# Kubernetes
kubectl exec -it deployment/bankcore -n banking-prod -- curl localhost:8080/actuator/health

# VM
curl https://bankcore.yourdomain.com/actuator/health
```

### 2. API Testing
```bash
# List transactions
curl https://bankcore.yourdomain.com/api/v1/transactions

# Create transaction
curl -X POST https://bankcore.yourdomain.com/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC0000000001",
    "transactionType": "DEPOSIT",
    "amount": 100.00,
    "currency": "USD",
    "description": "Test deposit"
  }'
```

### 3. Performance Testing
```bash
# Run k6 load test
k6 run tests/load/k6-load-test.js
```

## Troubleshooting

### Common Issues

#### 1. Pods Not Starting
```bash
# Check pod status
kubectl describe pod <pod-name> -n banking-prod

# Check logs
kubectl logs <pod-name> -n banking-prod

# Common causes:
# - Image pull errors
# - Resource limits
# - Liveness probe failures
```

#### 2. Database Connection Issues
```bash
# Test connection from pod
kubectl exec -it deployment/bankcore -n banking-prod -- \
  nc -zv postgres-service.banking-prod.svc.cluster.local 5432

# Check network policies
kubectl get networkpolicy -n banking-prod
```

#### 3. Ingress Not Working
```bash
# Check ingress controller
kubectl get pods -n ingress-nginx

# Check ingress status
kubectl describe ingress bankcore-ingress -n banking-prod

# Test with curl
curl -v https://bankcore.yourdomain.com
```

#### 4. High Memory Usage
```bash
# Check JVM settings
kubectl exec -it deployment/bankcore -n banking-prod -- \
  jcmd 1 VM.flags

# Adjust resources
kubectl edit deployment bankcore -n banking-prod
```

### Debug Commands
```bash
# Get all resources
kubectl get all -n banking-prod

# Describe deployment
kubectl describe deployment bankcore -n banking-prod

# Get events
kubectl get events -n banking-prod --sort-by='.lastTimestamp'

# Port forward for debugging
kubectl port-forward deployment/bankcore 8080:8080 -n banking-prod

# Execute commands in pod
kubectl exec -it deployment/bankcore -n banking-prod -- /bin/sh
```

### Rollback Procedures
```bash
# Kubernetes rollback
kubectl rollout undo deployment/bankcore -n banking-prod

# Check rollout history
kubectl rollout history deployment/bankcore -n banking-prod

# VM rollback
ssh user@vm-host
cd /opt/bankcore
./scripts/rollback.sh
```

## Best Practices

1. **Always test in staging first**
2. **Use GitOps for production deployments**
3. **Monitor deployment progress**
4. **Have rollback plan ready**
5. **Document any manual changes**
6. **Keep secrets secure**
7. **Regular backup procedures**
8. **Update documentation after changes**

## Support

For issues or questions:
- Check logs first
- Consult troubleshooting guide
- Check platform documentation
- Create issue in repository