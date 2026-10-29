package com.invoiceapp.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Core invoice entity. Mutable so the service can update status fields in-place
 * (the in-memory repository works on the same object reference).
 */
public class Invoice {

    private String id;
    private String customerName;
    private BigDecimal amount;
    private InvoiceStatus status = InvoiceStatus.DRAFT;
    private Instant createdAt = Instant.now();

    /** Username of the final (or only) approver / rejecter. */
    private String decidedBy;

    /** Timestamp of the final decision. */
    private Instant decidedAt;

    /** Reason provided when the invoice was rejected or approval was refused. */
    private String decisionNote;

    /**
     * Username of the first approver on a high-value invoice (amount > $500,000).
     * Null until the first approval is recorded; the invoice stays in PENDING_APPROVAL
     * until a second, distinct Director also approves.
     */
    private String firstApprover;

    /** Role of the first approver, kept for audit purposes. */
    private String firstApproverRole;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Invoice() {}

    public Invoice(String id, String customerName, BigDecimal amount) {
        this.id = id;
        this.customerName = customerName;
        this.amount = amount;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }

    public String getFirstApprover() { return firstApprover; }
    public void setFirstApprover(String firstApprover) { this.firstApprover = firstApprover; }

    public String getFirstApproverRole() { return firstApproverRole; }
    public void setFirstApproverRole(String firstApproverRole) { this.firstApproverRole = firstApproverRole; }
}
