package com.invoiceapp.domain;

/**
 * Thrown when an approval attempt violates a business rule
 * (over-limit, same-user dual approval, wrong role for second approver, etc.).
 * Maps to HTTP 409 Conflict in the API layer.
 * The invoice is NEVER mutated before this exception is thrown.
 */
public class ApprovalRuleException extends RuntimeException {
    public ApprovalRuleException(String message) {
        super(message);
    }
}
