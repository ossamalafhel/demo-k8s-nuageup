#!/bin/bash
set -e

# Kubernetes Deployment Script

NAMESPACE=${NAMESPACE:-"banking-prod"}
ENVIRONMENT=${ENVIRONMENT:-"prod"}

echo "Deploying to Kubernetes cluster..."
echo "Namespace: $NAMESPACE"
echo "Environment: $ENVIRONMENT"

# Create namespace if not exists
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# Apply configurations
echo "Applying Kubernetes manifests..."
kubectl apply -f kubernetes/ -n $NAMESPACE

# Wait for deployment
echo "Waiting for deployment to be ready..."
kubectl rollout status deployment/banking-demo -n $NAMESPACE

# Show status
kubectl get all -n $NAMESPACE

echo "Deployment completed!"