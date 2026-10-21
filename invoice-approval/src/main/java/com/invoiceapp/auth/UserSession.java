package com.invoiceapp.auth;

/**
 * Immutable session object stored in the SessionStore.
 * Holds the authenticated user's username and role for the duration of a request.
 */
public record UserSession(String username, String role) {}
