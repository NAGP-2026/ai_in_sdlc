package com.invoiceapp.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Requires a valid bearer token on every /api call except login.
 * Attaches the resolved UserSession to a request attribute for downstream use.
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTR = "userSession";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionStore sessions;

    public SessionAuthFilter(SessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean needsSession = path.startsWith("/api/") && !path.startsWith("/api/auth/login");

        if (needsSession) {
            String header = request.getHeader("Authorization");
            String token = (header != null && header.startsWith(BEARER_PREFIX))
                ? header.substring(BEARER_PREFIX.length()) : null;

            UserSession session = sessions.get(token);

            if (session == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Unauthorized\"}");
                return;
            }

            request.setAttribute(SESSION_ATTR, session);
        }

        chain.doFilter(request, response);
    }
}
