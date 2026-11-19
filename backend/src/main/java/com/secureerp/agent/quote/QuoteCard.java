package com.secureerp.agent.quote;

import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.platform.Money;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The quote as the authenticated customer sees it (T-UC03-RENDER, ADR-008): built on the server
 * from the stored quote, never from model output, never added to the model's history. RESTRICTED
 * (customer prices): it is not an egress type and must not be passed to the gateway (ARCH-05).
 *
 * @param notes fixed server-side wording: "Giá chưa gồm VAT" (ADR-014) and the fitment disclaimer
 *              (A-31)
 * @param confirmationToken the server token of the confirm control of this card (EP-07, UC-04): the
 *              customer sends it back with the quote reference and version to confirm. Only the latest
 *              rendered card of a quote version holds a valid one; it never reaches the model
 */
public record QuoteCard(String quoteRef, int version, List<Line> lines, Money total, Instant validUntil,
        List<String> notes, String confirmationToken) {

    public static final String NOTE_VAT = "Giá chưa gồm VAT";
    public static final String NOTE_FITMENT =
            "Báo giá theo mã phụ tùng; vui lòng kiểm tra phụ tùng phù hợp với xe trước khi đặt hàng";

    public QuoteCard {
        Objects.requireNonNull(quoteRef, "quoteRef");
        lines = List.copyOf(lines);
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(validUntil, "validUntil");
        notes = List.copyOf(notes);
        Objects.requireNonNull(confirmationToken, "confirmationToken");
    }

    public record Line(Sku sku, int quantity, Money unitPrice, Money lineTotal) {

        public Line {
            Objects.requireNonNull(sku, "sku");
            Objects.requireNonNull(unitPrice, "unitPrice");
            Objects.requireNonNull(lineTotal, "lineTotal");
        }

        /** The SKU code as text, so the conversation render does not depend on {@code catalog.spi} (EP-04). */
        public String skuCode() {
            return sku.value();
        }
    }

    @Override
    public String toString() {
        return "QuoteCard[" + quoteRef + " v" + version + ", " + lines.size() + " lines]";
    }
}
