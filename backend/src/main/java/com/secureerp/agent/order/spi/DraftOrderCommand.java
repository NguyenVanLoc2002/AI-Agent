package com.secureerp.agent.order.spi;

import com.secureerp.agent.identity.spi.ErpCustomerLink;
import com.secureerp.agent.platform.Money;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A draft order for the ERP (ERP_CONNECTOR §3.6): the ERP customer, our reference and the confirmed
 * quote lines with their price snapshot. RESTRICTED (customer key, customer prices):
 * {@link #toString()} is redacted and the command is never stored or logged.
 */
public record DraftOrderCommand(ErpCustomerLink customer, ExternalRef externalRef, List<Line> lines) {

    public static final int MAX_LINES = 20;

    public DraftOrderCommand {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(externalRef, "externalRef");
        lines = List.copyOf(lines);
        if (lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("draft order needs 1 to 20 lines");
        }
    }

    public record Line(String sku, int quantity, Money unitPrice) {

        private static final Pattern SKU = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,31}$");

        public Line {
            if (sku == null || !SKU.matcher(sku).matches()) {
                throw new IllegalArgumentException("invalid sku format");
            }
            if (quantity < 1 || quantity > 9999) {
                throw new IllegalArgumentException("quantity out of range");
            }
            Objects.requireNonNull(unitPrice, "unitPrice");
        }

        @Override
        public String toString() {
            return "Line[redacted]";
        }
    }

    @Override
    public String toString() {
        return "DraftOrderCommand[" + lines.size() + " lines, redacted]";
    }
}
