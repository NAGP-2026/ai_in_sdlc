# Task Breakdown — Approval Limits Feature

**Method:** BMAD (Break, Map, Act, Deliver)  
**Date:** 21 October 2026

---

## Phase 1 — Break (Decompose)

### Epic: Role-Based Approval Limits

| Task ID | Task | Estimate | Depends on |
|---------|------|----------|-----------|
| T-1 | Define `ApprovalLimits` constants class | 30 min | — |
| T-2 | Add `firstApprover` / `firstApproverRole` fields to `Invoice` | 15 min | — |
| T-3 | Implement single-approver limits (Manager ≤ $50k, Director ≤ $500k) | 1 hr | T-1, T-2 |
| T-4 | Write JUnit tests for single-approver limits (scenarios 1–5) | 1 hr | T-3 |
| T-5 | Implement dual-approver rule (> $500k requires two distinct Directors) | 1 hr | T-3 |
| T-6 | Write JUnit tests for dual-approver rule (scenarios 6–9) | 1 hr | T-5 |
| T-7 | Write JUnit tests for reject, legacy roles, edge cases (scenarios 10–17) | 45 min | T-6 |
| T-8 | Wire `ApprovalsController` to read role from session | 30 min | T-3 |
| T-9 | Add Playwright E2E test (over-limit Manager rejection) | 1 hr | T-8 |
| T-10 | Run PIT mutation testing; add tests to kill survivors | 45 min | T-4, T-6 |

---

## Phase 2 — Map (Git Commits)

### Commit 1: `feat: add spec and AGENTS.md for approval-limits feature`
- `AGENTS.md` (approval rules, layout, test commands)
- `docs/spec/approval-limits-spec.md`
- `docs/spec/task-breakdown.md`

### Commit 2: `feat(step-1): single-approver limits with tests`
- `ApprovalLimits.java` — constants + authority checks
- `Invoice.java` — entity with audit fields
- `ApprovalService.java` — Manager/Director branch (no dual-approver yet)
- `ApprovalServiceTest.java` — scenarios 1–5, 10–17

### Commit 3: `feat(step-2): dual-approver rule for invoices > $500k`
- `ApprovalService.java` — `approveHighValue()` method
- `ApprovalServiceTest.java` — scenarios 6–9 added; 27 tests total
- Full API layer + static UI
- Playwright E2E tests

---

## Phase 3 — Act (Implementation Notes)

### Key design decisions

1. **Guard clauses throw before mutation** — `approveHighValue()` checks identity, then role, then mutates. A failed attempt is guaranteed to leave the invoice unchanged.

2. **Identity check before role check** — Scenario 7b tests this explicitly. If the same-user block were after the role check, a Director would get "must be a different person" only sometimes (wrong ordering).

3. **FakeInvoiceRepository for tests** — `update()` is a no-op because the test code holds a direct reference to the invoice object. The service mutates the object in-place, so tests see changes without a real persistence call.

4. **No Spring context in unit tests** — `ApprovalServiceTest` creates `ApprovalService` with `new`, passing a `FakeInvoiceRepository`. Tests run in milliseconds.

---

## Phase 4 — Deliver (Definition of Done)

- [ ] All 27 unit tests pass (`mvn test`)
- [ ] PIT mutation score ≥ 85%
- [ ] At least 1 Playwright E2E test passes (Manager rejected on over-limit invoice)
- [ ] PR description lists every review finding with verdict (fixed / dismissed / deferred)
- [ ] `AI_INTERACTION_LOG.md` captures all agent sessions
- [ ] `ASSIGNMENT.md` at repo root with write-up and video link
