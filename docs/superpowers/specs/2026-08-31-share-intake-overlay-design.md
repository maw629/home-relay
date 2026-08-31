# Share Intake And Overlay Design

## Purpose

Replace Home Relay's full-screen share confirmation with a short-lived,
centered overlay that returns to the source app. At the same time, make share
intake durable across activity recreation, manual dismissal, and process
death. Every accepted `content://` URI must obtain a durable intake outcome
before the receiver can close.

The overlay is confirmation that Home Relay queued a private staged copy or
recorded why it could not. It is not confirmation of document-provider, cloud,
or Windows synchronization.

## Scope

This design changes the existing Android share entry point, Room upload queue,
application startup recovery, recent-upload error rendering, and tests. It
does not add a server, OAuth, provider-specific API, external storage
permission, a second database, or a separate recovery screen.

## Durable Intake Model

`upload_items` remains the only durable queue. Add the `STAGING` upload state
and `SHARE_INTERRUPTED` upload error code.

For every accepted URI, the share-intake coordinator first creates one
`STAGING` row, then opens its source stream. The row ID is generated before
staging and derives its fixed target path in `noBackupFilesDir/pending`. It has
the parser's fallback filename, a provisional output name, byte size zero, and
no persisted source URI. Android's temporary URI grant is never persisted. If
the initial row cannot be written, the coordinator never opens the source; it
reports an immediate queue-unavailable terminal status for that URI.

The coordinator processes accepted URIs in order. For each row it copies the
source stream to a temporary private file, renames the completed file to the
row's target path, then atomically writes resolved metadata and transitions the
row from `STAGING` to `QUEUED` in one Room statement. This database transition
is not atomic with the file rename. If it fails, the coordinator deletes the
completed private file and leaves the durable row for startup recovery. Only
after the guarded transition succeeds does it schedule the existing unique
`upload:<itemId>` WorkManager request.

`UploadWorker` continues to claim only `QUEUED` rows. It cannot access a file
that is still being staged.

## State Transitions

The following transitions apply in addition to the existing upload lifecycle:

| Event | Initial state | Result | Private file |
| --- | --- | --- | --- |
| Accepted URI recorded | none | `STAGING` | target path reserved; no file required yet |
| Private copy succeeds | `STAGING` | `QUEUED` | completed file retained |
| Source cannot be read | `STAGING` | `NEEDS_ATTENTION` + `SOURCE_UNREADABLE` | partial file deleted |
| Private storage is full | `STAGING` | `NEEDS_ATTENTION` + `STAGING_STORAGE_FULL` | partial file deleted |
| No usable destination after staging | `STAGING` | `NEEDS_ATTENTION` + `DESTINATION_ACCESS_LOST` | completed file retained |
| Process starts with incomplete intake | `STAGING` | `NEEDS_ATTENTION` + `SHARE_INTERRUPTED` | partial or completed file deleted |
| Initial queue-row persistence fails | none | no row; overlay reports queue unavailable | source is never opened |
| Final queue-row transition fails | `STAGING` | retained for process-start `SHARE_INTERRUPTED` recovery | completed private file deleted |

The coordinator exposes a process-start recovery gate. Application startup
reconciles every `STAGING` row through that gate before it resumes pending
upload work, and every new intake waits for the same gate before it creates a
row. It never retries an external share URI: normal Android sharing grants may
already have expired. A `SHARE_INTERRUPTED` row requires the sender to share
the source file again.

All other existing guarded upload transitions remain unchanged. Retry is
available only for `NEEDS_ATTENTION` outcomes with a completed staged file and
a retryable destination/write condition. It is unavailable for
`SOURCE_UNREADABLE`, `STAGING_STORAGE_FULL`, and `SHARE_INTERRUPTED`.

## Coordinator And Receiver Lifecycle

`AppContainer` owns one share-intake coordinator backed by the application
scope. The coordinator owns all active intake jobs and exposes an observable
operation status keyed by an intake ID. The operation tracks the original
accepted URI count, current staging progress, queued count, attention count,
and queue-unavailable count. It contains no source data beyond the live,
in-memory URIs required until private staging is complete.

`ShareReceiverActivity` owns a `ShareIntakeViewModel` with saved-state intake
ID. On its initial creation, the ViewModel starts the coordinator once for the
parsed intent. On configuration recreation, it attaches to the existing
operation rather than parsing and staging again. If process death leaves a
saved intake ID with no live operation, the coordinator first completes
startup recovery and the ViewModel displays a terminal interrupted result; it
does not reuse the restored intent because its temporary URI grant cannot be
trusted. The sender must start a new Android share action. Because the
coordinator is application-owned, a receiver recreation does not cancel or
duplicate an active intake.

While the operation is preparing, the receiver intercepts Back and predictive
Back. It cannot finish until all accepted URIs have a durable terminal outcome.
After that outcome is visible, normal Back is allowed. Closing the activity
does not cancel an active coordinator operation.

When an Android process is killed, in-memory operation state is lost. At next
application start, the coordinator converts all `STAGING` rows to
`SHARE_INTERRUPTED` and deletes their private files. This creates an explicit
Recent Uploads record instead of silently losing an accepted share.

## Overlay UI

The exported receiver activity uses a transparent, non-dimming theme with
transparent system bars, including the required contrast-enforcement settings
for supported API levels. It never starts `MainActivity`, a folder picker, or a
notification permission request.

Compose displays only a compact Material card centered within
`WindowInsets.safeDrawing`. The source app remains visible outside that card.
The receiver initially says that Home Relay is preparing files. Its terminal
message reflects the aggregate operation outcome:

- All successful: `Queued N file(s) for Home Relay`.
- All failed for one known reason: the existing specific error message, or a
  queue-unavailable message when an initial durable row could not be written.
- Mixed result: `Queued N file(s); M need attention`.

The terminal card remains for `BuildConfig.SHARE_STATUS_DISPLAY_MILLIS` and
then finishes the receiver activity, returning Android to the source app. The
value reads the `SHARE_STATUS_DISPLAY_MILLIS` environment variable during the
Gradle build. It accepts a non-negative integral value in milliseconds; unset,
invalid, negative, and overflowing values use the 2,000 ms default.

Recent Uploads maps `SHARE_INTERRUPTED` to `Share the file again.` It renders
the state as `NEEDS_ATTENTION` without a Retry control. Destination recovery
continues to use its existing choose-folder action.

## Error Handling

The coordinator catches source I/O and security failures and maps them to the
documented staging outcomes. It catches queue/database/scheduler failures so a
receiver cannot remain indefinitely on Preparing. It preserves rows already
written durably, never opens a source if its initial row cannot be written,
removes a completed private file if its matching final transition cannot be
written, and reports the aggregate terminal result to the overlay.

An item that has reached `QUEUED` is durable and will follow existing
WorkManager retry/reboot recovery. A failure to schedule a newly queued row is
recoverable through application-start `resumePending`, which schedules all
queued rows.

## Database Migration

Increase the Room database version and supply a migration that preserves all
current `upload_items` rows. Existing rows use only existing state/error text;
the new enum values require no data transformation. Export the new schema JSON
under `app/schemas/` and add migration coverage from version 1.

## Testing

Local unit tests cover coordinator state transitions, aggregate terminal
messages, initial/final persistence failure handling, startup recovery-gate
ordering, conversion of `STAGING` to `SHARE_INTERRUPTED`, retry eligibility,
and valid/invalid build-duration defaults.

Instrumentation tests use the existing test `content://` provider and real
Room/WorkManager test components to verify:

- a URI is staged and persisted before the receiver finishes;
- recreating the receiver produces one row per URI, not duplicate rows;
- Back and predictive Back do not close the receiver during preparation;
- terminal state closes the receiver after the configured delay;
- the card is centered in the same safe-drawing coordinate system production
  uses;
- the transparent receiver leaves an origin-activity background visible
  outside the card and never launches `MainActivity`;
- a recovered interrupted row appears in Recent Uploads with the
  share-again message and no Retry control;
- Room migration from version 1 preserves prior uploads and supports the new
  state/error values.

Device validation records a Zalo share that leaves only the centered overlay
visible and returns to Zalo after two seconds. It covers gesture and
three-button navigation on an Android 26-34 device or emulator and Android
15+ edge-to-edge behavior. It records device, Android API level, navigation
mode, source app, file type and size, app commit, and evidence path.

## Documentation Updates

Update the maintained architecture document with `STAGING`,
`SHARE_INTERRUPTED`, the coordinator boundary, startup recovery ordering, and
the transparent overlay contract. Update the README with the build environment
variable and the revised user flow. Update the test guide and manual acceptance
checklist with transparent-overlay and source-app-return validation.
