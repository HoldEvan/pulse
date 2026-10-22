# Protocol Session Persistence

## Overview

`ProtocolSessionStore` owns locally persisted protocol session data shared by the WebSocket and MQTT tools. `ServiceLocator` supplies the app-wide instance and uses the same persistence handle as the other stores.

## Data

- WebSocket history stores an ID, endpoint, event text, system/message direction, and timestamp. The store keeps entries oldest-to-newest and retains at most 2,000 entries.
- MQTT subscriptions store the topic filter and QoS. The store preserves one entry per topic filter.

## Persistence

`AppStatePersistence` reads and writes the `wsSessionHistory`, `wsSessionNextId`, and `mqttSubscriptions` fields in `app_data.json`. The same fields participate in external JSON backup restoration. Older files without these fields restore empty protocol collections. Invalid MQTT entries with a blank topic or QoS outside `0..2` are ignored.

Store mutations trigger the existing synchronous persistence callback. ViewModels can observe state through the store's `StateFlow` properties and restore the store using its snapshot API.

## WebSocket History and Reconnect

The WebSocket screen observes the stored history and records engine events with their original timestamps and the resolved connection endpoint. Clearing the log persists an empty history and cancels pending retries; an open socket remains connected, and a subsequent unexpected drop still reconnects while the automatic-reconnect toggle stays enabled. Event collection and retry coordination are owned by the WebSocket ViewModel and end when that ViewModel is cleared.

Automatic reconnect is opt-in and defaults to disabled. The default base delay is 3 seconds and the maximum is 60 seconds. Valid delays are between 1 and 3,600 seconds, with the maximum at least the base. Invalid input leaves the last valid stored settings intact.

`WsReconnectController` maintains one retry job. Failed attempts double the delay up to the maximum; a successful attempt ends the cycle, and the next cycle starts at the base delay. Both closed/failed connection states and engine events can trigger scheduling. Explicit disconnect, disabling automatic reconnect, clearing history, and ViewModel cleanup cancel retries.

The JSON fields `wsAutoReconnect`, `wsReconnectBaseDelaySec`, and `wsReconnectMaxDelaySec` participate in startup restore and external backup restoration. Saved collection snapshots also include reconnect preferences alongside the existing heartbeat settings. Older snapshots use defaults.

Unit tests cover history retention and clearing, settings snapshot restoration, bounded retry delays, cancellation, duplicate scheduling, and a new retry cycle after success. Device-level disconnect and navigation behavior still require manual verification.

## MQTT Subscriptions and Topic Tree

Subscriptions live in the store as `MqttSubscriptionEntry` (topic filter + QoS), one entry per filter. The ViewModel validates input with `isValidMqttFilter` before touching the store: the filter must be non-empty, `+` must occupy a whole level, and `#` must be the final whole level; empty levels (for example `a//b`) are valid MQTT topics and pass. An invalid input shows inline localized feedback (`mqtt_filter_invalid`) and leaves the saved set unchanged. Adding an existing filter updates its QoS.

On every successful connect the ViewModel resubscribes every saved filter with its stored QoS, because the engine connects with `cleanSession = true`. The engine interface has no unsubscribe, so removing a subscription only drops it from the saved set and the tree; the broker-side subscription ends with the connection. Startup restore, external backup restore, and collection snapshots behave as described above.

The subscribe card renders saved filters through `buildMqttTopicTree`, a pure projection into segment nodes (`MqttTopicNode` with a single-level `segment`, a full `path` used as the collapse key, child nodes, and exact-filter leaves). Expand/collapse only mutates a local collapsed-path set and never the saved filters; each leaf shows the exact filter and QoS with a remove action.

Unit tests in `MqttTopicTreeTest` cover wildcard placement validation, blank filters, prefix grouping, leaf fidelity (exact filter and QoS), distinct-once projection, and duplicate-topic collapsing.
