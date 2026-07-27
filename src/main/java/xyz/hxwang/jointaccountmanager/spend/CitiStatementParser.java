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
 * Reads Citi card exports.
 *
 * <p>Header: {@code Status,Date,Description,Debit,Credit,Member Name}
 *
 * <p>Structurally unlike the others: instead of one signed amount there are
 * separate Debit and Credit columns, exactly one of which is populated per row.
 * Debits are written positive and credits negative, so the magnitude is taken
 * from whichever column is filled and the direction from which one it was.
 *
 * <p>The Status column matters. Only settled rows are imported — a pending
 * charge can still change amount or vanish entirely, and importing one would
 * either overstate the month or leave a transaction behind that never happened.
 */
@Component
public class CitiStatementParser implements StatementParser {

    private static final String STATUS = "Status";
    private static final String DATE = "Date";
    private static final String DESCRIPTION = "Description";
    private static final String DEBIT = "Debit";
    private static final String CREDIT = "Credit";
    private static final String MEMBER_NAME = "Member Name";

    @Override
    public StatementFormat format() {
        return StatementFormat.CITI;
    }

    @Override
    public boolean matchesHeader(List<String> headers) {
        return headers.contains(DEBIT) && headers.contains(CREDIT) && headers.contains(STATUS);
    }

    @Override
    public List<String> requiredColumns() {
        return List.of("Status", "Date", "Description", "Debit", "Credit");
    }

    @Override
    public List<ParsedTxn> parse(Reader reader) throws IOException {
        List<ParsedTxn> results = new ArrayList<>();
        Map<String, Integer> occurrences = Support.newOccurrenceCounter();

        try (CSVParser parser = Support.open(reader)) {
            for (CSVRecord record : parser) {
                String description = Support.require(record, DESCRIPTION);
                String rawDebit = Support.get(record, DEBIT);
                String rawCredit = Support.get(record, CREDIT);
                // Exactly one of the two carries the amount. Neither means the
                // row is not what this format promises.
                if (rawDebit == null && rawCredit == null) {
                    throw new IllegalStateException("row " + record.getRecordNumber()
                            + " has neither a Debit nor a Credit amount");
                }

                // Anything not settled is skipped; it may still change.
                String status = Support.get(record, STATUS);
                if (status != null && !"cleared".equalsIgnoreCase(status)) {
                    continue;
                }

                boolean isDebit = rawDebit != null;
                BigDecimal amount = Support.parseAmount(isDebit ? rawDebit : rawCredit).abs();
                Direction direction = isDebit ? Direction.DEBIT : Direction.CREDIT;
                var date = Support.parseDate(Support.get(record, DATE));

                results.add(ParsedTxn.builder()
                        .txnDate(date)
                        .descriptionRaw(description)
                        .amount(amount)
                        .direction(direction)
                        .txnType(txnType(description, direction))
                        .cardMember(Support.get(record, MEMBER_NAME))
                        // Citi publishes no transaction id, so identity has to
                        // come from the row's contents. Signed so that a debit
                        // and a credit of the same amount never collide.
                        .dedupKey(Support.hashKey(date,
                                isDebit ? amount : amount.negate(), description, occurrences))
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
