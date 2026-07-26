package xyz.hxwang.jointaccountmanager.spend;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Reduces a statement descriptor to a stable key for the merchant it came from.
 *
 * <p>Banks pack per-purchase noise into the descriptor — Amazon writes a
 * different order id every time, Whole Foods appends a store number. Left
 * as-is, thirteen Amazon purchases look like thirteen merchants nobody has ever
 * seen before, which wrecks both merchant totals and any attempt to cache a
 * classification.
 *
 * <p>Stripping that noise collapses them: on a real Chase export the thirteen
 * Amazon variants reduce to two keys.
 *
 * <p>The goal is consistency, not beauty. The same descriptor must always
 * produce the same key; whether the key reads nicely matters much less, since
 * a friendlier display name can be attached to it separately.
 */
@Component
public class MerchantNormalizer {

    /**
     * A run of six or more characters mixing letters and digits — an order id,
     * an authorisation code, a reference. Never part of a merchant's name.
     */
    private static final String RANDOM_TOKEN = "^(?=.*\\d)(?=.*[A-Z])[A-Z0-9]{6,}$";

    /** A bare number: store number, lane, terminal. */
    private static final String NUMERIC_TOKEN = "^#?\\d{3,}$";

    /**
     * A date or an order index the merchant stamped into the descriptor —
     * "07-22", "07/21-3". Ride-hailing and delivery apps do this on every
     * charge, so leaving it in gives each ride its own merchant key. Worse, a
     * key that embeds today's date can never match a decision made yesterday,
     * so those merchants would need reclassifying forever.
     */
    private static final String DATE_LIKE_TOKEN = "^\\d{1,4}([-/]\\d{1,4})+$";

    /**
     * Payment-app prefixes that describe how it was paid rather than to whom,
     * so leaving them in splits one merchant across two keys. "SP" is Shopify
     * Payments — every small shop selling through Shopify arrives with it, so
     * without stripping it they all cluster under a processor rather than
     * appearing as themselves.
     *
     * Matching requires a trailing space, so a merchant whose own name begins
     * with these letters ("SPROUTS FARMERS MKT") is unaffected.
     */
    private static final String[] PAYMENT_PREFIXES =
            {"APLPAY ", "SQ *", "TST* ", "PAYPAL *", "PY *", "SP *", "SP "};

    public String lookupKey(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) {
            return "";
        }

        String working = descriptor.toUpperCase().replace('*', ' ').trim();
        for (String prefix : PAYMENT_PREFIXES) {
            String upper = prefix.replace('*', ' ').trim() + " ";
            if (working.startsWith(upper)) {
                working = working.substring(upper.length());
            }
        }

        String cleaned = Arrays.stream(working.split("\\s+"))
                .filter(token -> !token.isBlank())
                .filter(token -> !token.matches(RANDOM_TOKEN))
                .filter(token -> !token.matches(NUMERIC_TOKEN))
                .filter(token -> !token.matches(DATE_LIKE_TOKEN))
                .collect(Collectors.joining(" "))
                .replaceAll("[#.,\\-]+$", "")
                .trim();

        // Everything looked like noise — fall back rather than key on an empty string.
        if (cleaned.isBlank()) {
            cleaned = working.trim();
        }
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    /**
     * A readable version of the key, used until something better (a manual
     * correction, or a classifier) supplies a proper name.
     */
    public String displayName(String lookupKey) {
        if (lookupKey == null || lookupKey.isBlank()) {
            return null;
        }
        return Arrays.stream(lookupKey.split("\\s+"))
                .map(word -> word.length() <= 2
                        ? word
                        : word.charAt(0) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
