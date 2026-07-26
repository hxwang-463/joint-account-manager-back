package xyz.hxwang.jointaccountmanager.spend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Response shapes for the spend-tracking endpoints. */
public final class SpendDtos {

    private SpendDtos() {
    }

    /**
     * An account with the two staleness signals.
     *
     * <p>Both are needed and they answer different questions: {@code lastTxnDate}
     * is the newest transaction we hold, while {@code lastImportedAt} is when we
     * last pulled an export. A card downloaded yesterday with no recent spending
     * looks stale by the first measure and current by the second — the second is
     * the one that tells you whether to go and download it again.
     */
    public record AccountView(
            Long id,
            String name,
            int dayOfMonth,
            BigDecimal defaultAmount,
            StatementFormat statementFormat,
            String defaultCategory,
            LocalDate lastTxnDate,
            LocalDateTime lastImportedAt,
            Long daysSinceImport,
            long txnCount) {
    }

    public record AccountConfigRequest(StatementFormat statementFormat, String defaultCategory) {
    }

    public record TransactionView(
            Long id,
            Long accountId,
            String accountName,
            LocalDate txnDate,
            String description,
            String merchant,
            BigDecimal amount,
            Direction direction,
            TxnType txnType,
            String category,
            CategorySource categorySource,
            String bankCategory,
            String cardMember,
            Long recordId) {
    }

    public record CategoryOverrideRequest(String category, Boolean applyToMerchant) {
    }

    public record CategoryTotal(String category, String displayName, BigDecimal amount, long txnCount) {
    }

    public record PersonTotal(String cardMember, BigDecimal amount, long txnCount) {
    }

    public record MerchantTotal(String merchant, BigDecimal amount, long txnCount) {
    }

    /**
     * Totals for a window. Amounts are net — refunds subtract rather than
     * appearing as separate positives — and card payments are excluded
     * throughout, since paying the card off is a transfer, not spending.
     */
    public record SpendSummary(
            LocalDate from,
            LocalDate to,
            BigDecimal totalSpend,
            List<CategoryTotal> byCategory,
            List<PersonTotal> byPerson) {
    }

    /** One month's net spend, for the trend chart. */
    public record MonthTotal(int year, int month, String label, BigDecimal amount, long txnCount) {
    }

    /** Filter options, derived from the data actually present. */
    public record FilterOptions(
            List<AccountOption> accounts,
            List<String> categories,
            List<String> cardMembers) {

        public record AccountOption(Long id, String name) {
        }
    }

    public record ImportView(
            Long id,
            Long accountId,
            String accountName,
            String filename,
            LocalDateTime importedAt,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer rowCount,
            Integer insertedCount,
            Integer duplicateCount,
            String status) {
    }
}
