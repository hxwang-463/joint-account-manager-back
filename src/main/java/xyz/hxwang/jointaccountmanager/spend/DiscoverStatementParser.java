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
 * Reads Discover card exports.
 *
 * <p>Header: {@code Trans. Date,Post Date,Description,Amount,Category}
 *
 * <p>Signs purchases positive, like Amex and unlike Chase. Its category column
 * is unusually useful: unlike every other issuer here it names payments
 * outright, so the card payoff is identified from data rather than guessed at
 * from the description.
 *
 * <p>Cashback and statement credits arrive under "Awards and Rebate Credits".
 * They are recorded as adjustments rather than refunds — they are not money
 * back from a merchant — but still reduce net spend, which is the point of them.
 */
@Component
public class DiscoverStatementParser implements StatementParser {

    private static final String TRANS_DATE = "Trans. Date";
    private static final String POST_DATE = "Post Date";
    private static final String DESCRIPTION = "Description";
    private static final String AMOUNT = "Amount";
    private static final String CATEGORY = "Category";

    private static final String PAYMENTS_CATEGORY = "Payments and Credits";
    private static final String AWARDS_CATEGORY = "Awards and Rebate Credits";

    @Override
    public StatementFormat format() {
        return StatementFormat.DISCOVER;
    }

    @Override
    public boolean matchesHeader(List<String> headers) {
        return headers.contains(TRANS_DATE) && headers.contains(AMOUNT) && headers.contains(CATEGORY);
    }

    @Override
    public List<String> requiredColumns() {
        return List.of("Trans. Date","Description","Amount","Category");
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
                var date = Support.parseDate(Support.get(record, TRANS_DATE));
                String category = Support.get(record, CATEGORY);

                results.add(ParsedTxn.builder()
                        .txnDate(date)
                        .postDate(Support.parseDate(Support.get(record, POST_DATE)))
                        .descriptionRaw(description)
                        .amount(signed.abs())
                        .direction(direction)
                        .txnType(txnType(description, category, direction))
                        .bankCategory(category)
                        .dedupKey(Support.hashKey(date, signed, description, occurrences))
                        .build());
            }
        }
        return results;
    }

    private TxnType txnType(String description, String category, Direction direction) {
        // Discover states this outright, which is more reliable than reading
        // the description — so prefer it and fall back only if it is absent.
        if (PAYMENTS_CATEGORY.equalsIgnoreCase(category) && direction == Direction.CREDIT) {
            return TxnType.PAYMENT;
        }
        if (AWARDS_CATEGORY.equalsIgnoreCase(category)) {
            return TxnType.ADJUSTMENT;
        }
        if (direction == Direction.CREDIT && Support.looksLikePayment(description)) {
            return TxnType.PAYMENT;
        }
        return direction == Direction.DEBIT ? TxnType.PURCHASE : TxnType.REFUND;
    }
}
