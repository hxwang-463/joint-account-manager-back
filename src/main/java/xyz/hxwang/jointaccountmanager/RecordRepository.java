package xyz.hxwang.jointaccountmanager;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface RecordRepository extends JpaRepository<Record, Long> {
    List<Record> findAllByDateAfterOrderByDateAsc(LocalDate date);

    Record getRecordById(Long id);

    List<Record> findAllByDateEquals(LocalDate date);

    List<Record> findAllByAcctNameEqualsAndDateEquals(String acctName, LocalDate date);

    @Modifying
    @Transactional
    @Query("UPDATE Record m SET m.isPaid = true WHERE m.id = :id")
    void updateIsPaidById(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE Record m SET m.amount = :amount WHERE m.id = :id")
    void updateAmountById(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Modifying
    @Transactional
    @Query("UPDATE Record m SET m.date = :date WHERE m.id = :id")
    void updateDateById(@Param("id") Long id, @Param("date") LocalDate date);
}

