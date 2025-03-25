package xyz.hxwang.jointaccountmanager;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findAccountsByDayOfMonthEquals(int dayOfMonth);

}
