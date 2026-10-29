package com.invoiceapp.infrastructure;

import com.invoiceapp.domain.Invoice;
import com.invoiceapp.domain.InvoiceRepository;
import com.invoiceapp.domain.InvoiceStatus;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe in-memory invoice store. Seeded with demo invoices including
 * high-value ones to demonstrate role-based limits and dual-approver flow.
 */
@Repository
public class InMemoryInvoiceRepository implements InvoiceRepository {

    private final Object lock = new Object();
    private final AtomicInteger nextId = new AtomicInteger(1009);

    private final List<Invoice> store = new ArrayList<>(List.of(
        invoice("INV-1001", "Lakeside School District",      new BigDecimal("4250.00"),      InvoiceStatus.PENDING_APPROVAL),
        invoice("INV-1002", "Hilltop Academy",               new BigDecimal("980.50"),       InvoiceStatus.PENDING_APPROVAL),
        invoice("INV-1003", "Riverside Charter",             new BigDecimal("12300.00"),     InvoiceStatus.APPROVED),
        invoice("INV-1004", "Oakwood Independent",           new BigDecimal("615.75"),       InvoiceStatus.PENDING_APPROVAL),
        invoice("INV-1005", "Maple Grove Prep",              new BigDecimal("310.00"),       InvoiceStatus.DRAFT),
        // High-value invoices to demo role-limit and dual-approver features
        invoice("INV-1006", "Regional Transport Bureau",     new BigDecimal("75000.00"),     InvoiceStatus.PENDING_APPROVAL),
        invoice("INV-1007", "State Infrastructure Fund",     new BigDecimal("750000.00"),    InvoiceStatus.PENDING_APPROVAL),
        invoice("INV-1008", "National Research Alliance",    new BigDecimal("1200000.00"),   InvoiceStatus.PENDING_APPROVAL)
    ));

    private static Invoice invoice(String id, String customer, BigDecimal amount, InvoiceStatus status) {
        Invoice inv = new Invoice(id, customer, amount);
        inv.setStatus(status);
        return inv;
    }

    @Override
    public List<Invoice> findAll() {
        synchronized (lock) {
            return new ArrayList<>(store);
        }
    }

    @Override
    public Optional<Invoice> findById(String id) {
        synchronized (lock) {
            return store.stream().filter(i -> id.equals(i.getId())).findFirst();
        }
    }

    @Override
    public Invoice save(Invoice invoice) {
        synchronized (lock) {
            if (invoice.getId() == null || invoice.getId().isBlank()) {
                invoice.setId("INV-" + nextId.getAndIncrement());
            }
            store.add(invoice);
            return invoice;
        }
    }

    @Override
    public void update(Invoice invoice) {
        synchronized (lock) {
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getId().equals(invoice.getId())) {
                    store.set(i, invoice);
                    return;
                }
            }
        }
    }

    @Override
    public boolean deleteById(String id) {
        synchronized (lock) {
            return store.removeIf(i -> id.equals(i.getId()));
        }
    }
}
