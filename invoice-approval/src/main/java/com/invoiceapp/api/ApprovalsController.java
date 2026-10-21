package com.invoiceapp.api;

import com.invoiceapp.auth.SessionAuthFilter;
import com.invoiceapp.auth.UserSession;
import com.invoiceapp.domain.ApprovalService;
import com.invoiceapp.domain.Invoice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for invoice approval and rejection.
 * Role and username are read from the authenticated session — never from the request body.
 *
 * Allowed roles: Manager, Director, Approver, Admin.
 * The ApprovalService enforces the per-role amount limits.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalsController {

    private final ApprovalService approvalService;

    public ApprovalsController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    public record RejectRequest(String reason) {}

    @PostMapping("/{invoiceId}/approve")
    public ResponseEntity<?> approve(@PathVariable String invoiceId,
                                     HttpServletRequest request) {
        UserSession session = (UserSession) request.getAttribute(SessionAuthFilter.SESSION_ATTR);

        if (!isApprovalRole(session.role())) {
            return ResponseEntity.status(403).body(new ErrorResponse(
                "Role '" + session.role() + "' is not permitted to approve invoices."));
        }

        try {
            Invoice result = approvalService.approve(invoiceId, session.username(), session.role());
            return ResponseEntity.ok(result);
        } catch (com.invoiceapp.domain.InvoiceNotFoundException ex) {
            return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
        } catch (com.invoiceapp.domain.ApprovalRuleException ex) {
            return ResponseEntity.status(409).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @PostMapping("/{invoiceId}/reject")
    public ResponseEntity<?> reject(@PathVariable String invoiceId,
                                    @RequestBody RejectRequest body,
                                    HttpServletRequest request) {
        UserSession session = (UserSession) request.getAttribute(SessionAuthFilter.SESSION_ATTR);

        if (!isApprovalRole(session.role())) {
            return ResponseEntity.status(403).body(new ErrorResponse(
                "Role '" + session.role() + "' is not permitted to reject invoices."));
        }

        try {
            Invoice result = approvalService.reject(invoiceId, session.username(), body.reason());
            return ResponseEntity.ok(result);
        } catch (com.invoiceapp.domain.InvoiceNotFoundException ex) {
            return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
        } catch (com.invoiceapp.domain.ApprovalRuleException ex) {
            return ResponseEntity.status(409).body(new ErrorResponse(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    private static boolean isApprovalRole(String role) {
        return role != null && (
            "Manager".equals(role) || "Director".equals(role) ||
            "Approver".equals(role) || "Admin".equals(role));
    }

    public record ErrorResponse(String error) {}
}
