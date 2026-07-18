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
    Balance findLatestBalance();
    List<Balance> getLatestHistories(int limit);
}
