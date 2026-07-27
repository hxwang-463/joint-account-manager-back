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
 * Reads Bank of America card exports.
 *
 * <p>Header: {@code Posted Date,Reference Number,Payee,Address,Amount}
 *
 * <p>Signs purchases negative, like Chase and unlike Amex. It publishes a
 * reference number per transaction, which makes a far better identity than a
 * hash of the row's contents — including telling apart two identical charges to
 * the same merchant on the same day.
 *
 * <p>Only a posted date is exported; there is no separate transaction date, so
 * the two are the same here.
 */
@Component
public class BoaStatementParser implements StatementParser {

    private static final String POSTED_DATE = "Posted Date";
    private static final String REFERENCE = "Reference Number";
    private static final String PAYEE = "Payee";
    private static final String AMOUNT = "Amount";

    @Override
    public StatementFormat format() {
        return StatementFormat.BOA;
    }

    @Override
    public boolean matchesHeader(List<String> headers) {
        return headers.contains(POSTED_DATE) && headers.contains(REFERENCE) && headers.contains(PAYEE);
    }

    @Override
    public List<String> requiredColumns() {
        return List.of("Posted Date","Payee","Amount");
    }

    @Override
    public List<ParsedTxn> parse(Reader reader) throws IOException {
        List<ParsedTxn> results = new ArrayList<>();
        Map<String, Integer> occurrences = Support.newOccurrenceCounter();

        try (CSVParser parser = Support.open(reader)) {
            for (CSVRecord record : parser) {
                String rawAmount = Support.require(record, AMOUNT);
                String description = Support.require(record, PAYEE);

                BigDecimal signed = Support.parseAmount(rawAmount);
                // Negative is money leaving the card, as with Chase.
                Direction direction = signed.signum() < 0 ? Direction.DEBIT : Direction.CREDIT;
                var date = Support.parseDate(Support.get(record, POSTED_DATE));
                String reference = Support.get(record, REFERENCE);

                results.add(ParsedTxn.builder()
                        .txnDate(date)
                        .postDate(date)
                        .descriptionRaw(description)
                        .amount(signed.abs())
                        .direction(direction)
                        .txnType(txnType(description, direction))
                        .sourceRef(reference)
                        .dedupKey(reference != null
                                ? "ref:" + reference
                                : Support.hashKey(date, signed, description, occurrences))
                        .build());
            }
        }
        return results;
    }

    private TxnType txnType(String description, Direction direction) {
        if (direction == Direction.CREDIT && Support.looksLikePayment(description)) {
            return TxnType.PAYMENT;
        }
        return direction == Direction.DEBIT ? TxnType.PURCHASE : TxnType.REFUND;
    }
}
