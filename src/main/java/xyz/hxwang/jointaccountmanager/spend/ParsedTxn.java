package xyz.hxwang.jointaccountmanager.spend;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A transaction read out of a statement export, after each bank's quirks have
 * been normalised away but before it has been categorised or stored.
 *
 * <p>{@code amount} is always positive here; whichever way the source bank
 * happened to sign it has already been resolved into {@code direction}.
 */
@Getter
@Builder
public class ParsedTxn {

    private final LocalDate txnDate;
    private final LocalDate postDate;
    private final String descriptionRaw;
    private final BigDecimal amount;
    private final Direction direction;
    private final TxnType txnType;
    /** The bank's own category label, or null if it did not supply one. */
    private final String bankCategory;
    private final String cardMember;
    private final String cardRef;
    /** The bank's own transaction id, where it publishes one. */
    private final String sourceRef;
    /** Stable identity within the account; see the parsers for how it is derived. */
    private final String dedupKey;
}
