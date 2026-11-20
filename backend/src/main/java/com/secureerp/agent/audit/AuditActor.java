package com.secureerp.agent.audit;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Who acted, as audit sees it: the pseudonymous principal and the role name, both from the authenticated
 * session (built by {@code identity}; audit depends on no business module, ARCH-10). Never a name, an
 * e-mail address or a customer key.
 */
public record AuditActor(String pseudonym, String role) {

    private static final Pattern PSEUDONYM = Pattern.compile("^usr_[A-Za-z0-9_-]{22}$");
    /** RBAC_MATRIX roles; kept in line with {@code identity.Role} and the V14 check constraint. */
    static final Set<String> ROLES = Set.of("CUST", "SALES", "OPS", "SECREV");

    public AuditActor {
        if (pseudonym == null || !PSEUDONYM.matcher(pseudonym).matches()) {
            throw new IllegalArgumentException("invalid audit field: actor");
        }
        if (role == null || !ROLES.contains(role)) {
            throw new IllegalArgumentException("invalid audit field: role");
        }
    }

    @Override
    public String toString() {
        return "AuditActor[" + role + ", redacted]";
    }
}
