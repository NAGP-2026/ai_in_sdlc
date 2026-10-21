# AI in SDLC Assignment — Write-up

**Student:** Piyush Chandel  
**Repository:** `invoice-approval/` (Java 17 / Spring Boot 3.2)  
**Reference repo:** https://github.com/NITIN1670/ai-sdlc-demo-app.git  
**AI tool:** GitHub Copilot (free tier) via Cline agent framework  
**Date:** 21 October 2026

---

## 1. Feature Built

Added role-based approval limits to the invoice approval service:

| Role | Approval Authority |
|------|--------------------|
| Manager (or legacy Approver) | Up to $50,000 |
| Director (or Admin) | Up to $500,000 |
| Two distinct Directors | Required for invoices > $500,000 |

Additional rules:
- The same user cannot count as both approvers on one invoice
- A failed approval attempt leaves the invoice **completely unchanged**
- A rejected attempt returns a clear, human-readable reason

---

## 2. Context File (AGENTS.md)

`invoice-approval/AGENTS.md` tells any agent arriving cold:
- The approval rules table (source of truth)
- Package layout and layering constraints (`domain/` must not import Spring MVC)
- Demo credentials for all five test users
- The exact test command (`mvn test -f invoice-approval/pom.xml`)
- All 10 required scenarios with expected outcomes
- Review expectations (what to flag in a PR)

**Why it matters:** When I fed the AGENTS.md to Copilot before each session, the agent correctly placed all rule logic in `ApprovalService` (domain layer) and kept controllers thin. Without it, the first draft mixed business logic into the controller.

---

## 3. Spec-Driven Tool Used

**Method:** BMAD (Break, Map, Act, Deliver)

Files committed:
- `docs/spec/approval-limits-spec.md` — Acceptance criteria (AC-1 to AC-11) and formal business rules (B1–B3)
- `docs/spec/task-breakdown.md` — Decomposed tasks T-1 to T-10 with dependencies; mapped to git commits

---

## 4. Two-Step Implementation

### Commit 1: `feat: add spec and AGENTS.md for approval-limits feature`
Spec files and AGENTS.md only. No code. Establishes the contract before any implementation.

### Commit 2: `feat(step-1): single-approver limits — Manager ≤ $50k, Director ≤ $500k`
- `ApprovalLimits.java` with constants
- `ApprovalService.approve()` — Manager/Director branches only (no dual-approver yet)
- Unit tests for scenarios 1–5 plus edge cases (reject, legacy roles, wrong status)

### Commit 3: `feat(step-2): dual-approver rule for invoices > $500k`
- `approveHighValue()` method — identity check before role check (test 7b verifies ordering)
- All 27 unit tests pass
- Full API layer, static UI, Playwright E2E tests

---

## 5. Tests

### Unit Tests (JUnit 5)

27 tests in `ApprovalServiceTest`. No Spring context — runs in 0.2 seconds.

```
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Key scenarios covered:

| # | Test | Assertion |
|---|------|-----------|
| 2a | Manager > $50k throws | invoice status, decidedBy, firstApprover all null after throw |
| 5  | Manager > $500k throws | firstApprover NOT set (critical: rule-engine must not partially mutate) |
| 7a | Same Director second approval | throws "different person"; firstApprover still intact |
| 7b | Identity check precedes role check | error message says "different person" NOT "must be Director" |
| 8  | Manager second approval on > $500k | throws "Director"; firstApprover intact, decidedBy null |

### Playwright E2E Tests

`e2e/tests/approval-limits.spec.ts` — 5 tests:

1. **Manager over-limit rejection** — clicks Approve on INV-1006 ($75k as Manager); verifies `#decision-error` contains "50000"; verifies invoice still shows `status-PENDING_APPROVAL`
2. Director first approval on > $500k stays pending
3. Same Director second approval → "different person" error
4. Manager second approval on > $500k → "Director" error
5. Manager can reject > $500k invoice (no amount check)

---

## 6. Mutation Scores (PIT)

### Before final test pass (after step-1 only):
```
>> Line Coverage: 38/52 (73%)
>> Mutation Score: 31/38 killed (82%)
```

### After all 27 tests (step-2 complete):
```
>> Line Coverage: 52/52 (100%)
>> Mutation Score: 44/44 killed (100%)
```

Notable survivors killed by specific tests:
- Boundary `<=` vs `<` on `MANAGER_LIMIT` → killed by test 1a (exact-limit approval)
- Boundary `<=` vs `<` on `DIRECTOR_LIMIT` → killed by test 3b (exact-limit approval)
- Negated `hasDirectorAuthority` on second-approver check → killed by test 8

---

## 7. PR Review Findings

Ran the workshop review prompt against the diff. Every finding has a verdict:

| # | Finding | Verdict |
|---|---------|---------|
| 1 | `AuthController` injects `@Value("${demo.users}")` but parameter is unused | **Fixed** — parameter removed; `HARDCODED_USERS` used directly |
| 2 | `InvoicesController.submit()` does not check who is submitting | **Dismissed** — demo app; any authenticated user may submit a draft |
| 3 | `FakeInvoiceRepository.update()` is a no-op | **Dismissed** — tests hold direct object reference; mutations are visible without persistence. Behaviour documented in Javadoc |
| 4 | E2E tests share mutable in-memory state across test runs | **Deferred** — a `POST /api/test/reset` endpoint would fix this; out of scope for demo |
| 5 | `@Service` annotation on `ApprovalService` introduces Spring dependency into domain layer | **Fixed** — annotation moved; domain layer is now Spring-free |

---

## 8. AI Interaction Log

`invoice-approval/AI_INTERACTION_LOG.md` — captures all 6 sessions, 14 prompts, structured JSON hook output for each session, agent reasoning steps, and judgement calls.

**Skills used:**
- Spec generation (BMAD task breakdown from plain-English rules)
- Test-first skill (tests written alongside implementation)
- Structured output hook (every session emits JSON with files changed, test count, mutation score)
- Code review skill (review prompt run against PR diff)

**Sub-agents created:**
1. **Spec Agent** — turned business rules into formal spec + task breakdown
2. **Domain Agent** — implemented `ApprovalService` and unit tests (domain layer only)
3. **API Agent** — wired controllers, session management, static UI
4. **E2E Agent** — wrote Playwright tests against the running app
5. **Review Agent** — ran review prompt; produced verdict table

---

## 9. Demo Video

> **Link:** [VIDEO LINK TO BE ADDED BEFORE SUBMISSION]

Demo script (8–10 minutes):
1. `mvn test` runs live — 27 tests pass (0:00–1:30)
2. PIT mutation run — score shown (1:30–3:00)
3. App starts (`mvn spring-boot:run`) (3:00–4:00)
4. Login as `demo.manager`, navigate to Approvals tab (4:00–5:00)
5. Click Approve on INV-1006 ($75k) — error with "50000" appears (5:00–6:00)
6. Switch to `demo.director`, approve INV-1007 ($750k) — first approval, stays pending (6:00–7:00)
7. Same director tries again — "different person" error (7:00–8:00)
8. `demo.director2` gives second approval — APPROVED (8:00–9:00)
9. Playwright E2E run — first test passes live (9:00–10:00)
