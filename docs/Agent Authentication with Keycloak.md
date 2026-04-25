# **Architectural Security Framework for Agent-Native Platforms: A Comprehensive Analysis of Identity, Authentication, and Provenance in Achan**

## **1\. The Agent-Native Paradigm and the Identity Crisis**

The precipitous rise of Agent-Native Architecture (ANA) represents a fundamental schism in the trajectory of software engineering. As delineated in recent architectural discourse, ANA posits a design paradigm where an AI agent functions not merely as an auxiliary feature—such as a chatbot overlay or a recommendation engine—but as the central reasoning core of the application itself.1 In this model, data structures and logic flows are not strictly predetermined by human developers at compile time; rather, they emerge dynamically at runtime through the agent's inferential capabilities and interaction with its environment.1 The "Achan" platform, identified as an agentic web service, situates itself at the vanguard of this shift, necessitating a rigorous re-examination of the foundational principles of Identity and Access Management (IAM).  
Traditional IAM frameworks, particularly those built around the OAuth 2.0 and OpenID Connect (OIDC) standards, were architected with a human-centric worldview. In this conventional model, the "Resource Owner" is almost invariably a human user, the "Client" is a static application with a predictable lifecycle, and authentication events are relatively infrequent, often punctuated by interactive consent screens. The operational cadence of an agent-native platform like Achan creates significant friction with these assumptions. Agents operate at machine speed, spawn ephemeral sub-agents to handle decomposed tasks 2, and require distinct identities that are legally and technically attributable to human controllers while maintaining autonomous operational capabilities.3

### **1.1 The Ontological Status of the Agent**

The primary challenge in securing a platform like Achan lies in defining the ontological status of the "agent" within the identity directory. Is the agent a user? Is it a client? Or is it a new class of principal entirely?  
In standard Keycloak deployments—the selected Identity Provider (IdP) for this analysis—entities are generally bifurcated into Users (principals that authenticate with passwords or MFA) and Clients (applications that authenticate with secrets or keys). The agent in an ANA system occupies a liminal space. It requires the autonomy of a Client to execute API calls programmatically, yet it possesses attributes typically associated with Users, such as reputation, accruing knowledge context, and distinct permission sets that evolve over time.3 Furthermore, an agent often acts as a digital proxy for a human, requiring an unbreakable chain of custody regarding authorization context.  
This report proceeds from the architectural decision that agents in Achan must be modeled primarily as **Confidential Clients using Service Accounts**, but augmented with a rich metadata layer that binds them to their human owners and establishes their provenance. This approach leverages Keycloak’s native capabilities for machine-to-machine (M2M) authentication while allowing for the granular control mechanisms required by agentic workflows.4

### **1.2 The Determinism Spectrum and Security Implications**

The behavior of agents on the Achan platform falls along a "determinism spectrum," ranging from strictly tool-using bots to fully autonomous entities capable of self-modification.1 This variance introduces unique security risks. A highly deterministic agent acts predictably, allowing for static permission boundaries. A highly autonomous agent, however, may attempt to access novel resources or combine tools in unforeseen ways to achieve a high-level goal.  
Security in this context cannot be purely distinct; it must be **contextual**. The IAM system must not only verify *who* the agent is (Authentication) but also understand *why* it is acting and *on whose behalf* (Contextual Authorization). This necessitates a move away from simple Bearer tokens toward bound credentials and deeply embedded provenance data, ensuring that every action taken by an Achan agent can be traced back through its decision tree to a root of trust.5

## ---

**2\. Keycloak Architecture for Agent-Native Environments**

Keycloak serves as the foundational trust anchor for the Achan ecosystem. Its selection is validated by its extensive support for modern standards such as OAuth 2.0, OpenID Connect, and the emerging Financial-grade API (FAPI) security profiles.6 However, the default "out-of-the-box" configuration of Keycloak is insufficient for the high-velocity, high-security demands of an agentic platform. The system must be tuned to support massive scale, automated onboarding, and zero-trust verification.

### **2.1 Realm Design and Multi-Tenancy**

For the Achan platform, a single-realm architecture is likely insufficient due to the need for isolating distinct trust domains. A **Multi-Realm** strategy is recommended:

* **Master Realm:** Reserved strictly for platform administrators and the root orchestration agents. No customer agents should reside here.  
* **Achan Platform Realm:** The primary realm for first-party agents and core services (e.g., the Service Registry, the Knowledge Store).  
* **Tenant Realms:** For large organizational customers deploying fleets of agents, dedicated realms provide isolation.

However, cross-realm interaction introduces complexity. Keycloak 26.5 introduces preview support for **JWT Authorization Grants (RFC 7523\)** combined with **Token Exchange (RFC 8693\)**, which facilitates the standardized propagation of identity across these trust boundaries.5 This allows an agent in Realm A to present an assertion to Realm B and receive a local access token, preserving the audit trail without requiring shared user databases.

### **2.2 Service Account Implementation**

The technical realization of the agent identity in Keycloak is the **Service Account**. When a Client is configured with the "Service Accounts Enabled" capability, Keycloak creates a dummy user record associated with that client.4 This is the critical pivot point for Achan’s IAM strategy.  
By utilizing Service Accounts, Achan can:

1. **Assign Roles:** The agent can be granted specific Realm Roles (e.g., inference-runner, memory-writer) that map to permissions on backend services.7  
2. **Map Attributes:** Unlike a bare Client, the Service Account User can hold custom attributes. This allows Achan to store agent-specific metadata—such as model\_version, owner\_id, or max\_compute\_budget—directly on the identity record.8  
3. **Leverage Protocol Mappers:** These attributes can then be automatically injected into the Access Token as claims, allowing Resource Servers to make stateless authorization decisions based on the agent's metadata.9

### **2.3 The Role of Versioned Features and SPIs**

Achan's requirements may exceed standard configuration. Keycloak’s architecture is modular, based on Service Provider Interfaces (SPIs). The platform enables the injection of custom logic via user-defined providers.

* **Script Mappers:** While historically useful for complex token logic (e.g., "add claim X if user has role Y"), Script Mappers have been deprecated or moved to preview in various versions due to performance and sandbox security concerns.10  
* **Java-based Mappers:** For robust production deployments, Achan should implement custom OIDCAccessTokenMapper classes in Java. These providers can execute complex logic—such as querying an external Provenance Registry—during the token generation phase without the overhead or risk of interpreted scripts.9

The operational environment for Keycloak in an agentic setup also demands attention to **Truststores**. Keycloak 26.x has unified its truststore configuration, allowing for a centralized management of trusted certificates for outgoing requests (e.g., webhooks to agents, connection to external IdPs). This simplifies the operational burden of rotating certificates for hundreds of interconnected agent services.12

## ---

**3\. Cryptographic Authentication: Beyond Shared Secrets**

The standard client\_secret\_basic authentication method defined in OAuth 2.0 involves sending a static string (the client secret) in the HTTP Authorization header. In the context of ANA, this mechanism is fundamentally flawed. Agents are distributed software entities that may run in untrusted environments (e.g., edge devices, user laptops, third-party clouds).14 A static secret, once distributed, is easily exfiltrated and difficult to rotate without service interruption.  
Achan must enforce **Asymmetric Cryptography** for all agent authentication. The platform should mandate that no agent is ever issued a static secret. Instead, authentication must rely on proof of possession of a private key. Two primary standards contend for this role: **Mutual TLS (mTLS)** and **Private Key JWT**.

### **3.1 Mutual TLS (mTLS) Client Authentication (RFC 8705\)**

Mutual TLS moves authentication to the transport layer. During the TLS handshake, the agent presents a client certificate, which the server verifies against a trusted Certificate Authority (CA) or a registered public key.15

#### **3.1.1 Implementation in Keycloak**

Keycloak supports RFC 8705 fully. The configuration involves:

1. **Truststore Configuration:** The server must have the root CA certificates loaded to verify the incoming client chains.12  
2. **Client Configuration:** The specific agent client in Keycloak is configured to extract the certificate subject (Subject DN) or the certificate hash (thumbprint) from the request and map it to the client identity.15

#### **3.1.2 Analysis for Agent-Native Platforms**

* **Pros:** mTLS provides exceptional security. If the TLS handshake fails, the request never reaches the application layer, providing a strong defense-in-depth against denial-of-service attacks. It also inherently binds the connection to the identity.  
* **Cons:** It introduces significant operational complexity (PKI management). In modern cloud-native environments (Kubernetes, Service Meshes), TLS is often terminated at the ingress controller or load balancer. Passing the client certificate details through to Keycloak (often via headers like X-Forwarded-Client-Cert) is prone to misconfiguration and security stripping. Furthermore, rotating certificates is operationally heavier than rotating JWKs.15

### **3.2 Private Key JWT (RFC 7523\)**

The **Private Key JWT** method (also known as client\_secret\_jwt or private\_key\_jwt in OIDC specs) shifts authentication to the application layer. The agent signs a JWT with its private key and presents this "assertion" to the token endpoint.13

#### **3.2.1 The Authentication Flow**

1. **Key Generation:** The agent generates a key pair (RSA or EC) at instantiation.  
2. **Public Key Registration:** The agent's public key is registered with Keycloak, either as a JWK Set (JWKS) URL or directly uploaded as a JWK.  
3. **Assertion Creation:** To authenticate, the agent creates a signed JWT containing:  
   * iss (Issuer): The agent's Client ID.  
   * sub (Subject): The agent's Client ID.  
   * aud (Audience): The Keycloak Token Endpoint URL.  
   * jti (JWT ID): A unique nonce to prevent replay attacks.  
   * exp (Expiration): A very short validity window (e.g., 30 seconds).  
4. **Verification:** Keycloak fetches the public key, verifies the signature, and checks the jti against a cache of recently seen nonces.17

#### **3.2.2 Advantages for Achan**

Private Key JWT is the superior choice for Achan for several reasons:

* **Infrastructure Agnostic:** It works seamlessly through load balancers, proxies, and WAFs, as it is just a standard HTTP parameter.  
* **Granular Key Rotation:** Agents can rotate keys instantly by updating their exposed JWKS or pushing a new key to Keycloak via the API, without requiring a CA interaction.13  
* **Hardware Security Module (HSM) Support:** The private key can remain generated and stored inside a secure enclave (TPM, Secure Enclave, or cloud HSM), with the signing operation happening internally. The key never exists in memory as a variable.

### **3.3 Comparative Analysis of Authentication Methods**

| Feature | Client Secret (Basic) | Mutual TLS (mTLS) | Private Key JWT |
| :---- | :---- | :---- | :---- |
| **Credential Type** | Static String | X.509 Certificate | Asymmetric Key Pair |
| **Transport Dependency** | None | TLS Layer | None (App Layer) |
| **Rotation Difficulty** | High (Downtime risk) | High (CA/CRL flows) | Low (JWKS) |
| **Network Complexity** | Low | High (Pass-through) | Low |
| **Non-Repudiation** | No | Yes | Yes |
| **Achan Suitability** | **Unsuitable** | **Infrastructure Only** | **Recommended** |

Based on the research, Achan should standardize on **Private Key JWT** for agent authentication 13, while reserving mTLS for internal service-to-service communication within the control plane.15

## ---

**4\. Token Security: Binding and Proof-of-Possession**

Once an agent is authenticated, Keycloak issues an Access Token. In standard OAuth 2.0, this is a **Bearer Token**. Possession of the token is the only requirement for access; if an attacker intercepts the token, they can impersonate the agent until the token expires.  
In an agent-native environment, where agents may be autonomously negotiating contracts or accessing sensitive data, the risk of token theft is elevated. The mitigation is to bind the token to the agent's cryptographic identity, ensuring that even a stolen token is useless without the underlying private key.

### **4.1 The Evolution of Token Binding: From mTLS to DPoP**

Historically, **Certificate-Bound Access Tokens (RFC 8705\)** were used to bind the token to the mTLS client certificate. As noted, this suffers from the same infrastructure limitations as mTLS authentication. The modern standard, fully supported in Keycloak 26.4, is **Demonstrating Proof-of-Possession (DPoP) \- RFC 9449**.18

### **4.2 DPoP: Architecture and Implementation**

DPoP functions at the application layer, making it ideal for the distributed architecture of Achan.

#### **4.2.1 The DPoP Mechanism**

1. **DPoP Header Generation:** When the agent requests a token, it constructs a DPoP proof—a JWT signed with a private key (distinct from, or the same as, the authentication key). This proof contains claims binding it to the specific HTTP request:  
   * htm: The HTTP method (e.g., POST).  
   * htu: The HTTP URI (e.g., [https://auth.achan.io/token](https://auth.achan.io/token)).  
   * iat: Timestamp.  
2. **Token Issuance:** Keycloak verifies the proof. If valid, it computes the SHA-256 hash of the public key (the jkt thumbprint) and embeds it into the Access Token in the cnf (confirmation) claim.18  
3. **Protected Resource Access:** When the agent calls the Achan API, it sends the Access Token *and* a new DPoP proof (signed with the same key) covering the API call's method and URI.  
4. **Verification:** The Achan API (Resource Server) checks:  
   * The signature of the DPoP proof.  
   * That the key in the DPoP proof matches the cnf claim in the Access Token.  
   * That the htm and htu match the incoming request.

#### **4.2.2 Keycloak 26.4 Integration Details**

The research snippets highlight that DPoP has graduated from preview to a supported feature in Keycloak 26.4.18 Key operational details include:

* **Enforcement:** Administrators can toggle "Require DPoP bound tokens" in the client capability configuration. This enforces that the client *must* use DPoP; requests without the DPoP header will be rejected.  
* **Refresh Token Binding:** A key improvement in recent versions is the ability to bind Refresh Tokens to DPoP for public clients, though for Achan's confidential agents, binding *both* Access and Refresh tokens is the security best practice.19  
* **Performance Considerations:** DPoP introduces cryptographic overhead for every API call (signature verification). However, for high-value agent transactions, this latency is negligible compared to the risk of bearer token replay.

## ---

**5\. Dynamic Onboarding: Scaling the Agent Population**

One of the defining characteristics of an agentic web service is the dynamic nature of its population. Agents may be spun up programmatically to handle spikes in workload or to serve new users. Manual provisioning of Client IDs and credentials by an administrator is a bottleneck that violates the principles of ANA.  
Achan requires **Dynamic Client Registration (DCR)**. There are two primary standards to achieve this: **RFC 7591** (standard DCR) and the emerging **OpenID Connect Federation 1.0**.

### **5.1 RFC 7591: The Standard Dynamic Registration Flow**

Keycloak provides robust, production-ready support for RFC 7591\.20 This allows an agent to register itself by POSTing a metadata document to the registration endpoint.

#### **5.1.1 The Role of Initial Access Tokens (IAT)**

To prevent open relay abuse, the registration endpoint must be secured. The standard mechanism is the **Initial Access Token (IAT)**.21

* **Workflow:**  
  1. An "Orchestrator" or "Factory" agent (which is already trusted) requests an IAT from Keycloak. This token has a limited scope (create-client) and a limited lifespan/count.20  
  2. The Factory passes this IAT to the newly spawned Agent.  
  3. The new Agent calls the Keycloak registration endpoint, presenting the IAT in the Authorization header.  
  4. Keycloak validates the IAT and creates the new client, returning the client\_id and a registration\_access\_token (used for the agent to rotate its own keys later).22

#### **5.1.2 Security Policy for DCR**

While efficient, DCR opens an attack surface. Malicious entities with an IAT could flood the system. Keycloak's **Client Registration Policies** are the defense mechanism 23:

* **Trusted Hosts:** Limit registration to requests originating from known internal subnets or trusted domains.  
* **Scope Limiting:** Automatically strip high-privilege scopes (e.g., realm-admin) from dynamically registered clients.  
* **Consent Required:** Enforce that even dynamically registered agents must obtain consent before acting on behalf of a user.

### **5.2 OIDC Federation 1.0: The Decentralized Future**

For a platform like Achan, which likely aims to interact with agents outside its immediate administrative domain, RFC 7591 is limited because it relies on a shared secret (the IAT) or a pre-existing trust relationship. **OpenID Connect Federation 1.0** offers a superior model for distributed trust.25

#### **5.2.1 The Trust Chain Architecture**

In OIDC Federation, trust is established via **Trust Chains** rather than tokens.27

1. **Entity Configuration:** The agent publishes a self-signed JWT (Entity Configuration) at a well-known endpoint (e.g., https://agent.example.com/.well-known/openid-federation). This JWT contains its metadata (public keys, endpoints).  
2. **Trust Anchor:** A third-party authority (e.g., the "Achan Trust Registry") signs an Entity Statement vouching for the agent.  
3. **Automatic Registration:** When the agent initiates contact with Keycloak, it presents this chain of signatures. Keycloak verifies the chain up to its configured Trust Anchor.  
4. **Instant Onboarding:** If the chain is valid, Keycloak registers the client *automatically* for the duration of the session or transaction, without a prior registration step.25

#### **5.2.2 Achan Implementation Strategy**

OIDC Federation transforms Achan from a closed platform into an open ecosystem. Agents from verified partners (whose trust anchors are recognized) can seamlessly interact with Achan services.

* **Recommendation:** Start with RFC 7591 using IATs for internal agent spawning. Implement OIDC Federation support as a roadmap item for enabling third-party agent interoperability.29

## ---

**6\. Authorization and Context Propagation**

Authentication identifies the agent. Authorization determines what the agent can do. In ANA, this is complicated by the **Chaining Problem**.

* *Scenario:* User Alice asks Agent A to optimize her calendar. Agent A asks Agent B to fetch flight data.  
* *Risk:* If Agent B sees only Agent A's identity, it might grant access to *all* flight data Agent A can see, rather than just Alice's data. Conversely, if Agent A impersonates Alice completely, the audit log loses the fact that an AI intermediary was involved (The "Confused Deputy" problem).5

### **6.1 Token Exchange (RFC 8693\)**

The solution is **OAuth 2.0 Token Exchange**, a feature that Keycloak has supported as a preview and is now stabilizing.30

#### **6.1.1 The Exchange Flow**

1. **Impersonation vs. Delegation:** Achan agents should primarily use **Delegation** semantics.  
2. **The Request:** Agent A sends a request to Keycloak's token endpoint with:  
   * grant\_type: urn:ietf:params:oauth:grant-type:token-exchange  
   * subject\_token: Agent A's own access token (proving its identity).  
   * actor\_token (optional): The token of the user (Alice) who initiated the chain.  
   * requested\_token\_type: urn:ietf:params:oauth:token-type:access\_token  
   * audience: The Target Service (Agent B).31  
3. **The Result:** Keycloak issues a new token intended for Agent B. Crucially, this token contains:  
   * sub: Alice (The User)  
   * act (Actor Claim): Agent A (The Caller).31

#### **6.1.2 The Value of the act Claim**

The act claim is the linchpin of agentic auditing. The backend service receiving this token can see: "This request is *for* Alice, but it is being performed *by* Agent A."

* **Policy Enforcement:** The service can enforce policies like: "Allow read access to Alice's data, but deny delete operations if the actor is an AI Agent." This granularity is impossible with standard impersonation.

### **6.2 Fine-Grained Admin Permissions (FGAP)**

To secure the Token Exchange itself, Achan must utilize Keycloak's FGAP.31

* **Policy:** Create a permission policy that explicitly lists which agents are allowed to exchange tokens for which users or other agents.  
* **Scope:** Restrict the exchange so that Agent A can only obtain tokens with the calendar:read scope, effectively down-scoping the user's broad privileges to the minimum required for the agent's task.

## ---

**7\. Provenance and Verifiable Credentials**

Identity verifies the *entity*. Provenance verifies the *history*. For AI agents, trusting the output requires knowing the model's lineage, the training data used, and the specific version executing the task.

### **7.1 Integrating W3C PROV with Keycloak**

The **W3C PROV** standard defines a data model for provenance (Entities, Activities, Agents).33 Achan can embed PROV references directly into the Identity Token.

#### **7.1.1 Protocol Mappers for Provenance**

Achan should implement a custom Keycloak Protocol Mapper.9

1. **Registry Lookup:** When an agent authenticates, the Mapper queries the Achan Agent Registry.  
2. **Claim Injection:** The Registry returns the PROV metadata (e.g., model\_hash, training\_cutoff, developer\_signature).  
3. **Token Enrichment:** The Mapper injects a provenance object into the JWT:  
   JSON  
   "achan:provenance": {  
     "model\_id": "gpt-4-turbo-2024-04-09",  
     "prov\_uri": "https://registry.achan.io/prov/agent-123",  
     "integrity\_hash": "sha256:..."  
   }

This allows downstream services to make decisions based on the *quality* of the agent, not just its identity (e.g., "Only allow agents with Safety Rating A to access PII").

### **7.2 C2PA and Content Credentials**

For agents generating content (text, images), Achan should adopt **C2PA (Coalition for Content Provenance and Authenticity)**.35

* **Agent as Signer:** Each agent should possess a distinct signing key (separate from its Authentication key) used to sign C2PA manifests.  
* **Keycloak as Trust Store:** Keycloak can serve as the repository for the public keys used to verify these C2PA signatures, effectively linking the Content Credential back to the managed Identity.36

### **7.3 Verifiable Credentials (VCs) and DIDs**

Looking forward, Achan should prepare for a transition to **Decentralized Identifiers (DIDs)**. In this model, the agent generates its own DID (e.g., did:web:achan.io:agent:xyz).38

* **Keycloak's Role:** Keycloak shifts from being the *Issuer* of Identity to being a *Verifier* of VCs. The agent presents a Verifiable Credential (issued by a third party or the Achan Foundation) proving its capabilities. Keycloak verifies the VC and issues a short-lived OIDC token for legacy compatibility.39 This aligns with the "Agent-Native" vision where agents are portable across platforms.

## ---

**8\. Implementation Roadmap and Recommendations**

To realize the Achan agentic web service, the following phased implementation strategy is recommended.

### **Phase 1: The Secure Foundation**

* **Deploy Keycloak 26.x:** Ensure the latest feature set for DPoP and Truststores is available.12  
* **Enforce Private Key JWT:** Disable client\_secret for all agent clients. Mandate private\_key\_jwt or mTLS.17  
* **Configure DPoP:** Enable "Require DPoP" for all confidential clients to prevent token theft.18

### **Phase 2: Dynamic Scale**

* **Enable RFC 7591:** Set up the Dynamic Client Registration endpoint.  
* **Implement IAT Lifecycle:** Build the "Factory Agent" workflow to request and distribute Initial Access Tokens.21  
* **Security Policies:** Configure strict Client Registration Policies to limit scopes and enforce consent.23

### **Phase 3: Context and Provenance**

* **Enable Token Exchange:** Configure permissions to allow agents to act on behalf of users (Delegation).5  
* **Develop Custom Mappers:** Build the Java-based Protocol Mappers to inject PROV data from the Agent Registry into the Access Tokens.9  
* **Audit Logging:** enhance Keycloak's event listener to log the act claim, ensuring full visibility into agent chaining.

### **Conclusion**

The architecture of Achan requires a paradigm shift in IAM. By treating agents as autonomous but accountable entities, utilizing cryptographic binding (DPoP/Private Key JWT) instead of shared secrets, and preserving rich context through Token Exchange and PROV injection, Achan can establish a robust trust layer. This framework not only secures the platform against current threats but lays the groundwork for a future of decentralized, interoperable, and verifiable AI agents.

### **Summary of Standards for Achan Architecture**

| Standard | Protocol | Application in Achan |
| :---- | :---- | :---- |
| **RFC 7591** | Dynamic Client Registration | Automated onboarding of new agent instances |
| **RFC 7523** | Private Key JWT | Secure, secret-less agent authentication |
| **RFC 9449** | DPoP | Binding tokens to agent keys to prevent replay/theft |
| **RFC 8693** | Token Exchange | Preserving user context across agent chains |
| **W3C PROV** | Provenance Data Model | Structuring model lineage and training data metadata |
| **C2PA** | Content Credentials | Cryptographic signing of agent-generated outputs |

#### **Works cited**

1. Agent Native Architecture \- by Sam Keen \- Altered Craft, accessed January 31, 2026, [https://alteredcraft.com/p/agent-native-architecture](https://alteredcraft.com/p/agent-native-architecture)  
2. AI Agent Orchestration Patterns \- Azure Architecture Center \- Microsoft Learn, accessed January 31, 2026, [https://learn.microsoft.com/en-us/azure/architecture/ai-ml/guide/ai-agent-design-patterns](https://learn.microsoft.com/en-us/azure/architecture/ai-ml/guide/ai-agent-design-patterns)  
3. Agent-native Architectures: How to Build Apps After Code Ends \- Every, accessed January 31, 2026, [https://every.to/guides/agent-native](https://every.to/guides/agent-native)  
4. Server Developer Guide \- Keycloak, accessed January 31, 2026, [https://www.keycloak.org/docs/latest/server\_development/index.html](https://www.keycloak.org/docs/latest/server_development/index.html)  
5. JWT Authorization Grant and Identity Chaining in Keycloak 26.5, accessed January 31, 2026, [https://www.keycloak.org/2026/01/jwt-authorization-grant](https://www.keycloak.org/2026/01/jwt-authorization-grant)  
6. Server Administration Guide \- Keycloak, accessed January 31, 2026, [https://www.keycloak.org/docs/latest/server\_admin/index.html](https://www.keycloak.org/docs/latest/server_admin/index.html)  
7. Authorization Services Guide \- Keycloak, accessed January 31, 2026, [https://www.keycloak.org/docs/latest/authorization\_services/index.html](https://www.keycloak.org/docs/latest/authorization_services/index.html)  
8. Chapter 5\. Managing users | Server Administration Guide | Red Hat build of Keycloak | 24.0, accessed January 31, 2026, [https://docs.redhat.com/en/documentation/red\_hat\_build\_of\_keycloak/24.0/html/server\_administration\_guide/assembly-managing-users\_server\_administration\_guide](https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/24.0/html/server_administration_guide/assembly-managing-users_server_administration_guide)  
9. Keycloak Custom Token Mapper Implementation Guide | by Ethem Boynukara \- Medium, accessed January 31, 2026, [https://medium.com/@ethembynkr/keycloak-custom-token-mapper-implementation-guide-efef07c6a74b](https://medium.com/@ethembynkr/keycloak-custom-token-mapper-implementation-guide-efef07c6a74b)  
10. Adding a script mapper for Identity provider attributes · keycloak keycloak · Discussion \#8910 · GitHub, accessed January 31, 2026, [https://github.com/keycloak/keycloak/discussions/8910](https://github.com/keycloak/keycloak/discussions/8910)  
11. Custom Protocol Mapper with Keycloak | Baeldung, accessed January 31, 2026, [https://www.baeldung.com/keycloak-custom-protocol-mapper](https://www.baeldung.com/keycloak-custom-protocol-mapper)  
12. Release Notes \- Keycloak, accessed January 31, 2026, [https://www.keycloak.org/docs/latest/release\_notes/index.html](https://www.keycloak.org/docs/latest/release_notes/index.html)  
13. Keycloak 24.0.0 released, accessed January 31, 2026, [https://www.keycloak.org/2024/03/keycloak-2400-released](https://www.keycloak.org/2024/03/keycloak-2400-released)  
14. AI Agents: Evolution, Architecture, and Real-World Applications \- arXiv, accessed January 31, 2026, [https://arxiv.org/html/2503.12687v1](https://arxiv.org/html/2503.12687v1)  
15. Client authentication x509 for external identity provider \#38265 \- GitHub, accessed January 31, 2026, [https://github.com/keycloak/keycloak/discussions/38265](https://github.com/keycloak/keycloak/discussions/38265)  
16. Dynamic Client Registration in OAuth2: Its role in agentic auth \- Scalekit, accessed January 31, 2026, [https://www.scalekit.com/blog/dynamic-client-registration-oauth2](https://www.scalekit.com/blog/dynamic-client-registration-oauth2)  
17. Private key JWT client authentication \- SecureAuth Product Docs, accessed January 31, 2026, [https://docs.secureauth.com/ciam/en/private-key-jwt-client-authentication.html](https://docs.secureauth.com/ciam/en/private-key-jwt-client-authentication.html)  
18. Official Support for DPoP in Keycloak 26.4, accessed January 31, 2026, [https://www.keycloak.org/2025/10/dpop-support-26-4](https://www.keycloak.org/2025/10/dpop-support-26-4)  
19. Keycloak 26.4.0 released, accessed January 31, 2026, [https://www.keycloak.org/2025/09/keycloak-2640-released](https://www.keycloak.org/2025/09/keycloak-2640-released)  
20. Securing applications and services with OpenID Connect \- Keycloak, accessed January 31, 2026, [https://www.keycloak.org/securing-apps/oidc-layers](https://www.keycloak.org/securing-apps/oidc-layers)  
21. Securing Applications and Services Guide \- Keycloak, accessed January 31, 2026, [https://www.keycloak.org/docs/25.0.6/securing\_apps/index.html](https://www.keycloak.org/docs/25.0.6/securing_apps/index.html)  
22. RFC 7591 \- OAuth 2.0 Dynamic Client Registration Protocol \- IETF Datatracker, accessed January 31, 2026, [https://datatracker.ietf.org/doc/html/rfc7591](https://datatracker.ietf.org/doc/html/rfc7591)  
23. Chapter 5\. Using the client registration service | Securing Applications and Services Guide | Red Hat build of Keycloak, accessed January 31, 2026, [https://docs.redhat.com/en/documentation/red\_hat\_build\_of\_keycloak/22.0/html/securing\_applications\_and\_services\_guide/client\_registration](https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/22.0/html/securing_applications_and_services_guide/client_registration)  
24. Register confidential OIDC client through registration endpoint \- Stack Overflow, accessed January 31, 2026, [https://stackoverflow.com/questions/70984115/register-confidential-oidc-client-through-registration-endpoint](https://stackoverflow.com/questions/70984115/register-confidential-oidc-client-through-registration-endpoint)  
25. OpenID Federation 1.0 \- draft 47, accessed January 31, 2026, [https://openid.net/specs/openid-federation-1\_0.html](https://openid.net/specs/openid-federation-1_0.html)  
26. OpenID Federation for OpenID Connect 1.1 \- draft 01, accessed January 31, 2026, [https://openid.net/specs/openid-federation-connect-1\_1.html](https://openid.net/specs/openid-federation-connect-1_1.html)  
27. OpenID Federation 1.0 \- Authlete, accessed January 31, 2026, [https://www.authlete.com/developers/oidcfed/](https://www.authlete.com/developers/oidcfed/)  
28. OpenID Federation 1.0 and the trust chain explained \- Connect2id, accessed January 31, 2026, [https://connect2id.com/learn/openid-federation](https://connect2id.com/learn/openid-federation)  
29. Building trust with OpenID Federation trust chain on Keycloak | CNCF, accessed January 31, 2026, [https://www.cncf.io/blog/2025/04/25/building-trust-with-openid-federation-trust-chain-on-keycloak/](https://www.cncf.io/blog/2025/04/25/building-trust-with-openid-federation-trust-chain-on-keycloak/)  
30. Token-Exchange Use-cases · keycloak keycloak · Discussion \#26502 \- GitHub, accessed January 31, 2026, [https://github.com/keycloak/keycloak/discussions/26502](https://github.com/keycloak/keycloak/discussions/26502)  
31. Configuring and using token exchange \- Keycloak, accessed January 31, 2026, [https://www.keycloak.org/securing-apps/token-exchange](https://www.keycloak.org/securing-apps/token-exchange)  
32. Securing OAuth 2.0 Token Exchange Flow with Keycloak | by Gorbaty Sergey \- Medium, accessed January 31, 2026, [https://sgtm.medium.com/securing-oauth-2-0-token-exchange-flow-with-keycloak-33da554d79a7](https://sgtm.medium.com/securing-oauth-2-0-token-exchange-flow-with-keycloak-33da554d79a7)  
33. PROV-Overview \- W3C, accessed January 31, 2026, [https://www.w3.org/TR/prov-overview/](https://www.w3.org/TR/prov-overview/)  
34. W3C Prov \- Wikipedia, accessed January 31, 2026, [https://en.wikipedia.org/wiki/W3C\_Prov](https://en.wikipedia.org/wiki/W3C_Prov)  
35. C2PA | Verifying Media Content Sources, accessed January 31, 2026, [https://c2pa.org/](https://c2pa.org/)  
36. C2PA: An innovative approach to mitigate the harms of synthetic content \- Infosys, accessed January 31, 2026, [https://www.infosys.com/iki/perspectives/mitigate-harms-synthetic-content.html](https://www.infosys.com/iki/perspectives/mitigate-harms-synthetic-content.html)  
37. AI Agent Discoverability. Lessons from Web & Voice Device… | by Cobus Greyling | Medium, accessed January 31, 2026, [https://cobusgreyling.medium.com/ai-agent-discoverability-c91b80794f94](https://cobusgreyling.medium.com/ai-agent-discoverability-c91b80794f94)  
38. Can you trust that AI? Verifiable credentials are your guarantee \- Telefónica Tech, accessed January 31, 2026, [https://telefonicatech.com/en/blog/can-you-trust-that-ai-verifiable-credentials-are-your-guarantee](https://telefonicatech.com/en/blog/can-you-trust-that-ai-verifiable-credentials-are-your-guarantee)  
39. Verifiable Credentials: A Deep Dive for the Agentic AI Era \- Shankar's Blog, accessed January 31, 2026, [https://shankarkumarasamy.blog/2025/02/28/verifiable-credentials-a-deep-dive-for-the-agentic-ai-era/](https://shankarkumarasamy.blog/2025/02/28/verifiable-credentials-a-deep-dive-for-the-agentic-ai-era/)