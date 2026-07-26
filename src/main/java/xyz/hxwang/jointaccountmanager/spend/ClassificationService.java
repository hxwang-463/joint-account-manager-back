package xyz.hxwang.jointaccountmanager.spend;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The interface the classifier skill works against.
 *
 * <p>Work is handed out per <em>merchant</em>, not per transaction. On a real
 * two-month import of 246 transactions there were only 149 distinct merchants,
 * and Uber alone accounted for 28 rows — asking about each row separately would
 * mean reaching the same conclusion 28 times.
 *
 * <p>Decisions are stored against the merchant rather than the row, so they
 * apply to every matching transaction at once and are reused automatically on
 * later imports. Classifying a merchant is therefore a permanent answer, not a
 * per-statement chore.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationService {

    private final BankTxnRepository bankTxns;
    private final MerchantAliasRepository merchantAliases;
    private final CategoryRepository categories;
    private final MerchantNormalizer merchantNormalizer;

    /** A merchant awaiting a decision, with enough context to make one. */
    public record PendingMerchant(
            String lookupKey,
            /** Distinct raw descriptors seen for this merchant. */
            List<String> samples,
            /** Whatever the banks called it — a hint, not an answer. */
            List<String> bankCategories,
            List<String> accounts,
            int txnCount,
            BigDecimal totalAmount,
            String firstSeen,
            String lastSeen) {
    }

    public record PendingBatch(
            /** The codes a decision may use; anything else creates a new category. */
            List<String> categories,
            List<PendingMerchant> merchants,
            int remainingMerchants,
            long remainingTransactions) {
    }

    public record Decision(String lookupKey, String merchant, String category) {
    }

    public record ApplyResult(
            int merchantsUpdated,
            int transactionsUpdated,
            List<String> categoriesCreated,
            List<String> unknownKeys) {
    }

    public record PendingSummary(long pendingTransactions, long pendingMerchants) {
    }

    public PendingSummary summary() {
        return new PendingSummary(
                bankTxns.countUnprocessed(),
                bankTxns.countDistinctUnprocessedDescriptors());
    }

    /**
     * The next batch of merchants to classify, busiest first — so the decisions
     * that cover the most transactions get made earliest, and a run that stops
     * halfway still leaves the data in a useful state.
     */
    @Transactional(readOnly = true)
    public PendingBatch nextBatch(int limit) {
        List<String> categoryCodes = categories.findByStatusOrderByCodeAsc("ACTIVE").stream()
                .map(Category::getCode)
                .filter(code -> !CategoryResolver.UNCATEGORIZED.equals(code))
                .toList();

        // Grouping happens here rather than in SQL because the key is derived by
        // the same normaliser the importer uses; keeping one implementation
        // avoids the two drifting apart.
        List<BankTxn> unprocessed = bankTxns.findUnprocessed(PageRequest.of(0, 2000));
        var grouped = new java.util.LinkedHashMap<String, List<BankTxn>>();
        for (BankTxn txn : unprocessed) {
            grouped.computeIfAbsent(merchantNormalizer.lookupKey(txn.getDescriptionRaw()),
                    key -> new ArrayList<>()).add(txn);
        }

        List<PendingMerchant> merchants = grouped.entrySet().stream()
                .map(entry -> toPendingMerchant(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Integer.compare(b.txnCount(), a.txnCount()))
                .toList();

        return new PendingBatch(
                categoryCodes,
                merchants.stream().limit(limit).toList(),
                merchants.size(),
                unprocessed.size());
    }

    private PendingMerchant toPendingMerchant(String key, List<BankTxn> txns) {
        return new PendingMerchant(
                key,
                txns.stream().map(BankTxn::getDescriptionRaw).distinct().limit(3).toList(),
                txns.stream().map(BankTxn::getBankCategory).filter(java.util.Objects::nonNull)
                        .distinct().limit(3).toList(),
                txns.stream().map(t -> String.valueOf(t.getAccountId())).distinct().limit(3).toList(),
                txns.size(),
                txns.stream().map(BankTxn::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                txns.stream().map(t -> t.getTxnDate().toString()).min(String::compareTo).orElse(null),
                txns.stream().map(t -> t.getTxnDate().toString()).max(String::compareTo).orElse(null));
    }

    /**
     * Records the classifier's decisions.
     *
     * <p>Keyed by merchant, so re-running after a crash simply re-applies the
     * same answers. A category that does not exist yet is created rather than
     * rejected — that is what lets a genuine one-off, an immigration fee or a
     * car repair, get its own line without a redeploy. Newly created codes are
     * reported back so they can be reviewed.
     */
    @Transactional
    public ApplyResult apply(List<Decision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No decisions supplied");
        }

        List<String> created = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        int merchantsUpdated = 0;
        int transactionsUpdated = 0;

        for (Decision decision : decisions) {
            if (decision.lookupKey() == null || decision.lookupKey().isBlank()
                    || decision.category() == null || decision.category().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each decision needs a lookupKey and a category");
            }

            String code = normaliseCode(decision.category());
            if (categories.findByCode(code).isEmpty()) {
                categories.save(Category.builder()
                        .code(code)
                        .displayName(displayNameFor(code))
                        .kind("AD_HOC")
                        .status("ACTIVE")
                        .createdBy("AI")
                        .createdAt(LocalDateTime.now())
                        .build());
                created.add(code);
            }

            String merchantName = decision.merchant() == null || decision.merchant().isBlank()
                    ? merchantNormalizer.displayName(decision.lookupKey())
                    : decision.merchant().trim();

            // Stored against the merchant, so it also applies to imports that
            // have not happened yet.
            MerchantAlias alias = merchantAliases.findByLookupKey(decision.lookupKey())
                    .orElseGet(() -> MerchantAlias.builder()
                            .lookupKey(decision.lookupKey())
                            .createdAt(LocalDateTime.now())
                            .build());
            // A correction made by hand outranks the classifier and is left alone.
            if ("USER".equals(alias.getSource())) {
                continue;
            }
            alias.setMerchant(trim(merchantName, 120));
            alias.setCategory(code);
            alias.setSource("AI");
            merchantAliases.save(alias);
            merchantsUpdated++;

            int applied = applyToTransactions(decision.lookupKey(), trim(merchantName, 120), code);
            if (applied == 0) {
                unknown.add(decision.lookupKey());
            }
            transactionsUpdated += applied;
        }

        log.info("Classifier updated {} merchants covering {} transactions{}",
                merchantsUpdated, transactionsUpdated,
                created.isEmpty() ? "" : ", creating categories " + created);

        return new ApplyResult(merchantsUpdated, transactionsUpdated, created, unknown);
    }

    /**
     * Fans a decision out to every unprocessed row whose descriptor normalises
     * to the same key. Rows already corrected by hand are left alone.
     */
    private int applyToTransactions(String lookupKey, String merchant, String category) {
        int applied = 0;
        for (BankTxn txn : bankTxns.findUnprocessed(PageRequest.of(0, 5000))) {
            if (!lookupKey.equals(merchantNormalizer.lookupKey(txn.getDescriptionRaw()))) {
                continue;
            }
            if (txn.getCategorySource() == CategorySource.USER) {
                continue;
            }
            txn.setMerchant(merchant);
            txn.setCategory(category);
            txn.setCategorySource(CategorySource.AI);
            txn.setProcessed(true);
            bankTxns.save(txn);
            applied++;
        }
        return applied;
    }

    /** Uppercase with underscores, so casing variants cannot coexist. */
    private String normaliseCode(String raw) {
        return raw.trim().toUpperCase().replaceAll("[\\s-]+", "_").replaceAll("[^A-Z0-9_]", "");
    }

    private String displayNameFor(String code) {
        String[] words = code.split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return out.toString();
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
