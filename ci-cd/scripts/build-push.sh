#!/bin/bash
set -e

# CI/CD Build and Push Script

REGISTRY=${REGISTRY:-"docker.io"}
NAMESPACE=${NAMESPACE:-"banking"}
IMAGE_NAME=${IMAGE_NAME:-"banking-demo"}
VERSION=${VERSION:-"latest"}

echo "Building Docker image..."
docker build -t ${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${VERSION} ./app

echo "Pushing to registry..."
docker push ${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${VERSION}

echo "Image pushed: ${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${VERSION}"