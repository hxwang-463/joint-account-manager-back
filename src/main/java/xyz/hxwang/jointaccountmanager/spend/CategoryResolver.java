package xyz.hxwang.jointaccountmanager.spend;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Decides what an imported row already knows about itself.
 *
 * <p>Imports do not classify. The only thing resolved here is whether this
 * merchant has been seen before — if the classifier has already decided that
 * "UBER" is Uber/TRANSPORT, that decision is reused rather than asked for
 * again. Everything else arrives unprocessed and waits.
 *
 * <p>This is not the old mapping table in disguise. That translated a bank's
 * own guess into ours mechanically; this replays a decision that was made
 * deliberately once and can be corrected in one place.
 */
@Service
@RequiredArgsConstructor
public class CategoryResolver {

    /** Where a row sits until the classifier has looked at it. */
    public static final String UNCATEGORIZED = "UNCATEGORIZED";

    /** Card payoffs; excluded from spending by txn_type, never classified. */
    public static final String TRANSFER = "TRANSFER";

    private final MerchantAliasRepository merchantAliases;

    @Getter
    public static class Resolution {
        private final String category;
        private final String merchant;
        private final CategorySource source;
        /** False when this row still needs the classifier. */
        private final boolean processed;

        Resolution(String category, String merchant, CategorySource source, boolean processed) {
            this.category = category;
            this.merchant = merchant;
            this.source = source;
            this.processed = processed;
        }
    }

    /**
     * @param merchantKey normalised descriptor, used to find a prior decision
     * @param txnType     payments never need classifying
     */
    public Resolution resolve(String merchantKey, TxnType txnType) {
        // Paying off a card moves money between our own accounts. There is
        // nothing to name and nothing to categorise, so it is complete on
        // arrival rather than sitting in the pending queue forever.
        if (txnType == TxnType.PAYMENT) {
            return new Resolution(TRANSFER, "Card payment", CategorySource.DEFAULT, true);
        }

        if (merchantKey != null && !merchantKey.isBlank()) {
            var alias = merchantAliases.findByLookupKey(merchantKey).orElse(null);
            if (alias != null && alias.getCategory() != null) {
                return new Resolution(
                        alias.getCategory(),
                        alias.getMerchant(),
                        "USER".equals(alias.getSource()) ? CategorySource.USER : CategorySource.AI,
                        true);
            }
        }

        // Unknown merchant. The amount, date and direction are already correct;
        // only the name and category are missing, so the row still counts
        // towards totals while it waits.
        return new Resolution(UNCATEGORIZED, null, CategorySource.DEFAULT, false);
    }
}
