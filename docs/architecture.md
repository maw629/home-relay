# Home Relay Architecture

This is the maintained technical contract for the implemented app. The Version
1 design and implementation plan in `docs/superpowers/` are historical records.
Update this document whenever a maintained component boundary, invariant, or
state transition changes.

## Purpose and scope

Home Relay accepts Android file shares and writes them to one selected Android
document tree, including a Google Drive folder supplied by the installed Drive
app. It is a locally sideloaded, single-user Android app.

The app does not provide chat, iPhone support, Drive browsing, printing,
multiple users, a backend, Firebase, Google Drive API access, OAuth, or cloud
replication confirmation.

## Data flow

```text
Android Share Intent
  -> ShareIntentParser
  -> ShareReceiverActivity
  -> ShareIntakeCoordinator (application-owned operation)
  -> UploadRepository creates STAGING Room row
  -> AndroidShareStager (row-owned no-backup private file)
  -> UploadRepository guarded STAGING -> QUEUED transition
  -> WorkManager unique UploadWorker
  -> AndroidDocumentTreeGateway (SAF document provider)
  -> selected provider accepts write
  -> provider synchronizes independently to Drive and Windows
```

`ShareReceiverActivity` is the exported share entry point. It accepts only
`ACTION_SEND` and `ACTION_SEND_MULTIPLE` with file `content://` URIs from
`EXTRA_STREAM` or `ClipData`. It rejects text shares, `file://` URIs, and other
schemes. The application-owned share-intake coordinator creates a durable
`STAGING` row before opening a source URI, stages to its row-owned private
target, and only then performs the guarded transition to `QUEUED` before the
sender's temporary URI grant can expire.

## Components

| Area | Main paths | Responsibility |
| --- | --- | --- |
| Application container | `HomeRelayApplication.kt` | Builds one `AppContainer`, configures custom WorkManager factory, creates notification channel, resumes durable pending work. |
| Destination settings | `data/DestinationStore.kt`, `destination/` | Stores tree URI, validates a writable tree with a create/delete probe, and writes documents through SAF. |
| Queue data | `data/UploadItem.kt`, `UploadDao.kt`, `HomeRelayDatabase.kt` | Persists upload metadata, guarded lifecycle transitions, and Room schema. |
| Share intake | `share/` | Parses file intents, owns application-scoped intake operations, stages to pre-recorded private targets, and displays share queue status. |
| Queue orchestration | `domain/UploadRepository.kt`, `work/` | Creates records, schedules/cancels/retries unique work, and processes provider writes. |
| Notifications | `notifications/UploadNotifier.kt` | Posts queued, uploading, completed, attention, and foreground-progress notifications. |
| Main UI | `ui/` | Selects/changes destination, requests notification permission, and displays recent uploads. |

`AppContainer` owns the database, destination store, gateway, notifier, stager,
scheduler, repository, and share-intake coordinator. Workers receive these dependencies from
`HomeRelayWorkerFactory`; they do not construct replacements.

## Persistent state

| Store | Contents | Rules |
| --- | --- | --- |
| Preferences DataStore | `destination_tree_uri` | Stores only the selected document-tree URI. |
| Room `upload_items` | Upload metadata, state, retry count, and error code | Never stores file bytes. Schema history lives under `app/schemas/`. |
| `noBackupFilesDir/pending` | Staged shared-file bytes | Delete after provider success, explicit cancellation, source/staging failure, failed final queue transition, or interrupted-staging recovery. |

`UploadItem` states are `STAGING`, `QUEUED`, `UPLOADING`, `COMPLETED`,
`NEEDS_ATTENTION`, and `CANCELLED`. `SHARE_INTERRUPTED` is the durable error
code for a `STAGING` row that cannot be safely resumed. Guarded DAO transitions
are intentional:

- Share intake creates `STAGING` before source URI access, completes only its
  own staging row to `QUEUED`, and records staging failures as
  `NEEDS_ATTENTION`. Source failures delete partial and final private targets;
  a failed final queue transition deletes the completed target but leaves its
  `STAGING` row for interruption recovery.
- Startup recovery converts every incomplete `STAGING` row to
  `NEEDS_ATTENTION` with `SHARE_INTERRUPTED` before worker resumption. It never
  restages a stale process-restored source URI, which may no longer have a
  grant, and deletes its reserved or completed private target.
- An active receiver recreation observes its existing application-owned intake
  operation rather than starting another staging operation. A process-restored
  receiver with no active operation waits for recovery and reports the
  interrupted-share outcome without restaging its temporary URI.
- Worker claims only `QUEUED -> UPLOADING`.
- Worker finishes only an item it still owns in `UPLOADING`.
- Retry permits retryable `NEEDS_ATTENTION -> QUEUED` rows and creates a new
  output name. `SHARE_INTERRUPTED` recovery records are nonretryable and have
  no retry action.
- Cancel permits `QUEUED`, `UPLOADING`, or `NEEDS_ATTENTION -> CANCELLED` and
  cancels work before deleting its staged file.
- After staging recovery, application startup returns interrupted `UPLOADING`
  rows to `QUEUED` and schedules queued rows.

Each item has unique work named `upload:<itemId>`. Work requires a connected
network and uses exponential backoff. A 10 MiB or larger provider write uses
WorkManager foreground execution with data-sync service type and progress.

## SAF security and destination ownership

The app uses `ACTION_OPEN_DOCUMENT_TREE`, requests read/write persistable URI
access, and validates the returned tree by creating and deleting a probe
document. It must not assume the provider authority is Google Drive.

Only one destination is retained:

1. Acquire a candidate URI grant.
2. Validate that candidate through SAF.
3. On validation failure, release the candidate grant unless it is the same URI
   as the already selected destination.
4. On success, persist the new URI first.
5. Release the prior distinct destination grant after persistence succeeds.

Provider success means the stream was accepted and closed. It does not prove
that the provider replicated content to cloud storage or a Windows computer.

## Error handling

| Condition | Result |
| --- | --- |
| Temporary network/provider I/O failure | Keep staged file and return WorkManager retry. |
| Source unreadable or missing staged file | Mark `NEEDS_ATTENTION` with `SOURCE_UNREADABLE`. |
| Private staging storage full | Mark the staging row `NEEDS_ATTENTION` with `STAGING_STORAGE_FULL`. |
| Process interrupted during staging | Mark the `STAGING` row `NEEDS_ATTENTION` with nonretryable `SHARE_INTERRUPTED`; the sender must share again. |
| Lost folder/account/grant | Mark `NEEDS_ATTENTION` with `DESTINATION_ACCESS_LOST`; offer folder reselection. |
| Provider quota or policy rejection | Mark `NEEDS_ATTENTION` with the corresponding error. |
| Failure after provider document creation | Mark `NEEDS_ATTENTION` with `WRITE_OUTCOME_UNKNOWN`; retry uses a new output name. |

## Android UI constraints

- The share receiver never launches a folder picker or notification-permission
  request.
- Android 13+ notification permission is requested only through the Settings
  flow. Queue behavior must work when it is denied.
- The share receiver is a non-dimming transparent window. Its only content is
  a centered Material status card within safe-drawing insets, so the sending
  app remains visible outside the card and through transparent system bars on
  Android 15+ edge-to-edge enforcement.
- The visible share status is one of preparing, queued count, missing
  destination, unreadable source, local storage full, interrupted share, or
  queue unavailable. A terminal aggregate status uses its original terminal
  deadline, including across activity recreation; at that deadline the
  receiver finishes and returns to the source app.
- During preparation, Back and predictive Back cannot dismiss the receiver
  before an accepted share has a durable terminal outcome. After that outcome,
  Back may dismiss the receiver before its automatic deadline.

## Change impact guide

Review these areas together when changing the listed concern:

| Change | Also inspect |
| --- | --- |
| Intent handling or URI policy | Parser, stager, exported manifest filter, share receiver tests, privacy rules. |
| Staging format or cleanup | Queue metadata, cancellation, worker completion, private-storage tests. |
| Destination selection | Persisted grants, validation mapping, settings UI, lost-access recovery. |
| Upload state or scheduling | DAO guards, repository mutex/order, worker results, startup resumption, notifications. |
| Upload UI | ViewModel mappings, Compose tests, notification-permission behavior. |
| Room entity/schema | Database migration strategy, `app/schemas/`, DAO tests. |
