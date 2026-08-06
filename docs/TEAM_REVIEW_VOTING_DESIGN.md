# Team Review: Reviewer Assignment and Voting Design

Status: **Reviewer Assignment and Team Voting backend/frontend implemented; Session Closing and Audit pending**

Applies to: Shared Review Sessions only

Reviewed against: application code and Flyway V1–V12

Implementation note: Slice A now provides the reviewer roster APIs,
`review_session_reviewers`, and minimal append-only assignment audit events.
V11 intentionally limits audit event types to ASSIGNED, REMOVED, and
REACTIVATED; vote/close/reopen event types remain future migrations. The roster
endpoint exposes ASSIGNED rows only, remove reason is always required, and both
new assignment and reactivation return `200 OK` with the authoritative DTO.
Slice B renders the active roster on Shared Session Detail. OWNER/ADMIN load
workspace members for candidate selection and manage assignments; other ACTIVE
roles remain read-only. The UI requires a removal reason, preserves rows until
the server succeeds, and refreshes after stale `404`/`409` responses. Personal
Session routes do not mount the reviewer feature.

Slice C is implemented by V12 and the Team Voting backend. It adds
`decision_card_votes`, nullable `micro_decisions.team_decision`, vote audit
events, the vote list/PUT APIs, unanimous aggregate calculation, and reviewer
lifecycle hooks. Each vote stores the reviewer assignment's optimistic version.
Removal/reactivation increments that version, so the old vote remains visible
for audit with `counted=false` and is excluded until the reviewer confirms it
again with PUT. No close/reopen state, OWNER override, frontend voting UI, or
alternative quorum is part of this slice.

## 1. Baseline before V11/V12 (historical)

### Personal decision model

`ReviewSession` is shared by Personal and Shared workflows. `MicroDecision`
currently stores one mutable decision slot directly on the card:

```text
human_decision  PENDING | APPROVED | REJECTED
reviewer_note
decided_by
decided_at
```

The Personal vote endpoint accepts one APPROVED/REJECTED decision for a card.
Only the Personal session owner may use it. A decision can be written only while
the card is PENDING, so the current Personal API does not support editing a
vote. After every card is processed, `PersonalSessionService` sets the session
to REJECTED if any card is rejected, otherwise APPROVED, and sets
`completed_at`.

There is no vote table. One `MicroDecision` therefore represents one card and
at most one human decision. This is appropriate for Personal Workspace but
cannot represent multiple reviewers, quorum, vote history, or an aggregate Team
result.

### Shared session model

A Shared session is the same `ReviewSession` aggregate with
`workspace_type=SHARED` and a required `workspace_id`. Rule and AI produce the
same `MicroDecision` records. Every ACTIVE workspace member can read Shared
sessions; OWNER, ADMIN, and REVIEWER can create them. Shared cards are currently
read-only and their legacy `human_decision` remains PENDING.

`ReviewSession.assigned_to` is a nullable single-user field introduced in V1.
It has no service, repository query, DTO, endpoint, or test usage. It is a
legacy placeholder, not a reviewer-assignment model, and must not be reused for
multi-reviewer assignment.

### Membership and audit

Workspace roles are OWNER, ADMIN, REVIEWER, MEMBER, and AUDITOR. Membership is
ACTIVE, PENDING, or REMOVED. Workspace access deliberately hides inaccessible
resources with 404.

There is no audit-event entity or audit history table. `audit_links` grants
external audit access; it is not an audit log. `notifications` records delivery
state, not domain history. Neither can satisfy assignment, vote revision,
override, close, or reopen audit requirements.

### Compatibility conclusion

Personal and Shared can continue using the same session/card creation pipeline,
but cannot safely share the current single-decision storage. Personal must keep
using `human_decision`, while Shared must use separate assignments, votes, and
an aggregate Team result. Public DTOs should explicitly distinguish these
concepts.

## 2. Business decisions for the MVP

### Reviewer assignment

- Assignment is at **session level**, not card level. Every active assignment
  covers every Decision Card in the session.
- OWNER and ADMIN may assign or remove reviewers. Session ownership alone does
  not grant roster-management authority.
- Eligible assignees are ACTIVE workspace members with role OWNER, ADMIN, or
  REVIEWER. MEMBER cannot be assigned. AUDITOR remains read-only.
- REVIEWER cannot self-enrol unless an OWNER/ADMIN assigns them.
- The creator is not automatically assigned. This avoids silently giving a
  creator a second responsibility and keeps old Shared sessions compatible.
- A session may exist with zero reviewers. Its Team result remains PENDING.
- Reviewers may be added while review is open. Their addition makes every card
  without their vote PENDING again and triggers aggregate recalculation.
- A reviewer may be removed while review is open. If votes already exist, a
  removal reason is mandatory. Old votes remain in history but are excluded
  from the current quorum. The removal and resulting status changes are audited.
- No roster mutation is allowed after the session is closed.
- When a workspace membership becomes REMOVED, all open assignments for that
  membership must be marked REMOVED in the same membership transaction and all
  affected sessions recalculated. Existing votes remain historical.

This deliberately favors a transparent, editable roster over a frozen-roster
workflow. Requiring an audit reason after voting prevents silent outcome
manipulation without adding a separate review-start phase to the MVP.

### Voting

- Each currently assigned reviewer has one **current vote per Decision Card**.
- MVP vote values are APPROVED and REJECTED only. ABSTAIN and
  NEEDS_DISCUSSION are deferred because they require additional quorum rules.
- A reviewer may replace their own vote while the session is open.
- REJECTED requires a non-blank note; APPROVED note is optional.
- A reviewer must eventually vote on every card for unanimous approval.
- Only the assigned reviewer can create or replace their vote. OWNER/ADMIN
  cannot vote on another user's behalf.
- A removed assignment cannot submit or edit votes. Its historical revisions
  remain queryable.
- If a reviewer is removed from the workspace after voting, previous votes are
  retained for audit but cease contributing once the assignment is REMOVED.

### Quorum and card result

MVP quorum is all currently ASSIGNED reviewers:

```text
zero assigned reviewers                         -> PENDING
at least one current REJECTED vote              -> REJECTED
all assigned reviewers have current APPROVED    -> APPROVED
otherwise                                       -> PENDING
```

A rejection concludes the card immediately; waiting for remaining votes adds no
value to the blocking result, although other reviewers may still record votes
until the session is closed. There is no tie under unanimous quorum. Risk
severity does not alter quorum in the MVP.

`NEEDS_DISCUSSION` is not a persisted MVP result. Discussion can be represented
by a REJECTED vote with a required note until comments and an explicit
discussion workflow are designed.

### Session result and closing

The existing `ReviewSession.status` remains the calculated review result:

```text
PENDING    no active reviewer, or cards await quorum
IN_REVIEW  at least one vote exists and unresolved cards remain
REJECTED   at least one card is REJECTED
APPROVED   every card is APPROVED, or analysis produced no cards
```

Closing is a separate lifecycle dimension represented by `closed_at` and
`closed_by`, not a replacement status. This preserves whether a closed session
was APPROVED or REJECTED and avoids breaking existing consumers of `status`.

- OWNER or ADMIN may close a session only when its calculated result is
  APPROVED or REJECTED.
- Closing requires an optional note and freezes roster/vote mutations.
- OWNER may reopen a closed session; a non-blank reason is mandatory.
- Reopen clears close metadata, audits the action, and recalculates current
  results. ADMIN cannot reopen in the MVP.
- There is no timeout or expiration in the MVP.
- OWNER override is deferred. If later introduced, only OWNER may override, a
  reason is mandatory, votes are never rewritten, and the override is stored
  separately and audited.

## 3. Alternatives considered

### A — Separate assignment and vote tables (selected)

Advantages:

- supports any number of reviewers;
- database uniqueness prevents duplicate assignment and duplicate current vote;
- current vote can be updated while revisions are appended to audit history;
- Personal fields and endpoint remain untouched;
- quorum and authorization can query explicit session membership;
- supports future reviewer workload, reminders, delegation, and richer quorum.

Costs:

- introduces joins and aggregate recalculation;
- requires explicit concurrency control;
- Shared response DTOs must expose `teamDecision` separately from the Personal
  `humanDecision` field.

### B — Extend `MicroDecision.humanDecision` directly (rejected)

This preserves a smaller schema but still provides only one decision per card.
Arrays/JSON would weaken foreign keys, uniqueness, indexing, and audit. Adding
columns per reviewer is unbounded. Overwriting `decided_by` destroys prior
votes. It also risks changing the stable Personal API and makes quorum logic
implicit. This option is unsuitable beyond a single reviewer.

### C — Move Personal and Shared to one universal vote table (deferred)

This creates a conceptually uniform model but requires migrating every existing
Personal decision, changing the Personal endpoint/response semantics, and
carefully preserving its one-time-vote behavior. It provides no immediate user
value for the first Team Voting slice. A later compatibility migration can move
Personal if a real shared use case appears.

## 4. Implemented persistence model (close fields deferred)

V11 implements reviewer assignment and V12 implements the vote/card aggregate
structures below. Close/reopen columns remain a future slice.

### `review_session_reviewers`

| Column | Purpose |
|---|---|
| `id VARCHAR(36)` | Primary key |
| `session_id VARCHAR(36)` | Shared Review Session |
| `workspace_member_id VARCHAR(36)` | Reviewer membership and role context |
| `assigned_by_user_id VARCHAR(36)` | Actor |
| `assigned_at TIMESTAMP(6)` | Assignment time |
| `status ENUM('ASSIGNED','REMOVED')` | Current roster participation |
| `removed_by_user_id VARCHAR(36) NULL` | Removal actor |
| `removed_at TIMESTAMP(6) NULL` | Removal time |
| `removal_reason TEXT NULL` | Required when votes already exist |
| `version BIGINT NOT NULL` | Optimistic concurrency |

Constraints and indexes:

- `UNIQUE(session_id, workspace_member_id)`; re-assignment reactivates the same
  row instead of creating duplicates.
- FK session → `review_sessions` with `ON DELETE CASCADE`.
- FK membership → `workspace_members` with `ON DELETE RESTRICT`; memberships
  are soft removed and therefore remain valid historical identities.
- Actor FKs → `users` with `ON DELETE RESTRICT`.
- indexes `(session_id, status)`, `(workspace_member_id, status)`.
- service invariant: session is SHARED and membership belongs to the same
  workspace. MySQL cannot express this cross-table equality with a CHECK.

`COMPLETED` is not an assignment status because completion is derivable from
vote coverage and changes when cards or roster change.

### `decision_card_votes`

| Column | Purpose |
|---|---|
| `id VARCHAR(36)` | Primary key |
| `decision_card_id VARCHAR(36)` | `micro_decisions.id` |
| `reviewer_assignment_id VARCHAR(36)` | Session reviewer identity |
| `decision ENUM('APPROVED','REJECTED')` | Current vote |
| `note VARCHAR(2000) NULL` | Required for REJECTED |
| `created_at TIMESTAMP(6)` | First vote |
| `updated_at TIMESTAMP(6)` | Last replacement |
| `assignment_version BIGINT NOT NULL` | Eligibility generation captured at vote time |
| `version BIGINT NOT NULL` | Optimistic concurrency/ETag source |

Constraints and indexes:

- `UNIQUE(decision_card_id, reviewer_assignment_id)`.
- FK card → `micro_decisions` with `ON DELETE CASCADE`.
- FK assignment → `review_session_reviewers` with `ON DELETE RESTRICT`.
- indexes `decision_card_id`, `reviewer_assignment_id`, `decision`, and
  `(decision_card_id, decision)`; the unique key also covers card/assignment.
- service invariant: card and assignment belong to the same session.
- CHECK or service validation: REJECTED requires a trimmed non-empty note.

### Card aggregate and session close columns

Add nullable `micro_decisions.team_decision` using
`PENDING|APPROVED|REJECTED`; populate it only for SHARED cards. Keep
`human_decision` exclusively authoritative for PERSONAL cards.

The following close/reopen columns are deferred and are not part of V12:

```text
closed_by VARCHAR(36) NULL
closed_at TIMESTAMP(6) NULL
close_note TEXT NULL
version BIGINT NOT NULL DEFAULT 0
```

The existing `status` remains the Shared aggregate result. Team Voting must not
write `completed_at` during mutable recalculation: current rows already use that
field inconsistently (Personal finalization and zero-card Shared approval).
`closed_at` is the only authoritative Team-review completion time. Existing
`completed_at` values remain untouched for response compatibility and can be
deprecated for Shared sessions in a later API version.

### `team_review_audit_events`

An append-only table is required because `audit_links` is unrelated:

```text
id
session_id
actor_user_id
event_type
target_user_id NULL
target_assignment_id NULL
decision_card_id NULL
old_value_json NULL
new_value_json NULL
reason NULL
created_at TIMESTAMP(6)
```

Implemented event types are REVIEWER_ASSIGNED, REVIEWER_REMOVED,
REVIEWER_REACTIVATED, VOTE_CREATED, and VOTE_UPDATED. Vote audit rows reference
the card and assignment and store old/new decision, note, assignment version,
and vote version as JSON. They never store secrets or raw source code.

## 5. Authorization matrix

All actions first require an ACTIVE membership; non-members and inactive
members receive the existing hidden 404 contract. “Assigned” means an ASSIGNED
reviewer row and an eligible ACTIVE role.

| Actor | Assign/remove | View reviewers | Vote/change own | View votes | Close | Reopen | Override |
|---|---:|---:|---:|---:|---:|---:|---:|
| OWNER | Yes | Yes | Only if assigned | Yes | Yes | Yes | Deferred; OWNER only |
| ADMIN | Yes | Yes | Only if assigned | Yes | Yes | No | No |
| REVIEWER | No | Yes | Only if assigned | Yes | No | No | No |
| MEMBER | No | Yes | No | Yes | No | No | No |
| AUDITOR | No | Yes | No | Yes | No | No | No |
| Session creator | By workspace role | Yes | Only if assigned | Yes | By workspace role | By workspace role | By workspace role |
| Assigned reviewer | No extra roster right | Yes | Yes | Yes | No | No | No |
| Non-member/inactive | Hidden 404 | Hidden 404 | Hidden 404 | Hidden 404 | Hidden 404 | Hidden 404 | Hidden 404 |

Votes and notes are visible to all ACTIVE members for team transparency. If the
product later requires anonymous voting, that is a separate policy change.

## 6. State machines

### Assignment

```text
absent -> ASSIGNED       OWNER/ADMIN assigns eligible member
ASSIGNED -> REMOVED      OWNER/ADMIN; reason required after any vote
REMOVED -> ASSIGNED      OWNER/ADMIN reassigns while session is open
```

Every transition appends an audit event and recalculates all card/session
results. Closed sessions reject transitions with 409.

### Vote

```text
absent -> APPROVED | REJECTED
APPROVED <-> REJECTED
```

Only the owning active assignment may transition. REJECTED requires a note.
Every replacement updates the current row and records old/new values in the
same transaction. Closed sessions or removed assignments return 409.

### Decision Card Team result

```text
PENDING -> APPROVED      all assigned reviewers approve
PENDING -> REJECTED      any assigned reviewer rejects
APPROVED/REJECTED -> PENDING/APPROVED/REJECTED
                         vote or roster changes while open
```

### Shared Session result and lifecycle

```text
PENDING -> IN_REVIEW     first relevant vote while unresolved cards remain
PENDING/IN_REVIEW -> APPROVED    all cards approved
PENDING/IN_REVIEW/APPROVED -> REJECTED  any card rejected
APPROVED/REJECTED -> other calculated result after open vote/roster change

OPEN + APPROVED/REJECTED -> CLOSED      OWNER/ADMIN
CLOSED -> OPEN                         OWNER with reason
```

Closing does not replace APPROVED/REJECTED. No vote/roster mutation is legal
while `closed_at` is non-null.

## 7. Proposed API

All paths are workspace- and Shared-session-scoped. IDs belonging to another
workspace or Personal sessions return 404.

### Reviewers

```http
GET /api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers
```

Returns currently ASSIGNED DTOs with assignment ID, user/display identity,
workspace role, assignment status/timestamps, assignment actor, removal
metadata fields, and version. Any ACTIVE member: `200`.

```http
POST /api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers
Content-Type: application/json

{ "workspaceMemberId": "..." }
```

OWNER/ADMIN: `200` for first assignment and reactivation. `400` invalid
role/input, `403` insufficient active role, hidden `404`, and `409` duplicate
active assignment.

```http
POST /api/workspaces/{workspaceId}/sessions/{sessionId}/reviewers/{assignmentId}/remove
Content-Type: application/json

{ "reason": "Required for every removal" }
```

OWNER/ADMIN: `200` with the authoritative REMOVED DTO. `400` missing required
reason, hidden `404`, and `409` already removed.

### Votes

```http
GET /api/workspaces/{workspaceId}/sessions/{sessionId}/votes
```

Any ACTIVE member: `200`. Return card-grouped current votes plus aggregate card
results. Audit-history details may be a separate endpoint to avoid an
unbounded response.

```http
PUT /api/workspaces/{workspaceId}/sessions/{sessionId}/cards/{cardId}/vote
Content-Type: application/json

{ "decision": "APPROVED", "note": null, "version": 3 }
```

Assigned reviewer: `200` with updated own vote, card result, session status,
and new version. `version` is omitted for the first vote and required for an
update/reconfirmation. Repeating the same representation is idempotent. `400` invalid
decision/missing rejection note, `403` active member but not assigned, hidden
`404`, `409` removed assignment/closed session/stale version.

### Close and reopen

```http
POST /api/workspaces/{workspaceId}/sessions/{sessionId}/close
{ "note": "Ready to merge" }
```

OWNER/ADMIN: `200` detail response. `409` if unresolved or already closed.

```http
POST /api/workspaces/{workspaceId}/sessions/{sessionId}/reopen
{ "reason": "New evidence requires another review" }
```

OWNER only: `200`. `400` blank reason, `409` if already open.

### Audit history

```http
GET /api/workspaces/{workspaceId}/sessions/{sessionId}/audit-events
```

Any ACTIVE member: paginated `200`, newest last or with an explicit stable
cursor. Raw source content and secrets are never returned in event payloads.

## 8. Transactions and concurrency

- Assignment create relies on `UNIQUE(session_id, workspace_member_id)` and
  maps duplicate-key races to 409. Lock the session row before checking closed
  state and reactivating/removing an assignment.
- Vote PUT locks the session, target card, and reviewer-assignment rows in a
  consistent order. The vote row uses a version token to prevent two
  tabs silently overwriting each other.
- Current vote write, audit append, card recalculation, and session
  recalculation occur in one transaction.
- Recalculation reads all ASSIGNED reviewers and their current votes while the
  session is locked. It writes materialized `team_decision` and `status` only
  when changed.
- Reviewer removal locks the session and assignment, marks it REMOVED, appends
  audit, and recalculates every card in one transaction.
- Workspace-member removal must first lock membership and affected open session
  rows in stable ID order, remove assignments, and recalculate. This avoids a
  vote being accepted after membership removal.
- Close locks the session, recalculates from authoritative rows, verifies a
  final result, writes close metadata, and audits. Concurrent late votes either
  commit before close and are included or fail after close with 409.
- Never calculate authority or quorum from client-supplied role/count fields.

## 9. Personal and migration compatibility

1. Add new nullable Team columns/tables without changing existing Personal
   columns or endpoint.
2. Set `team_decision=PENDING` for existing Shared cards; leave it NULL for
   Personal cards. Do not copy Personal `human_decision` into Team votes.
3. Existing Shared sessions receive no automatic reviewers and remain open with
   PENDING Team results unless they have zero cards, in which case their current
   APPROVED result remains valid.
4. Keep `PATCH /api/v1/personal/sessions/decisions/{id}` and its one-time vote
   semantics unchanged.
5. Shared DTOs add `teamDecision`, reviewers, vote summary, and close metadata;
   retain legacy `humanDecision` temporarily only for response compatibility,
   document that it is not authoritative for SHARED, then deprecate it in a
   future version.
6. Do not drop `assigned_to` in the first voting migration. Verify it is NULL or
   define a deliberate backfill in a later cleanup migration; current code
   confirms it has no active workflow.
7. Rehearse migrations on a copy containing Personal decisions, Shared cards,
   legacy `assigned_to`, zero-card sessions, and all session statuses. Run
   Hibernate validate and both existing regression suites.

## 10. Future frontend workflow

```text
Session Detail
├── Session result and Open/Closed state
├── Reviewers
│   ├── progress per reviewer
│   └── OWNER/ADMIN roster controls
├── Decision Cards
│   ├── aggregate Team result
│   ├── current votes
│   └── My Vote (assigned reviewers only)
└── Audit history
```

- OWNER/ADMIN manage reviewers near the session summary, not separately on each
  card.
- Assigned reviewers see APPROVE/REJECT and a note input per card. Reject note
  validation occurs client-side and server-side.
- MEMBER/AUDITOR see roster, current votes, aggregate results, and audit history
  read-only.
- Vote changes use a confirmation when replacing REJECTED with APPROVED and
  display a 409 conflict with reload/retry rather than silently retrying.
- Loading is scoped independently to roster, each vote mutation, close action,
  and audit pagination. A failed vote keeps the typed note.
- Closed sessions remove mutation controls and clearly show closer/time/note.

## 11. Delivery roadmap

### Slice A — Reviewer Assignment backend (implemented)

- Scope: migration for reviewer assignments and audit events; reviewer list,
  assign, remove/reactivate APIs; eligibility and role policy; recalculation
  hooks with no voting endpoint.
- Out: vote, close/reopen UI, notification.
- Depends on: current Shared Session and Workspace membership.
- Tests: role matrix, same-workspace/ACTIVE eligibility, duplicate races,
  reactivation, removal reason, hidden 404, Personal isolation.
- Risk: membership removal coordination and legacy `assigned_to` data.

### Slice B — Reviewer Assignment frontend (implemented)

- Scope: active roster section, OWNER/ADMIN controls, read-only roles, eligible
  membership filtering, required-reason removal, loading/error/409 recovery,
  responsive presentation, and Shared/Personal route isolation.
- Out: voting and close controls.
- Depends on: Slice A.
- Tests: five-role UI matrix, add/remove/reactivate, stale role, responsive.
- Risk: DTO freshness after concurrent roster changes.

### Slice C — Team Voting backend (implemented)

- Scope: vote/current history persistence, PUT vote, vote list, unanimous
  aggregation, materialized card/session results, locks/version conflicts.
- Out: override, comments, alternative quorum.
- Depends on: Slice A.
- Tests: vote matrix, rejection note, vote replacement/history, quorum,
  concurrent same-reviewer requests, roster/vote race, Personal regression.
- Risk: lock ordering and aggregate correctness.

Implementation resolves this risk by serializing mutations on the Shared
session, then locking cards, assignments, and votes in stable order. Aggregate
queries use locking current reads so MySQL `REPEATABLE_READ` cannot overwrite a
newer concurrent aggregate with a stale snapshot. The unique card/assignment
constraint is the final duplicate-create defense; vote `version` rejects stale
updates with `409 Conflict`.

### Slice D — Team Voting frontend (implemented)

- Scope: current votes, My Vote, editable vote, card/session aggregate states,
  409 recovery and read-only views.
- Out: close/reopen and override.
- Depends on: Slices B and C.
- Tests: Rule/AI cards, approve/reject validation, change vote, conflicts,
  MEMBER/AUDITOR read-only, responsive and HTTP E2E.
- Risk: stale aggregate display after another reviewer votes.

The frontend renders backend-authoritative aggregates and all current votes,
including `counted=false` audit-visible votes. My Vote is shown only when JWT
identity matches an ASSIGNED roster entry and the current workspace role is
OWNER, ADMIN, or REVIEWER. Update/reconfirm requests carry the current vote
`version`; `409 Conflict` displays a targeted message and refetches without an
automatic resubmit. The slice intentionally has no realtime transport, so other
reviewers' changes appear on reload, roster refresh, successful mutation, or
conflict recovery.

### Slice E — Session closing and audit

- Scope: close/reopen columns and APIs, append-only history endpoint, UI
  timeline and closed-state controls.
- Out: override, expiration, notification, webhook.
- Depends on: Slices A–D.
- Tests: close/reopen role matrix, unresolved conflict, vote-vs-close race,
  immutable history, archived/deletion policy.
- Risk: deciding retention before adding any Shared hard-delete endpoint.

## 12. Open stakeholder questions

The technical design is actionable with the defaults above. Only these product
policy choices should be confirmed before implementation:

1. Should every ACTIVE member see individual reviewer votes and notes, or only
   aggregate results (with OWNER/ADMIN/AUDITOR seeing identities)? This design
   defaults to transparent votes for all active members.
2. Is OWNER override required for the first release? This design defers it; if
   required, the product must confirm whether an override changes only the
   session result or may also override individual cards.
3. Should removing a reviewer after they voted be allowed with mandatory reason
   as proposed, or should the roster freeze after the first vote?

ABSTAIN, NEEDS_DISCUSSION, severity-weighted quorum, timeout, anonymous voting,
and notification are intentionally future features, not unresolved technical
questions for the MVP.
