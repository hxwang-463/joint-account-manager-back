package xyz.hxwang.jointaccountmanager;

import java.math.BigDecimal;
import java.util.List;

public interface BalanceService {
    Balance updateBalance(BigDecimal offset, String comment);
    Balance findLatestBalance();
    List<Balance> getLatestHistories(int limit);
}
