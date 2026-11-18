package com.secureerp.agent.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque reference to the customer bound to the authenticated session (ERP_CONNECTOR §2).
 *
 * <p>RESTRICTED. Only the {@code identity} module can create one: the factory is package-private,
 * and ARCH-08 fails the build if any other module calls the constructor. It is never taken from a
 * request, never placed in an egress DTO (ARCH-05) and never printed ({@link #toString()} is
 * redacted).
 *
 * <p>The only constructor takes a private marker argument, so JSON libraries cannot auto-detect a
 * creator and turn a client-supplied string into a reference (architecture review M4(d): Jackson
 * did exactly that with a single-String constructor).
 *
 * <p>SEC-01a only introduces the type and its guards. Session authentication and the channel
 * binding lookup ({@code CustomerLinkPort}) belong to ADR-010, which is still PROPOSED. How identity
 * builds a reference from an spi result is an OPEN DECISION (architecture review H1).
 */
public final class AuthenticatedCustomerRef {

    private static final Pattern OPAQUE = Pattern.compile("^cref_[A-Za-z0-9_-]{16,64}$");

    /** Proof that the caller is the factory in this package. */
    private enum Issued {
        BY_IDENTITY
    }

    private final String opaqueValue;

    private AuthenticatedCustomerRef(String opaqueValue, Issued issued) {
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(opaqueValue, "opaqueValue");
        if (!OPAQUE.matcher(opaqueValue).matches()) {
            throw new IllegalArgumentException("invalid customer reference format");
        }
        this.opaqueValue = opaqueValue;
    }

    static AuthenticatedCustomerRef fromVerifiedBinding(String opaqueValue) {
        return new AuthenticatedCustomerRef(opaqueValue, Issued.BY_IDENTITY);
    }

    /** For {@link ErpCustomerKeys} only (same package). */
    String opaqueValue() {
        return opaqueValue;
    }

    /**
     * SHA-256 (hex) of the reference, for binding server-side rows (quote, quote handle) to the
     * customer without storing the reference itself (ADR-008). Not reversible to the ERP key.
     */
    public String bindingDigest() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("apas:customer-binding:" + opaqueValue).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuthenticatedCustomerRef ref && opaqueValue.equals(ref.opaqueValue);
    }

    @Override
    public int hashCode() {
        return opaqueValue.hashCode();
    }

    @Override
    public String toString() {
        return "AuthenticatedCustomerRef[redacted]";
    }
}
