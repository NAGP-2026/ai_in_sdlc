# AI Interaction Log — Invoice Approval Feature
## Traceability Record for AI-Assisted Development

> **Machine-readable version:** `AI_INTERACTION_LOG.jsonl` (one JSON object per session)  
> **Project:** invoice-approval — Java 17 / Spring Boot 3.2  
> **Date range:** 21 October 2026  
> **Primary AI tool:** Cline (VS Code extension) — Agent execution mode  
> **Model:** claude-sonnet-4-5  
> **Total sessions:** 6 | **Total prompts:** 14 | **Total files changed:** 26

---

## Tool & Agent Configuration

| Field | Value |
|-------|-------|
| AI Tool | Cline (VS Code extension, free tier via GitHub Copilot API) |
| AI Model | claude-sonnet-4-5 |
| Agent Type | Custom multi-agent pipeline (SpecAgent → DomainAgent → APIAgent → E2EAgent → ReviewAgent) |
| Execution Mode | Agent (autonomous file creation, terminal commands, iterative refinement) |
| Custom Instructions | `invoice-approval/AGENTS.md` loaded as system context at start of every session |
| Hooks | Structured JSON output block appended to each session response (see `AI_INTERACTION_LOG.jsonl`) |
| MCP Servers | None used |
| External Tools | `mvn` (compile, test, mutation), `git` (commits, diff), `curl` (smoke tests), `npx` (Playwright) |

---

## Session S-001 — Context Setup & BMAD Specification

**Timestamp:** 2026-10-21 10:05 IST  
**Sub-agent:** SpecAgent  
**Execution mode:** Agent  

### Prompt submitted
```
I have an AI in SDLC assignment. I need to add approval limits by role to an
invoice approval service in Java Spring Boot.

Rules:
- Manager may approve up to $50,000
- Director may approve up to $500,000
- Above $500,000 needs two distinct approvers, second must be Director
- Same user cannot count as both approvers
- Failed attempt leaves invoice unchanged with a clear reason

Build the project in invoice-approval/ directory. Start by writing AGENTS.md
so any AI agent arriving cold understands the domain, then produce a BMAD
specification and task breakdown.
```

### AI Actions & Outputs
- Ran `mvn --version` to detect Java 17 / Maven 3.9 from environment
- Created `AGENTS.md` with: approval rules table, package layout diagram, demo credentials, test command (`mvn test`), 10 required scenarios, review expectations
- Created `docs/spec/approval-limits-spec.md` — BMAD format: Goal, Actors, Business Rules B1–B3, Acceptance Criteria AC-1 to AC-11
- Created `docs/spec/task-breakdown.md` — T-1 to T-10 tasks mapped to two-step commit strategy

### Human Intervention
| Action | Reason |
|--------|--------|
| Added instruction to AGENTS.md: *"identity check must fire BEFORE role check"* | Agent's first draft spec did not mention this ordering constraint — it is the rule the assignment says agents most often get wrong |
| Added AC-7b to spec: *"when a Director who already approved attempts second approval, error says 'different person' not 'you need Director role'"* | Without this explicit AC, no test would ever verify the ordering |

### Execution Evidence
- **Files produced:** `AGENTS.md`, `docs/spec/approval-limits-spec.md`, `docs/spec/task-breakdown.md`
- **Tests:** 0 added / 0 passing (spec-only session)
- **Commit:** `9a1b4e1` feat: add spec and AGENTS.md for approval-limits feature

### Outcome
✅ **Success with human additions** — Agent produced a solid spec skeleton; human added the critical ordering constraint that would have caused a gap in step 2.

---

## Session S-002 — Step 1: Single-Approver Limits

**Timestamp:** 2026-10-21 10:45 IST  
**Sub-agent:** DomainAgent  
**Execution mode:** Agent  

### Prompt submitted
```
Implement Step 1 only: single-approver limits.
Manager can approve invoices <= $50,000.
Director can approve invoices <= $500,000.
Use a pure domain class ApprovalService with NO Spring annotations.
Inject InvoiceRepository as a constructor parameter.
Add JUnit 5 unit tests for every rule: exact limits, one dollar over,
Manager/Director exact boundary.
Do NOT implement the dual-approver rule yet — just throw a clear exception
for amounts above Director limit.
```

### Follow-up Prompts (agent corrections required)
```
Follow-up 1:
The test for managerApproveAtExactLimit should also assert the invoice status
is APPROVED after success, not just that no exception was thrown.

Follow-up 2:
Add a test that verifies the invoice status is still PENDING_APPROVAL after
a rejected attempt — the clean-fail guarantee is a key requirement.
```

### AI Actions & Outputs
- Created `ApprovalLimits.java` — `MANAGER_LIMIT = 50_000`, `DIRECTOR_LIMIT = 500_000`, `hasManagerAuthority()`, `hasDirectorAuthority()`
- Created `Invoice.java` — id, vendorName, amount, status, firstApprover, firstApproverRole, decidedAt, decisionNote
- Created `InvoiceStatus.java` enum — DRAFT, PENDING_APPROVAL, APPROVED, REJECTED
- Created `InvoiceRepository.java` interface — findById, save, update
- Created `ApprovalRuleException.java`, `InvoiceNotFoundException.java`
- Created `ApprovalService.java` — Manager branch (≤50k), Director branch (≤500k), high-value stub
- Created `FakeInvoiceRepository.java` test double
- Created 8 unit tests covering Manager/Director exact limits, over-limit rejections

### Human Intervention
| Action | Reason |
|--------|--------|
| Follow-up prompt #1 | Agent's first test for `managerApproveAtExactLimit` only called the method and asserted no exception — it never checked the invoice status was APPROVED |
| Follow-up prompt #2 | Agent did not write the clean-fail guarantee test. This is one of the two most important negative tests |
| Manual check | Read each test method name against the 10 required scenarios in AGENTS.md to confirm coverage |

### Execution Evidence
- **Files produced:** 8 Java source files + partial ApprovalServiceTest.java
- **Test run:** `mvn test` → **8 tests, 0 failures** ✅
- **Commit:** `a2103e7` feat(step-1): single-approver limits — Manager ≤50k, Director ≤500k

### Outcome
✅ **Success with corrections** — Two follow-up prompts required. Key lesson: agent produces passing tests but may miss the *assertion content* (what you check, not just that no exception was thrown).

---

## Session S-003 — Step 2: Dual-Approver Rule

**Timestamp:** 2026-10-21 11:30 IST  
**Sub-agent:** DomainAgent  
**Execution mode:** Agent  

### Prompt submitted
```
Now implement the dual-approver rule for invoices above $500,000.

Requirements:
- First approval by any Director sets firstApprover field, invoice stays PENDING
- Same user as firstApprover is blocked — identity check must fire BEFORE role
  check so the error message says "same person" not "you need Director role"
- Second approver must be a Director
- Second distinct Director finalises approval to APPROVED

Add JUnit 5 tests for scenarios 6-10:
- First Director approval (invoice stays PENDING)
- Same-Director blocked as second approver
- Non-Director second-approver blocked
- Identity-before-role ordering test (test 7b)
- Successful dual-approval — two different Directors
```

### Follow-up Prompts
```
Follow-up 1:
The identityCheckFiresBeforeRoleCheck test is checking the exception message —
make it assert the message CONTAINS "same person" or "different person", not
just that an exception was thrown.

Follow-up 2:
Add an explicit test where a Manager attempts to be the second approver —
this should fail with a ROLE error, not an identity error.
```

### AI Actions & Outputs
- Extended `ApprovalService.java` — added private `approveHighValue()` method
- Identity guard: `if (approverUsername.equals(invoice.getFirstApprover())) throw new ApprovalRuleException("must be from a different person")`
- Role guard (after identity guard): `if (!hasDirectorAuthority(approverRole)) throw new ApprovalRuleException("second approver must be a Director")`
- Identity check is line 89; role check is line 95 — ordering verified
- Added 19 more unit tests — total now 27

### Human Intervention
| Action | Reason |
|--------|--------|
| Follow-up prompt #1 | Test `identityCheckFiresBeforeRoleCheck` only asserted an exception was thrown — message was not checked. This would not catch an agent that puts the checks in the wrong order |
| Follow-up prompt #2 | `managerCannotBeSecondApproverOnHighValueInvoice` was not in the agent's first draft |
| Manual code read | Physically read lines 85–100 of `ApprovalService.java` to confirm identity check (line 89) comes before role check (line 95) |

### Execution Evidence
- **Files modified:** `ApprovalService.java`, `ApprovalServiceTest.java`
- **Test run:** `mvn test` → **27 tests, 0 failures** ✅
- **Commit:** `5a7b15e` (combined with API layer)

### Outcome
✅ **Success with corrections** — Ordering was implemented correctly by the agent, but the test would not have verified it without human follow-up. This is the exact failure mode the assignment warns about.

---

## Session S-004 — REST API Layer, Session Auth & Static UI

**Timestamp:** 2026-10-21 13:00 IST  
**Sub-agent:** APIAgent  
**Execution mode:** Agent  

### Prompt submitted
```
Wire up the Spring Boot REST API.

Create:
- AuthController: POST /api/auth/login and /logout using hardcoded demo users
  (NO @Value injection — keep credentials in a DemoUser inner class)
- SessionStore: ConcurrentHashMap<String,UserSession>
- SessionAuthFilter: OncePerRequestFilter; reads Authorization bearer token;
  attaches UserSession to request attribute "user"
- ApprovalsController: reads username/role from session; delegates to ApprovalService
- InvoicesController: GET /api/invoices, POST /api/invoices, POST /api/invoices/{id}/submit
- InfrastructureConfig: @Bean for InMemoryInvoiceRepository and ApprovalService
  (ApprovalService must have NO @Service annotation — domain must not depend on Spring)
- Static HTML/JS/CSS UI seeded with invoices INV-1001 to INV-1007 covering all test scenarios
- application.properties: server.port=8080
```

### Follow-up Prompts
```
Follow-up 1:
The AuthController has an unused @Value("${demo.users}") parameter — remove it.
Credentials must come from the DemoUser inner class only.

Follow-up 2:
Move @Service off ApprovalService domain class — domain should have zero
Spring dependency. Wire it as a @Bean in InfrastructureConfig instead.
```

### AI Actions & Outputs
- Created `InvoiceAppApplication.java` (@SpringBootApplication)
- Created `AuthController.java` with DemoUser inner record: demo.manager/MANAGER, demo.director/DIRECTOR, demo.director2/DIRECTOR, demo.admin/ADMIN
- Created `SessionStore.java`, `SessionAuthFilter.java`, `UserSession.java`
- Created `ApprovalsController.java` — approve and reject endpoints
- Created `InvoicesController.java` — list, create, submit
- Created `InMemoryInvoiceRepository.java` seeded with 7 invoices
- Created `InfrastructureConfig.java` with @Bean for both services
- Created `index.html`, `app.js`, `styles.css`

### Human Intervention
| Action | Reason |
|--------|--------|
| Follow-up prompt #1 — removed @Value | Agent included `@Value("${demo.users}")` in AuthController even though the prompt said not to — agent default pattern |
| Follow-up prompt #2 — removed @Service | Agent added `@Service` to `ApprovalService` which introduces a Spring dependency in the domain layer |
| Manual smoke test via curl | `curl -X POST localhost:8080/api/auth/login -d '{"username":"demo.manager","password":"ChangeMe123!"}' -H "Content-Type: application/json"` — confirmed 200 + token |

### Execution Evidence
- **Files produced:** 13 files (see JSONL)
- **Files modified:** `ApprovalService.java` (removed @Service), `AuthController.java` (removed @Value)
- **Test run:** `mvn test` → **27 tests, 0 failures** ✅ (API layer does not affect unit tests)
- **Commit:** `5a7b15e`

### Outcome
✅ **Success with corrections** — Two human-prompted fixes (findings #1 and #5 from PR review). Both were agent-default patterns that violate the explicit constraints in the prompt.

---

## Session S-005 — Playwright E2E Tests

**Timestamp:** 2026-10-21 14:30 IST  
**Sub-agent:** E2EAgent  
**Execution mode:** Agent  

### Prompt submitted
```
Write Playwright E2E tests for the invoice approval UI at http://localhost:8080.
Use TypeScript. Write exactly 5 tests:

1. Manager approval of INV-1006 ($75,000) is rejected — assert error message
   contains "50000" AND assert invoice status remains PENDING_APPROVAL in the
   Invoices tab after the rejected attempt (clean-fail guarantee E2E proof)
2. Director first approval of INV-1007 ($750,000) — invoice stays PENDING
3. Same Director blocked as second approver — error says "different person"
4. Manager blocked as second approver on high-value — error mentions role
5. Manager reject of an over-limit invoice — reject always works regardless of amount
```

### Follow-up Prompts
```
Follow-up 1:
playwright.config.ts should set baseURL to http://localhost:8080 and use a
single chromium project only — remove webkit and firefox.

Follow-up 2:
Test 1 must navigate to the Invoices tab AFTER the rejection and assert the
status column still shows PENDING — the post-rejection status check is the
critical assertion, not just the error banner.
```

### AI Actions & Outputs
- Created `e2e/package.json` with `@playwright/test` dependency
- Created `e2e/playwright.config.ts` — baseURL `http://localhost:8080`, chromium only, 2 retries
- Created `e2e/tests/approval-limits.spec.ts` — 5 tests; test 1 includes post-rejection navigation to Invoices tab and status assertion

### Human Intervention
| Action | Reason |
|--------|--------|
| Follow-up prompt #1 | Agent used default 3-browser config; not needed for this assignment |
| Follow-up prompt #2 | Test 1 first draft only checked the error banner — did not navigate to Invoices tab to verify status unchanged. This is the key E2E assertion |
| Manual locator review | Read `index.html` and `app.js` to verify the CSS selectors the agent used (`[data-invoice-id]`, `.error-banner`, `.status-cell`) match actual DOM |

### Execution Evidence
- **Files produced:** `e2e/package.json`, `e2e/playwright.config.ts`, `e2e/tests/approval-limits.spec.ts`
- **5 E2E tests written** (require running app to execute)
- **Commit:** `5a7b15e`

### Outcome
✅ **Success with corrections** — The most important assertion (post-rejection invoice status) was missing from the agent's first draft. This is the *exact* failure mode the assignment calls out: "looks right and is not."

---

## Session S-006 — PIT Mutation Testing & PR Review

**Timestamp:** 2026-10-21 16:00 IST  
**Sub-agent:** ReviewAgent  
**Execution mode:** Agent  

### Prompt submitted
```
Run PIT mutation testing against the domain layer and report the score.
Then review the PR diff (git diff HEAD~1 HEAD). For every finding, assign
one of three verdicts: fixed, dismissed with reason, or deferred with reason.
For each surviving mutation, explain why it survived and whether it represents
a real gap.
```

### Follow-up Prompts
```
Follow-up 1:
For each surviving mutation, explain WHY it survived and whether it represents
a real gap or an acceptable survivor. Be specific about which line and what
mutation was injected.

Follow-up 2:
Update ASSIGNMENT.md with the before/after mutation scores and the PR review
findings table with verdicts.
```

### AI Actions & Outputs
- Ran: `mvn org.pitest:pitest-maven:mutationCoverage -f invoice-approval/pom.xml`
- **Result:** 57 mutations generated, 50 killed, **88% mutation score**, 92% line coverage, 91% test strength
- Analysed 7 survivors:
  - 5 × `VoidMethodCallMutator` — removed calls to `setDecidedAt()`, `setDecisionNote()`, `setFirstApproverRole()` (audit fields, no business logic)
  - 2 × `NO_COVERAGE` — paths in seeded-data helper methods not exercised by unit tests
- Produced PR review with 5 findings and verdicts
- Updated `ASSIGNMENT.md` and `AI_INTERACTION_LOG.md`

### Human Intervention
| Action | Reason |
|--------|--------|
| Manual review of each survivor | Read `target/pit-reports/com.invoiceapp.domain/ApprovalService.java.html` to confirm surviving mutations are on audit setters only |
| Verdict decisions | Agent suggested all as "acceptable" — human independently confirmed findings #1 and #5 must be **Fixed**, not just noted |
| Final verification | Re-ran `mvn test` after applying fixes to confirm still 27/27 passing |

### PR Review Findings with Verdicts

| # | Finding | Verdict | Action / Reason |
|---|---------|---------|-----------------|
| 1 | `AuthController` injects `@Value("${demo.users}")` — parameter is injected but never read | **Fixed** | Removed @Value; credentials defined in DemoUser inner class |
| 2 | `InvoicesController.submit()` has no ownership check — any authenticated user can submit any draft | **Dismissed** | Demo app scope. Production would add `invoice.getOwner().equals(session.getUsername())` check |
| 3 | `FakeInvoiceRepository.update()` is a no-op | **Dismissed** | Tests operate on the same object reference; mutations to the Invoice are immediately visible without a separate update call. Documented in class Javadoc |
| 4 | E2E tests share mutable seeded state — later tests depend on earlier tests' side-effects | **Deferred** | A `DELETE /api/invoices/reset` endpoint in test mode would fix this. Out of scope for this demo |
| 5 | `@Service` on `ApprovalService` introduces Spring dependency in domain layer | **Fixed** | Removed @Service from domain class; added @Bean in InfrastructureConfig |

### Mutation Score Summary

| Mutator | Generated | Killed | Rate |
|---------|-----------|--------|------|
| ConditionalsBoundaryMutator | 2 | 2 | **100%** |
| NegateConditionalsMutator | 12 | 12 | **100%** |
| BooleanTrueReturnValsMutator | 2 | 2 | **100%** |
| BooleanFalseReturnValsMutator | 2 | 2 | **100%** |
| NullReturnValsMutator | 14 | 13 | 93% |
| EmptyObjectReturnValsMutator | 6 | 5 | 83% |
| VoidMethodCallMutator | 19 | 14 | 74% |
| **TOTAL** | **57** | **50** | **88%** |

> The 5 VoidMethodCall survivors are all on audit setters (`setDecidedAt`, `setDecisionNote`, `setFirstApproverRole`). These carry no business logic. Dismissed in PR review.

### Execution Evidence
- **Command:** `mvn org.pitest:pitest-maven:mutationCoverage -f invoice-approval/pom.xml`
- **Report:** `invoice-approval/target/pit-reports/index.html`
- **Line coverage:** 97/105 (92%)
- **Test strength:** 91%
- **Commit:** `5a7b15e`

### Outcome
✅ **Success** — All business-logic mutations killed. Survivors are audit-field setters with no business consequence.

---

## End-to-End Traceability Matrix

| Acceptance Criterion | Unit Test | E2E Test | Mutation Killed? |
|---------------------|-----------|----------|-----------------|
| AC-1: Manager approves ≤$50k | `managerCanApproveAtExactLimit` | — | ✅ |
| AC-2: Manager blocked >$50k | `managerCannotApproveAboveManagerLimit` | Test 1 | ✅ |
| AC-3: Director approves ≤$500k | `directorCanApproveAtExactLimit` | — | ✅ |
| AC-4: Director blocked >$500k (needs 2nd) | `directorFirstApprovalOnHighValue` | Test 2 | ✅ |
| AC-5: Failed attempt leaves invoice unchanged | `managerCannotApproveAboveManagerLimit` (asserts status) | Test 1 (navigates to Invoices tab) | ✅ |
| AC-6: Rejected attempt returns clear reason | `managerCannotApproveAboveManagerLimit` (asserts message) | Test 1 (asserts banner) | ✅ |
| AC-7: Same user cannot count twice | `sameDirectorCannotBeSecondApprover` | Test 3 | ✅ |
| AC-7b: Identity check fires before role check | `identityCheckFiresBeforeRoleCheck` | — | ✅ |
| AC-8: Second approver must be Director | `managerCannotBeSecondApproverOnHighValueInvoice` | Test 4 | ✅ |
| AC-9: Dual approval succeeds with two distinct Directors | `twoDifferentDirectorsCanApproveHighValue` | — | ✅ |
| AC-10: Admin role treated same as Director | `adminRoleTreatedAsDirector` | — | ✅ |
| AC-11: Reject always works regardless of amount | `managerCanRejectAnyInvoice` | Test 5 | ✅ |

---

## Summary of Human Oversight Applied

| Session | Agent Failure Mode | Human Action | Impact |
|---------|--------------------|--------------|--------|
| S-001 | Spec omitted identity-before-role ordering constraint | Added AC-7b to spec and AGENTS.md | Without this, no test would verify the ordering |
| S-002 | Test asserted no exception but not invoice status | Follow-up prompt strengthened assertion | Clean-fail guarantee would not have been tested |
| S-002 | Clean-fail test not written at all | Prompted agent explicitly | Key negative test would be missing |
| S-003 | Test checked exception type only, not message | Follow-up prompt added message assertion | Identity-before-role ordering would be unverified |
| S-004 | @Value injected despite prompt saying not to | Follow-up prompt removed unused parameter | Dead code / PR finding #1 |
| S-004 | @Service added to domain class | Follow-up prompt moved to InfrastructureConfig | Layering violation / PR finding #5 |
| S-005 | E2E test 1 only checked error banner | Follow-up prompt added post-rejection status check | The most important E2E assertion was missing |
