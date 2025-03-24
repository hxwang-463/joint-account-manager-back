package xyz.hxwang.jointaccountmanager;

public interface BalanceService {
    void updateBalance(Long id, String offset);
    Balance findBalanceById(Long id);
}
