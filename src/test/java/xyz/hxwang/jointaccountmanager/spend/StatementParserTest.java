package xyz.hxwang.jointaccountmanager.spend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fixtures mirror the structure of real exports — including the parts most
 * likely to be got wrong: inverted sign conventions, Amex's multi-line quoted
 * fields, apostrophe-wrapped references, and repeated identical purchases.
 */
class StatementParserTest {

    private final ChaseStatementParser chase = new ChaseStatementParser();
    private final AmexStatementParser amex = new AmexStatementParser();

    private Reader fixture(String name) {
        InputStream in = getClass().getResourceAsStream("/statements/" + name);
        assertNotNull(in, "missing fixture " + name);
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    private ParsedTxn byDescription(List<ParsedTxn> all, String fragment) {
        return all.stream()
                .filter(t -> t.getDescriptionRaw().contains(fragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no transaction matching " + fragment));
    }

    // ---------------------------------------------------------------- Chase

    @Test
    @DisplayName("Chase: negative amounts are money out, positive are money in")
    void chaseSignConvention() throws Exception {
        List<ParsedTxn> txns = chase.parse(fixture("chase-sample.csv"));
        assertEquals(6, txns.size());

        ParsedTxn purchase = byDescription(txns, "WHOLEFDS");
        assertEquals(Direction.DEBIT, purchase.getDirection());
        assertEquals(TxnType.PURCHASE, purchase.getTxnType());
        assertEquals(0, new BigDecimal("112.20").compareTo(purchase.getAmount()),
                "amount is stored positive regardless of how the bank signed it");

        ParsedTxn refund = byDescription(txns, "AMAZON MKTPLACE");
        assertEquals(Direction.CREDIT, refund.getDirection());
        assertEquals(TxnType.REFUND, refund.getTxnType());
    }

    @Test
    @DisplayName("Chase: the autopay row is recognised as a payment, not spending")
    void chasePaymentDetected() throws Exception {
        ParsedTxn payment = byDescription(chase.parse(fixture("chase-sample.csv")), "AUTOMATIC PAYMENT");
        assertEquals(TxnType.PAYMENT, payment.getTxnType());
    }

    @Test
    @DisplayName("Chase: two identical purchases on one day stay two transactions")
    void chaseIdenticalRowsKeepDistinctKeys() throws Exception {
        List<ParsedTxn> starbucks = chase.parse(fixture("chase-sample.csv")).stream()
                .filter(t -> t.getDescriptionRaw().contains("STARBUCKS"))
                .toList();
        assertEquals(2, starbucks.size());
        assertNotEquals(starbucks.get(0).getDedupKey(), starbucks.get(1).getDedupKey(),
                "identical rows must not collapse into one, or a genuine repeat purchase is lost");
    }

    @Test
    @DisplayName("Chase: dedup keys are stable across re-parsing the same file")
    void chaseKeysAreStable() throws Exception {
        List<String> first = chase.parse(fixture("chase-sample.csv")).stream()
                .map(ParsedTxn::getDedupKey).toList();
        List<String> second = chase.parse(fixture("chase-sample.csv")).stream()
                .map(ParsedTxn::getDedupKey).toList();
        assertEquals(first, second, "re-uploading the same export must collapse, not duplicate");
    }

    // ----------------------------------------------------------------- Amex

    @Test
    @DisplayName("Amex: sign convention is inverted — positive amounts are money out")
    void amexSignConventionIsInverted() throws Exception {
        List<ParsedTxn> txns = amex.parse(fixture("amex-sample.csv"));

        ParsedTxn purchase = byDescription(txns, "WHOLEFDS");
        assertEquals(Direction.DEBIT, purchase.getDirection(),
                "a positive Amex amount is a charge, the opposite of Chase");
        assertEquals(TxnType.PURCHASE, purchase.getTxnType());
        assertEquals(0, new BigDecimal("54.59").compareTo(purchase.getAmount()));

        ParsedTxn refund = byDescription(txns, "WEEE INC");
        assertEquals(Direction.CREDIT, refund.getDirection());
        assertEquals(TxnType.REFUND, refund.getTxnType());
    }

    @Test
    @DisplayName("Amex: multi-line quoted fields do not split one transaction into many")
    void amexMultiLineFieldsParseAsSingleRecords() throws Exception {
        List<ParsedTxn> txns = amex.parse(fixture("amex-sample.csv"));
        assertEquals(6, txns.size(),
                "the fixture spans ~30 physical lines; a line-based parse would find far more");
    }

    @Test
    @DisplayName("Amex: autopay is classified as a payment")
    void amexPaymentDetected() throws Exception {
        ParsedTxn payment = byDescription(amex.parse(fixture("amex-sample.csv")), "AUTOPAY");
        assertEquals(TxnType.PAYMENT, payment.getTxnType());
        assertEquals(Direction.CREDIT, payment.getDirection());
    }

    @Test
    @DisplayName("Amex: the reference is unwrapped and used as identity")
    void amexReferenceBecomesDedupKey() throws Exception {
        ParsedTxn txn = byDescription(amex.parse(fixture("amex-sample.csv")), "WHOLEFDS");
        assertEquals("320261760098263935", txn.getSourceRef(),
                "the apostrophes Amex wraps around the reference must be stripped");
        assertEquals("ref:320261760098263935", txn.getDedupKey());
    }

    @Test
    @DisplayName("Amex: same merchant, day and amount stay distinct via their references")
    void amexIdenticalChargesAreDistinct() throws Exception {
        List<ParsedTxn> publix = amex.parse(fixture("amex-sample.csv")).stream()
                .filter(t -> t.getDescriptionRaw().contains("PUBLIX"))
                .toList();
        assertEquals(2, publix.size());
        Set<String> keys = publix.stream().map(ParsedTxn::getDedupKey).collect(Collectors.toSet());
        assertEquals(2, keys.size());
    }

    @Test
    @DisplayName("Amex: cardholder and card are captured for the per-person breakdown")
    void amexCapturesCardMember() throws Exception {
        List<ParsedTxn> txns = amex.parse(fixture("amex-sample.csv"));
        assertEquals("HUIXUAN WANG", byDescription(txns, "WHOLEFDS").getCardMember());
        assertEquals("ZHIYU WU", byDescription(txns, "PUBLIX").getCardMember());
        assertEquals("-51011", byDescription(txns, "PUBLIX").getCardRef());
    }

    @Test
    @DisplayName("Amex: the bank's own category label is preserved for mapping")
    void amexKeepsBankCategory() throws Exception {
        List<ParsedTxn> txns = amex.parse(fixture("amex-sample.csv"));
        assertEquals("Transportation-Fuel", byDescription(txns, "GAS PUMP").getBankCategory());
        assertEquals(LocalDate.of(2026, 6, 9), byDescription(txns, "GAS PUMP").getTxnDate());
    }

    // --------------------------------------------------------- Header sniff

    @Test
    @DisplayName("Each parser recognises only its own export")
    void headerDetectionDistinguishesFormats() {
        List<String> chaseHeaders = List.of("Transaction Date", "Post Date", "Description",
                "Category", "Type", "Amount", "Memo");
        List<String> amexHeaders = List.of("Date", "Description", "Card Member", "Account #",
                "Amount", "Extended Details", "Reference", "Category");

        assertTrue(chase.matchesHeader(chaseHeaders));
        assertFalse(chase.matchesHeader(amexHeaders));
        assertTrue(amex.matchesHeader(amexHeaders));
        assertFalse(amex.matchesHeader(chaseHeaders));
    }
}
