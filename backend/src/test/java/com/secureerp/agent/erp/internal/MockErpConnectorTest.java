package com.secureerp.agent.erp.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.catalog.spi.AvailabilityBucket;
import com.secureerp.agent.catalog.spi.AvailabilityBucketPort;
import com.secureerp.agent.catalog.spi.CatalogUnavailableException;
import com.secureerp.agent.catalog.spi.ExactProductQuery;
import com.secureerp.agent.catalog.spi.ProductCandidate;
import com.secureerp.agent.catalog.spi.Provenance;
import com.secureerp.agent.catalog.spi.Sku;
import com.secureerp.agent.catalog.spi.VerifiedProductPort;
import com.secureerp.agent.catalog.spi.Vin;
import com.secureerp.agent.identity.AuthenticatedCustomerRef;
import com.secureerp.agent.identity.ErpCustomerKeys;
import com.secureerp.agent.identity.IdentityFixtures;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.Money;
import com.secureerp.agent.quote.spi.EligibilityStatus;
import com.secureerp.agent.quote.spi.PriceQuote;
import com.secureerp.agent.quote.spi.QuoteSourceUnavailableException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** T-ERP-01..03 for the Mock ERP connector (ERP_CONNECTOR §7), plus search semantics. */
@ExtendWith(OutputCaptureExtension.class)
class MockErpConnectorTest {

    /** Sentinels and markers seeded in MockErpBackend; none may cross the adapter boundary. */
    private static final List<String> FORBIDDEN = List.of(
            "987654321", "123456789", "555444333", "666777888", "ZZHoney", "KHO-SYNTH", "purchaseCost",
            "creditBalance", "customerPrice", "margin", "quantityOnHand", "warehouse", "supplierContact");

    /** ERP_CONNECTOR §4 / ARCH-09 forbidden capabilities, checked by reflection (T-ERP-01). */
    private static final Pattern FORBIDDEN_METHOD = Pattern.compile(
            "(?i).*(cost|margin|customers|purchasehistory|debt|credit|approve|updateprice|sql|url|filter).*");

    private final MockErpConnector connector = new MockErpConnector(MockErpFaultMode.NONE);

    @Test
    void tErp01PortsExposeNoForbiddenCapability() {
        for (Class<?> port : List.of(VerifiedProductPort.class, AvailabilityBucketPort.class)) {
            assertThat(Arrays.stream(port.getMethods()).map(Method::getName))
                    .as(port.getSimpleName())
                    .noneMatch(name -> FORBIDDEN_METHOD.matcher(name).matches());
        }
        assertThat(Arrays.stream(MockErpConnector.class.getMethods()).map(Method::getName))
                .noneMatch(name -> FORBIDDEN_METHOD.matcher(name).matches());
    }

    @Test
    void tErp02ExtraErpFieldsAreStripped(CapturedOutput output) {
        List<ProductCandidate> results = connector.search(new ExactProductQuery.ByOe("04465-0k290"));

        assertThat(results).hasSize(1);
        assertThat(Arrays.stream(ProductCandidate.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("sku", "oe", "category", "provenance", "sourceRef");
        assertNoForbidden(results.toString());
        assertNoForbidden(output.getAll());
    }

    @ParameterizedTest
    @EnumSource(value = MockErpFaultMode.class, names = {"ERROR_WITH_SENTINEL", "TIMEOUT"})
    void tErp03BackendErrorBecomesCodeOnly(MockErpFaultMode mode, CapturedOutput output) {
        MockErpConnector failing = new MockErpConnector(mode);

        for (Runnable call : List.<Runnable>of(
                () -> failing.search(new ExactProductQuery.ByOe("04465-0K290")),
                () -> failing.bucket(new Sku("SKU-BRK-PAD-A")))) {
            assertThatThrownBy(call::run)
                    .isInstanceOf(CatalogUnavailableException.class)
                    .hasNoCause()
                    .hasMessage("SERVICE_UNAVAILABLE")
                    .satisfies(e -> {
                        assertThat(((CatalogUnavailableException) e).code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                        assertNoForbidden(stackTrace(e));
                    });
        }
        assertThat(output.getAll()).contains("erp.catalog.unavailable code=SERVICE_UNAVAILABLE");
        assertNoForbidden(output.getAll());
    }

    @Test
    void duplicateResponsesAreDeduplicated() {
        MockErpConnector duplicating = new MockErpConnector(MockErpFaultMode.DUPLICATE_RESPONSE);
        List<ProductCandidate> results =
                duplicating.search(new ExactProductQuery.ByCategoryAndModel("ma phanh TRUOC", " Vios  2019 "));
        assertThat(results).extracting(c -> c.sku().value()).containsExactly("SKU-BRK-PAD-A", "SKU-BRK-PAD-B");
    }

    @Test
    void malformedRowsAreSkippedWholeAndNotLogged(CapturedOutput output) {
        MockErpConnector malformed = new MockErpConnector(MockErpFaultMode.MALFORMED_ROWS);
        List<ProductCandidate> results = malformed.search(new ExactProductQuery.ByOe("04465-0K290"));
        assertThat(results).extracting(c -> c.sku().value()).containsExactly("SKU-BRK-PAD-A");
        assertThat(output.getAll()).contains("erp.catalog.rows_skipped count=4");
        assertNoForbidden(output.getAll());
    }

    @Test
    void searchIsExactAndBounded() {
        assertThat(connector.search(new ExactProductQuery.ByOe("04465"))).isEmpty();
        assertThat(connector.search(new ExactProductQuery.ByCategoryAndModel("Ma phanh", "Vios 2019"))).isEmpty();
        assertThat(connector.search(new ExactProductQuery.ByCategoryAndModel("Ma phanh truoc", "Vios 2019")))
                .hasSizeLessThanOrEqualTo(ExactProductQuery.MAX_RESULTS);
        assertThatThrownBy(() -> new ExactProductQuery.ByOe("%")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void availabilityIsCoarseAndQuantityIndependent() {
        assertThat(connector.bucket(new Sku("SKU-BRK-PAD-A"))).isEqualTo(AvailabilityBucket.CON_HANG);
        assertThat(connector.bucket(new Sku("SKU-BRK-PAD-B"))).isEqualTo(AvailabilityBucket.CAN_KIEM_TRA);
        assertThat(connector.bucket(new Sku("SKU-OIL-FLT-A"))).isEqualTo(AvailabilityBucket.HET_HANG);
        assertThat(connector.bucket(new Sku("SKU-UNKNOWN"))).isEqualTo(AvailabilityBucket.CAN_KIEM_TRA);
        assertThat(Stream.of(AvailabilityBucketPort.class.getMethods()).filter(m -> m.getName().equals("bucket")))
                .allSatisfy(m -> assertThat(m.getParameterTypes()).containsExactly(Sku.class));
    }

    /**
     * EP-05: VIN fitment. Only {@code vehicleModel} is read from the vehicle row (owner name, phone
     * and credit sentinels are dropped); fitment results are CATALOG_LOOKUP, one label each.
     */
    @Test
    void vinSearchReturnsFitmentAsCatalogLookup(CapturedOutput output) {
        List<ProductCandidate> vios = connector.search(new ExactProductQuery.ByVin(new Vin(MockErpBackend.NA_VIN)));
        List<ProductCandidate> innova = connector.search(new ExactProductQuery.ByVin(new Vin(MockErpBackend.ASIA_VIN)));

        assertThat(vios).extracting(c -> c.sku().value())
                .containsExactly("SKU-BRK-PAD-A", "SKU-BRK-PAD-B", "SKU-OIL-FLT-A");
        assertThat(innova).extracting(c -> c.sku().value()).containsExactly("SKU-AIR-FLT-A");
        assertThat(Stream.concat(vios.stream(), innova.stream()))
                .allSatisfy(c -> assertThat(c.provenance()).isEqualTo(Provenance.CATALOG_LOOKUP));
        assertNoForbidden(vios.toString() + innova);
        assertThat(vios.toString() + innova).doesNotContain(MockErpBackend.NA_VIN, "0399000111");
        assertThat(output.getAll()).doesNotContain(MockErpBackend.NA_VIN, MockErpBackend.ASIA_VIN);
    }

    @Test
    void unknownVinHasNoFitmentAndOtherQueriesDoNotMatchByVin() {
        assertThat(connector.search(new ExactProductQuery.ByVin(new Vin("1ZZAPAS16RA000003")))).isEmpty();
        // OE and category searches still label the synthetic catalog VERIFIED (unchanged, D-05).
        assertThat(connector.search(new ExactProductQuery.ByOe("04465-0K290")))
                .extracting(ProductCandidate::provenance).containsExactly(Provenance.VERIFIED);
    }

    @ParameterizedTest
    @EnumSource(value = MockErpFaultMode.class, names = {"ERROR_WITH_SENTINEL", "TIMEOUT"})
    void vinSearchBackendErrorBecomesCodeOnly(MockErpFaultMode mode, CapturedOutput output) {
        MockErpConnector failing = new MockErpConnector(mode);

        assertThatThrownBy(() -> failing.search(new ExactProductQuery.ByVin(new Vin(MockErpBackend.NA_VIN))))
                .isInstanceOf(CatalogUnavailableException.class)
                .satisfies(e -> assertNoForbidden(stackTrace(e)));
        assertNoForbidden(output.getAll());
        assertThat(output.getAll()).contains("erp.vehicle.unavailable").doesNotContain(MockErpBackend.NA_VIN);
    }

    @Test
    void malformedVehicleRowMeansNoFitment(CapturedOutput output) {
        MockErpConnector malformed = new MockErpConnector(MockErpFaultMode.MALFORMED_ROWS);

        assertThat(malformed.search(new ExactProductQuery.ByVin(new Vin(MockErpBackend.NA_VIN)))).isEmpty();
        assertThat(output.getAll()).contains("erp.vehicle.row_skipped").doesNotContain(MockErpBackend.NA_VIN);
        assertNoForbidden(output.getAll());
    }

    /**
     * EP-06 quote ports. The price row carries cost, margin and a contact; only unit price, currency,
     * price list and revision cross the adapter boundary. The customer key comes from identity only.
     */
    @Test
    void customerPriceKeepsOnlyAllowlistedFields(CapturedOutput output) {
        AuthenticatedCustomerRef customerA = IdentityFixtures.customer("SynthCustomerA");

        PriceQuote price = connector.priceFor(customerA, new Sku("SKU-BRK-PAD-A")).orElseThrow();

        assertThat(ErpCustomerKeys.keyOf(customerA)).isEqualTo(MockErpBackend.CUSTOMER_A);
        assertThat(price.unitPrice().amount()).isEqualByComparingTo("555444333");
        assertThat(price.unitPrice().currency().getCurrencyCode()).isEqualTo("VND");
        assertThat(price.priceSource()).isEqualTo("mock-erp:pl:contract");
        assertThat(price.revision()).isEqualTo("r1");
        assertThat(Arrays.stream(PriceQuote.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("unitPrice", "priceSource", "revision");
        assertThat(price.toString()).doesNotContain("555444333");
        assertThat(connector.priceFor(IdentityFixtures.customer("SynthCustomerB"), new Sku("SKU-BRK-PAD-A"))
                .orElseThrow().unitPrice().amount()).isEqualByComparingTo("666777888");
        assertThat(connector.priceFor(IdentityFixtures.customer("SynthCustomerC"), new Sku("SKU-BRK-PAD-A"))).isEmpty();
        assertThat(connector.priceFor(customerA, new Sku("SKU-UNKNOWN"))).isEmpty();
        assertNoForbidden(output.getAll());
        assertThat(output.getAll()).doesNotContain("555444333", MockErpBackend.CUSTOMER_A);
    }

    /** Backend review M-3: 19 significant digits survive parsing exactly (no double on the way). */
    @Test
    void pricesAreParsedExactly() {
        PriceQuote price = connector.priceFor(IdentityFixtures.customer("SynthCustomerA"), new Sku("SKU-AIR-FLT-A"))
                .orElseThrow();

        assertThat(price.unitPrice().amount()).isEqualTo(new BigDecimal("555444333123456.7891"));
    }

    /** ERP_CONNECTOR §3.3: the revision is optional (D-01). */
    @Test
    void priceRevisionIsOptional() {
        PriceQuote price = new PriceQuote(Money.of(BigDecimal.TEN, Currency.getInstance("VND")), "mock-erp:pl:contract",
                null);

        assertThat(price.revision()).isNull();
        assertThatThrownBy(() -> new PriceQuote(Money.of(BigDecimal.TEN, Currency.getInstance("VND")), "x", "bad rev"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creditStatusIsCoarseAndFailsClosed() {
        assertThat(connector.check(IdentityFixtures.customer("SynthCustomerA"))).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(connector.check(IdentityFixtures.customer("SynthCustomerB"))).isEqualTo(EligibilityStatus.NOT_ELIGIBLE);
        assertThat(connector.check(IdentityFixtures.customer("SynthCustomerC")))
                .isEqualTo(EligibilityStatus.REVIEW_REQUIRED);
    }

    @Test
    void stockQuantityIsInternalAndUnknownMeansNone() {
        assertThat(connector.quantityAvailable(new Sku("SKU-BRK-PAD-A"))).hasValue(12);
        assertThat(connector.quantityAvailable(new Sku("SKU-OIL-FLT-A"))).hasValue(0);
        assertThat(connector.quantityAvailable(new Sku("SKU-UNKNOWN"))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = MockErpFaultMode.class, names = {"ERROR_WITH_SENTINEL", "TIMEOUT"})
    void quotePortFailuresAreCodeOnly(MockErpFaultMode mode, CapturedOutput output) {
        MockErpConnector failing = new MockErpConnector(mode);
        AuthenticatedCustomerRef customerA = IdentityFixtures.customer("SynthCustomerA");

        for (ThrowingCallable call : List.<ThrowingCallable>of(
                () -> failing.priceFor(customerA, new Sku("SKU-BRK-PAD-A")),
                () -> failing.quantityAvailable(new Sku("SKU-BRK-PAD-A")),
                () -> failing.check(customerA))) {
            assertThatThrownBy(call).isInstanceOf(QuoteSourceUnavailableException.class)
                    .satisfies(e -> assertNoForbidden(stackTrace(e)));
        }
        assertNoForbidden(output.getAll());
    }

    @Test
    void malformedPriceRowMeansNoPrice(CapturedOutput output) {
        MockErpConnector malformed = new MockErpConnector(MockErpFaultMode.MALFORMED_ROWS);

        assertThat(malformed.priceFor(IdentityFixtures.customer("SynthCustomerA"), new Sku("SKU-BRK-PAD-A"))).isEmpty();
        assertThat(output.getAll()).contains("erp.price.row_skipped");
        assertNoForbidden(output.getAll());
    }

    private static void assertNoForbidden(String text) {
        for (String token : FORBIDDEN) {
            assertThat(text).as("must not contain a stripped field or sentinel").doesNotContain(token);
        }
    }

    private static String stackTrace(Throwable e) {
        StringWriter out = new StringWriter();
        e.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
