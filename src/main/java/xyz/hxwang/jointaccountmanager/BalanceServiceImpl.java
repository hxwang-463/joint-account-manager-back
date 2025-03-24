package xyz.hxwang.jointaccountmanager;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BalanceServiceImpl implements BalanceService {
    private final BalanceRepository balanceRepository;

    public BalanceServiceImpl(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }
    @Override
    public void updateBalance(Long id, String offset) {
        BigDecimal amount = balanceRepository.findBalanceById(id).getAmount();
        balanceRepository.updateBalanceById(id, amount.add(BigDecimal.valueOf(Double.parseDouble(offset))));
    }

    @Override
    public Balance findBalanceById(Long id) {
        return balanceRepository.findBalanceById(id);
    }
}
