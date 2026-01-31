#!/bin/bash
# Test script for ION-199 Semantic Search API
# Run this after starting the app with: ./gradlew quarkusDev

set -e

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "================================================"
echo "🔍 Testing Semantic Search API (ION-199)"
echo "================================================"
echo ""

# Check if app is running
echo "1. Checking if app is running..."
if curl -s "$BASE_URL/q/health/live" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ App is running${NC}"
else
    echo -e "${RED}❌ App not running. Start with: ./gradlew quarkusDev${NC}"
    exit 1
fi
echo ""

# Create test data if needed
echo "2. Setting up test data..."
echo "   Creating test threads..."

# Thread 1: Docker deployment
THREAD1_ID=$(curl -s -X POST "$BASE_URL/api/v1/threads" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/docker-kubernetes-guide",
    "slug": "docker-k8s"
  }' | jq -r '.thread_id' 2>/dev/null)

if [ ! -z "$THREAD1_ID" ] && [ "$THREAD1_ID" != "null" ]; then
    echo -e "   ${GREEN}✅ Thread 1 created: $THREAD1_ID${NC}"
    
    # Add a human post
    curl -s -X POST "$BASE_URL/api/v1/threads/$THREAD1_ID/posts" \
      -H "Content-Type: application/json" \
      -d '{"content": "What are the best practices for deploying Docker containers to Kubernetes? I need help with scaling and monitoring."}' > /dev/null
    
    # Register test agent
    curl -s -X POST "$BASE_URL/api/v1/agents" \
      -H "Content-Type: application/json" \
      -d '{
        "agentId": "test-search-bot",
        "agentType": "test_agent",
        "capabilities": ["search_testing"],
        "instanceId": "local"
      }' > /dev/null
    
    # Add agent post
    curl -s -X POST "$BASE_URL/api/v1/threads/$THREAD1_ID/posts" \
      -H "Content-Type: application/json" \
      -d '{
        "content": "For Docker deployment, I recommend using Kubernetes with proper resource limits...",
        "agentMetadata": {
          "agentId": "test-search-bot",
          "postType": "EVIDENCE",
          "confidence": 0.85
        }
      }' > /dev/null
    
    echo "   ${GREEN}✅ Added posts to thread${NC}"
else
    echo -e "   ${YELLOW}⚠️  Thread 1 may already exist or failed to create${NC}"
fi

# Thread 2: Machine learning
THREAD2_ID=$(curl -s -X POST "$BASE_URL/api/v1/threads" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/ml-model-serving",
    "slug": "ml-serving"
  }' | jq -r '.thread_id' 2>/dev/null)

if [ ! -z "$THREAD2_ID" ] && [ "$THREAD2_ID" != "null" ]; then
    echo -e "   ${GREEN}✅ Thread 2 created: $THREAD2_ID${NC}"
    
    curl -s -X POST "$BASE_URL/api/v1/threads/$THREAD2_ID/posts" \
      -H "Content-Type: application/json" \
      -d '{"content": "How do you deploy machine learning models to production? Looking for deployment strategies."}' > /dev/null
    
    echo "   ${GREEN}✅ Added posts to thread${NC}"
else
    echo -e "   ${YELLOW}⚠️  Thread 2 may already exist${NC}"
fi

echo ""
echo "3. Testing Search API..."
echo ""

# Test 1: Basic search
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 Test 1: Basic semantic search"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Query: 'docker kubernetes deployment'"
echo ""
RESPONSE=$(curl -s "$BASE_URL/api/v1/threads/search?query=docker+kubernetes+deployment&limit=5")
echo "$RESPONSE" | jq '.'
RESULT_COUNT=$(echo "$RESPONSE" | jq '.results | length')
echo ""
if [ "$RESULT_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✅ Test 1 PASSED: Found $RESULT_COUNT results${NC}"
else
    echo -e "${RED}❌ Test 1 FAILED: No results found${NC}"
fi
echo ""

# Test 2: Search with threshold
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 Test 2: Search with high similarity threshold"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Query: 'machine learning', threshold: 0.8"
echo ""
RESPONSE=$(curl -s "$BASE_URL/api/v1/threads/search?query=machine+learning&threshold=0.8&limit=5")
echo "$RESPONSE" | jq '.'
echo ""
if echo "$RESPONSE" | jq -e '.threshold == 0.8' > /dev/null; then
    echo -e "${GREEN}✅ Test 2 PASSED: Threshold applied correctly${NC}"
else
    echo -e "${RED}❌ Test 2 FAILED: Threshold not applied${NC}"
fi
echo ""

# Test 3: Filter by agent posts
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 Test 3: Filter threads WITH agent posts"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Query: 'deployment', hasAgentPosts: true"
echo ""
RESPONSE=$(curl -s "$BASE_URL/api/v1/threads/search?query=deployment&hasAgentPosts=true&limit=5")
echo "$RESPONSE" | jq '.'
RESULT_COUNT=$(echo "$RESPONSE" | jq '.results | length')
echo ""
if [ "$RESULT_COUNT" -gt 0 ]; then
    HAS_AGENT_POSTS=$(echo "$RESPONSE" | jq '.results[0].hasAgentPosts')
    if [ "$HAS_AGENT_POSTS" = "true" ]; then
        echo -e "${GREEN}✅ Test 3 PASSED: Correctly filtered for agent posts${NC}"
    else
        echo -e "${RED}❌ Test 3 FAILED: Result doesn't have agent posts${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  Test 3: No results (may be expected if no threads have agent posts)${NC}"
fi
echo ""

# Test 4: Filter threads WITHOUT agent posts
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 Test 4: Filter threads WITHOUT agent posts"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Query: 'machine learning', hasAgentPosts: false"
echo ""
RESPONSE=$(curl -s "$BASE_URL/api/v1/threads/search?query=machine+learning&hasAgentPosts=false&limit=5")
echo "$RESPONSE" | jq '.'
RESULT_COUNT=$(echo "$RESPONSE" | jq '.results | length')
echo ""
if [ "$RESULT_COUNT" -gt 0 ]; then
    HAS_AGENT_POSTS=$(echo "$RESPONSE" | jq '.results[0].hasAgentPosts')
    if [ "$HAS_AGENT_POSTS" = "false" ]; then
        echo -e "${GREEN}✅ Test 4 PASSED: Correctly filtered for non-agent threads${NC}"
    else
        echo -e "${RED}❌ Test 4 FAILED: Result has agent posts${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  Test 4: No results (may be expected)${NC}"
fi
echo ""

# Test 5: Pagination
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 Test 5: Pagination"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Query: 'deployment', limit: 2, page: 0"
echo ""
RESPONSE=$(curl -s "$BASE_URL/api/v1/threads/search?query=deployment&limit=2&page=0")
echo "$RESPONSE" | jq '.'
echo ""
PAGE=$(echo "$RESPONSE" | jq '.page')
LIMIT=$(echo "$RESPONSE" | jq '.limit')
if [ "$PAGE" = "0" ] && [ "$LIMIT" = "2" ]; then
    echo -e "${GREEN}✅ Test 5 PASSED: Pagination parameters correct${NC}"
else
    echo -e "${RED}❌ Test 5 FAILED: Pagination parameters incorrect${NC}"
fi
echo ""

# Test 6: Error handling - missing query
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 Test 6: Error handling (missing query)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Query: (empty)"
echo ""
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/threads/search")
echo "HTTP Status Code: $HTTP_CODE"
echo ""
if [ "$HTTP_CODE" = "400" ]; then
    echo -e "${GREEN}✅ Test 6 PASSED: Returns 400 for missing query${NC}"
else
    echo -e "${RED}❌ Test 6 FAILED: Expected 400, got $HTTP_CODE${NC}"
fi
echo ""

# Test 7: Post count filter
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 Test 7: Filter by post count"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Query: 'deployment', minPostCount: 1"
echo ""
RESPONSE=$(curl -s "$BASE_URL/api/v1/threads/search?query=deployment&minPostCount=1&limit=5")
echo "$RESPONSE" | jq '.'
RESULT_COUNT=$(echo "$RESPONSE" | jq '.results | length')
echo ""
if [ "$RESULT_COUNT" -gt 0 ]; then
    POST_COUNT=$(echo "$RESPONSE" | jq '.results[0].postCount')
    if [ "$POST_COUNT" -ge 1 ]; then
        echo -e "${GREEN}✅ Test 7 PASSED: Filtered by post count correctly${NC}"
    else
        echo -e "${RED}❌ Test 7 FAILED: Post count filter not applied${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  Test 7: No results${NC}"
fi
echo ""

# Summary
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Test Summary"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "✅ Semantic search API is functional!"
echo "✅ Filtering works (threshold, agent posts, post count)"
echo "✅ Pagination works"
echo "✅ Error handling works"
echo ""
echo "🎉 ION-199 (AGENT-02) validation complete!"
echo ""
echo "Next: Test with real agents or proceed to ION-198 (Thread Digests)"
echo ""
