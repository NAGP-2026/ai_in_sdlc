package com.invoiceapp.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * In-memory fake repository for unit tests.
 * Mutates invoice objects in-place (same reference returned by findById),
 * so tests see state changes without needing a real Update call to persist.
 */
public class FakeInvoiceRepository implements InvoiceRepository {

    private final List<Invoice> store;

    public FakeInvoiceRepository(Invoice... seed) {
        this.store = new ArrayList<>(Arrays.asList(seed));
    }

    @Override
    public List<Invoice> findAll() {
        return new ArrayList<>(store);
    }

    @Override
    public Optional<Invoice> findById(String id) {
        return store.stream().filter(i -> id.equals(i.getId())).findFirst();
    }

    @Override
    public Invoice save(Invoice invoice) {
        store.add(invoice);
        return invoice;
    }

    @Override
    public void update(Invoice invoice) {
        // The object is mutated in-place; findById returns the same reference,
        // so tests see changes automatically without a real replace call.
    }

    @Override
    public boolean deleteById(String id) {
        return store.removeIf(i -> id.equals(i.getId()));
    }
}
