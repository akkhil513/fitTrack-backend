# FitTrack Backend

A serverless REST API backend for the **FitTrack** fitness tracking application. Built with **Quarkus** (Java 17) and deployed as an **AWS Lambda** function behind an **API Gateway HTTP API**. User data is persisted in **Amazon DynamoDB** and AI-generated workout/nutrition plans are powered by the **Anthropic Claude API**.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [DynamoDB Schema](#dynamodb-schema)
- [API Reference](#api-reference)
- [Environment Variables](#environment-variables)
- [Local Development](#local-development)
- [Building & Packaging](#building--packaging)
- [Deploying to AWS](#deploying-to-aws)
- [IAM Permissions](#iam-permissions)

---

## Architecture Overview

```
Mobile / Web Client
        │
        ▼
  API Gateway (HTTP API)
        │
        ▼
  AWS Lambda (Quarkus JVM)
        │
   ┌────┴────┐
   ▼         ▼
DynamoDB   Anthropic
(3 tables)  Claude API
```

All infrastructure is defined in `template.yaml` (AWS SAM / CloudFormation).

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17 |
| Framework | Quarkus 3.x |
| Deployment | AWS Lambda |
| API Layer | AWS API Gateway HTTP API (v2) |
| Database | Amazon DynamoDB (on-demand) |
| AI / Plans | Anthropic Claude API |
| Auth | AWS Cognito (JWT validation) |
| IaC | AWS SAM / CloudFormation (`template.yaml`) |
| Build | Maven (`mvnw`) |

---

## Project Structure

```
src/main/java/com/fitTrack/
├── resource/           # JAX-RS REST endpoints (controllers)
│   ├── UserResource.java
│   ├── PlanResource.java
│   ├── LogResource.java
│   ├── MeasurementResource.java
│   └── HealthResource.java
├── service/            # Business logic & external API calls
│   └── ClaudeService.java
├── repository/         # DynamoDB data access layer
│   └── UserRepository.java
├── model/              # POJOs / request-response models
│   ├── UserProfile.java
│   ├── FitPlan.java
│   ├── FitLog.java
│   └── OnboardingRequest.java
└── mapper/             # Conversion between models and DynamoDB attribute maps
    └── UserMapper.java
```

---

## DynamoDB Schema

### `fittrack-users`

Stores user profile and body measurement data.

| Attribute | Type | Role |
|---|---|---|
| `userId` | String | Partition Key |
| `username` | String | GSI Hash Key (`username-index`) |
| `firstName` | String | |
| `lastName` | String | |
| `startDate` | String | |
| `measurements` | String (JSON) | Body measurements |

**GSI:** `username-index` — enables fast username availability checks without a full table scan.

---

### `fittrack-plans`

Stores AI-generated workout/nutrition plans per user.

| Attribute | Type | Role |
|---|---|---|
| `userId` | String | Partition Key |
| `plan` | String (JSON) | Claude-generated fitness plan |

---

### `fittrack-logs`

Stores daily fitness logs per user.

| Attribute | Type | Role |
|---|---|---|
| `userId` | String | Partition Key |
| `date` | String (`YYYY-MM-DD`) | Sort Key |
| `logData` | String (JSON) | Workout / nutrition log for the day |

**GSI:** `date-index` — enables querying all logs across users for a specific date.

---

## API Reference

> All routes are served through the API Gateway base URL output from the CloudFormation stack.  
> Protected routes require a valid Cognito JWT in the `Authorization` header.

### Users

| Method | Path | Description |
|---|---|---|
| `POST` | `/users/create` | Create a new user profile |
| `GET` | `/users/{userId}` | Fetch a user profile by ID |
| `PUT` | `/users/update/{userId}` | Update name & start date |
| `GET` | `/users/check/{username}` | Check if a username is already taken |
| `PUT` | `/measurements/{userId}` | Update body measurements |

#### `POST /users/create` — Request Body
```json
{
  "userId": "abc123",
  "username": "johndoe",
  "firstName": "John",
  "lastName": "Doe",
  "startDate": "2026-01-01"
}
```

#### `PUT /users/update/{userId}` — Request Body
```json
{
  "firstName": "John",
  "lastName": "Smith",
  "startDate": "2026-02-01"
}
```

#### `GET /users/check/{username}` — Responses
- `200 OK` → `{ "available": true, "message": "Username available" }`
- `409 Conflict` → `{ "available": false, "message": "Username already taken" }`

---

### Plans

| Method | Path | Description |
|---|---|---|
| `POST` | `/plans/generate` | Generate an AI fitness plan via Claude |
| `POST` | `/plans/createPlan` | Save a plan manually |
| `GET` | `/plans/{userId}` | Retrieve the saved plan for a user |

---

### Logs

| Method | Path | Description |
|---|---|---|
| `POST` | `/logs/createLog` | Create a new daily fitness log |
| `GET` | `/logs/{userId}` | Get all logs for a user |
| `GET` | `/logs/{userId}/{date}` | Get a specific log by date (`YYYY-MM-DD`) |
| `PUT` | `/logs/update/{userId}/{date}` | Update an existing log entry |

---

### Health

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Lambda warm-up / health check |

---

## Environment Variables

Configured in `template.yaml` under the Lambda `Environment.Variables` block:

| Variable | Description |
|---|---|
| `COGNITO_USER_POOL_ID` | AWS Cognito User Pool ID for JWT validation |
| `ANTHROPIC_API_KEY` | Anthropic Claude API key for AI plan generation |
| `ENVIRONMENT` | Deployment stage (`dev` / `prod`) |

---

## Local Development

### Prerequisites

- Java 17+
- Maven (or use the included `./mvnw` wrapper)
- AWS credentials configured (`~/.aws/credentials` or environment variables)

### Run in Dev Mode (live reload)

```shell
./mvnw compile quarkus:dev
```

The Quarkus Dev UI is available at: [http://localhost:8080/q/dev/](http://localhost:8080/q/dev/)

---

## Building & Packaging

### Standard JVM JAR

```shell
./mvnw package
```

Output: `target/fitTrack-backend-1.0.0-runner.jar`

### Über-JAR

```shell
./mvnw package -Dquarkus.package.type=uber-jar
```

### Lambda Deployment ZIP

The `function.zip` is automatically produced under `target/` during `./mvnw package`.

### Native Executable (GraalVM)

```shell
./mvnw package -Dnative
```

Or using a container (no local GraalVM install required):

```shell
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Run the native binary: `./target/fitTrack-backend-1.0.0-runner`

---

## Deploying to AWS

### Prerequisites

- [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html)
- AWS CLI configured with appropriate credentials and region
- An S3 bucket named `fittrack-deployments` (or update the bucket name in `template.yaml`)

### Steps

**1. Build and package**
```shell
./mvnw package
```

**2. Upload the function ZIP to S3**
```shell
aws s3 cp target/function.zip s3://fittrack-deployments/backend/function.zip
```

**3. Deploy the CloudFormation stack**
```shell
sam deploy \
  --template-file template.yaml \
  --stack-name fittrack-backend \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides Environment=dev
```

**4. Retrieve the API endpoint**
```shell
aws cloudformation describe-stacks \
  --stack-name fittrack-backend \
  --query "Stacks[0].Outputs[?OutputKey=='ApiEndpoint'].OutputValue" \
  --output text
```

---

## IAM Permissions

The `LambdaExecutionRole` grants the following permissions:

**DynamoDB** (`GetItem`, `PutItem`, `UpdateItem`, `DeleteItem`, `Query`, `Scan`):
- `table/fittrack-users`
- `table/fittrack-users/index/*` (GSI: `username-index`)
- `table/fittrack-plans`
- `table/fittrack-logs`
- `table/fittrack-logs/index/*` (GSI: `date-index`)

**CloudWatch Logs** (`CreateLogGroup`, `CreateLogStream`, `PutLogEvents`):
- `*`

---

## Related Resources

- [Quarkus Documentation](https://quarkus.io/guides/)
- [Quarkus AWS Lambda Guide](https://quarkus.io/guides/aws-lambda)
- [AWS SAM Developer Guide](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/)
- [Anthropic Claude API Docs](https://docs.anthropic.com/)
- [Amazon DynamoDB Developer Guide](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/)
