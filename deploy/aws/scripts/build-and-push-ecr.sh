#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${AWS_REGION:-}" ]]; then
  echo "AWS_REGION is required, for example: export AWS_REGION=us-east-1" >&2
  exit 1
fi

if [[ -z "${ECR_REPOSITORY_URI:-}" ]]; then
  echo "ECR_REPOSITORY_URI is required. Get it from the flowbridge-ecr CloudFormation stack output." >&2
  exit 1
fi

IMAGE_TAG="${IMAGE_TAG:-latest}"
AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

docker build -t "flowbridge:$IMAGE_TAG" .
docker tag "flowbridge:$IMAGE_TAG" "$ECR_REPOSITORY_URI:$IMAGE_TAG"
docker push "$ECR_REPOSITORY_URI:$IMAGE_TAG"

echo "Pushed image:"
echo "$ECR_REPOSITORY_URI:$IMAGE_TAG"

