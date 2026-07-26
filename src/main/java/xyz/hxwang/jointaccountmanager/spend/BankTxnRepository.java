package xyz.hxwang.jointaccountmanager.spend;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Transactions, plus the aggregates the analysis endpoints are built on.
 *
 * <p>Every aggregate excludes PAYMENT rows and nets refunds against
 * purchases. Paying off a card is a transfer from chequing, not spending —
 * counting it would roughly double the apparent outgoings.
 */
@Repository
public interface BankTxnRepository extends JpaRepository<BankTxn, Long> {

    Optional<BankTxn> findByAccountIdAndDedupKey(Long accountId, String dedupKey);

    Optional<BankTxn> findByRecordId(Long recordId);

    List<BankTxn> findByStatementImportId(Long statementImportId);

    void deleteByStatementImportId(Long statementImportId);

    @Query("SELECT COUNT(t) FROM BankTxn t WHERE t.processed = FALSE")
    long countUnprocessed();

    /**
     * Distinct raw descriptors still awaiting classification. An approximation
     * of "how many merchants" for the banner — the exact figure needs the
     * normaliser, which lives in Java.
     */
    @Query("SELECT COUNT(DISTINCT t.descriptionRaw) FROM BankTxn t WHERE t.processed = FALSE")
    long countDistinctUnprocessedDescriptors();

    @Query("SELECT t FROM BankTxn t WHERE t.processed = FALSE ORDER BY t.txnDate DESC, t.id DESC")
    List<BankTxn> findUnprocessed(Pageable pageable);

    /** Latest activity per account — half of "which export should I download?". */
    @Query("SELECT t.accountId, MAX(t.txnDate) FROM BankTxn t GROUP BY t.accountId")
    List<Object[]> findLatestTxnDatePerAccount();

    /**
     * Filtered transaction list. Every filter is optional; a null means
     * "no restriction", which keeps this to one query instead of a
     * combinatorial set of finder methods.
     */
    @Query("""
            SELECT t FROM BankTxn t
            WHERE (:from IS NULL OR t.txnDate >= :from)
              AND (:to IS NULL OR t.txnDate <= :to)
              AND (:accountId IS NULL OR t.accountId = :accountId)
              AND (:category IS NULL OR t.category = :category)
              AND (:cardMember IS NULL OR t.cardMember = :cardMember)
              AND (:merchant IS NULL OR LOWER(t.merchant) LIKE LOWER(CONCAT('%', :merchant, '%'))
                                     OR LOWER(t.descriptionRaw) LIKE LOWER(CONCAT('%', :merchant, '%')))
              AND (:includePayments = TRUE OR t.txnType <> xyz.hxwang.jointaccountmanager.spend.TxnType.PAYMENT)
            ORDER BY t.txnDate DESC, t.id DESC
            """)
    List<BankTxn> search(@Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("accountId") Long accountId,
                         @Param("category") String category,
                         @Param("cardMember") String cardMember,
                         @Param("merchant") String merchant,
                         @Param("includePayments") boolean includePayments,
                         Pageable pageable);

    /**
     * Net spend per calendar month, for the trend chart. Grouping in SQL rather
     * than issuing one request per month keeps this to a single round trip.
     */
    @Query("""
            SELECT YEAR(t.txnDate), MONTH(t.txnDate),
                   SUM(CASE WHEN t.direction = xyz.hxwang.jointaccountmanager.spend.Direction.DEBIT
                            THEN t.amount ELSE -t.amount END),
                   COUNT(t)
            FROM BankTxn t
            WHERE t.txnDate >= :from AND t.txnDate <= :to
              AND t.txnType <> xyz.hxwang.jointaccountmanager.spend.TxnType.PAYMENT
            GROUP BY YEAR(t.txnDate), MONTH(t.txnDate)
            ORDER BY YEAR(t.txnDate), MONTH(t.txnDate)
            """)
    List<Object[]> sumByMonth(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Distinct cardholders seen, for populating a filter without a separate table. */
    @Query("SELECT DISTINCT t.cardMember FROM BankTxn t WHERE t.cardMember IS NOT NULL ORDER BY t.cardMember")
    List<String> findDistinctCardMembers();

    /**
     * Net spend per category over a window: purchases less refunds, so a
     * returned item stops counting instead of showing up twice.
     */
    @Query("""
            SELECT t.category,
                   SUM(CASE WHEN t.direction = xyz.hxwang.jointaccountmanager.spend.Direction.DEBIT
                            THEN t.amount ELSE -t.amount END),
                   COUNT(t)
            FROM BankTxn t
            WHERE t.txnDate >= :from AND t.txnDate <= :to
              AND t.txnType <> xyz.hxwang.jointaccountmanager.spend.TxnType.PAYMENT
            GROUP BY t.category
            ORDER BY 2 DESC
            """)
    List<Object[]> sumByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** The same net figure grouped by whoever's card was used. */
    @Query("""
            SELECT COALESCE(t.cardMember, 'Unattributed'),
                   SUM(CASE WHEN t.direction = xyz.hxwang.jointaccountmanager.spend.Direction.DEBIT
                            THEN t.amount ELSE -t.amount END),
                   COUNT(t)
            FROM BankTxn t
            WHERE t.txnDate >= :from AND t.txnDate <= :to
              AND t.txnType <> xyz.hxwang.jointaccountmanager.spend.TxnType.PAYMENT
            GROUP BY COALESCE(t.cardMember, 'Unattributed')
            ORDER BY 2 DESC
            """)
    List<Object[]> sumByCardMember(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(t.merchant, t.descriptionRaw),
                   SUM(CASE WHEN t.direction = xyz.hxwang.jointaccountmanager.spend.Direction.DEBIT
                            THEN t.amount ELSE -t.amount END),
                   COUNT(t)
            FROM BankTxn t
            WHERE t.txnDate >= :from AND t.txnDate <= :to
              AND t.txnType <> xyz.hxwang.jointaccountmanager.spend.TxnType.PAYMENT
            GROUP BY COALESCE(t.merchant, t.descriptionRaw)
            ORDER BY 2 DESC
            """)
    List<Object[]> sumByMerchant(@Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            SELECT SUM(CASE WHEN t.direction = xyz.hxwang.jointaccountmanager.spend.Direction.DEBIT
                            THEN t.amount ELSE -t.amount END)
            FROM BankTxn t
            WHERE t.txnDate >= :from AND t.txnDate <= :to
              AND t.txnType <> xyz.hxwang.jointaccountmanager.spend.TxnType.PAYMENT
            """)
    BigDecimal totalNetSpend(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
