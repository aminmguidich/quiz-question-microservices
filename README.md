# Microservices Communication Workflow

This document explains the communication architecture and request workflows in the **Quiz-Question Microservices** project, clarifying the role of the **API Gateway**, **Eureka Service Registry**, and **Spring Cloud OpenFeign**.

---

## 1. Key Concept: North-South vs. East-West Traffic

In this architecture, there are two distinct types of network traffic:

| Traffic Type | Description | Path | Uses API Gateway? |
| :--- | :--- | :--- | :---: |
| **North-South Traffic** (External $\rightarrow$ Internal) | Outside clients (Browser, Postman, Mobile App) calling the system. | Client $\rightarrow$ **API Gateway** $\rightarrow$ Microservice | **YES** |
| **East-West Traffic** (Internal $\rightarrow$ Internal) | Microservices communicating directly with each other. | `quiz-service` $\rightarrow$ `question-service` | **NO** |

> [!IMPORTANT]
> **When `quiz-service` communicates with `question-service`, the API Gateway is NOT in the path.**
> `quiz-service` uses **Spring Cloud OpenFeign** and **Eureka** to establish a direct service-to-service connection.

---

## 2. High-Level Architecture Diagram

```mermaid
flowchart TD
    Client(["Outside Client (Postman / Browser)"])

    subgraph "External Traffic (North-South)"
        Gateway["API Gateway (Port 8765)"]
    end

    subgraph "Discovery & Directory"
        Eureka["Eureka Service Registry (Port 8761)"]
    end

    subgraph "Internal Services (East-West Direct Communication)"
        QuizService["Quiz Service (Port 8090)"]
        QuestionService["Question Service"]
    end

    subgraph "Databases"
        QuizDB[("quizdb (PostgreSQL)")]
        QuestionDB[("questiondb (PostgreSQL)")]
    end

    %% Client entry
    Client -->|"Calls http://localhost:8765/..."| Gateway
    
    %% Gateway resolution
    Gateway -.->|"1. Where is quiz-service?"| Eureka
    Gateway -->|"2. Forward request to Port 8090"| QuizService
    
    %% Internal Feign communication (Direct, No Gateway!)
    QuizService -.->|"3. Feign asks: Where is QUESTION-SERVICE?"| Eureka
    QuizService == "4. Direct REST Call via Feign (No Gateway!)" ==> QuestionService
    
    %% DB connections
    QuizService -->|"Saves/Reads Quiz Meta & IDs"| QuizDB
    QuestionService -->|"Saves/Reads Questions & Answers"| QuestionDB
```

---

## 3. Detailed Workflows

### Workflow 1: Creating a Quiz (`POST /quiz/create`)

When an administrator or user requests the creation of a quiz:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Outside Client
    participant GW as API Gateway (8765)
    participant Eureka as Eureka Registry (8761)
    participant Quiz as Quiz Service (8090)
    participant Feign as Feign Client (QuizInterface)
    participant Question as Question Service
    participant QDB as Question DB
    participant QuizDB as Quiz DB

    Client->>GW: POST /quiz-service/quiz/create
    Note over Client,GW: JSON: {categoryName: "Java", numQuestions: 5, title: "Java Basics"}
    
    GW->>Eureka: Lookup "QUIZ-SERVICE"
    Eureka-->>GW: IP:Port of Quiz Service (e.g., localhost:8090)
    GW->>Quiz: POST /quiz/create
    
    Note over Quiz,Question: === Direct East-West Call (Bypasses Gateway) ===
    Quiz->>Feign: getQuestionsForQuiz("Java", 5)
    Feign->>Eureka: Lookup "QUESTION-SERVICE"
    Eureka-->>Feign: IP:Port of Question Service
    Feign->>Question: GET /question/generate?categoryName=Java&numQuestions=5
    
    Question->>QDB: Query 5 random IDs WHERE category = 'Java'
    QDB-->>Question: [3, 7, 12, 19, 24]
    Question-->>Feign: HTTP 200: [3, 7, 12, 19, 24]
    Feign-->>Quiz: List<Integer> questionIds
    
    Quiz->>QuizDB: INSERT INTO quiz (title, question_ids) VALUES ('Java Basics', [3,7,12,19,24])
    QuizDB-->>Quiz: Saved
    
    Quiz-->>GW: HTTP 201 Created ("Success")
    GW-->>Client: HTTP 201 Created ("Success")
```

---

### Workflow 2: Getting Quiz Questions to Play (`POST /quiz/get/{id}`)

When a student starts a quiz, the quiz questions must be shown **without** revealing the correct answers.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Outside Client
    participant GW as API Gateway
    participant Quiz as Quiz Service
    participant QuizDB as Quiz DB
    participant Feign as Feign Client
    participant Question as Question Service
    participant QDB as Question DB

    Client->>GW: POST /quiz-service/quiz/get/1
    GW->>Quiz: POST /quiz/get/1
    
    Quiz->>QuizDB: SELECT question_ids FROM quiz WHERE id = 1
    QuizDB-->>Quiz: [3, 7, 12, 19, 24]
    
    Note over Quiz,Question: === Direct East-West Call (No Gateway) ===
    Quiz->>Feign: getQuestionsFromId([3, 7, 12, 19, 24])
    Feign->>Question: POST /question/getQuestions (Body: [3, 7, 12, 19, 24])
    
    Question->>QDB: SELECT * FROM question WHERE id IN (3, 7, 12, 19, 24)
    QDB-->>Question: Questions with correct answers
    Note over Question: Strips "right_answer" and wraps into QuestionWrapper
    Question-->>Feign: List<QuestionWrapper> (ID, title, option1..option4)
    Feign-->>Quiz: List<QuestionWrapper>
    
    Quiz-->>GW: HTTP 200: List<QuestionWrapper>
    GW-->>Client: HTTP 200: List<QuestionWrapper>
```

---

### Workflow 3: Submitting Answers and Getting the Score (`POST /quiz/submit/{id}`)

`quiz-service` does not know what the correct answers are. It delegates scoring to `question-service`.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Outside Client
    participant GW as API Gateway
    participant Quiz as Quiz Service
    participant Feign as Feign Client
    participant Question as Question Service
    participant QDB as Question DB

    Client->>GW: POST /quiz-service/quiz/submit/1
    Note over Client,GW: Responses: [{id: 3, response: "JVM"}, {id: 7, response: "Bytecode"}, ...]
    
    GW->>Quiz: POST /quiz/submit/1
    
    Note over Quiz,Question: === Direct East-West Call (No Gateway) ===
    Quiz->>Feign: getScore(responses)
    Feign->>Question: POST /question/getScore (Body: responses)
    
    Question->>QDB: Fetch right answers for IDs [3, 7, ...]
    QDB-->>Question: Correct answers
    Note over Question: Compares user answers with database right_answer
    Question-->>Feign: HTTP 200: score (e.g. 4)
    Feign-->>Quiz: score = 4
    
    Quiz-->>GW: HTTP 200: 4
    GW-->>Client: HTTP 200: 4
```

---

## 4. Code Implementation Summary

### Feign Interface in Quiz Service (`quiz-service`)
File: `quiz-service/src/main/java/com/amine/quizservice/feign/QuizInterface.java`

```java
@FeignClient("QUESTION-SERVICE") // Eureka service ID resolved directly
public interface QuizInterface {

    @GetMapping("question/generate")
    public ResponseEntity<List<Integer>> getQuestionsForQuiz(
            @RequestParam String categoryName, 
            @RequestParam Integer numQuestions);

    @PostMapping("question/getQuestions")
    public ResponseEntity<List<QuestionWrapper>> getQuestionsFromId(
            @RequestBody List<Integer> questionIds);

    @PostMapping("question/getScore")
    public ResponseEntity<Integer> getScore(
            @RequestBody List<Response> responses);
}
```

### Destination Controller in Question Service (`question-service`)
File: `question-service/src/main/java/com/amine/questionservice/controller/QuestionController.java`

- `GET question/generate` $\rightarrow$ Generates random question IDs.
- `POST question/getQuestions` $\rightarrow$ Returns question objects without answers (`QuestionWrapper`).
- `POST question/getScore` $\rightarrow$ Compares submitted answers against correct answers and returns score.

---

## 5. Why the Gateway is Bypassed for Internal Calls

1. **Lower Latency**: Calling directly cuts out extra network hops and serialization through the gateway.
2. **Prevent Gateway Bottlenecks**: The gateway focuses on routing external traffic and rate-limiting. Flooding it with internal calls degrades overall system performance.
3. **Decentralized Load Balancing**: Feign uses Eureka's local cache on `quiz-service` to pick an available instance of `question-service` directly.

---

## 6. How to Run Locally

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

## 7. Testing APIs with Postman & cURL

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
