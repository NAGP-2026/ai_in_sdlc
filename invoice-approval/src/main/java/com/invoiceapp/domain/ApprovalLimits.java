package com.invoiceapp.domain;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Central source of truth for approval thresholds and role authority checks.
 * Never hard-code the $50,000 or $500,000 values outside this class.
 */
public final class ApprovalLimits {

    /** Maximum invoice amount a Manager (or legacy Approver) may approve alone. */
    public static final BigDecimal MANAGER_LIMIT = new BigDecimal("50000");

    /** Maximum invoice amount a Director (or Admin) may approve alone. */
    public static final BigDecimal DIRECTOR_LIMIT = new BigDecimal("500000");

    public static final String MANAGER_ROLE  = "Manager";
    public static final String DIRECTOR_ROLE = "Director";

    // Roles with Director-level (or higher) approval authority
    private static final Set<String> DIRECTOR_AUTHORITY_ROLES = Set.of("Director", "Admin");

    // Roles with at least Manager-level approval authority
    private static final Set<String> MANAGER_AUTHORITY_ROLES  = Set.of("Manager", "Director", "Admin", "Approver");

    private ApprovalLimits() { /* utility class — no instances */ }

    /** Returns true when the role carries Director-level (or higher) approval authority. */
    public static boolean hasDirectorAuthority(String role) {
        return DIRECTOR_AUTHORITY_ROLES.contains(role);
    }

    /** Returns true when the role carries at least Manager-level approval authority. */
    public static boolean hasManagerAuthority(String role) {
        return MANAGER_AUTHORITY_ROLES.contains(role);
    }
}
