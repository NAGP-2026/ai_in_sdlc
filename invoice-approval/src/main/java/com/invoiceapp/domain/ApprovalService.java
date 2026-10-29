package com.invoiceapp.domain;

import java.time.Instant;

/**
 * Moves an invoice through its lifecycle: DRAFT → PENDING_APPROVAL → APPROVED or REJECTED.
 *
 * Approval limits (enforced via {@link ApprovalLimits}):
 *   • Manager (or legacy Approver): may approve invoices up to $50,000.
 *   • Director (or Admin): may approve invoices up to $500,000.
 *   • Above $500,000: requires two distinct approvers; the second must be a Director.
 *   • The same user may never count as both approvers on one invoice.
 *   • A rejected attempt leaves the invoice completely unchanged.
 *
 * NOTE: This class is intentionally free of Spring annotations so the domain layer
 * has no compile-time dependency on the Spring framework.
 * The Spring bean is registered in {@code InfrastructureConfig}.
 */
public class ApprovalService {

    private final InvoiceRepository repository;

    public ApprovalService(InvoiceRepository repository) {
        this.repository = repository;
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    public Invoice submit(String invoiceId) {
        Invoice invoice = find(invoiceId);

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new ApprovalRuleException(
                "Invoice " + invoiceId + " is " + invoice.getStatus() +
                "; only DRAFT invoices can be submitted.");
        }

        invoice.setStatus(InvoiceStatus.PENDING_APPROVAL);
        repository.update(invoice);
        return invoice;
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    /**
     * Approves an invoice, enforcing role-based amount limits and the dual-approver rule.
     * Throws {@link ApprovalRuleException} with a clear reason on any rule violation;
     * the invoice is never mutated before the exception is thrown.
     *
     * @param invoiceId    ID of the invoice to approve.
     * @param approver     Username of the approver (from session).
     * @param approverRole Role of the approver (from session).
     */
    public Invoice approve(String invoiceId, String approver, String approverRole) {
        Invoice invoice = find(invoiceId);
        ensurePending(invoice);

        int cmpManager  = invoice.getAmount().compareTo(ApprovalLimits.MANAGER_LIMIT);
        int cmpDirector = invoice.getAmount().compareTo(ApprovalLimits.DIRECTOR_LIMIT);

        if (cmpManager <= 0) {
            enforceManagerAuthority(approverRole);
            return finaliseApproval(invoice, approver);
        }

        if (cmpDirector <= 0) {
            enforceDirectorAuthority(approverRole, invoice);
            return finaliseApproval(invoice, approver);
        }

        // Amount > $500,000: dual-approver path
        return approveHighValue(invoice, approver, approverRole);
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    public Invoice reject(String invoiceId, String approver, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to reject an invoice.");
        }

        Invoice invoice = find(invoiceId);
        ensurePending(invoice);

        invoice.setStatus(InvoiceStatus.REJECTED);
        invoice.setDecidedBy(approver);
        invoice.setDecidedAt(Instant.now());
        invoice.setDecisionNote(reason);
        repository.update(invoice);
        return invoice;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Handles the two-approver path for invoices above the Director limit.
     * All guard clauses throw BEFORE any mutation so a failed attempt is always clean.
     */
    private Invoice approveHighValue(Invoice invoice, String approver, String approverRole) {

        if (invoice.getFirstApprover() == null) {
            // First approval: must be a Director; record and stay pending.
            enforceDirectorAuthorityForHighValue(approverRole);

            invoice.setFirstApprover(approver);
            invoice.setFirstApproverRole(approverRole);
            repository.update(invoice);

            // Invoice is still PENDING_APPROVAL — the caller sees firstApprover set
            // but status unchanged; a second distinct Director must still approve.
            return invoice;
        }

        // Second approval: check IDENTITY before ROLE — gives the most helpful error.
        if (approver.equals(invoice.getFirstApprover())) {
            throw new ApprovalRuleException(
                "Invoice " + invoice.getId() + " already has a first approval from '" +
                invoice.getFirstApprover() + "'. The second approver must be a different person.");
        }

        if (!ApprovalLimits.hasDirectorAuthority(approverRole)) {
            throw new ApprovalRuleException(
                "Invoice amount of $" + invoice.getAmount().toPlainString() +
                " exceeds the Director limit of $" + ApprovalLimits.DIRECTOR_LIMIT.toPlainString() +
                ". The second approver must be a Director.");
        }

        return finaliseApproval(invoice, approver);
    }

    private static void enforceManagerAuthority(String approverRole) {
        if (!ApprovalLimits.hasManagerAuthority(approverRole)) {
            throw new ApprovalRuleException(
                "Role '" + approverRole + "' does not have approval authority. " +
                "A Manager or Director is required to approve invoices up to $" +
                ApprovalLimits.MANAGER_LIMIT.toPlainString() + ".");
        }
    }

    private static void enforceDirectorAuthority(String approverRole, Invoice invoice) {
        if (!ApprovalLimits.hasDirectorAuthority(approverRole)) {
            throw new ApprovalRuleException(
                "Invoice amount of $" + invoice.getAmount().toPlainString() +
                " exceeds the Manager limit of $" + ApprovalLimits.MANAGER_LIMIT.toPlainString() +
                ". Only a Director may approve this invoice.");
        }
    }

    private static void enforceDirectorAuthorityForHighValue(String approverRole) {
        if (!ApprovalLimits.hasDirectorAuthority(approverRole)) {
            throw new ApprovalRuleException(
                "Invoice amount exceeds the Director limit of $" +
                ApprovalLimits.DIRECTOR_LIMIT.toPlainString() +
                " and requires two Director approvals. Only a Director may provide the first approval.");
        }
    }

    private Invoice finaliseApproval(Invoice invoice, String approver) {
        invoice.setStatus(InvoiceStatus.APPROVED);
        invoice.setDecidedBy(approver);
        invoice.setDecidedAt(Instant.now());
        repository.update(invoice);
        return invoice;
    }

    private Invoice find(String invoiceId) {
        return repository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
    }

    private static void ensurePending(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.PENDING_APPROVAL) {
            throw new ApprovalRuleException(
                "Invoice " + invoice.getId() + " is " + invoice.getStatus() +
                "; only PENDING_APPROVAL invoices can be decided.");
        }
    }
}
