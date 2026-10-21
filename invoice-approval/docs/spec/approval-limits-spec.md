# Feature Specification: Approval Limits by Role

**Tool:** BMAD Method  
**Version:** 1.0  
**Author:** AI-assisted (GitHub Copilot via Cline)  
**Date:** 21 October 2026

---

## 1. Problem Statement

The invoice approval service currently allows any authenticated user to approve any invoice regardless of amount or role. The business requires role-based approval limits to prevent unauthorized commitment of funds.

## 2. Acceptance Criteria

| ID | Criterion | Priority |
|----|-----------|----------|
| AC-1 | A Manager may approve invoices with amount ≤ $50,000 | MUST |
| AC-2 | A Manager is rejected with a clear message for invoices > $50,000 | MUST |
| AC-3 | A Director may approve invoices with amount ≤ $500,000 | MUST |
| AC-4 | A Director is rejected with a clear message for invoices > $500,000 (solo) | MUST |
| AC-5 | Invoices > $500,000 require exactly two Director approvals | MUST |
| AC-6 | The two approvers must be distinct users | MUST |
| AC-7 | A failed approval attempt leaves the invoice completely unchanged | MUST |
| AC-8 | A rejected attempt returns a clear human-readable reason | MUST |
| AC-9 | Reject does not enforce amount limits | SHOULD |
| AC-10 | Legacy "Approver" role is treated as Manager level | SHOULD |
| AC-11 | "Admin" role is treated as Director level | SHOULD |

## 3. Data Model Changes

### Invoice (additions)

| Field | Type | Description |
|-------|------|-------------|
| `firstApprover` | String (nullable) | Username of first approver on high-value invoices |
| `firstApproverRole` | String (nullable) | Role of first approver (audit trail) |

## 4. Business Rules

### Rule B1: Manager Limit
```
IF invoice.amount <= 50000 AND approver.role IN [Manager, Approver]
  THEN approve
ELSE IF invoice.amount <= 50000 AND approver.role IN [Director, Admin]
  THEN approve (Director authority subsumes Manager)
ELSE
  THROW ApprovalRuleException("Manager limit: $50,000")
```

### Rule B2: Director Limit (single approval)
```
IF invoice.amount IN (50000, 500000] AND approver.role IN [Director, Admin]
  THEN approve
ELSE
  THROW ApprovalRuleException("Exceeds Manager limit; only Director may approve")
```

### Rule B3: High-Value Dual Approval
```
IF invoice.amount > 500000:
  IF invoice.firstApprover == null:
    IF approver.role NOT IN [Director, Admin]:
      THROW "Only Director may give first approval"
    SET invoice.firstApprover = approver.username
    RETURN invoice (still PENDING_APPROVAL)
  ELSE:
    IF approver.username == invoice.firstApprover:   ← check identity FIRST
      THROW "Must be a different person"
    IF approver.role NOT IN [Director, Admin]:
      THROW "Second approver must be a Director"
    APPROVE
```

## 5. Error Messages (required format)

All error messages must:
1. State what rule was violated
2. State the relevant limit in dollars
3. Not expose internal state (e.g. passwords, tokens)

## 6. Non-Goals

- This feature does NOT change the rejection flow
- This feature does NOT add email notifications
- This feature does NOT add persistent storage (demo app stays in-memory)
