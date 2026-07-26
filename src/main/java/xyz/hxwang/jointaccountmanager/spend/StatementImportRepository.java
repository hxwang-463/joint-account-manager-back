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

@Repository
public interface StatementImportRepository extends JpaRepository<StatementImport, Long> {

    List<StatementImport> findAllByOrderByImportedAtDesc(Pageable pageable);

    /** Recognises a byte-identical re-upload before anything is parsed. */
    Optional<StatementImport> findFirstByAccountIdAndFileSha256(Long accountId, String fileSha256);

    /** The other half of "which export should I download?" — last time we imported one. */
    @Query("SELECT s.accountId, MAX(s.importedAt) FROM StatementImport s GROUP BY s.accountId")
    List<Object[]> findLatestImportPerAccount();
}
