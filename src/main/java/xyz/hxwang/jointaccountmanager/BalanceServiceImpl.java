package xyz.hxwang.jointaccountmanager;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class BalanceServiceImpl implements BalanceService {
    private final BalanceRepository balanceRepository;

    public BalanceServiceImpl(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    @Override
    @Transactional
    public Balance updateBalance(BigDecimal offset, String comment) {
        BigDecimal amount = balanceRepository.findTopByOrderByIdDesc().getAmount();
//        BigDecimal.valueOf(Double.parseDouble(offset))
        Balance balance = Balance.builder()
                .amount(amount.add(offset))
                .comment(comment)
                .delta(offset)
                .date(LocalDate.now())
                .build();
        return balanceRepository.save(balance);
    }

    @Override
    public Balance findLatestBalance() {
        return balanceRepository.findTopByOrderByIdDesc();
    }

    @Override
    public List<Balance> getLatestHistories(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return balanceRepository.findAllByOrderByDateDescIdDesc(pageable);
    }
}
