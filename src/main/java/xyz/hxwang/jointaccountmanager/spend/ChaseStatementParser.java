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
 * Reads Chase card exports.
 *
 * <p>Header: {@code Transaction Date,Post Date,Description,Category,Type,Amount,Memo}
 *
 * <p>Chase writes purchases as <em>negative</em> amounts and credits as
 * positive — the opposite of Amex. It publishes no per-transaction reference,
 * so identity has to be derived from the row's contents.
 */
@Component
public class ChaseStatementParser implements StatementParser {

    private static final String TRANSACTION_DATE = "Transaction Date";
    private static final String POST_DATE = "Post Date";
    private static final String DESCRIPTION = "Description";
    private static final String CATEGORY = "Category";
    private static final String TYPE = "Type";
    private static final String AMOUNT = "Amount";

    @Override
    public StatementFormat format() {
        return StatementFormat.CHASE;
    }

    @Override
    public boolean matchesHeader(List<String> headers) {
        return headers.contains(TRANSACTION_DATE) && headers.contains(TYPE) && headers.contains(AMOUNT);
    }

    @Override
    public List<String> requiredColumns() {
        return List.of("Transaction Date","Description","Type","Amount");
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
                // Negative is money leaving the card; positive is a credit back to it.
                Direction direction = signed.signum() < 0 ? Direction.DEBIT : Direction.CREDIT;
                var date = Support.parseDate(Support.get(record, TRANSACTION_DATE));

                results.add(ParsedTxn.builder()
                        .txnDate(date)
                        .postDate(Support.parseDate(Support.get(record, POST_DATE)))
                        .descriptionRaw(description)
                        .amount(signed.abs())
                        .direction(direction)
                        .txnType(txnType(Support.get(record, TYPE), direction))
                        .bankCategory(Support.get(record, CATEGORY))
                        .dedupKey(Support.hashKey(date, signed, description, occurrences))
                        .build());
            }
        }
        return results;
    }

    /**
     * Chase labels each row, which saves guessing. "Payment" is the card being
     * paid off from chequing — a transfer we keep but never count as spending.
     */
    private TxnType txnType(String type, Direction direction) {
        if (type == null) {
            return direction == Direction.DEBIT ? TxnType.PURCHASE : TxnType.REFUND;
        }
        return switch (type.trim().toLowerCase()) {
            case "payment" -> TxnType.PAYMENT;
            case "return" -> TxnType.REFUND;
            case "fee" -> TxnType.FEE;
            case "adjustment" -> TxnType.ADJUSTMENT;
            default -> direction == Direction.DEBIT ? TxnType.PURCHASE : TxnType.REFUND;
        };
    }
}
