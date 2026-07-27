package xyz.hxwang.jointaccountmanager.spend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BOA, Citi and Discover.
 *
 * <p>The point of these is the sign convention and payment detection. Across
 * five issuers there are now three different conventions — Chase and BOA write
 * purchases negative, Amex and Discover positive, and Citi splits them across
 * two columns — and a card whose payoff is miscounted as spending roughly
 * doubles that month.
 */
class AdditionalBankParserTest {

    private final BoaStatementParser boa = new BoaStatementParser();
    private final CitiStatementParser citi = new CitiStatementParser();
    private final DiscoverStatementParser discover = new DiscoverStatementParser();

    private Reader fixture(String name) {
        InputStream in = getClass().getResourceAsStream("/statements/" + name);
        assertNotNull(in, "missing fixture " + name);
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    private ParsedTxn find(List<ParsedTxn> all, String fragment) {
        return all.stream().filter(t -> t.getDescriptionRaw().contains(fragment)).findFirst()
                .orElseThrow(() -> new AssertionError("no transaction matching " + fragment));
    }

    // ------------------------------------------------------------------ BOA

    @Test
    @DisplayName("BOA: negative is money out, like Chase")
    void boaSignConvention() throws Exception {
        List<ParsedTxn> txns = boa.parse(fixture("boa-sample.csv"));

        ParsedTxn gym = find(txns, "WHOLEHEALTH");
        assertEquals(Direction.DEBIT, gym.getDirection());
        assertEquals(TxnType.PURCHASE, gym.getTxnType());
        assertEquals(0, new BigDecimal("45.00").compareTo(gym.getAmount()));

        ParsedTxn refund = find(txns, "AMTRAK .COM");
        assertEquals(Direction.CREDIT, refund.getDirection());
        assertEquals(TxnType.REFUND, refund.getTxnType());
    }

    @Test
    @DisplayName("BOA: the reference number becomes the identity")
    void boaUsesReferenceNumber() throws Exception {
        ParsedTxn txn = find(boa.parse(fixture("boa-sample.csv")), "WHOLEHEALTH");
        assertEquals("24055226187812670276318", txn.getSourceRef());
        assertEquals("ref:24055226187812670276318", txn.getDedupKey());
    }

    @Test
    @DisplayName("BOA: a payment is recognised despite there being no type column")
    void boaPaymentDetected() throws Exception {
        assertEquals(TxnType.PAYMENT, find(boa.parse(fixture("boa-sample.csv")), "PAYMENT - THANK").getTxnType());
    }

    // ----------------------------------------------------------------- Citi

    @Test
    @DisplayName("Citi: separate debit and credit columns resolve to one amount plus a direction")
    void citiSplitColumns() throws Exception {
        List<ParsedTxn> txns = citi.parse(fixture("citi-sample.csv"));

        ParsedTxn purchase = find(txns, "SWEETGREEN");
        assertEquals(Direction.DEBIT, purchase.getDirection());
        assertEquals(0, new BigDecimal("17.07").compareTo(purchase.getAmount()));

        ParsedTxn payment = find(txns, "AUTOPAY");
        assertEquals(Direction.CREDIT, payment.getDirection());
        assertEquals(TxnType.PAYMENT, payment.getTxnType());
        assertEquals(0, new BigDecimal("492.40").compareTo(payment.getAmount()),
                "the credit column is negative; the stored amount is not");
    }

    @Test
    @DisplayName("Citi: a pending row is not imported")
    void citiSkipsPending() throws Exception {
        List<ParsedTxn> txns = citi.parse(fixture("citi-sample.csv"));
        assertTrue(txns.stream().noneMatch(t -> t.getDescriptionRaw().contains("NOT YET SETTLED")),
                "an unsettled charge can still change amount or vanish");
        assertEquals(6, txns.size(), "7 rows in the file, one of them pending");
    }

    @Test
    @DisplayName("Citi: two identical same-day charges stay distinct")
    void citiIdenticalRowsStayDistinct() throws Exception {
        List<ParsedTxn> repeats = citi.parse(fixture("citi-sample.csv")).stream()
                .filter(t -> t.getDescriptionRaw().contains("BRAIN FREEZE")).toList();
        assertEquals(2, repeats.size());
        assertNotEquals(repeats.get(0).getDedupKey(), repeats.get(1).getDedupKey());
    }

    @Test
    @DisplayName("Citi: the cardholder is captured")
    void citiCapturesMember() throws Exception {
        assertEquals("HUIXUAN WANG", find(citi.parse(fixture("citi-sample.csv")), "SWEETGREEN").getCardMember());
    }

    // ------------------------------------------------------------- Discover

    @Test
    @DisplayName("Discover: positive is money out, the opposite of Chase")
    void discoverSignConvention() throws Exception {
        ParsedTxn fuel = find(discover.parse(fixture("discover-sample.csv")), "SHELL");
        assertEquals(Direction.DEBIT, fuel.getDirection());
        assertEquals(TxnType.PURCHASE, fuel.getTxnType());
        assertEquals(0, new BigDecimal("2.99").compareTo(fuel.getAmount()));
    }

    @Test
    @DisplayName("Discover: the category column names payments outright")
    void discoverPaymentFromCategory() throws Exception {
        ParsedTxn payment = find(discover.parse(fixture("discover-sample.csv")), "DIRECTPAY");
        assertEquals(TxnType.PAYMENT, payment.getTxnType());
        assertEquals(Direction.CREDIT, payment.getDirection());
    }

    @Test
    @DisplayName("Discover: cashback is an adjustment, not a merchant refund")
    void discoverAwardsAreAdjustments() throws Exception {
        ParsedTxn credit = find(discover.parse(fixture("discover-sample.csv")), "AUTOMATIC STATEMENT CREDIT");
        assertEquals(TxnType.ADJUSTMENT, credit.getTxnType());
        assertEquals(Direction.CREDIT, credit.getDirection(), "it still reduces net spend");
    }

    @Test
    @DisplayName("Discover: the wallet suffix, which carries card digits, is not part of the merchant")
    void discoverWalletSuffixStripped() throws Exception {
        MerchantNormalizer normalizer = new MerchantNormalizer();
        String key = normalizer.lookupKey(
                find(discover.parse(fixture("discover-sample.csv")), "CVS/PHARMACY").getDescriptionRaw());
        assertFalse(key.contains("0786"), "card digits must not reach the merchant key: " + key);
        assertFalse(key.contains("PAY ENDING"), key);
    }

    // ------------------------------------------------------- Header sniffing

    @Test
    @DisplayName("Each export is claimed by exactly one parser")
    void headersAreUnambiguous() throws Exception {
        List<StatementParser> all = List.of(new ChaseStatementParser(), new AmexStatementParser(),
                boa, citi, discover);

        record Case(String name, List<String> headers, StatementFormat expected) {
        }
        List<Case> cases = List.of(
                new Case("chase", List.of("Transaction Date", "Post Date", "Description", "Category",
                        "Type", "Amount", "Memo"), StatementFormat.CHASE),
                new Case("amex", List.of("Date", "Description", "Card Member", "Account #", "Amount",
                        "Extended Details", "Reference", "Category"), StatementFormat.AMEX),
                new Case("boa", List.of("Posted Date", "Reference Number", "Payee", "Address",
                        "Amount"), StatementFormat.BOA),
                new Case("citi", List.of("Status", "Date", "Description", "Debit", "Credit",
                        "Member Name"), StatementFormat.CITI),
                new Case("discover", List.of("Trans. Date", "Post Date", "Description", "Amount",
                        "Category"), StatementFormat.DISCOVER));

        for (Case testCase : cases) {
            List<StatementFormat> matched = all.stream()
                    .filter(p -> p.matchesHeader(testCase.headers()))
                    .map(StatementParser::format)
                    .toList();
            assertEquals(List.of(testCase.expected()), matched,
                    testCase.name() + " should be claimed by exactly one parser");
        }
    }
}
