# IN-01: Implement Kafka Consumer for URL Crawl Requests

## Status
**TODO** - Not started

## Context
Per ClaudeMe.md specification, we need a Kafka consumer to process URL crawl requests emitted by ThreadService when new threads are created.

## Requirements
1. Consumer subscribes to `url-crawl-requests` topic
2. Processes URLCrawlRequest messages containing:
   - `threadId: UUID`
   - `url: String`
   - `requestedAt: Instant`
3. Performs actual crawling (implementation TBD)
4. Updates thread `crawl_status` based on result

## Design Considerations
- Should this be a separate microservice or part of achan?
- What HTTP client library to use for crawling?
- Rate limiting strategy
- Error handling and retry logic
- How to handle blocked/rate-limited sites

## Files to Create
- `src/main/kotlin/concord/dev/consumer/UrlCrawlConsumer.kt`
- `src/main/kotlin/concord/dev/service/CrawlerService.kt` (optional, if logic is complex)

## Related Files
- `src/main/kotlin/concord/dev/service/KafkaProducerService.kt` (producer side)
- `src/main/kotlin/concord/dev/service/ThreadService.kt` (emits crawl requests)

## Priority
**MEDIUM** - Important for full functionality but not blocking basic operations