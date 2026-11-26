package com.secureerp.agent.agent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.secureerp.agent.agent.internal.OutputValidator.Rejection;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OUTPUT-ENGLISH-MONEY (A-85). English amounts the model could write: (1A) currencies as words or codes after a number
 * ("45 dollars", "45 EUR") and their symbols or codes before it ("£45", "EUR 45"); (2A) the price words cost, subtotal,
 * fee, discount followed by a digit in the same sentence; (3A) the English teens and tens as number words before a
 * currency ("forty five dollars"). Ordinary English about parts must still pass.
 */
class OutputValidatorEnglishMoneyTest {

    private static final Set<String> NO_CODES = Set.of();

    @ParameterizedTest
    @ValueSource(strings = {
        // 1A: currency words and codes after a number.
        "45 dollars", "1 dollar", "45 Dollars", "45 DOLLARS", "45 bucks", "1 buck", "45 euros", "1 euro", "45 EUR",
        "45 eur", "45 cents", "45 GBP", "45 yen", "45 JPY", "45,5 euros", "45.5 dollars", "45-dollar part",
        "45 dollar(s)", "The part is 45 dollars.", "about 1,200 euros",
        // 1A: symbols and codes before a number.
        "£45", "£ 45", "¥45", "EUR 45", "eur45", "GBP 45", "JPY 4500", "45£", "45¥",
        // 2A: price words then a digit.
        "Cost: 45 dollars", "The cost is 45", "It costs 45", "costs about 45", "Total cost 45", "Costing: 45",
        "Subtotal 45", "Fee: 45", "fee 45", "Fees: 45", "Discount 10%", "discount 45", "COST 45",
        "Cost:\n45",
        // 3A: number words and a currency.
        "forty five dollars", "fifty dollars", "two hundred euros", "twelve bucks", "ninety cents",
        "seventy-five dollars", "thirty euros",
        // Review security H2: a country before dollars.
        "45 US dollars", "45 U.S. dollars", "45 Australian dollars", "forty-five US dollars",
        // Review QA L6, security M: case, hyphenated number words, dollars before a digit, other symbols.
        "45 Yen", "Eur 45", "forty-five euros", "fifty-dollar part", "Dollars: 45", "dollar 45", "₩4500", "₹45",
        "45₹", "99¢",
        // Review QA M1: the sentence rule (a full stop, then no upper-case letter).
        "Fee. 45", "Cost.\n45",
        // Review QA M2, M3: up to 40 characters; a hyphen before a digit.
        "The cost of this brake pad is 45", "The subtotal for the whole order is 45", "Cost-45", "Fee-45",
        // Review security M: forms of "price"; price words before number words.
        "Priced at 45", "Pricing: 45", "prices from 45", "It costs forty-five.", "The fee is twenty-five fifty.",
        "Subtotal: forty five"
    })
    void englishAmountsAreRejected(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.MONEY);
    }

    /** Ordinary English about parts. */
    @ParameterizedTest
    @ValueSource(strings = {
        "cost-effective part", "A cost-effective 2 piece set", "This is a low-cost option", "low-cost 2 options",
        "costing details are handled by staff", "The cost depends on staff", "Ask staff about the cost.",
        "Fee waived", "Fits 2 models", "Euro 5 emission standard", "Euro 6 engine", "Dollar sign",
        "about 45 minutes", "45 km", "45 percent of buyers choose it", "45 centimeters",
        "Total 2 sets", "Total of 2 parts", "Amount of 2 bolts", "Charge 12V battery", "pay 2 visits",
        "worth checking 2 sizes",
        "Pay at the counter", "The fee is set by staff. Two options fit",
        // Review QA M1: an upper-case letter starts the next sentence.
        "Ask about the fee. Fits 2 models", "The cost is set by staff.\nFits 2 models",
        // Review security: "Euro" before a digit is an emission standard; whole words only.
        "Fits Euro 5, Euro 6 engines", "Euro 5 - Euro 6", "2 Euro 6 engines", "45 euroclass"
    })
    void ordinaryEnglishPasses(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /**
     * Documented gaps (A-85): "pound(s)" may be a weight (1A); total, amount, charge, pay, worth often count (2A); a
     * price word after a hyphen ("low-cost") passes, and so does "Low-cost 45"; "Euros 45" ("Euro 6" is a standard);
     * one number word after a price word; amounts without a currency or price word ("45 each"); currency words and
     * codes not in the list, including Vietnamese names of foreign money (review security H1, PROPOSED below in A-85).
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "45 pounds", "Total: 45", "Amount: 45", "Charge: 45", "Pay 45 now", "worth 45", "Low-cost 45", "Late-fee 45",
        "Euros 45", "Discount of ten percent", "That one is 45 each", "45 per unit", "45 AUD", "45 baht", "45 đô",
        "45 đô la", "45 tệ", "45 bảng Anh"
    })
    void documentedGapsAreNotBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).isEmpty();
    }

    /** Accepted false positives (A-85): blocked and handed to staff. */
    @ParameterizedTest
    @ValueSource(strings = {
        "2 cents worth of advice", "Costs vary across 2 sizes", "no extra cost, 2-year warranty",
        "The cost depends on staff and fits 2 models", "2 yen xe"
    })
    void acceptedFalsePositivesAreBlocked(String text) {
        assertThat(OutputValidator.check(text, NO_CODES)).as(text).contains(Rejection.MONEY);
    }

    /** The new alternatives stay fast on the longest allowed answer. */
    @ParameterizedTest
    @ValueSource(strings = {"cost ", "fee a ", "1 ", "forty ", "eur ", "£", "1-", "cost-", "discount x "})
    void longAnswersStayFast(String piece) {
        String text = piece.repeat(OutputValidator.MAX_CHARS / piece.length());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> OutputValidator.check(text, NO_CODES));
    }
}
