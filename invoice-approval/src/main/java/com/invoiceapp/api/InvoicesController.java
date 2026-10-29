package com.invoiceapp.api;

import com.invoiceapp.auth.SessionAuthFilter;
import com.invoiceapp.auth.UserSession;
import com.invoiceapp.domain.ApprovalService;
import com.invoiceapp.domain.Invoice;
import com.invoiceapp.domain.InvoiceRepository;
import com.invoiceapp.domain.InvoiceStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
public class InvoicesController {

    private final InvoiceRepository repository;
    private final ApprovalService approvalService;

    public InvoicesController(InvoiceRepository repository, ApprovalService approvalService) {
        this.repository = repository;
        this.approvalService = approvalService;
    }

    public record CreateRequest(String customerName, BigDecimal amount) {}

    @GetMapping
    public List<Invoice> list() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<Invoice> create(@RequestBody CreateRequest request) {
        if (request.customerName() == null || request.customerName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().build();
        }
        Invoice invoice = new Invoice();
        invoice.setCustomerName(request.customerName());
        invoice.setAmount(request.amount());
        invoice.setStatus(InvoiceStatus.DRAFT);
        return ResponseEntity.ok(repository.save(invoice));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submit(@PathVariable String id) {
        try {
            return ResponseEntity.ok(approvalService.submit(id));
        } catch (Exception e) {
            return ResponseEntity.status(409).body(new ErrorResponse(e.getMessage()));
        }
    }

    public record ErrorResponse(String error) {}
}
