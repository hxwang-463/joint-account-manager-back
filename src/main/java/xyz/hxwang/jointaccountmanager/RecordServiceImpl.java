package xyz.hxwang.jointaccountmanager;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class RecordServiceImpl implements RecordService{
    private final RecordRepository recordRepository;
    private final BalanceRepository balanceRepository;

    public RecordServiceImpl(RecordRepository recordRepository, BalanceRepository balanceRepository) {
        this.recordRepository = recordRepository;
        this.balanceRepository = balanceRepository;
    }

    @Override
    public List<RecordDTO> getAllRecordsAfterDate(LocalDate date) {
        List<Record> result = recordRepository.findAllByDateAfterOrderByDateAsc(date);
        return result.stream().map(r -> RecordDTO.builder()
                .acctName(r.getAcctName())
                .id(r.getId())
                .date(r.getDate())
                .amount(r.getAmount())
                .isPaid(r.isPaid())
                .build()).toList();
    }

    @Override
    public void changeAmount(String id, String amount) {
        recordRepository.updateAmountById(Long.valueOf(id), new BigDecimal(amount));
    }

    @Override
    @Transactional
    public void markPaid(String id) {
        recordRepository.updateIsPaidById(Long.valueOf(id));
        BigDecimal balance = balanceRepository.findBalanceById(0L).getAmount();
        BigDecimal amount = recordRepository.getRecordById(Long.valueOf(id)).getAmount();
        balanceRepository.updateBalanceById(0L, balance.subtract(amount));
    }

    @Override
    public void changeDate(String id, String offset) {
        LocalDate date = recordRepository.getRecordById(Long.valueOf(id)).getDate();
        LocalDate newDate;
        int offsetInt = Integer.parseInt(offset);
        if (offsetInt > 0) {
            newDate = date.plusDays(offsetInt);
        } else {
            newDate = date.minusDays(offsetInt * -1);
        }
        recordRepository.updateDateById(Long.valueOf(id), newDate);
    }

}
