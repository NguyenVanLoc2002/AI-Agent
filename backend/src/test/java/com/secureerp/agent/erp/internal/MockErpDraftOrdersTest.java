package com.secureerp.agent.erp.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.identity.spi.ErpCustomerLink;
import com.secureerp.agent.order.spi.DraftOrderCommand;
import com.secureerp.agent.order.spi.DraftOrderOutcome;
import com.secureerp.agent.order.spi.DraftOrderPort;
import com.secureerp.agent.order.spi.DraftOrderStatus;
import com.secureerp.agent.order.spi.ExternalRef;
import com.secureerp.agent.order.spi.IdempotencyKey;
import com.secureerp.agent.platform.Money;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** T-ERP-01..04 for the Mock ERP draft-order adapter (ERP_CONNECTOR §3.6, §7; EP-07). */
@ExtendWith(OutputCaptureExtension.class)
class MockErpDraftOrdersTest {

    /** Sentinels and markers of the simulated ERP; none may cross the adapter boundary. */
    private static final List<String> FORBIDDEN = List.of("987654321", "123456789", "555444333", "ZZHoney",
            "creditBalance", "purchaseCost", "approver", "SYNTH-CUST", "E-99");

    private static final Pattern FORBIDDEN_METHOD = Pattern.compile(
            "(?i).*(cost|margin|customers|purchasehistory|debt|credit|approve|cancel|release|price|sql|url|filter).*");

    private static final ExternalRef REF = new ExternalRef("BG-ABCDEFGH:1");
    private static final IdempotencyKey KEY = new IdempotencyKey("BG-ABCDEFGH:1");

    private final MockErpConnector connector = new MockErpConnector(MockErpFaultMode.NONE);
    private final MockErpSimulation erp = connector.simulation();
    private final MockErpDraftOrders orders = new MockErpDraftOrders(connector);

    private static DraftOrderCommand command(String customerKey) {
        return new DraftOrderCommand(new ErpCustomerLink(customerKey), REF, List.of(new DraftOrderCommand.Line(
                "SKU-BRK-PAD-A", 2, Money.of(new BigDecimal("555444333"), Currency.getInstance("VND")))));
    }

    @Test
    void tErp01ThePortCannotApproveCancelOrReprice() {
        assertThat(Arrays.stream(DraftOrderPort.class.getMethods()).map(Method::getName))
                .containsExactlyInAnyOrder("create", "findByExternalRef")
                .noneMatch(name -> FORBIDDEN_METHOD.matcher(name).matches());
    }

    @Test
    void tErp02ACreatedDraftCarriesOnlyTheErpDraftNumber(CapturedOutput output) {
        DraftOrderOutcome outcome = orders.create(command(MockErpBackend.CUSTOMER_A), Optional.empty());

        assertThat(outcome).isEqualTo(new DraftOrderOutcome.Created("MOCK-DO-000001"));
        assertThat(erp.draftCount()).isEqualTo(1);
        assertThat(erp.draftExternalRefs()).containsExactly(REF.value());
        assertThat(erp.draftsWithIdempotencyKey()).isZero();
        assertClean(outcome.toString() + output.getAll());
    }

    @ParameterizedTest
    @EnumSource(value = MockErpOrderFault.class, names = {"TIMEOUT_AFTER_COMMIT", "MALFORMED_RESPONSE"})
    void tErp04ALostOrUnreadableAnswerAfterTheErpWroteIsUnknown(MockErpOrderFault fault, CapturedOutput output) {
        erp.failOrders(fault);

        DraftOrderOutcome outcome = orders.create(command(MockErpBackend.CUSTOMER_A), Optional.empty());

        assertThat(outcome).isInstanceOf(DraftOrderOutcome.Unknown.class);
        assertThat(erp.draftCount()).as("the ERP did write the draft").isEqualTo(1);
        assertThat(erp.createCalls()).as("one call, no retry").isEqualTo(1);
        assertClean(outcome.toString() + output.getAll());
    }

    @Test
    void aTimeoutIsUnknownEvenWhenNothingWasWritten() {
        erp.failOrders(MockErpOrderFault.TIMEOUT_BEFORE_COMMIT);

        assertThat(orders.create(command(MockErpBackend.CUSTOMER_A), Optional.empty()))
                .isEqualTo(new DraftOrderOutcome.Unknown(DraftOrderOutcome.UnknownReason.TIMEOUT));
        assertThat(erp.draftCount()).isZero();
        assertThat(erp.createCalls()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = MockErpOrderFault.class, names = {"ERROR_WITH_SENTINEL", "UNLISTED_ERROR"})
    void tErp03AnErrorTheAdapterCannotClassifyIsUnknownAndLeaksNothing(MockErpOrderFault fault,
            CapturedOutput output) {
        erp.failOrders(fault);

        DraftOrderOutcome outcome = orders.create(command(MockErpBackend.CUSTOMER_A), Optional.empty());

        assertThat(outcome).isEqualTo(new DraftOrderOutcome.Unknown(DraftOrderOutcome.UnknownReason.UNCLASSIFIED));
        assertClean(outcome.toString() + output.getAll());
    }

    @Test
    void tErp03AListedRejectionIsACodeWithoutTheErpMessage(CapturedOutput output) {
        erp.failOrders(MockErpOrderFault.REJECT);
        DraftOrderOutcome rejected = orders.create(command(MockErpBackend.CUSTOMER_A), Optional.empty());
        erp.failOrders(MockErpOrderFault.NONE);
        DraftOrderOutcome unknownCustomer = orders.create(command("SYNTH-CUST-NONE"), Optional.empty());

        assertThat(rejected)
                .isEqualTo(new DraftOrderOutcome.Rejected(DraftOrderOutcome.Rejection.CUSTOMER_NOT_ALLOWED));
        assertThat(unknownCustomer).isEqualTo(rejected);
        assertThat(erp.draftCount()).isZero();
        assertClean(rejected.toString() + output.getAll());
    }

    @Test
    void anIdempotencyKeyMakesARepeatedCallReturnTheSameDraft() {
        DraftOrderOutcome first = orders.create(command(MockErpBackend.CUSTOMER_A), Optional.of(KEY));
        DraftOrderOutcome second = orders.create(command(MockErpBackend.CUSTOMER_A), Optional.of(KEY));

        assertThat(second).isEqualTo(first);
        assertThat(erp.draftCount()).isEqualTo(1);
        assertThat(erp.draftsWithIdempotencyKey()).isEqualTo(1);
    }

    /** Why nothing is ever sent twice without the capability: the ERP would hold two drafts (SEC-04). */
    @Test
    void withoutAKeyARepeatedCallWouldCreateADuplicate() {
        orders.create(command(MockErpBackend.CUSTOMER_A), Optional.empty());
        orders.create(command(MockErpBackend.CUSTOMER_A), Optional.empty());

        assertThat(erp.draftCount()).isEqualTo(2);
    }

    @Test
    void lookupFindsADraftByOurReferenceOnly(CapturedOutput output) {
        assertThat(orders.findByExternalRef(REF)).isEmpty();
        erp.failOrders(MockErpOrderFault.TIMEOUT_AFTER_COMMIT);
        orders.create(command(MockErpBackend.CUSTOMER_A), Optional.empty());

        Optional<DraftOrderStatus> found = orders.findByExternalRef(REF);

        assertThat(found).contains(new DraftOrderStatus("MOCK-DO-000001"));
        assertThat(orders.findByExternalRef(new ExternalRef("BG-ZZZZZZZZ:1"))).isEmpty();
        assertThat(erp.lookupCalls()).isEqualTo(3);
        assertClean(found.toString() + output.getAll());
    }

    @Test
    void commandAndLinesDoNotPrintCustomerOrPrices() {
        assertThat(command(MockErpBackend.CUSTOMER_A).toString() + command(MockErpBackend.CUSTOMER_A).lines())
                .doesNotContain("SYNTH-CUST").doesNotContain("555444333").doesNotContain("SKU-BRK");
    }

    private static void assertClean(String text) {
        for (String value : FORBIDDEN) {
            assertThat(text).doesNotContain(value);
        }
    }
}
