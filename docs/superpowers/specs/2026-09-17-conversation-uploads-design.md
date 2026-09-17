# Conversation Uploads Design

> Spec for revamping the Uploads tab into a single-channel, own-messages-only
> conversation. Maintained contract: `docs/architecture.md`.

## Purpose

Present each queued upload as a chat message from the owner's account in one
conversation channel. This is a presentation revamp and the first visual step
toward a future chat app; it builds no chat backend.

## Scope

Included:

- Right-aligned own-message bubbles with delivery states for every
  non-cancelled upload, newest at the bottom with bottom-stick on arrival.
- A pure upload-state to message-status mapping plus a short local-time
  formatter, both JVM-unit-tested for reuse by the future chat UI.
- Removal of cancelled uploads from the conversation view.

Excluded:

- Any chat backend, `Message` domain or Room model, multiple channels,
  incoming messages, or composing/sending new messages from the UI.
- Any change to intent parsing, staging, Room queue, WorkManager scheduling,
  SAF destination handling, notifications, or the Settings tab.
- New icon assets, custom palette, or empty-state artwork.

## Context

Current flow (`ui/UploadsScreen.kt`, `ui/HomeRelayViewModel.kt`):

- `UploadRepository.observeUploads()` maps Room rows to `UploadRow`
  (`id`, `name`, `sizeBytes`, `createdAtMillis`, `state`, `errorCode`,
  `errorMessage`).
- `UploadsScreen` renders a newest-first `LazyColumn` of full-width rows:
  name, `"N bytes"`, ISO-8601 timestamp, raw state name, error text, and
  `Retry` / `Cancel` / `Choose folder again` buttons.
- `UploadsScreenTest` asserts on those texts and button callbacks.

## Approaches considered

A. UI-only revamp (chosen). `UploadRow` stays the model; bubbles,
`reverseLayout`, one ViewModel filter line, pure mapper/formatter helpers.
Smallest blast radius, fully reversible, zero queue-behavior change.

B. Introduce a `Message` domain model now (sender/channel fields,
UploadItem-to-Message mapping, possible Room columns). Rejected: premature
abstraction with no consumers — sender is always the owner, channel is
always one — plus Room schema churn. The real chat backend will reshape any
model designed today.

C. Cosmetic restyle only (bubbles, keep newest-on-top order and all states).
Rejected: fails the approved conversation semantics (ordering, delivery
states, cancelled-vanishes).

## Design

### Message model and state mapping

No new model. `UploadRow` is the message. New pure function
`messageStatus(row: UploadRow): MessageStatus`:

| `UploadState`      | `MessageStatus` | Presentation                        |
| ---                | ---             | ---                                 |
| `QUEUED`           | `SENDING`       | `Sending…` + Cancel text-button     |
| `UPLOADING`        | `SENDING`       | `Uploading…`, no action (as today)  |
| `COMPLETED`        | `SENT`          | `Sent ✓`, no actions                |
| `NEEDS_ATTENTION`  | `FAILED`        | Failed styling, error text, Retry text-button (+ `Choose folder again` when `errorCode` is `DESTINATION_ACCESS_LOST`) |
| `CANCELLED`        | —               | Never reaches the mapper; filtered upstream |

`FAILED` carries the existing `errorMessage` through unchanged. Existing
`onRetry`, `onCancel`, and `onChooseFolder` callbacks are rewired 1:1 onto
the bubble actions.

### Layout, ordering, and styling

- `LazyColumn(reverseLayout = true)` over the unchanged newest-first list:
  newest message lands at the bottom and the list auto-sticks to the bottom
  on new arrivals. The header item (`Home Relay` / `Recent uploads`, text
  unchanged) moves last in composition order so it stays visually on top.
- Each message is a `Row(Arrangement.End)` containing a rounded `Surface`
  bubble in `primaryContainer`. The left gutter stays free for future
  incoming messages.
- Bubble content, in order: file name (single line, ellipsis on overflow),
  size (existing `"N bytes"` string, no new formatting), relative-day
  timestamp from `formatMessageTime(createdAtMillis: Long, nowMillis: Long =
  System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault())`, then
  the status row from the table above. Timestamp rules, comparing calendar
  `LocalDate`s in `zone`: same date → `HH:mm` (e.g. `17:05`); 1 day ago →
  `Yesterday 17:05`; 2–6 days ago → full English day name + time (e.g.
  `Monday 17:05`); 7+ days ago → `yyyy/MM/dd` date + time (e.g.
  `2026/09/17 17:05`); future timestamps (clock skew) render as today.
  24-hour clock and space separators throughout.
- All colors and typography come from `HomeRelayTheme`; no custom palette,
  no icon assets (text affordances only, preserving the existing tests'
  text-assertion style). Existing safe-drawing/`Modifier.padding` handling
  is unchanged.

### Component changes

- New `ui/ChatMessage.kt`: `MessageStatus` enum (`SENDING`, `SENT`,
  `FAILED`), `messageStatus()`, `formatMessageTime()` with the relative-day
  rules above, and the `OwnMessageBubble` composable.
- `ui/UploadsScreen.kt`: list body rewritten to bubbles; composable
  signature unchanged.
- `ui/HomeRelayViewModel.kt`: `uploads` mapping appends
  `.filter { it.state != UploadState.CANCELLED }` (one line).
- Tests: `HomeRelayViewModelTest` gains a cancelled-exclusion test; new
  local unit test covers the mapper for all five states plus the time
  formatter; `UploadsScreenTest` updates the ISO-timestamp assertion to the
  `HH:mm` format and gains bubble, visual-ordering, callback, and
  cancelled-absent assertions.

### Edge cases

- Empty queue renders header only, as today; no empty-state artwork.
- Cancelling an in-flight upload removes its bubble on the next emission;
  no tombstone, no animation.
- Long file names ellipsize to one line; layout follows system RTL.
- No new held UI state besides `LazyColumn` scroll position.

## Testing

- TDD red-to-green for the mapper, formatter (including today / yesterday /
  weekday / date / future boundaries with injected `nowMillis`), and
  cancelled filter.
- Compose tests for bubble rendering, newest-visually-lowest ordering,
  callback wiring, and cancelled absence.
- WSL `./gradlew testDebugUnitTest lintDebug assembleDebug`; Windows
  `connectedDebugAndroidTest` on a connected phone or emulator. No device
  behavior is claimed without a connected-device result, per `AGENTS.md`.

## Forward compatibility notes (not built)

- `Arrangement.End` with a free left gutter anticipates incoming bubbles.
- `MessageStatus` seeds the future delivery vocabulary (`DELIVERED`,
  `READ` to be added by the chat feature, not here).
- `formatMessageTime` is intended for reuse by real chat timestamps.
- When the chat backend lands, an `UploadRow`-to-`Message` mapping will be
  written against real sync requirements; this spec deliberately does not
  pre-design it.

## Risks

- `reverseLayout` bottom-stick behavior must be confirmed on a real device;
  if it misbehaves, fall back to ascending order plus explicit
  `scrollToItem` on new arrivals.
