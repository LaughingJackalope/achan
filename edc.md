# Project Plan: Event-Driven Cognitive Architecture

## 1. Vision

To create a "shared cognitive space" using an event-driven architecture with Kafka as its backbone. This system allows multiple, independent "agents" to collaborate on processing user intents, enabling a more robust, scalable, and eventually-consistent approach to generating intelligent responses.

## 2. Phase 1: Foundational Implementation (Completed)

This phase focused on building the core infrastructure and a complete, end-to-end processing pipeline. All work has been committed to the repository.

### 2.1. Core Event-Driven Infrastructure
*   **Deliverable:** Kafka topics, producers, and consumers for the cognitive event loop.
*   **Details:**
    *   Defined Kafka topics: `intent.declared`, `context.materialized`, `act.proposed`, `act.committed`, `act.failed`.
    *   Implemented `CognitiveEventProducerService` to emit events.
    *   Implemented `CognitiveEventConsumer` as the host for our agent logic.
*   **Status:** Completed.

### 2.2. Cognitive Event Schema
*   **Deliverable:** A clear, typed schema for all events in the system.
*   **Details:**
    *   Created `CognitiveEvent.kt` with a sealed interface to define the event vocabulary.
    *   Defined data classes for each stage: `IntentDeclared`, `ContextMaterialized`, `ActProposed`, `ActCommitted`, `ActFailed`.
*   **Status:** Completed.

### 2.3. Initial Agent Implementations
*   **Deliverable:** A functional, end-to-end chain of agents to process an intent.
*   **Details:**
    *   **Context Agent (`consumeIntentDeclared`):** Uses the existing `SearchService` to perform semantic search and find relevant context for a given intent.
    *   **LLM Agent (`consumeContextMaterialized`):** Constructs a detailed prompt from the intent and the materialized context, then uses `OllamaService` to generate a proposed answer.
    *   **Decider Agent (`consumeActProposed`):** A simple but effective "first proposal wins" implementation to move the process forward.
    *   **Persistence Agent (`consumeActCommitted`):** Persists the final, chosen answer to the database.
*   **Status:** Completed.

### 2.4. API Facade (`/v1/responses`)
*   **Deliverable:** An asynchronous, RESTful API for interacting with the cognitive system.
*   **Details:**
    *   `POST /v1/responses`: An endpoint to submit a new intent, which immediately returns a `202 Accepted` status with a unique `intentId`.
    *   `GET /v1/responses/{intentId}`: An endpoint to poll for the final, committed result using the `intentId`.
*   **Status:** Completed.

### 2.5. Data Persistence
*   **Deliverable:** Database schema and entity for storing final results.
*   **Details:**
    *   Created a `committed_acts` table via a Flyway migration (`V6__add_committed_acts.sql`) to store all final answers.
    *   Created the `CommittedAct` Panache entity for easy database interaction.
*   **Status:** Completed.

## 3. Phase 2: Refinement and Expansion

This phase will build upon the foundation, enhancing the system's intelligence, robustness, and user experience.

### 3.1. Advanced Decider Agent
*   **Objective:** Evolve from a "first proposal wins" model to a more intelligent decider that can handle multiple competing proposals.
*   **Tasks:**
    *   Design a mechanism for the decider to collect multiple proposals for a single intent (e.g., using a time-based window or a temporary store like Redis).
    *   Implement ranking logic based on proposal `confidence`, `priority`, or other custom metrics.
    *   Explore using an LLM call to evaluate, synthesize, or merge the best parts of multiple proposals into a superior final answer.

### 3.2. Multi-Agent System
*   **Objective:** Expand the system with a variety of specialized "proposer" agents that can work in parallel.
*   **Tasks:**
    *   Create a "Summarization Agent" that always proposes a concise summary, competing with the main answer-generating agent.
    *   Develop a "Fact-Checking Agent" that validates claims in other proposals against the provided context.
    *   Build a "Tool-Using Agent" that can propose actions other than generating text (e.g., calling an external API). This will require expanding the `ActProposed` schema to support structured actions.

### 3.3. Real-Time User Feedback
*   **Objective:** Replace the polling-based API with a real-time push mechanism for a better user experience.
*   **Tasks:**
    *   Implement a WebSocket endpoint that clients can subscribe to using their `intentId`.
    *   Update the `consumeActCommitted` logic to push the final result through the WebSocket to the subscribed client as soon as it's persisted.

### 3.4. Robust Error Handling and Monitoring
*   **Objective:** Make the distributed system more resilient and observable.
*   **Tasks:**
    *   Implement the `consumeActFailed` consumer to log detailed error information to a persistent store (e.g., the existing `failed_events` table).
    *   Design and implement a retry mechanism with backoff for transient failures in any consumer.
    *   Integrate distributed tracing across the entire Kafka event chain to visualize the flow and identify bottlenecks.

### 3.5. Prompt and Model Engineering
*   **Objective:** Continuously improve the quality and relevance of the LLM-generated responses.
*   **Tasks:**
    *   Systematically experiment with different prompt templates in the `LLM Agent`.
    *   Evaluate and benchmark different LLM models available through `OllamaService` for this specific task.
    *   Explore the possibility of fine-tuning a smaller, specialized model on high-quality question/answer pairs generated by this system.
