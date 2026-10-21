package com.invoiceapp.infrastructure;

import com.invoiceapp.domain.ApprovalService;
import com.invoiceapp.domain.InvoiceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that wires domain services.
 * Keeping @Bean declarations here lets the domain package stay Spring-free.
 */
@Configuration
public class InfrastructureConfig {

    @Bean
    public ApprovalService approvalService(InvoiceRepository repository) {
        return new ApprovalService(repository);
    }
}
