package com.invoiceapp.domain;

/**
 * Thrown when an operation is requested on an invoice ID that does not exist.
 * Maps to HTTP 404 Not Found in the API layer.
 */
public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(String invoiceId) {
        super("Invoice " + invoiceId + " was not found.");
    }
}
