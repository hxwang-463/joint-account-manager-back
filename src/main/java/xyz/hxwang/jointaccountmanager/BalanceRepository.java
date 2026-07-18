package xyz.hxwang.jointaccountmanager;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, Long> {
    Balance findTopByOrderByIdDesc();
    List<Balance> findAllByOrderByDateDescIdDesc(Pageable pageable);

    Optional<Balance> findByRecordId(Long recordId);

    /**
     * Shifts the running total of every row after the given one. Used when a paid
     * record's amount is corrected: the rows that follow were all computed on top
     * of the old figure and have to move by the same difference.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Balance b SET b.amount = b.amount - :diff WHERE b.id > :afterId")
    void shiftAmountsAfter(@Param("afterId") Long afterId, @Param("diff") BigDecimal diff);
}
