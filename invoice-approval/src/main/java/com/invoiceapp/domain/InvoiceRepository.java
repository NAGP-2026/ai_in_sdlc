package com.invoiceapp.domain;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository {
    List<Invoice> findAll();
    Optional<Invoice> findById(String id);
    Invoice save(Invoice invoice);
    void update(Invoice invoice);
    boolean deleteById(String id);
}
