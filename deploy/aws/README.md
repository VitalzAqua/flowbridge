# FlowBridge AWS Deployment Guide

This folder contains the first practical AWS deployment path for FlowBridge Lite.

The goal is to deploy the existing Spring Boot backend with a practical AWS architecture:

```text
Spring Boot Docker image -> ECR -> ECS Fargate -> RDS PostgreSQL
Kafka -> Docker on EC2
Logs -> CloudWatch
Traffic -> Application Load Balancer
```

## Architecture

```text
Internet
  |
  v
Application Load Balancer
  |
  v
ECS Fargate task running FlowBridge
  |                  |
  |                  v
  |              EC2-hosted Kafka
  v
RDS PostgreSQL

ECS task logs -> CloudWatch Logs
```

Cost-aware design:

- The ECS task runs in public subnets but only accepts inbound traffic from the load balancer security group.
- RDS runs in private subnets and only accepts PostgreSQL traffic from the ECS task security group.
- Kafka runs on one EC2 instance and only accepts Kafka traffic from the ECS task security group.
- This avoids NAT Gateway cost for the first AWS deployment.

For a production system, ECS tasks would usually run in private subnets with NAT Gateways or VPC endpoints. That can be added later if the deployment needs stronger network isolation.

## Files

```text
deploy/aws/
  README.md
  cloudformation/
    01-ecr.yml
    02-ecs-rds-kafka.yml
  scripts/
    build-and-push-ecr.sh
```

## Prerequisites

Install and configure:

- AWS CLI
- Docker
- An AWS account with permission to create ECR, ECS, EC2, RDS, IAM, ALB, VPC, and CloudWatch resources

Check your caller identity:

```bash
aws sts get-caller-identity
```

Choose a region:

```bash
export AWS_REGION=us-east-1
export PROJECT_NAME=flowbridge
```

## Step 1: Create ECR Repository

ECR is where AWS stores your Docker image.

```bash
aws cloudformation deploy \
  --region "$AWS_REGION" \
  --stack-name "$PROJECT_NAME-ecr" \
  --template-file deploy/aws/cloudformation/01-ecr.yml \
  --parameter-overrides RepositoryName="$PROJECT_NAME"
```

Get the repository URI:

```bash
export ECR_REPOSITORY_URI=$(aws cloudformation describe-stacks \
  --region "$AWS_REGION" \
  --stack-name "$PROJECT_NAME-ecr" \
  --query "Stacks[0].Outputs[?OutputKey=='RepositoryUri'].OutputValue" \
  --output text)

echo "$ECR_REPOSITORY_URI"
```

## Step 2: Build And Push The Docker Image

```bash
export IMAGE_TAG=$(git rev-parse --short HEAD)

AWS_REGION="$AWS_REGION" \
ECR_REPOSITORY_URI="$ECR_REPOSITORY_URI" \
IMAGE_TAG="$IMAGE_TAG" \
bash deploy/aws/scripts/build-and-push-ecr.sh
```

The final image URI will look like:

```text
123456789012.dkr.ecr.us-east-1.amazonaws.com/flowbridge:abc1234
```

Store it:

```bash
export IMAGE_URI="$ECR_REPOSITORY_URI:$IMAGE_TAG"
```

## Step 3: Deploy ECS, RDS, Kafka EC2, ALB, IAM, And CloudWatch

Pick a database password for the dev deployment:

```bash
export DB_PASSWORD='replace-with-a-long-dev-password'
```

Deploy the main stack:

```bash
aws cloudformation deploy \
  --region "$AWS_REGION" \
  --stack-name "$PROJECT_NAME-app" \
  --template-file deploy/aws/cloudformation/02-ecs-rds-kafka.yml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    ProjectName="$PROJECT_NAME" \
    ImageUri="$IMAGE_URI" \
    DbPassword="$DB_PASSWORD"
```

Security note: this stack passes the database password into the ECS task as an environment variable from a CloudFormation `NoEcho` parameter. For a longer-lived deployment, move the password into AWS Secrets Manager or SSM Parameter Store and reference it from the ECS task definition as a secret.

Get the public API URL:

```bash
export FLOWBRIDGE_URL=$(aws cloudformation describe-stacks \
  --region "$AWS_REGION" \
  --stack-name "$PROJECT_NAME-app" \
  --query "Stacks[0].Outputs[?OutputKey=='LoadBalancerUrl'].OutputValue" \
  --output text)

echo "$FLOWBRIDGE_URL"
```

## Step 4: Smoke Test The Deployed API

Health check:

```bash
curl "$FLOWBRIDGE_URL/actuator/health"
```

Create a workflow:

```bash
curl -X POST "$FLOWBRIDGE_URL/api/workflows/account-opening" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "C123",
    "fullName": "Alice Chen",
    "dateOfBirth": "2001-05-01",
    "accountType": "SAVINGS",
    "advisorCode": "ADV001"
  }'
```

Then check the returned workflow ID:

```bash
curl "$FLOWBRIDGE_URL/api/workflows/<workflowId>"
```

Expected eventual status:

```text
COMPLETED
```

## Step 5: Check CloudWatch Logs

The ECS task writes logs to:

```text
/ecs/flowbridge
```

In the AWS Console:

```text
CloudWatch -> Logs -> Log groups -> /ecs/flowbridge
```

Look for log lines containing:

- workflow ID
- correlation ID
- Kafka event publishing
- processing started
- workflow completed or failed

This connects the app's correlation ID design to real cloud observability.

## Step 6: Clean Up To Stop Charges

When you are done testing, delete the app stack first:

```bash
aws cloudformation delete-stack \
  --region "$AWS_REGION" \
  --stack-name "$PROJECT_NAME-app"
```

Wait until it finishes:

```bash
aws cloudformation wait stack-delete-complete \
  --region "$AWS_REGION" \
  --stack-name "$PROJECT_NAME-app"
```

Then delete the ECR stack:

```bash
aws cloudformation delete-stack \
  --region "$AWS_REGION" \
  --stack-name "$PROJECT_NAME-ecr"
```

If the ECR repository still contains images, delete the images first or empty the repository in the AWS Console.

## Components

- ECR stores deployable Docker images.
- ECS Fargate runs the Spring Boot container without managing app servers.
- RDS PostgreSQL replaces the local Docker PostgreSQL database.
- CloudWatch captures container logs.
- An Application Load Balancer exposes the API and checks `/actuator/health`.
- IAM roles let ECS pull images and write logs.
- Security groups control which components can talk to each other.
- Kafka on EC2 is a cost-aware first step before managed MSK.
