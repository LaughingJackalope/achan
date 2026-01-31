# ADR 002: Sealed Interfaces for Domain Modeling and Functional Error Handling

## Status
**PROPOSED** - 2026-01-04

## Context

Our current codebase, while functional, relies on primitive types (e.g., `String`, `Long`) for domain-specific identifiers and concepts. For instance, a `ThreadId` and a user-provided `URL` may both be represented as `String`. This practice, often called "primitive obsession," can lead to subtle bugs where incorrect data is passed to functions, a risk that is magnified when working with AI-assisted development.

Furthermore, our primary mechanism for handling expected business rule failures (e.g., an invalid URL, a non-existent entity) is through exceptions. While effective, this approach can obscure the expected outcomes of a function and requires `try-catch` blocks for control flow, which can be verbose and less discoverable than type-based systems.

This proposal is inspired by the principles outlined in `semisolicited_advice.md`, which advocates for leveraging Kotlin's type system to build more robust and self-documenting software.

## Decision Drivers

1.  **Compile-Time Safety**: We want to catch logical errors at compile time, not in production. The type system should prevent us from passing a `UserId` where an `Email` is expected.
2.  **Expressive Domain Models**: Our code should be a clear reflection of our business domain. Types should carry business meaning, making the codebase easier to understand and maintain.
3.  **Clarity of Intent**: A function's signature should explicitly declare all its possible outcomes, both success and failure. This makes code more predictable and self-documenting.
4.  **Developer/Agent Velocity**: A strongly-typed, explicit system reduces the cognitive load on developers and minimizes the risk of "hallucination" or misuse by AI agents. It provides clear guardrails that guide development.
5.  **Robust Error Handling**: We need a structured, composable way to handle expected failures without resorting to exceptions for non-exceptional logic.

## Options Considered

### Option A: Status Quo
Continue using primitive types and standard exception handling.

- **Pros:** No upfront cost; developers are already familiar with the pattern.
- **Cons:** Retains the risks of runtime errors from type confusion and relies on documentation or implementation-diving to understand a function's potential failures.

### Option B: Adopt Value Classes and Sealed Interfaces (Recommended)
Incrementally adopt `value class` for strong, zero-cost typing of identifiers and simple domain values. Concurrently, use `sealed interface` to represent the results of operations that can have multiple, predictable outcomes (including failures).

- **Pros:**
    - Drastically improves type safety at compile time (Driver 1).
    - Creates highly expressive and self-documenting domain models (Driver 2, 3).
    - Provides clear, compiler-enforced guardrails for developers and AI agents (Driver 4).
    - Establishes a clean, functional pattern for error handling (Driver 5).
    - Aligns with the "sweet spot" of safety and clarity as noted in project documentation.
- **Cons:**
    - Requires a small learning curve for team members unfamiliar with these patterns.
    - Involves a minor upfront time investment to refactor existing code or apply to new code.

### Option C: Full Adoption of Arrow Library Now
Immediately introduce the `arrow-kt` functional programming library to use types like `Either<L, R>` and `Option<T>` for all error handling and optionality.

- **Pros:** Provides a powerful, battle-tested, and mathematically rigorous toolkit for functional programming.
- **Cons:** Introduces a significant new dependency and a steeper learning curve. May be overkill for our current needs, leading to "over-engineering" and increased code verbosity where simpler patterns would suffice.

## Decision

**We choose Option B: Adopt Value Classes and Sealed Interfaces.**

### Rationale

This decision provides the most significant return on investment in terms of code safety and clarity for the lowest adoption cost. It directly addresses all decision drivers by leveraging native Kotlin features that are relatively easy to understand and apply.

It strikes a pragmatic balance: we gain the vast majority of the benefits of a functional approach to domain modeling and error handling without immediately committing to the complexity and learning curve of a full functional library like Arrow. This approach also serves as a natural stepping stone, allowing the team to become comfortable with functional concepts before deciding if a more powerful library is warranted.

### Implementation Plan

#### Phase 1: Initial Adoption in New Code
1.  **Target**: The next significant feature or refactoring effort (e.g., Post creation/modification).
2.  **Action**:
    - Define `value class`es for all relevant identifiers and simple value-objects (e.g., `PostId`, `ThreadId`, `NormalizedUrl`).
    - Refactor the primary service function (e.g., `PostService.createPost`) to return a `sealed interface` representing the outcome (e.g., `CreatePostResult`).
    - The sealed interface will have `data class Success(...)` and `data object/class FailureReason` subtypes.
    - The corresponding API Resource (`PostResource`) will use a `when` expression on the result to map outcomes to appropriate HTTP status codes (e.g., 201 Created, 400 Bad Request).

#### Phase 2: Gradual Refactoring & Convention Setting
1.  **Action**: As existing code is touched for bug fixes or enhancements, apply these patterns opportunistically.
2.  **Documentation**: Create a small section in `README.dev.md` or a new `CONTRIBUTING.md` to codify the convention:
    - **Use `Result`-like sealed interfaces for expected business failures.** (e.g., validation errors, entity not found).
    - **Reserve exceptions for unexpected, catastrophic failures.** (e.g., database connection lost, infrastructure issue).

#### Phase 3: Evaluate Arrow Library Adoption
1.  **Trigger**: This phase is initiated when we observe that our manual sealed `Result` types are becoming cumbersome. Specific anti-patterns to look for include:
    - Deeply nested `when` expressions to chain multiple fallible operations.
    - Manually writing `flatMap`-like logic to pass a success value from one function to the next.
    - A desire for more advanced functional composition (e.g., `traverse`, `sequence`).
2.  **Action**: When the trigger condition is met, conduct a time-boxed proof-of-concept (1-2 days) to refactor a complex business flow using Arrow's `Either` and the `either { ... }` computation block.
3.  **Decision**: Based on the PoC's outcome, make a formal decision on whether to add `arrow-kt-core` as a project dependency.

## Consequences

### Positive
- ✅ Codebase becomes significantly more robust and resistant to common bugs.
- ✅ Increased developer and agent productivity due to clearer, self-documenting code.
- ✅ Compiler enforces that all outcomes of a business operation are handled.
- ✅ Establishes a solid foundation for more advanced functional patterns in the future, if needed.

### Negative
- ❌ An initial, minor time investment is required for team members to learn and apply the patterns.
- ❌ Can lead to a temporary increase in the number of types/files in the project.

### Neutral
- This is a purely stylistic and architectural change with no expected impact on runtime performance (`value class`es are zero-cost abstractions).

## Follow-up Decisions Required

1.  **Initial Bounded Context**: Formal agreement on the first service/domain area to apply these patterns.
2.  **Convention Details**: Precise team-wide agreement on the line between an expected failure (`Result`) and an exceptional one (`Exception`).

## Business Case: Strategic Alignment and Risk Mitigation
The shift toward Value Classes and Sealed Interfaces is a strategic investment in the "correctness-by-design" philosophy. By replacing generic primitive types with explicit domain models, we effectively codify business rules within the compiler itself, transforming potential runtime failures into immediate build-time errors. This creates a high-integrity environment for agentic coding; AI agents no longer have to "guess" the context of a string or long, but are instead guided by strict type constraints that provide clear guardrails and reduce the risk of logic hallucinations.

From a lifecycle perspective, this approach minimizes technical debt by addressing "primitive obsession" before it permeates the entire architecture. While there is a marginal upfront cost in setup and learning, the return on investment is realized through significantly lower debugging costs, faster onboarding of both human and AI developers, and the elimination of the "migration fatigue" that occurs when trying to refactor a loosely-typed system later in the product lifecycle. This decision prioritizes a robust, self-documenting foundation that scales with the complexity of the business domain without sacrificing development velocity.

To ensure our AI agents and developers handle failures consistently, we need to distinguish between **Expected Outcomes** (modeled via Sealed Interfaces) and **Exceptional Circumstances** (handled via standard Exceptions).

### Categorizing Failures for ADR 002

This framework provides the "logic gates" for the implementation plan outlined in Phase 2:

| Category | Description | Implementation (Kotlin) | Examples |
| --- | --- | --- | --- |
| **Expected** (Business Logic) | Anticipated scenarios where the business rule cannot be satisfied. These are part of the normal API flow. | **Sealed Interface** (e.g., `data class Failure(val reason: Reason)`) | `InvalidUrlFormat`, `ThreadNotFound`, `DuplicatePost`, `RateLimitExceeded`. |
| **Exceptional** (Infrastructure) | Catastrophic or unpredictable failures that prevent the system from functioning as designed. | **Standard Exceptions** (e.g., `RuntimeException`, `IOException`) | `DatabaseConnectionLost`, `KafkaBrokerUnavailable`, `OutOfMemoryError`, `DiskFull`. |

---

### Implementation Guidelines for Agents

To guide the agents during "Phase 2: Gradual Refactoring", we can establish these specific rules:

1. **Exhaustive Handling**: Agents must use a `when` expression on any function returning a `sealed interface`. The compiler will flag a "non-exhaustive" error if the agent forgets to handle a specific business failure, such as a missing metadata field.
2. **Zero-Guessing Identifiers**: When an agent sees a `ThreadId` instead of a `String`, it should be programmed to recognize that this value has already been validated and normalized. It does not need to re-verify the format, reducing redundant code.
3. **Clean Boundary**: The `chan-api` resource layer should be the final point of translation. It maps `Success` to `200/201` and `FailureReason` to specific `4xx` codes, while allowing `Exceptions` to bubble up to a global `500` error handler.

### Rationale for Agents

As noted in the decision drivers, this approach reduces the cognitive load on AI agents and minimizes "hallucinations" by providing clear, type-enforced guardrails. It aligns with the project's goal of building a robust foundation that avoids future technical debt.

