package xyz.hxwang.jointaccountmanager;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
        return updateBalance(offset, comment, null);
    }

    @Override
    @Transactional
    public Balance updateBalance(BigDecimal offset, String comment, Long recordId) {
        BigDecimal amount = balanceRepository.findTopByOrderByIdDesc().getAmount();
        Balance balance = Balance.builder()
                .amount(amount.add(offset))
                .comment(comment)
                .delta(offset)
                .date(LocalDate.now())
                .recordId(recordId)
                .build();
        return balanceRepository.save(balance);
    }

    @Override
    @Transactional
    public void adjustForRecordAmountChange(Long recordId, BigDecimal oldAmount, BigDecimal newAmount) {
        BigDecimal previous = oldAmount == null ? BigDecimal.ZERO : oldAmount;
        BigDecimal diff = newAmount.subtract(previous);
        if (diff.signum() == 0) {
            return;
        }

        Balance entry = balanceRepository.findByRecordId(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Record " + recordId + " is paid but has no linked balance entry, so the "
                                + "balance history cannot be corrected automatically. It was paid "
                                + "before payments began recording their record id."));

        // Paying a record subtracts its amount, so a larger amount means a more
        // negative delta and a lower running total from that point onwards.
        entry.setDelta(entry.getDelta().subtract(diff));
        entry.setAmount(entry.getAmount().subtract(diff));
        balanceRepository.save(entry);

        balanceRepository.shiftAmountsAfter(entry.getId(), diff);
    }

    @Override
    @Transactional
    public void removeForRevertedRecord(Long recordId) {
        Balance entry = balanceRepository.findByRecordId(recordId).orElse(null);
        if (entry == null) {
            // A legacy or manual payment with no linked ledger row: nothing to unwind.
            return;
        }

        Long entryId = entry.getId();
        BigDecimal delta = entry.getDelta();

        // The row's delta was -amount, so shifting later totals by that delta adds the
        // amount back to each of them. Done before the delete so the row id is still
        // valid; the deleted row itself is excluded (id strictly greater).
        balanceRepository.shiftAmountsAfter(entryId, delta);
        balanceRepository.deleteById(entryId);
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
