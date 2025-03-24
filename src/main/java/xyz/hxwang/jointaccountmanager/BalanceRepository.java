package xyz.hxwang.jointaccountmanager;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, Long> {
    Balance findBalanceById(Long id);

    @Modifying
    @Transactional
    @Query("UPDATE Balance m SET m.amount = :amount WHERE m.id = :id")
    void updateBalanceById(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
