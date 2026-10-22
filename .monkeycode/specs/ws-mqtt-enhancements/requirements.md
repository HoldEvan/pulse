# Requirements Document

## Introduction

Pulse extends its WebSocket and MQTT tools with durable message history, resilient WebSocket sessions, and hierarchical MQTT topic subscriptions.

## Glossary

- **Session log**: A locally stored, timestamped WebSocket event record associated with an endpoint.
- **Reconnect policy**: User-configurable automatic retry behavior after an unexpected WebSocket disconnect.
- **Topic tree**: A hierarchical view of MQTT subscription filters grouped by topic path segments.

## Requirements

### Requirement 1: WebSocket Session History

**User Story:** AS a developer, I want WebSocket events retained locally, so that I can inspect messages after leaving the protocol screen or restarting the app.

#### Acceptance Criteria

1. WHEN a WebSocket event is observed, Pulse SHALL append the event content, direction, timestamp, and endpoint to local session history.
2. WHILE a session history contains more than 2,000 events, Pulse SHALL retain the newest 2,000 events.
3. WHEN Pulse starts, Pulse SHALL restore persisted WebSocket session history.
4. WHEN a user clears a WebSocket log, Pulse SHALL remove the visible session history and persist the cleared state.

### Requirement 2: WebSocket Reconnection

**User Story:** AS a developer, I want unexpected WebSocket disconnects retried automatically, so that transient network interruptions do not require manual reconnection.

#### Acceptance Criteria

1. WHEN automatic reconnect is enabled and a connection ends unexpectedly, Pulse SHALL retry using a delay that starts at the configured base delay and doubles after each failed attempt up to the configured maximum delay.
2. WHEN the user disables automatic reconnect or explicitly disconnects, Pulse SHALL stop pending retries.
3. WHEN a retry succeeds, Pulse SHALL reset the retry delay to the configured base delay.
4. IF a retry attempt fails, Pulse SHALL append a timestamped failure event and continue according to the configured retry policy.

### Requirement 3: MQTT Topic Subscription Tree

**User Story:** AS a developer, I want MQTT subscriptions organized by topic path, so that I can manage related topic filters as a tree.

#### Acceptance Criteria

1. WHEN a user adds a valid MQTT subscription filter, Pulse SHALL store the filter and QoS locally and display it under topic path segments.
2. WHEN Pulse starts, Pulse SHALL restore saved subscription filters and QoS values.
3. WHEN an MQTT connection is established or re-established, Pulse SHALL subscribe to every saved filter using its stored QoS.
4. WHEN a user removes a subscription filter, Pulse SHALL remove it from the saved subscription set and the topic tree.
5. IF a filter is empty or contains an invalid wildcard placement, Pulse SHALL preserve the existing subscriptions and report validation feedback.

### Requirement 4: MQTT Topic Tree Interaction

**User Story:** AS a developer, I want to expand and collapse topic branches, so that large subscription sets remain scannable.

#### Acceptance Criteria

1. WHEN a user expands or collapses a topic branch, Pulse SHALL update the visible tree without changing the saved subscription filters.
2. WHILE a topic node represents one or more saved filters, Pulse SHALL expose the exact filter and QoS for each subscription leaf.
