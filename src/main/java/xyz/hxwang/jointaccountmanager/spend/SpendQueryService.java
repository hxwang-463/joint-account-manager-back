package xyz.hxwang.jointaccountmanager.spend;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import xyz.hxwang.jointaccountmanager.Account;
import xyz.hxwang.jointaccountmanager.AccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read paths and small mutations for the spend data. */
@Service
@RequiredArgsConstructor
public class SpendQueryService {

    private final AccountRepository accountRepository;
    private final BankTxnRepository bankTxns;
    private final StatementImportRepository imports;
    private final CategoryRepository categories;
    private final MerchantAliasRepository merchantAliases;
    private final MerchantNormalizer merchantNormalizer;

    /** Accounts plus the two staleness signals — the "what should I download?" view. */
    public List<SpendDtos.AccountView> accountsWithStaleness() {
        Map<Long, LocalDate> lastTxn = new HashMap<>();
        for (Object[] row : bankTxns.findLatestTxnDatePerAccount()) {
            lastTxn.put((Long) row[0], (LocalDate) row[1]);
        }
        Map<Long, LocalDateTime> lastImport = new HashMap<>();
        for (Object[] row : imports.findLatestImportPerAccount()) {
            lastImport.put((Long) row[0], (LocalDateTime) row[1]);
        }
        Map<Long, Long> counts = bankTxns.findAll().stream()
                .collect(Collectors.groupingBy(BankTxn::getAccountId, Collectors.counting()));

        return accountRepository.findAll().stream()
                .sorted(Comparator.comparing(Account::getAcctName))
                .map(account -> {
                    LocalDateTime imported = lastImport.get(account.getId());
                    return new SpendDtos.AccountView(
                            account.getId(),
                            account.getAcctName(),
                            account.getDayOfMonth(),
                            account.getDefaultAmount(),
                            account.getStatementFormat(),
                            account.getDefaultCategory(),
                            lastTxn.get(account.getId()),
                            imported,
                            imported == null ? null : ChronoUnit.DAYS.between(imported.toLocalDate(), LocalDate.now()),
                            counts.getOrDefault(account.getId(), 0L));
                })
                .toList();
    }

    @Transactional
    public SpendDtos.AccountView configureAccount(Long accountId, SpendDtos.AccountConfigRequest request) {
        Account account = accountRepository.findById(accountId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No account with id " + accountId));

        if (request.statementFormat() != null) {
            account.setStatementFormat(request.statementFormat());
        }
        if (request.defaultCategory() != null) {
            String code = normaliseCategoryCode(request.defaultCategory());
            requireCategoryExists(code);
            account.setDefaultCategory(code);
        }

        // A statement-backed account must not also generate its own transaction:
        // the import already carries the detail, so doing both would count the
        // same spending twice.
        if (account.getStatementFormat() != StatementFormat.NONE) {
            account.setDefaultCategory(null);
        }

        accountRepository.save(account);
        return accountsWithStaleness().stream()
                .filter(view -> view.id().equals(accountId))
                .findFirst()
                .orElseThrow();
    }

    public List<SpendDtos.TransactionView> transactions(LocalDate from, LocalDate to, Long accountId,
                                                        String category, String cardMember, String merchant,
                                                        boolean includePayments, int page, int size) {
        Map<Long, String> names = accountNames();
        return bankTxns.search(from, to, accountId,
                        category == null ? null : normaliseCategoryCode(category),
                        cardMember, blankToNull(merchant), includePayments, PageRequest.of(page, size))
                .stream()
                .map(txn -> toView(txn, names))
                .toList();
    }

    /**
     * Correcting a transaction's category. By default the correction is also
     * remembered against the merchant, so every future purchase from them picks
     * it up instead of needing the same fix again.
     */
    @Transactional
    public SpendDtos.TransactionView overrideCategory(Long txnId, SpendDtos.CategoryOverrideRequest request) {
        BankTxn txn = bankTxns.findById(txnId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No transaction with id " + txnId));
        if (request == null || request.category() == null || request.category().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category is required");
        }

        String code = normaliseCategoryCode(request.category());
        requireCategoryExists(code);

        txn.setCategory(code);
        txn.setCategorySource(CategorySource.USER);
        // A correction by hand is a classification. Without this the row keeps
        // its pending flag and the classifier is handed it again on every run,
        // only to skip it for being user-set.
        txn.setProcessed(true);
        if (txn.getMerchant() == null || txn.getMerchant().isBlank()) {
            txn.setMerchant(merchantNormalizer.displayName(
                    merchantNormalizer.lookupKey(txn.getDescriptionRaw())));
        }
        bankTxns.save(txn);

        boolean remember = request.applyToMerchant() == null || request.applyToMerchant();
        if (remember) {
            String key = merchantNormalizer.lookupKey(txn.getDescriptionRaw());
            if (key != null && !key.isBlank()) {
                MerchantAlias alias = merchantAliases.findByLookupKey(key)
                        .orElseGet(() -> MerchantAlias.builder()
                                .lookupKey(key)
                                .merchant(merchantNormalizer.displayName(key))
                                .createdAt(LocalDateTime.now())
                                .build());
                alias.setCategory(code);
                alias.setSource("USER");
                merchantAliases.save(alias);
            }
        }
        return toView(txn, accountNames());
    }

    public SpendDtos.SpendSummary summary(LocalDate from, LocalDate to) {
        Map<String, String> displayNames = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getCode, Category::getDisplayName, (a, b) -> a));

        List<SpendDtos.CategoryTotal> byCategory = bankTxns.sumByCategory(from, to).stream()
                .map(row -> new SpendDtos.CategoryTotal(
                        (String) row[0],
                        displayNames.getOrDefault((String) row[0], (String) row[0]),
                        (BigDecimal) row[1],
                        (Long) row[2]))
                .toList();

        List<SpendDtos.PersonTotal> byPerson = bankTxns.sumByCardMember(from, to).stream()
                .map(row -> new SpendDtos.PersonTotal((String) row[0], (BigDecimal) row[1], (Long) row[2]))
                .toList();

        BigDecimal total = bankTxns.totalNetSpend(from, to);
        return new SpendDtos.SpendSummary(from, to,
                total == null ? BigDecimal.ZERO : total, byCategory, byPerson);
    }

    /**
     * Net spend for each of the last {@code months} calendar months, most recent
     * last. Months with no activity are filled in with zero so the chart keeps
     * an even time axis instead of silently closing the gap.
     */
    public List<SpendDtos.MonthTotal> monthlyTrend(int months) {
        YearMonth current = YearMonth.now();
        YearMonth earliest = current.minusMonths(months - 1L);

        Map<YearMonth, Object[]> found = new HashMap<>();
        for (Object[] row : bankTxns.sumByMonth(earliest.atDay(1), current.atEndOfMonth())) {
            found.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()), row);
        }

        List<SpendDtos.MonthTotal> result = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = earliest.plusMonths(i);
            Object[] row = found.get(ym);
            result.add(new SpendDtos.MonthTotal(
                    ym.getYear(), ym.getMonthValue(),
                    ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.US) + " " + ym.getYear(),
                    row == null ? BigDecimal.ZERO : (BigDecimal) row[2],
                    row == null ? 0L : (Long) row[3]));
        }
        return result;
    }

    /** Filter choices built from the data present, so nothing offered returns nothing. */
    public SpendDtos.FilterOptions filterOptions() {
        List<SpendDtos.FilterOptions.AccountOption> accounts = accountRepository.findAll().stream()
                .sorted(Comparator.comparing(Account::getAcctName))
                .map(a -> new SpendDtos.FilterOptions.AccountOption(a.getId(), a.getAcctName()))
                .toList();
        List<String> categoryCodes = activeCategories().stream().map(Category::getCode).toList();
        return new SpendDtos.FilterOptions(accounts, categoryCodes, bankTxns.findDistinctCardMembers());
    }

    public List<SpendDtos.MerchantTotal> topMerchants(LocalDate from, LocalDate to, int limit) {
        return bankTxns.sumByMerchant(from, to, PageRequest.of(0, limit)).stream()
                .map(row -> new SpendDtos.MerchantTotal((String) row[0], (BigDecimal) row[1], (Long) row[2]))
                .toList();
    }

    public List<SpendDtos.ImportView> recentImports(int limit) {
        Map<Long, String> names = accountNames();
        return imports.findAllByOrderByImportedAtDesc(PageRequest.of(0, limit)).stream()
                .map(record -> new SpendDtos.ImportView(
                        record.getId(), record.getAccountId(), names.get(record.getAccountId()),
                        record.getFilename(), record.getImportedAt(),
                        record.getPeriodStart(), record.getPeriodEnd(),
                        record.getRowCount(), record.getInsertedCount(), record.getDuplicateCount(),
                        record.getStatus()))
                .toList();
    }

    public List<Category> activeCategories() {
        return categories.findByStatusOrderByCodeAsc("ACTIVE");
    }

    // ------------------------------------------------------------- internals

    private Map<Long, String> accountNames() {
        return accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getId, Account::getAcctName, (a, b) -> a));
    }

    private SpendDtos.TransactionView toView(BankTxn txn, Map<Long, String> names) {
        return new SpendDtos.TransactionView(
                txn.getId(), txn.getAccountId(), names.get(txn.getAccountId()),
                txn.getTxnDate(), txn.getDescriptionRaw(), txn.getMerchant(),
                txn.getAmount(), txn.getDirection(), txn.getTxnType(),
                txn.getCategory(), txn.getCategorySource(), txn.getBankCategory(),
                txn.getCardMember(), txn.getRecordId());
    }

    /** An empty filter box should mean "no filter", not "match the empty string". */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Uppercase with underscores, so casing variants of one category cannot coexist. */
    private String normaliseCategoryCode(String raw) {
        return raw.trim().toUpperCase().replaceAll("[\\s-]+", "_");
    }

    private void requireCategoryExists(String code) {
        if (categories.findByCode(code).isEmpty()) {
            String known = activeCategories().stream()
                    .map(Category::getCode)
                    .collect(Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown category '" + code + "'. Known categories: " + known);
        }
    }

    /** Exposed for the bill-generation path, which needs the same validation. */
    public Function<String, String> categoryCodeNormaliser() {
        return this::normaliseCategoryCode;
    }
}
