# AGENTS.md — Invoice Approval Service (Java/Spring Boot)

This file is the context document for any AI agent working on this repository.
Read every section before writing code or tests.

---

## 1. What this project is

A Spring Boot REST API that manages invoice approvals with role-based amount limits.
The app is intentionally simple: no database (in-memory store), no OAuth — just
bearer-token sessions and a set of strict approval rules.

---

## 2. Approval rules (source of truth)

| Rule | Detail |
|------|--------|
| Manager limit  | May approve invoices with amount ≤ $50,000 |
| Director limit | May approve invoices with amount ≤ $500,000 |
| High-value dual approver | Amount > $500,000 requires **two distinct Directors** |
| Same-user guard | The same person **cannot count as both approvers** on one invoice |
| Failed attempt is clean | A rejected attempt **must leave the invoice completely unchanged** |
| Reject has no amount limit | Any authorised user may reject any pending invoice regardless of amount |
| Reason required | `reject` throws `IllegalArgumentException` if reason is null/blank |

**Role hierarchy** (defined in `ApprovalLimits`):
- `Manager` → Manager authority (≤ $50k)
- `Director` → Director authority (≤ $500k; eligible as dual approver)
- `Approver` (legacy) → treated as Manager
- `Admin` → treated as Director

All thresholds live in `ApprovalLimits.MANAGER_LIMIT` and `ApprovalLimits.DIRECTOR_LIMIT`.
**Never hard-code $50,000 or $500,000 anywhere else.**

---

## 3. Package layout

```
com.invoiceapp
├── domain/          ← pure business logic; no Spring dependencies except @Service
│   ├── ApprovalLimits.java      (constants + authority checks)
│   ├── ApprovalService.java     (THE approval engine — all rules live here)
│   ├── Invoice.java             (entity)
│   ├── InvoiceStatus.java       (DRAFT | PENDING_APPROVAL | APPROVED | REJECTED)
│   ├── InvoiceRepository.java   (interface)
│   ├── ApprovalRuleException    (→ HTTP 409)
│   └── InvoiceNotFoundException (→ HTTP 404)
├── infrastructure/  ← in-memory repository (thread-safe)
├── auth/            ← bearer-token session management
├── api/             ← REST controllers (thin — delegate to domain)
└── InvoiceAppApplication.java
```

**The layering rule:** `domain` knows nothing about Spring MVC or HTTP.
Controllers are thin; they extract session from request attribute and delegate.

---

## 4. Demo credentials

| Username | Password | Role |
|---|---|---|
| `demo.manager`   | `ChangeMe123!` | Manager  |
| `demo.director`  | `ChangeMe123!` | Director |
| `demo.director2` | `ChangeMe123!` | Director |
| `demo.approver`  | `ChangeMe123!` | Approver (legacy) |
| `demo.admin`     | `ChangeMe123!` | Admin    |

---

## 5. Test command

```bash
mvn test -f invoice-approval/pom.xml
```

Mutation testing (PIT):
```bash
mvn org.pitest:pitest-maven:mutationCoverage -f invoice-approval/pom.xml
```

E2E (Playwright — requires app running on :8080):
```bash
cd invoice-approval/e2e && npm ci && npx playwright test
```

---

## 6. Scenarios every agent must cover

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Manager approves ≤ $50k | APPROVED |
| 2 | Manager attempts > $50k | throws ApprovalRuleException; invoice unchanged |
| 3 | Director approves $50k–$500k | APPROVED |
| 4 | Director approves ≤ $50k | APPROVED (subsumes Manager) |
| 5 | Manager attempts > $500k | throws; invoice unchanged (no firstApprover set) |
| 6 | Director first approval on > $500k | PENDING_APPROVAL; firstApprover set; decidedBy null |
| 7 | Same Director attempts second approval | throws "different person"; invoice unchanged |
| 8 | Manager attempts second approval on > $500k | throws "Director"; invoice unchanged |
| 9 | Different Director gives second approval | APPROVED; firstApprover preserved in audit trail |
| 10 | Reject any amount | REJECTED; no amount check |

---

## 7. Review expectations

When reviewing a PR on this repo, flag:
- Any approval rule bypassed by the implementation
- Any test that passes for the wrong reason (no real assertion)
- `@Service` or HTTP concerns leaking into `domain/`
- Hard-coded dollar amounts outside `ApprovalLimits`
- Mutation that is not killed by any test
