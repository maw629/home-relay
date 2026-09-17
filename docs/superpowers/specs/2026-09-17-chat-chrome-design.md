# Chat Chrome Design (Uploads, UI-only)

> Spec for making Uploads read as chat without building chat. Follow-up to
> `2026-09-17-conversation-uploads-design.md`. Maintained contract:
> `docs/architecture.md` (unchanged by this spec).

## Purpose

Make the Uploads conversation feel like a chat tool through pure UI
scaffolding: readable bubbles, day dividers, conversation header with
sender avatar, and empty state. No backend, no sync, no
data-model change. This is the approved option A, split into independently
testable PRs that do not rewrite each other.

## Scope

Included:

- Bubble readability polish (file-type icon, `size • time` meta row).
- Day grouping with date dividers.
- Conversation header refresh plus true empty state.
- Hardcoded `"Me"` sender label plus circular avatar initial in the top
  conversation header bar (`senderAvatar` test tag lives on the header,
  not in bubbles).

Excluded:

- Any chat backend, P2P link, Drive polling/inbox, push, or message
  transport. Performance advice stands: Drive is the durable offline
  fallback, P2P covers live sessions; neither is built here.
- Any Room schema change (`UploadItem` untouched), any DataStore change
  (no editable profile name yet), any WorkManager/SAF/notification change.
- Editable display name, text composing/sending, captions, delivery beyond
  the existing `Sending… / Uploading… / Sent ✓ / Failed` vocabulary.
- New icon assets, custom palette, or empty-state artwork (text + existing
  Material icons only).

## Context

Current state (`ui/UploadsScreen.kt`, `ui/ChatMessage.kt`,
`ui/HomeRelayViewModel.kt`):

- `UploadsScreen` is a `LazyColumn(reverseLayout = true)` of right-aligned
  `OwnMessageBubble` rows plus a tail header item (`Home Relay` /
  `Recent uploads`).
- Bubble content order: file name (1 line, ellipsis), size string, relative
  timestamp from `formatMessageTime()`, then status row (Retry / Cancel /
  `Choose folder again` wired 1:1).
- `UploadRow` (`id`, `name`, `sizeBytes`, `createdAtMillis`, `state`,
  `errorCode`, `errorMessage`) is the message; `CANCELLED` rows are filtered
  upstream. `testTag("messageBubble")` plus the left-gutter constraint
  (`bubbleDoesNotSpanFullWidth`) guard the future incoming side.
- `UploadsScreenTest` asserts on texts (`"42 bytes"`, formatted time,
  `Sent ✓`, Retry/Cancel) and callback wiring.

## Approaches considered

A. Chat chrome scaffolding, 4 stacked PRs (chosen). Each PR owns a separate
region (bubble body / list structure / header-empty / header identity), so
nothing is restructured twice. Zero migration, fully reversible per PR. (The
incoming-bubble scaffold originally scoped to PR4 was dropped before merge:
layout may change before the family inbox lands, so it will be redesigned
with that feature instead.)

B. One big PR for all of A. Rejected: harder to review, harder to revert,
against the explicit request for PR gates.

C. Jump to shared-folder inbox preview now (render other devices' files as
incoming). Rejected: hits the Drive-polling performance worry head-on and
needs device-ID plus dedup; deferred until after A.

## Design

### PR gates and ownership

Order is additive so later PRs do not restructure earlier ones:

- **PR1 — Bubble readability.** `UploadsScreen.kt`, `OwnMessageBubble`
  interior only, plus new `ui/FileIcon.kt` (extension/MIME → Material icon,
  pure function, JVM-tested). Meta row becomes a `Row` of separate `Text`
  nodes (`size`, `•`, `time`) so existing `onNodeWithText("42 bytes")` and
  time assertions keep passing. Status row unchanged.
- **PR2 — Day grouping + dividers.** List structure only. Pure helpers in
  `ui/ChatMessage.kt` (`dayKey(createdAtMillis, zone)`, `dayHeaderText(...)`
  reusing `formatMessageTime` rules) plus new `ui/DayHeader.kt` centered
  pill. `UploadsScreen` groups the existing newest-first list by day;
  bubbles from PR1 are reused untouched. Order (newest visually lowest) and
  `reverseLayout` preserved.
- **PR3 — Header + empty state.** Header/empty branch only. Tail item keeps
  the existing `Home Relay` / `Recent uploads` texts, restyled as a compact
  conversation header; empty list renders header plus empty copy with the
  proposed text `No uploads yet — shared files will appear here like
  messages` (exact wording may be adjusted in the PR).
  No bubble or grouping change.
- **PR4 — Identity (header bar).** Additive only. The tail
conversation header item gains a sender row (`Me` + avatar-initial
circle, hardcoded `"M"`/`Me`, no DataStore) above the `Home Relay` /
`Recent uploads` texts; own bubbles stay as PR1 left them (no in-bubble
identity).

### Component changes

- `ui/UploadsScreen.kt`: region-owned edits per PR as above; public
  composable signatures unchanged (`uploads`, `onRetry`, `onCancel`,
  `onChooseFolder`).
- `ui/ChatMessage.kt`: adds `dayKey`/`dayHeaderText` pure functions only.
- New files: `ui/FileIcon.kt` (PR1), `ui/DayHeader.kt` (PR2).
- No changes to `UploadItem`, DAO, database schemas, repository, worker,
  scheduler, destination store/gateway, notifier, share intake, Settings,
  or navigation.

### Edge cases

- Empty queue: header + empty copy only; no dividers, no bubbles (PR3).
- Single-day queue: no visible change from PR2 except the one divider.
- Long names still single-line ellipsis; layout follows system RTL;
  dividers use the same `zone`-based calendar-day logic as
  `formatMessageTime` (future timestamps render as today).

## Testing

- PR1: Compose — icon renders per file type; size/time texts still found;
  existing Retry/Cancel/Sent tests pass unmodified (or with row-scope
  selectors only if composition forces it).
- PR2: JVM unit — `dayKey`/`dayHeaderText` boundaries (today / yesterday /
  weekday / 7+ days / future); Compose — dividers appear once per day,
  newest-visually-lowest preserved.
- PR3: Compose — empty list shows header + empty copy; non-empty list shows
  header without empty copy.
- PR4: Compose — top conversation header shows `Me` + circular avatar
  initial exactly once.
- Per-PR gates (WSL): `./gradlew testDebugUnitTest lintDebug
  assembleDebug`. Windows `connectedDebugAndroidTest` plus manual UAT before
  merge for UI PRs, per `AGENTS.md`; no device behavior claimed without a
  connected-device result.

## Risks

- PR1 meta-row `Row` may tighten narrow-screen layouts; mitigation: keep
  separate `Text` nodes with ellipsis on the name, meta row allowed to wrap
  to two lines on overflow.
- PR2 grouping must not disturb `reverseLayout` bottom-stick; mitigation:
  group the already-sorted list without re-sorting, confirm on device.

## Forward compatibility (not built)

- `DayHeader`, `FileIcon`, and the meta-row structure are intended for reuse
  by real chat messages.
- Incoming-message UI will be designed with the family-inbox feature, when
  the layout requirements are known.
- Editable profile name (DataStore) and compose/caption box belong to the
  next spec (option B), not this one.
