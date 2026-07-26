package xyz.hxwang.jointaccountmanager.spend;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One purchase, refund, fee or card payment.
 *
 * <p>Rows come from two places: parsing a statement export, or marking a bill
 * paid on an account that has no statement behind it (rent, utilities). The two
 * are told apart by which of {@code statementImportId} and {@code recordId} is
 * set.
 *
 * <p>This table never affects the chequing balance — that stays driven by
 * marking bills paid, so the two ledgers describe the same money at different
 * grains without double counting.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "bank_txn")
public class BankTxn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The account row this belongs to — a card, or a direct-pay bill. */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** Set when imported from a statement; null when generated from a bill. */
    @Column(name = "statement_import_id")
    private Long statementImportId;

    /** Set when generated from a bill, so amending or reverting can find it. */
    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "post_date")
    private LocalDate postDate;

    @Column(name = "description_raw", nullable = false, length = 255)
    private String descriptionRaw;

    /** Tidied name for display; null until something has cleaned it up. */
    @Column(name = "merchant", length = 120)
    private String merchant;

    /**
     * Always positive. Chase signs purchases negative and Amex signs them
     * positive, so the sign is resolved into {@link #direction} at parse time
     * and never stored.
     */
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 20)
    private TxnType txnType;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", nullable = false, length = 20)
    private CategorySource categorySource;

    /** The bank's own label, kept verbatim so a mapping can be added later. */
    @Column(name = "bank_category", length = 80)
    private String bankCategory;

    /** Which cardholder spent it — Amex reports this, Chase does not. */
    @Column(name = "card_member", length = 60)
    private String cardMember;

    @Column(name = "card_ref", length = 20)
    private String cardRef;

    /** The bank's own transaction id where one exists (Amex reference). */
    @Column(name = "source_ref", length = 60)
    private String sourceRef;

    /**
     * Stable identity within the account. Unique per account, which is what
     * makes re-uploading an overlapping export a no-op instead of a duplicate.
     */
    @Column(name = "dedup_key", nullable = false, length = 80)
    private String dedupKey;

    /**
     * False until the classifier has assigned a merchant and category. The
     * amount, date and direction are correct from the moment of import, so an
     * unprocessed row still counts towards totals — it just has no name yet.
     */
    @Column(name = "processed", nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
