package com.invoiceapp.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApprovalService — plain JUnit 5, no Spring context.
 *
 * Coverage matrix (matches the 10 scenarios in AGENTS.md):
 *
 *   Single-approver limits
 *     1.  Manager approves ≤ $50k                            → APPROVED
 *     2.  Manager attempts > $50k                            → throws, invoice unchanged
 *     3.  Director approves $50k–$500k                       → APPROVED
 *     4.  Director approves ≤ $50k (subsumes Manager auth)   → APPROVED
 *     5.  Manager attempts > $500k                           → throws, invoice unchanged
 *   Two-approver rule
 *     6.  Director first approval on > $500k                 → PENDING_APPROVAL, firstApprover set
 *     7.  Same Director attempts second approval             → throws (same-user rule), invoice unchanged
 *     8.  Manager attempts second approval on > $500k        → throws (role rule), invoice unchanged
 *     9.  Different Director gives second approval            → APPROVED
 *   Rejection
 *     10. Reject does NOT enforce amount limits
 *   Legacy / edge cases
 *     11. Approver role (legacy) is treated as Manager-level
 *     12. Admin role is treated as Director-level
 *     13. Unknown role cannot approve any invoice
 *     14. Submit moves DRAFT → PENDING_APPROVAL
 *     15. Approve on a DRAFT throws
 *     16. Approve on non-existent invoice throws InvoiceNotFoundException
 *     17. Reject requires a non-blank reason
 */
class ApprovalServiceTest {

    // ── Test factories ─────────────────────────────────────────────────────────

    private static Invoice pendingInvoice(String id, String amount) {
        Invoice inv = new Invoice(id, "Test School", new BigDecimal(amount));
        inv.setStatus(InvoiceStatus.PENDING_APPROVAL);
        return inv;
    }

    private static Invoice pendingInvoiceWithFirstApproval(String id, String amount, String firstApprover) {
        Invoice inv = pendingInvoice(id, amount);
        inv.setFirstApprover(firstApprover);
        inv.setFirstApproverRole(ApprovalLimits.DIRECTOR_ROLE);
        return inv;
    }

    private static ApprovalService serviceFor(Invoice... invoices) {
        return new ApprovalService(new FakeInvoiceRepository(invoices));
    }

    // ── 1. Manager approves within Manager limit ───────────────────────────────

    @Test
    @DisplayName("1a: Manager approves invoice at exact Manager limit ($50,000)")
    void manager_approvesInvoiceAtExactManagerLimit() {
        Invoice invoice = pendingInvoice("INV-1", "50000");
        Invoice result = serviceFor(invoice).approve("INV-1", "mgr.alice", ApprovalLimits.MANAGER_ROLE);

        assertEquals(InvoiceStatus.APPROVED, result.getStatus());
        assertEquals("mgr.alice", result.getDecidedBy());
        assertNotNull(result.getDecidedAt());
    }

    @Test
    @DisplayName("1b: Manager approves invoice well below Manager limit")
    void manager_approvesInvoiceBelowManagerLimit() {
        Invoice invoice = pendingInvoice("INV-1", "10000");
        Invoice result = serviceFor(invoice).approve("INV-1", "mgr.alice", ApprovalLimits.MANAGER_ROLE);

        assertEquals(InvoiceStatus.APPROVED, result.getStatus());
    }

    // ── 2. Manager rejected above Manager limit ────────────────────────────────

    @Test
    @DisplayName("2a: Manager is rejected when invoice exceeds $50,000 — invoice unchanged")
    void manager_throwsWhenAmountExceedsManagerLimit() {
        Invoice invoice = pendingInvoice("INV-1", "50001");
        ApprovalService service = serviceFor(invoice);

        ApprovalRuleException ex = assertThrows(ApprovalRuleException.class,
            () -> service.approve("INV-1", "mgr.alice", ApprovalLimits.MANAGER_ROLE));

        assertTrue(ex.getMessage().contains("Manager limit") || ex.getMessage().contains("50000"),
            "Error message must mention the Manager limit");

        // Invoice must be completely unchanged
        assertEquals(InvoiceStatus.PENDING_APPROVAL, invoice.getStatus());
        assertNull(invoice.getDecidedBy());
        assertNull(invoice.getFirstApprover());
    }

    @Test
    @DisplayName("2b: Manager rejection message mentions the $50,000 limit clearly")
    void manager_throwsWithClearMessageMentioningLimit() {
        Invoice invoice = pendingInvoice("INV-1", "60000");

        ApprovalRuleException ex = assertThrows(ApprovalRuleException.class,
            () -> serviceFor(invoice).approve("INV-1", "mgr.alice", ApprovalLimits.MANAGER_ROLE));

        assertTrue(ex.getMessage().contains("50000"),
            "Message should mention 50000. Actual: " + ex.getMessage());
    }

    // ── 3. Director approves between Manager and Director limit ────────────────

    @Test
    @DisplayName("3a: Director approves invoice between $50,000 and $500,000")
    void director_approvesInvoiceBetweenManagerAndDirectorLimit() {
        Invoice invoice = pendingInvoice("INV-1", "250000");
        Invoice result = serviceFor(invoice).approve("INV-1", "dir.bob", ApprovalLimits.DIRECTOR_ROLE);

        assertEquals(InvoiceStatus.APPROVED, result.getStatus());
        assertEquals("dir.bob", result.getDecidedBy());
    }

    @Test
    @DisplayName("3b: Director approves invoice at exact Director limit ($500,000)")
    void director_approvesInvoiceAtExactDirectorLimit() {
        Invoice invoice = pendingInvoice("INV-1", "500000");
        Invoice result = serviceFor(invoice).approve("INV-1", "dir.bob", ApprovalLimits.DIRECTOR_ROLE);

        assertEquals(InvoiceStatus.APPROVED, result.getStatus());
    }

    // ── 4. Director subsumes Manager authority ─────────────────────────────────

    @Test
    @DisplayName("4: Director can also approve invoices below the Manager limit")
    void director_canApproveInvoiceBelowManagerLimit() {
        Invoice invoice = pendingInvoice("INV-1", "5000");
        Invoice result = serviceFor(invoice).approve("INV-1", "dir.bob", ApprovalLimits.DIRECTOR_ROLE);

        assertEquals(InvoiceStatus.APPROVED, result.getStatus());
    }

    // ── 5. Manager rejected above Director limit ───────────────────────────────

    @Test
    @DisplayName("5: Manager is rejected on a > $500,000 invoice — invoice completely unchanged")
    void manager_throwsWhenAmountExceedsDirectorLimit() {
        Invoice invoice = pendingInvoice("INV-1", "500001");
        ApprovalService service = serviceFor(invoice);

        assertThrows(ApprovalRuleException.class,
            () -> service.approve("INV-1", "mgr.alice", ApprovalLimits.MANAGER_ROLE));

        // Nothing may change — especially not firstApprover
        assertEquals(InvoiceStatus.PENDING_APPROVAL, invoice.getStatus());
        assertNull(invoice.getDecidedBy());
        assertNull(invoice.getFirstApprover());
    }

    // ── 6. Director first approval on high-value invoice ──────────────────────

    @Test
    @DisplayName("6: Director's first approval on > $500k sets firstApprover; invoice stays PENDING_APPROVAL")
    void director_firstApprovalOnHighValueInvoiceRecordsFirstApproverAndStaysPending() {
        Invoice invoice = pendingInvoice("INV-1", "500001");
        Invoice result = serviceFor(invoice).approve("INV-1", "dir.alice", ApprovalLimits.DIRECTOR_ROLE);

        assertEquals(InvoiceStatus.PENDING_APPROVAL, result.getStatus(),
            "Invoice must stay PENDING_APPROVAL after first approval");
        assertEquals("dir.alice", result.getFirstApprover());
        assertEquals(ApprovalLimits.DIRECTOR_ROLE, result.getFirstApproverRole());
        assertNull(result.getDecidedBy(), "decidedBy must remain null until full approval");
    }

    // ── 7. Same user cannot be both approvers ─────────────────────────────────

    @Test
    @DisplayName("7a: Same Director is rejected as second approver — same-user rule")
    void sameDirector_throwsWhenAttemptingSecondApproval() {
        Invoice invoice = pendingInvoiceWithFirstApproval("INV-1", "750000", "dir.alice");
        ApprovalService service = serviceFor(invoice);

        ApprovalRuleException ex = assertThrows(ApprovalRuleException.class,
            () -> service.approve("INV-1", "dir.alice", ApprovalLimits.DIRECTOR_ROLE));

        assertTrue(ex.getMessage().contains("dir.alice"),
            "Error must name the original approver");
        assertTrue(ex.getMessage().contains("different person"),
            "Error must mention 'different person'");

        // Invoice must be completely unchanged
        assertEquals(InvoiceStatus.PENDING_APPROVAL, invoice.getStatus());
        assertEquals("dir.alice", invoice.getFirstApprover());
        assertNull(invoice.getDecidedBy());
    }

    @Test
    @DisplayName("7b: Same-user check fires BEFORE role check — gives most helpful error")
    void sameUserCheck_precedesRoleCheck() {
        Invoice invoice = pendingInvoiceWithFirstApproval("INV-1", "750000", "dir.alice");

        // dir.alice is a Director (would normally be eligible) — but identity check fires first
        ApprovalRuleException ex = assertThrows(ApprovalRuleException.class,
            () -> serviceFor(invoice).approve("INV-1", "dir.alice", ApprovalLimits.DIRECTOR_ROLE));

        assertTrue(ex.getMessage().contains("different person"),
            "Must say 'different person', not the Director-only message");
        assertFalse(ex.getMessage().contains("second approver must be a Director"),
            "Must NOT show the Director-role message when same-user is the real problem");
    }

    // ── 8. Non-Director cannot be second approver ──────────────────────────────

    @Test
    @DisplayName("8: Manager is rejected as second approver on > $500k invoice — role rule")
    void manager_throwsWhenAttemptingSecondApprovalOnHighValueInvoice() {
        Invoice invoice = pendingInvoiceWithFirstApproval("INV-1", "750000", "dir.alice");
        ApprovalService service = serviceFor(invoice);

        ApprovalRuleException ex = assertThrows(ApprovalRuleException.class,
            () -> service.approve("INV-1", "mgr.bob", ApprovalLimits.MANAGER_ROLE));

        assertTrue(ex.getMessage().contains("Director"),
            "Error must mention that a Director is required");

        // First approver must still be intact, no final decision
        assertEquals(InvoiceStatus.PENDING_APPROVAL, invoice.getStatus());
        assertEquals("dir.alice", invoice.getFirstApprover());
        assertNull(invoice.getDecidedBy());
    }

    // ── 9. Different Director completes dual approval ──────────────────────────

    @Test
    @DisplayName("9: Different Director completes dual approval on > $500k invoice")
    void differentDirector_completesApprovalOnHighValueInvoice() {
        Invoice invoice = pendingInvoiceWithFirstApproval("INV-1", "750000", "dir.alice");
        Invoice result = serviceFor(invoice).approve("INV-1", "dir.bob", ApprovalLimits.DIRECTOR_ROLE);

        assertEquals(InvoiceStatus.APPROVED, result.getStatus());
        assertEquals("dir.bob", result.getDecidedBy());
        assertNotNull(result.getDecidedAt());
        assertEquals("dir.alice", result.getFirstApprover(), "Audit trail must preserve firstApprover");
    }

    // ── 10. Reject does not enforce amount limits ──────────────────────────────

    @Test
    @DisplayName("10a: Manager can reject a > $500,000 invoice without any amount check")
    void reject_managerCanRejectHighValueInvoice() {
        Invoice invoice = pendingInvoice("INV-1", "1000000");
        Invoice result = serviceFor(invoice).reject("INV-1", "mgr.alice", "Duplicate submission");

        assertEquals(InvoiceStatus.REJECTED, result.getStatus());
        assertEquals("Duplicate submission", result.getDecisionNote());
        assertEquals("mgr.alice", result.getDecidedBy());
    }

    @Test
    @DisplayName("10b: Reject records reason, approver, timestamp, and status")
    void reject_recordsAllFields() {
        Invoice invoice = pendingInvoice("INV-1", "5000");
        Invoice result = serviceFor(invoice).reject("INV-1", "dir.alice", "Budget exceeded");

        assertEquals(InvoiceStatus.REJECTED, result.getStatus());
        assertEquals("Budget exceeded", result.getDecisionNote());
        assertEquals("dir.alice", result.getDecidedBy());
        assertNotNull(result.getDecidedAt());
    }

    // ── 11. Legacy Approver role (Manager-equivalent) ──────────────────────────

    @Test
    @DisplayName("11a: Legacy Approver role can approve within Manager limit")
    void legacyApprover_canApproveWithinManagerLimit() {
        Invoice invoice = pendingInvoice("INV-1", "30000");
        Invoice result = serviceFor(invoice).approve("INV-1", "demo.approver", "Approver");

        assertEquals(InvoiceStatus.APPROVED, result.getStatus());
    }

    @Test
    @DisplayName("11b: Legacy Approver role is rejected above Manager limit")
    void legacyApprover_throwsAboveManagerLimit() {
        Invoice invoice = pendingInvoice("INV-1", "55000");

        assertThrows(ApprovalRuleException.class,
            () -> serviceFor(invoice).approve("INV-1", "demo.approver", "Approver"));
    }

    // ── 12. Admin role (Director-equivalent) ──────────────────────────────────

    @Test
    @DisplayName("12a: Admin role can approve within Director limit")
    void admin_canApproveWithinDirectorLimit() {
        Invoice invoice = pendingInvoice("INV-1", "300000");
        Invoice result = serviceFor(invoice).approve("INV-1", "demo.admin", "Admin");

        assertEquals(InvoiceStatus.APPROVED, result.getStatus());
    }

    @Test
    @DisplayName("12b: Admin role can act as first approver on > $500k invoice")
    void admin_canActAsFirstApproverOnHighValueInvoice() {
        Invoice invoice = pendingInvoice("INV-1", "750000");
        Invoice result = serviceFor(invoice).approve("INV-1", "demo.admin", "Admin");

        assertEquals(InvoiceStatus.PENDING_APPROVAL, result.getStatus());
        assertEquals("demo.admin", result.getFirstApprover());
    }

    // ── 13. Unknown role ───────────────────────────────────────────────────────

    @Test
    @DisplayName("13: Unknown role is rejected even for a small invoice")
    void unknownRole_throwsForAnyInvoice() {
        Invoice invoice = pendingInvoice("INV-1", "100");

        assertThrows(ApprovalRuleException.class,
            () -> serviceFor(invoice).approve("INV-1", "stranger", "Stranger"));
    }

    // ── 14. Submit ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("14a: Submit moves DRAFT to PENDING_APPROVAL")
    void submit_movesDraftToPendingApproval() {
        Invoice invoice = new Invoice("INV-1", "Test", new BigDecimal("100"));
        // Default status is DRAFT
        Invoice result = serviceFor(invoice).submit("INV-1");

        assertEquals(InvoiceStatus.PENDING_APPROVAL, result.getStatus());
    }

    @Test
    @DisplayName("14b: Submit throws when invoice is already PENDING_APPROVAL")
    void submit_throwsWhenNotDraft() {
        Invoice invoice = pendingInvoice("INV-1", "100");

        assertThrows(ApprovalRuleException.class, () -> serviceFor(invoice).submit("INV-1"));
    }

    // ── 15. Approve on wrong status ────────────────────────────────────────────

    @Test
    @DisplayName("15: Approve throws when invoice is still DRAFT")
    void approve_throwsWhenInvoiceIsStillDraft() {
        Invoice invoice = new Invoice("INV-1", "Test", new BigDecimal("100"));

        assertThrows(ApprovalRuleException.class,
            () -> serviceFor(invoice).approve("INV-1", "dir.alice", ApprovalLimits.DIRECTOR_ROLE));
    }

    // ── 16. Missing invoice ────────────────────────────────────────────────────

    @Test
    @DisplayName("16: Approve throws InvoiceNotFoundException when invoice does not exist")
    void approve_throwsWhenInvoiceDoesNotExist() {
        assertThrows(InvoiceNotFoundException.class,
            () -> serviceFor().approve("INV-404", "dir.alice", ApprovalLimits.DIRECTOR_ROLE));
    }

    // ── 17. Reject requires reason ─────────────────────────────────────────────

    @Test
    @DisplayName("17a: Reject throws when reason is blank whitespace")
    void reject_throwsWhenReasonIsWhitespace() {
        Invoice invoice = pendingInvoice("INV-1", "5000");

        assertThrows(IllegalArgumentException.class,
            () -> serviceFor(invoice).reject("INV-1", "dir.alice", "   "));
    }

    @Test
    @DisplayName("17b: Reject throws when reason is empty string")
    void reject_throwsWhenReasonIsEmpty() {
        Invoice invoice = pendingInvoice("INV-1", "5000");

        assertThrows(IllegalArgumentException.class,
            () -> serviceFor(invoice).reject("INV-1", "dir.alice", ""));
    }

    @Test
    @DisplayName("17c: Reject throws when reason is null")
    void reject_throwsWhenReasonIsNull() {
        Invoice invoice = pendingInvoice("INV-1", "5000");

        assertThrows(IllegalArgumentException.class,
            () -> serviceFor(invoice).reject("INV-1", "dir.alice", null));
    }
}
