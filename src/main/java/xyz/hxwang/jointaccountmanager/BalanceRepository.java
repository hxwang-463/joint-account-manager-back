package xyz.hxwang.jointaccountmanager;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, Long> {
    Balance findTopByOrderByIdDesc();
    List<Balance> findAllByOrderByDateDescIdDesc(Pageable pageable);
}
