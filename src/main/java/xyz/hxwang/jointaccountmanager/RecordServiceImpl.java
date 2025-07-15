package xyz.hxwang.jointaccountmanager;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class RecordServiceImpl implements RecordService{
    private final RecordRepository recordRepository;
    private final BalanceService balanceService;

    public RecordServiceImpl(RecordRepository recordRepository, BalanceService balanceService) {
        this.recordRepository = recordRepository;
        this.balanceService = balanceService;
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
        Record record = recordRepository.getRecordById(Long.valueOf(id));
        String comment = "Mark " + record.getAcctName() + " paid successfully, id: " + id;
        balanceService.updateBalance(record.getAmount().negate(), comment);
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
