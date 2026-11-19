package com.secureerp.agent.order;

import com.secureerp.agent.identity.spi.ErpCustomerLink;
import com.secureerp.agent.platform.Money;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A confirmed quote version to turn into a draft order (DATA_FLOW §2 step 4). Built by quote from
 * its stored snapshot after the confirmation re-check; never from a request body. RESTRICTED
 * (customer key, prices): redacted {@link #toString()}, not persisted by order.
 */
public record DraftOrderSubmission(String quoteRef, int quoteVersion, ErpCustomerLink customer, List<Line> lines) {

    static final Pattern QUOTE_REF = Pattern.compile("^BG-[A-HJ-NP-Z2-9]{8}$");

    public DraftOrderSubmission {
        requireQuote(quoteRef, quoteVersion);
        Objects.requireNonNull(customer, "customer");
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("draft order needs lines");
        }
    }

    static void requireQuote(String quoteRef, int quoteVersion) {
        if (quoteRef == null || !QUOTE_REF.matcher(quoteRef).matches() || quoteVersion < 1) {
            throw new IllegalArgumentException("invalid quote reference or version");
        }
    }

    public record Line(String sku, int quantity, Money unitPrice) {

        public Line {
            Objects.requireNonNull(sku, "sku");
            Objects.requireNonNull(unitPrice, "unitPrice");
        }

        @Override
        public String toString() {
            return "Line[redacted]";
        }
    }

    /**
     * SHA-256 (hex) over the quote version, the ERP customer and every line (amounts compared by value,
     * not scale). Order stores it at registration and refuses to send anything else for that request.
     */
    public String digest() {
        // Every field is length-prefixed, so no value can imitate a separator.
        StringBuilder canonical = new StringBuilder("apas:draft-order:v1");
        field(canonical, quoteRef);
        field(canonical, Integer.toString(quoteVersion));
        field(canonical, customer.value());
        field(canonical, Integer.toString(lines.size()));
        for (Line line : lines) {
            field(canonical, line.sku());
            field(canonical, Integer.toString(line.quantity()));
            field(canonical, line.unitPrice().amount().stripTrailingZeros().toPlainString());
            field(canonical, line.unitPrice().currency().getCurrencyCode());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void field(StringBuilder canonical, String value) {
        canonical.append('|').append(value.length()).append(':').append(value);
    }

    @Override
    public String toString() {
        return "DraftOrderSubmission[" + lines.size() + " lines, redacted]";
    }
}
