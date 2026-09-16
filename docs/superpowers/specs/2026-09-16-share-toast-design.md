# Share Toast Overlay Design

> Follow-up to `2026-08-29-home-relay-android-design.md` (historical V1).
> Maintained contract: `docs/architecture.md`.

## Purpose

Change the share intake UX from "redirects to the Home Relay screen with
`Queued N file(s) for Home Relay` staying on screen" to "same message as a
transient system overlay, share flow ends, user is back in the previous app".

## Scope

Included:

- `ShareReceiverActivity` becomes headless: no Compose, no full-screen surface.
- Same five `ShareQueueStatus` outcomes map to the same user strings, shown via
  `Toast.LENGTH_SHORT`, followed by `finish()`.
- Manifest-only window behavior: translucent/no-display theme, `noHistory`,
  `excludeFromRecents` on the share activity.
- Test and `docs/architecture.md` updates for the removed full-screen surface.

Excluded:

- Any change to intent parsing, URI policy (`content://` only, no text,
  no `file://`), private staging in `noBackupFilesDir/pending`, Room queue,
  WorkManager scheduling (`upload:<itemId>`, connected-network, backoff),
  SAF destination ownership, notifications, or Main UI.
- Exact 1-second timing or custom toast styling. Duration (~2s) and styling are
  system-controlled per explicit user acceptance.
- Notification-only (no overlay) behavior. An overlay message is required even
  when notifications are denied.

## Context

Current flow (`share/ShareReceiverActivity.kt`):

- Exported activity handles `ACTION_SEND` / `ACTION_SEND_MULTIPLE`, `*/*`.
- `onCreate` parses via `ShareIntentParser.parse(intent)`, calls `setContent`
  with full-screen `ShareQueueScreen(queueStatus)`, then `queueShares()` in
  `lifecycleScope`.
- `queueShares()` checks `destinationStore.destinationTreeUri`, stages each
  share via `shareStager.stage()`, enqueues via `uploadRepository.enqueue()`,
  returns `Preparing | Queued(count) | DestinationMissing | SourceUnreadable |
  StorageFull`.
- `docs/architecture.md` "Android UI constraints" currently mandates a
  full-window Compose surface with safe-drawing insets for this confirmation.

## Approaches considered

A. Headless receiver + system Toast + `finish()` (chosen). Smallest diff, no
timing code, standard Android transient confirmation, immediate return to the
sending app.

B. Translucent Compose pill with exact 1s `delay(1000)` then `finish()`.
Rejected: more code, keeps an activity on top for 1s (not truly back in the
previous app during that second), timer/lifecycle edge cases, flakier tests.

C. Silent `finish()` relying on `UploadNotifier`. Rejected: violates the
overlay-message requirement and is invisible when notifications are denied.

## Design

### Data flow

```
Android Share Intent
 -> ShareIntentParser.parse()
 -> ShareReceiverActivity.queueShares() (unchanged logic)
 -> statusMessage(status) (new pure mapper)
 -> Toast.LENGTH_SHORT
 -> finish()
```

Staging still completes before `finish()`, preserving the
"stage before the sender's temporary URI grant expires" invariant.

### Component changes

1. `share/ShareReceiverActivity.kt`
   - Remove `setContent`, `ShareQueueScreen` composable, and Compose/Material3
     imports. The class remains a `ComponentActivity` with no content view.
   - Keep the `ShareQueueStatus` sealed interface unchanged.
   - Add `internal fun statusMessage(status: ShareQueueStatus): String` with
     the exact current strings:
     - `Preparing` -> `"Preparing files for Home Relay"` (defined for
       completeness; `queueShares()` never returns it so it is never toasted)
     - `Queued(count)` -> `"Queued $count ${if (count == 1) "file" else "files"} for Home Relay"`
     - `DestinationMissing` -> `"Choose a destination in Home Relay before sharing files"`
     - `SourceUnreadable` -> `"A shared file could not be read"`
     - `StorageFull` -> `"Not enough storage to queue shared files"`
     - `Preparing` is internal only and is never toasted.
   - `onCreate`: parse intent, launch `lifecycleScope`, compute
     `queueShares(shares)`, show toast, `finish()` in `finally` so any failure
     still exits and never strands the user on a blank screen.

2. `app/src/main/AndroidManifest.xml` (share activity only)
   - `android:theme="@android:style/Theme.Translucent.NoTitleBar"`
   - `android:noHistory="true"`
   - `android:excludeFromRecents="true"`
   - No change to intent filters, permissions, `MainActivity`, or authorities.

### Error handling

| Condition | Toast | Exit |
| --- | --- | --- |
| `Queued(count)` | Queued message | `finish()` |
| `DestinationMissing` | Reselection prompt | `finish()` (user reopens app manually if needed) |
| `SourceUnreadable` | Unreadable message | `finish()` |
| `StorageFull` | Storage message | `finish()` |
| Exception during staging/queue | No toast or best-effort error toast | `finish()` in `finally` |

The receiver still never launches a folder picker or notification-permission
request. Queue behavior with denied notifications is unchanged.

### UI constraints update

The `docs/architecture.md` bullets mandating a "full-window Compose surface
with safe-drawing insets" and "visible share status is one of preparing,
queued count, ..." for the receiver are replaced by: "The share receiver is
headless. It shows a transient system Toast for the terminal queue status and
finishes immediately. There is no receiver window content, so safe-inset and
surface-contrast rules do not apply to it."

## Testing

- Local unit test: `statusMessage()` mapping for `Queued(1)`, `Queued(2)`,
  `DestinationMissing`, `SourceUnreadable`, `StorageFull`.
- Instrumentation: rewrite `ShareReceiverActivityTest` to assert the activity
  finishes/destroys after `ACTION_SEND` instead of asserting a Compose node;
  remove the safe-insets assertion. Delete or repurpose
  `ShareQueueScreenVisibilityTest` (no surface remains).
- WSL: `./gradlew testDebugUnitTest lintDebug assembleDebug`.
- Windows + device: `connectedDebugAndroidTest` for the updated receiver test
  plus manual UAT: share from Zalo / Files by Google, confirm Toast floats over
  the sending app and the foreground app after the share is the sender, not
  Home Relay.
- Per `AGENTS.md`: red-green TDD for the mapper, no unrelated refactors, no
  device behavior claimed without a connected-device result.

## Risks

- Toast duration is system-controlled, not exactly 1s. Accepted by requester.
- Toast may be less noticeable than a full screen. Mitigated by keeping the
  existing `UploadNotifier` queued notification unchanged.
- `Theme.Translucent.NoTitleBar` flicker on some OEMs. Mitigated by no content
  view plus `noHistory`; verified during device UAT.
