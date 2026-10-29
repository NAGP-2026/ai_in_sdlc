package com.invoiceapp.api;

import com.invoiceapp.auth.SessionStore;
import com.invoiceapp.auth.UserSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Handles sign-in and sign-out. Does NOT require a session (no auth filter applied here).
 * Demo credentials are hardcoded in {@link DemoUser#HARDCODED_USERS} — never use real passwords here.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final List<DemoUser> users;
    private final SessionStore sessions;

    public AuthController(SessionStore sessions) {
        this.sessions = sessions;
        this.users = DemoUser.HARDCODED_USERS;
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, String username, String role) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return users.stream()
            .filter(u -> u.username().equals(request.username()) &&
                         u.password().equals(request.password()))
            .findFirst()
            .map(u -> {
                String token = sessions.create(new UserSession(u.username(), u.role()));
                return ResponseEntity.ok(new LoginResponse(token, u.username(), u.role()));
            })
            .orElseGet(() ->
                ResponseEntity.status(401)
                    .body(new LoginResponse(null, null, null)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            sessions.remove(auth.substring("Bearer ".length()));
        }
        return ResponseEntity.noContent().build();
    }

    /** Static demo credentials — never use real passwords here. */
    public record DemoUser(String username, String password, String role) {
        public static final List<DemoUser> HARDCODED_USERS = List.of(
            new DemoUser("demo.approver",  "ChangeMe123!", "Approver"),
            new DemoUser("demo.admin",     "ChangeMe123!", "Admin"),
            new DemoUser("demo.manager",   "ChangeMe123!", "Manager"),
            new DemoUser("demo.director",  "ChangeMe123!", "Director"),
            new DemoUser("demo.director2", "ChangeMe123!", "Director")
        );
    }
}
