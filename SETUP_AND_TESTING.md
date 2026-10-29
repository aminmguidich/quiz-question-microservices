# Local Setup & API Testing Guide

This guide explains how to set up the environment, run all microservices locally, and test the endpoints using Postman or cURL.

---

## 1. How to Run Locally

### Prerequisites
- **Java 17+** (JDK 17 or 21)
- **Maven 3.8+**
- **PostgreSQL** running on `localhost:5432` with databases `quizdb` and `questiondb`.

### Database Setup
1. Create the databases:
   ```sql
   CREATE DATABASE quizdb;
   CREATE DATABASE questiondb;
   ```
2. Populate seed data into `questiondb`:
   ```bash
   psql -U postgres -d questiondb -f question-table-data.sql
   ```

### Startup Order
Start each microservice in its own terminal:

1. **Service Registry** (Eureka - Port `8761`):
   ```bash
   cd service-registry && mvn spring-boot:run
   ```
2. **Question Service** (Port `8080`):
   ```bash
   cd question-service && mvn spring-boot:run
   ```
3. **Quiz Service** (Port `8090`):
   ```bash
   cd quiz-service && mvn spring-boot:run
   ```
4. **API Gateway** (Port `8765`):
   ```bash
   cd api-gateway && mvn spring-boot:run
   ```

Open `http://localhost:8761` in your browser to verify all microservices are registered in Eureka.

---

## 2. Testing APIs with Postman & cURL

A ready-to-import Postman collection is included in the project: [`postman_collection.json`](postman_collection.json).

### Quick cURL Examples (Via API Gateway: Port 8765)

**1. Create a Quiz:**
```bash
curl -X POST http://localhost:8765/quiz-service/quiz/create \
  -H "Content-Type: application/json" \
  -d '{"categoryName": "Java", "numQuestions": 5, "title": "Java Fundamentals"}'
```

**2. Fetch Questions for Quiz #1 (Fair play: No answers exposed):**
```bash
curl -X POST http://localhost:8765/quiz-service/quiz/get/1
```

**3. Submit Quiz Answers and Calculate Score:**
```bash
curl -X POST http://localhost:8765/quiz-service/quiz/submit/1 \
  -H "Content-Type: application/json" \
  -d '[{"id": 1, "response": "Bytecode"}, {"id": 2, "response": "Java"}]'
```
