package xyz.hxwang.jointaccountmanager.spend;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads American Express exports.
 *
 * <p>Header: {@code Date,Description,Card Member,Account #,Amount,Extended
 * Details,...,Reference,Category}
 *
 * <p>Three things differ from Chase and each one is a correctness trap:
 * <ul>
 *   <li>The sign convention is <em>inverted</em> — charges are positive here.
 *   <li>Extended Details holds multi-line text, so a single transaction spans
 *       several physical lines and the file cannot be read line by line.
 *   <li>There is no Type column, so the kind of transaction has to be inferred.
 * </ul>
 *
 * <p>In exchange Amex publishes a Reference per transaction, which makes a far
 * better identity than any hash — including distinguishing two genuinely
 * separate charges of the same amount, on the same day, to the same merchant.
 */
@Component
public class AmexStatementParser implements StatementParser {

    private static final String DATE = "Date";
    private static final String DESCRIPTION = "Description";
    private static final String CARD_MEMBER = "Card Member";
    private static final String ACCOUNT_NUMBER = "Account #";
    private static final String AMOUNT = "Amount";
    private static final String REFERENCE = "Reference";
    private static final String CATEGORY = "Category";

    @Override
    public StatementFormat format() {
        return StatementFormat.AMEX;
    }

    @Override
    public boolean matchesHeader(List<String> headers) {
        return headers.contains(CARD_MEMBER) && headers.contains(AMOUNT)
                && (headers.contains(REFERENCE) || headers.contains("Extended Details"));
    }

    @Override
    public List<String> requiredColumns() {
        return List.of("Date","Description","Amount");
    }

    @Override
    public List<ParsedTxn> parse(Reader reader) throws IOException {
        List<ParsedTxn> results = new ArrayList<>();
        Map<String, Integer> occurrences = Support.newOccurrenceCounter();

        try (CSVParser parser = Support.open(reader)) {
            for (CSVRecord record : parser) {
                String rawAmount = Support.require(record, AMOUNT);
                String description = Support.require(record, DESCRIPTION);

                BigDecimal signed = Support.parseAmount(rawAmount);
                // Inverted relative to Chase: a positive figure is money spent.
                Direction direction = signed.signum() > 0 ? Direction.DEBIT : Direction.CREDIT;
                var date = Support.parseDate(Support.get(record, DATE));
                String reference = stripQuotes(Support.get(record, REFERENCE));

                results.add(ParsedTxn.builder()
                        .txnDate(date)
                        .descriptionRaw(description)
                        .amount(signed.abs())
                        .direction(direction)
                        .txnType(txnType(description, direction))
                        .bankCategory(Support.get(record, CATEGORY))
                        .cardMember(Support.get(record, CARD_MEMBER))
                        .cardRef(Support.get(record, ACCOUNT_NUMBER))
                        .sourceRef(reference)
                        // Prefer Amex's own reference; fall back to a hash only
                        // if an export ever arrives without one.
                        .dedupKey(reference != null
                                ? "ref:" + reference
                                : Support.hashKey(date, signed, description, occurrences))
                        .build());
            }
        }
        return results;
    }

    /**
     * Amex exports the reference wrapped in literal apostrophes — a spreadsheet
     * escaping habit, so the long digit string is not read as a number.
     */
    private String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    /**
     * With no Type column, the autopay row has to be recognised by description.
     * Getting this wrong would count paying the card off as spending, which on
     * this sample would inflate the month by more than every purchase combined.
     */
    private TxnType txnType(String description, Direction direction) {
        String lower = description.toLowerCase();
        boolean looksLikePayment = lower.contains("autopay")
                || lower.contains("payment - thank")
                || lower.contains("payment received")
                || lower.contains("online payment")
                || (lower.contains("payment") && lower.contains("thank you"));
        if (looksLikePayment && direction == Direction.CREDIT) {
            return TxnType.PAYMENT;
        }
        if (lower.contains("annual membership fee") || lower.contains("interest charge")
                || lower.contains("late fee")) {
            return TxnType.FEE;
        }
        return direction == Direction.DEBIT ? TxnType.PURCHASE : TxnType.REFUND;
    }
}
