package xyz.hxwang.jointaccountmanager;

import java.math.BigDecimal;
import java.util.List;

public interface BalanceService {
    Balance updateBalance(BigDecimal offset, String comment);

    /**
     * As {@link #updateBalance(BigDecimal, String)}, but records which record's
     * payment produced the row so the entry can be corrected later.
     */
    Balance updateBalance(BigDecimal offset, String comment, Long recordId);

    /**
     * Corrects the ledger entry produced by paying {@code recordId} after that
     * record's amount changed, and shifts every later running total to match.
     */
    void adjustForRecordAmountChange(Long recordId, BigDecimal oldAmount, BigDecimal newAmount);

    /**
     * Removes the ledger entry produced by paying {@code recordId} and shifts every
     * later running total back, so the history reads as if the payment never
     * happened. A no-op when the record has no linked entry.
     */
    void removeForRevertedRecord(Long recordId);

    Balance findLatestBalance();
    List<Balance> getLatestHistories(int limit);
}
