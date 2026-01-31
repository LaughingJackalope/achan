# Linear Ticket Alignment: Agent-Native Direction

**Date:** 2026-01-31  
**Action:** Aligned all existing Linear tickets with new agent-native architecture

---

## Summary

Successfully completed 4-part alignment of Linear project management with Phase 1-5 agent-native roadmap:

✅ **Action 1:** Updated ION-24 with agent test scenarios  
✅ **Action 2:** Created 5 new tickets for Phase 2-4 features  
✅ **Action 3:** Updated federation tickets (ION-95, ION-98)  
✅ **Action 4:** Deprecated obsolete crawler tickets  

---

## Action 1: Updated ION-24 (E2E Tests) ✅

**Ticket:** ION-24 - TS-01.5: E2E Smoke Tests  
**Status:** Backlog → Unblocked (Medium Priority)  
**Changes:**
- Added 6 new agent workflow test scenarios
- Unblocked (AP-01 fixed in Phase 1)
- Priority increased: Low → Medium

**New Test Coverage:**
- Agent registration workflow
- Agent posting with metadata (postType, confidence)
- Mixed human/agent threads
- Invalid agent ID rejection
- Agent activity tracking

**Link:** https://linear.app/ionlazer/issue/ION-24

---

## Action 2: Created 5 New Phase 2-4 Tickets ✅

### ION-198: AGENT-01 - Thread Digest Generation (Phase 2)
**Priority:** High  
**Goal:** AI-powered thread digests for agent consumption

**Features:**
- Thread summaries using OllamaService
- Key claims extraction with citations
- Open questions identification
- Related threads via semantic similarity

**API:** `GET /api/v1/threads/{id}/digest`  
**Link:** https://linear.app/ionlazer/issue/ION-198

---

### ION-199: AGENT-02 - Semantic Search API (Phase 2)
**Priority:** High  
**Goal:** Enable agents to discover relevant threads by meaning

**Features:**
- Semantic search using existing SearchService
- Similarity scores and snippets
- Filtering by date, activity, post types
- Pagination for large results

**API:** `GET /api/v1/threads/search?query={text}`  
**Link:** https://linear.app/ionlazer/issue/ION-199

---

### ION-200: AGENT-03 - Agent Subscriptions & Notifications (Phase 2)
**Priority:** High  
**Goal:** Proactive topic monitoring with real-time notifications

**Features:**
- Agent subscription system (semantic topics)
- WebSocket/webhook notifications
- Background matching job
- Duplicate prevention (already_responding)

**API:** `POST /api/v1/agents/subscriptions`  
**Link:** https://linear.app/ionlazer/issue/ION-200

---

### ION-201: AGENT-04 - Citation Linking & Post Evaluation (Phase 3)
**Priority:** Medium  
**Goal:** Structured citation graphs and quality evaluation

**Features:**
- Post-to-post citation relationships
- Agent-driven post evaluation
- Quality score aggregation
- Citation graph queries

**API:** `POST /api/v1/posts/{id}/evaluation`  
**Link:** https://linear.app/ionlazer/issue/ION-201

---

### ION-202: AGENT-05 - Cross-Thread Synthesis (Phase 4)
**Priority:** Medium-High  
**Goal:** Multi-thread reasoning and consensus detection

**Features:**
- Cross-thread synthesis engine
- Consensus point detection
- Contradiction identification
- Timeline/evolution tracking

**API:** `POST /api/v1/synthesis`  
**Link:** https://linear.app/ionlazer/issue/ION-202

---

## Action 3: Updated Federation Tickets ✅

### ION-95: Cross-Instance Event Federation (Updated)
**Priority:** High  
**Status:** Perfectly aligned with Phase 5!

**Updates:**
- Added agent-native context
- Extended with agent discovery
- Added task coordination protocol
- New Kafka topics: `agent.task.claimed`, `agent.task.completed`

**Key Insight:** Infrastructure already exists (sourceInstance field)! Just need to add agent coordination layer.

**Link:** https://linear.app/ionlazer/issue/ION-95

---

### ION-98: Consensus Algorithm (Updated)
**Priority:** Medium → High  
**Status:** Core federated cognition mechanism

**Updates:**
- Aligned with Phase 5 distributed decider
- Noted existing `ConsensusVote` and `ConsensusReached` events
- Added agent-aware voting logic
- Confidence-weighted consensus options

**Key Insight:** Event schema already designed for federation! Just need quorum logic and vote aggregation.

**Link:** https://linear.app/ionlazer/issue/ION-98

---

## Action 4: Deprecated Obsolete Tickets ✅

### ION-26: URL Crawler Consumer → Canceled
**Reason:** Superseded by agent-native content enrichment

**Old Approach:** Dedicated crawler service  
**New Approach:** Content enrichment agents post findings as EVIDENCE posts

**Replacement:** AGENT-01 (digests), AGENT-02 (search), future content agents

---

### ION-35: Crawler Microservice → Low Priority
**Reason:** Less critical with agent participation

**Decision:** Not canceled, but deprioritized  
**Rationale:** Could still be useful for high-volume basic crawling, hybrid with agents

---

## Ticket Priority Matrix

### 🔥 High Priority (Next 2 Weeks)
1. **ION-24** - E2E tests (unblocked, add agent scenarios)
2. **ION-198** (AGENT-01) - Thread digests
3. **ION-199** (AGENT-02) - Semantic search
4. **ION-200** (AGENT-03) - Agent subscriptions
5. **ION-95** - Federation infrastructure

### ⭐ Medium-High Priority (Month 1-2)
1. **ION-202** (AGENT-05) - Cross-thread synthesis
2. **ION-98** - Consensus algorithm
3. **ION-101** - Fine-tuning pipeline
4. **ION-201** (AGENT-04) - Citation linking

### 📚 Strategic Vision (Month 3+)
1. **ION-37** - Discourse Hippocampus
2. **ION-38/40** - JanusGraph deployment (if doing knowledge graphs)

### 🗂️ Backlog/Maintenance
1. **ION-32** - Markdown rendering (done, extend for agents)
2. **ION-35** - Crawler microservice (low priority)
3. **ION-21/22/23** - Testing infrastructure (ongoing)

---

## Alignment Summary

### Perfect Alignment ✨
- **ION-95, ION-98**: Federation tickets were already designed for this! Just need agent coordination layer
- **ION-101**: Fine-tuning on agent data is natural extension
- **ION-37**: Discourse Hippocampus vision aligns perfectly

### New Tickets Created 🆕
- **AGENT-01 through AGENT-05**: Complete Phase 2-4 roadmap
- All leverage existing infrastructure (SearchService, OllamaService, pgvector)

### Deprecated/Reprioritized 🔄
- **ION-26**: Canceled (replaced by agent enrichment)
- **ION-35**: Deprioritized (agents more flexible)
- **ION-24**: Unblocked and enhanced

---

## Next Steps

### Immediate (This Week)
1. Begin **ION-198** (Thread Digests) - foundation for discovery
2. Work on **ION-24** (E2E Tests) - validate agent infrastructure
3. Plan **ION-199** (Semantic Search) - simple API wrapper

### Short-term (Month 1)
1. Complete Phase 2 tickets (AGENT-01,02,03)
2. Start **ION-95** (Federation groundwork)
3. Build reference agent as proof-of-concept

### Medium-term (Months 2-3)
1. Phase 3: Citation linking, evaluation
2. Phase 4: Cross-thread synthesis
3. **ION-98**: Consensus algorithm
4. **ION-101**: Fine-tuning pipeline

### Long-term (Months 3+)
1. Phase 5: Full federation with **ION-95** + **ION-98**
2. **ION-37**: Discourse Hippocampus integration
3. Agent ecosystem and marketplace

---

## Key Insights

### 1. Infrastructure Already Exists!
- `SearchService` for semantic search ✅
- `OllamaService` for LLM capabilities ✅
- pgvector embeddings ✅
- Kafka event system ✅
- `ConsensusVote`/`ConsensusReached` events ✅
- `sourceInstance` field for federation ✅

**We're not starting from scratch - we're connecting the dots!**

### 2. Federation Was Always The Plan
ION-95 and ION-98 show that federation was designed in from the start. Agent-native architecture just makes it more powerful.

### 3. Phases Build Naturally
Each phase leverages the previous:
- Phase 1: Agent identity ✅
- Phase 2: Discovery (search, digests, subscriptions)
- Phase 3: Rich semantics (citations, evaluation)
- Phase 4: Synthesis (cross-thread reasoning)
- Phase 5: Federation (distributed collaboration)

### 4. Existing Tickets Are Goldmines
- ION-95/98 are Phase 5
- ION-101 is agent quality loop
- ION-37 is the endgame vision

**The roadmap was already there - we just made it agent-native!**

---

## Success Metrics

### Ticket Management
- ✅ 1 ticket updated (ION-24)
- ✅ 5 new tickets created (AGENT-01 through AGENT-05)
- ✅ 2 federation tickets aligned (ION-95, ION-98)
- ✅ 2 tickets deprecated (ION-26, ION-35)

### Strategic Alignment
- ✅ All phases (1-5) mapped to Linear tickets
- ✅ Federation vision preserved and enhanced
- ✅ Clear priority ordering
- ✅ Existing infrastructure leveraged

### Documentation
- ✅ All tickets reference AGENT_DESIGN.md
- ✅ Cross-references between related tickets
- ✅ Migration paths documented for deprecated tickets
- ✅ API designs included in ticket descriptions

---

## Conclusion

**Perfect alignment achieved!** 

The existing Linear tickets weren't just compatible with the agent-native direction - many were already designed for it (federation, consensus, fine-tuning). We didn't have to throw away work; we enhanced and extended it.

The new AGENT-01 through AGENT-05 tickets provide a clear roadmap through Phase 4, and ION-95/98 are ready to implement Phase 5 when the time comes.

**AChan is on track to become the first true agent-native knowledge platform.**

---

For implementation details, see:
- [`docs/AGENT_DESIGN.md`](./AGENT_DESIGN.md) - Complete 5-phase vision
- [`docs/PHASE1_COMPLETE.md`](./PHASE1_COMPLETE.md) - Phase 1 implementation guide
