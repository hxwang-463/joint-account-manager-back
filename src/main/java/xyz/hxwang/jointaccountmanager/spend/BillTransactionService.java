package xyz.hxwang.jointaccountmanager.spend;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.hxwang.jointaccountmanager.Account;
import xyz.hxwang.jointaccountmanager.AccountRepository;
import xyz.hxwang.jointaccountmanager.Record;
import xyz.hxwang.jointaccountmanager.RecordRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the spend ledger in step with bills that have no statement behind them.
 *
 * <p>Rent, electricity and internet are paid straight from chequing, so no CSV
 * ever arrives to say what they were. Without this they would be missing from
 * the spend analysis entirely — and they are among the largest outgoings there
 * are. Marking one paid therefore generates a single transaction for it.
 *
 * <p>Accounts backed by a statement generate nothing: the import already
 * carries the detail, and doing both would count the same money twice. That is
 * why generation requires a default category as well as a NONE format — a card
 * that has not been configured yet satisfies neither and stays inert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillTransactionService {

    private final AccountRepository accountRepository;
    private final RecordRepository recordRepository;
    private final BankTxnRepository bankTxns;

    /**
     * Identity for a generated row. Because it is derived from the record id
     * and the table is unique on (account, key), generating twice is impossible
     * — which matters because the nightly job and a manual tap can both mark
     * the same bill paid.
     */
    private static String dedupKeyFor(Long recordId) {
        return "record:" + recordId;
    }

    /** Called after a bill is marked paid. A no-op for statement-backed accounts. */
    @Transactional
    public void onRecordPaid(Record record) {
        Optional<Account> account = findAccount(record.getAcctName());
        if (account.isEmpty() || !generatesTransactions(account.get())) {
            return;
        }
        if (bankTxns.findByRecordId(record.getId()).isPresent()) {
            return;
        }

        Account acct = account.get();
        BigDecimal amount = record.getAmount() == null ? BigDecimal.ZERO : record.getAmount();

        bankTxns.save(BankTxn.builder()
                .accountId(acct.getId())
                .recordId(record.getId())
                .txnDate(record.getDate())
                .descriptionRaw(acct.getAcctName())
                .merchant(acct.getAcctName())
                .amount(amount.abs())
                .direction(Direction.DEBIT)
                .txnType(TxnType.PURCHASE)
                .category(acct.getDefaultCategory())
                .categorySource(CategorySource.DEFAULT)
                .dedupKey(dedupKeyFor(record.getId()))
                .createdAt(LocalDateTime.now())
                .build());

        log.info("Generated a {} transaction of {} for {}",
                acct.getDefaultCategory(), amount, acct.getAcctName());
    }

    /** Called after a paid bill's amount is corrected. */
    @Transactional
    public void onRecordAmountChanged(Long recordId, BigDecimal newAmount) {
        bankTxns.findByRecordId(recordId).ifPresent(txn -> {
            txn.setAmount(newAmount == null ? BigDecimal.ZERO : newAmount.abs());
            bankTxns.save(txn);
            log.info("Updated generated transaction for record {} to {}", recordId, newAmount);
        });
    }

    /** Called when a payment is reverted, so the spend disappears with it. */
    @Transactional
    public void onRecordReverted(Long recordId) {
        bankTxns.findByRecordId(recordId).ifPresent(txn -> {
            bankTxns.delete(txn);
            log.info("Removed generated transaction for reverted record {}", recordId);
        });
    }

    /** What a backfill did, or would do when previewing. */
    public record BackfillResult(int created, java.math.BigDecimal totalAmount,
                                 java.util.List<String> byAccount, boolean dryRun) {
    }

    /**
     * Creates the missing spend transactions for bills that were already paid
     * before this feature existed.
     *
     * <p>Deliberately generates transactions only — it never touches the
     * balance ledger. Those bills were already deducted from chequing when they
     * were paid; the ledger is correct and complete. What is missing is the
     * itemised side, so that is all this fills in.
     *
     * <p>Do not be tempted to revert and re-pay a historical bill instead. That
     * appends a fresh balance row dated today, and because balance rows written
     * before record linking existed carry no record id, the original deduction
     * is not removed — the bill ends up counted twice and dated wrong.
     *
     * <p>Idempotent: the transaction's key is derived from the record id and the
     * table is unique on it, so running this twice cannot duplicate anything.
     */
    @Transactional
    public BackfillResult backfillPaidBills(java.time.LocalDate from, boolean dryRun) {
        Map<String, Account> byName = accountRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Account::getAcctName, a -> a, (a, b) -> a));

        java.util.Map<String, Integer> perAccount = new java.util.LinkedHashMap<>();
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        int created = 0;

        for (Record record : recordRepository.findAll()) {
            if (!record.isPaid() || record.getDate() == null || record.getAmount() == null) {
                continue;
            }
            if (from != null && record.getDate().isBefore(from)) {
                continue;
            }
            Account account = byName.get(record.getAcctName());
            if (account == null || !generatesTransactions(account)) {
                continue;
            }
            if (bankTxns.findByRecordId(record.getId()).isPresent()) {
                continue;
            }

            if (!dryRun) {
                bankTxns.save(BankTxn.builder()
                        .accountId(account.getId())
                        .recordId(record.getId())
                        .txnDate(record.getDate())
                        .descriptionRaw(account.getAcctName())
                        .merchant(account.getAcctName())
                        .amount(record.getAmount().abs())
                        .direction(Direction.DEBIT)
                        .txnType(TxnType.PURCHASE)
                        .category(account.getDefaultCategory())
                        .categorySource(CategorySource.DEFAULT)
                        // Already categorised from account configuration, so it
                        // never enters the classifier's queue.
                        .processed(true)
                        .dedupKey(dedupKeyFor(record.getId()))
                        .createdAt(LocalDateTime.now())
                        .build());
            }
            perAccount.merge(account.getAcctName(), 1, Integer::sum);
            total = total.add(record.getAmount().abs());
            created++;
        }

        if (!dryRun && created > 0) {
            log.info("Backfilled {} bill transactions totalling {}", created, total);
        }
        return new BackfillResult(created, total,
                perAccount.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toList(),
                dryRun);
    }

    private boolean generatesTransactions(Account account) {
        return account.getStatementFormat() == StatementFormat.NONE
                && account.getDefaultCategory() != null
                && !account.getDefaultCategory().isBlank();
    }

    /**
     * Records reference their account by name rather than by id, so the lookup
     * has to go through the name. Pre-existing shape; worth knowing that
     * renaming an account would sever this link.
     */
    private Optional<Account> findAccount(String acctName) {
        if (acctName == null) {
            return Optional.empty();
        }
        List<Account> all = accountRepository.findAll();
        return all.stream().filter(a -> acctName.equals(a.getAcctName())).findFirst();
    }
}
