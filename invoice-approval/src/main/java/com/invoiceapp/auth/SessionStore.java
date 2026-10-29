package com.invoiceapp.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps signed-in sessions in memory, keyed by an opaque bearer token.
 */
@Component
public class SessionStore {

    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String create(UserSession session) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes).toUpperCase();
        sessions.put(token, session);
        return token;
    }

    public UserSession get(String token) {
        return token == null ? null : sessions.get(token);
    }

    public void remove(String token) {
        if (token != null) sessions.remove(token);
    }
}
