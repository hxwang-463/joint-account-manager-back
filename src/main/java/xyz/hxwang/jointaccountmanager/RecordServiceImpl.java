package xyz.hxwang.jointaccountmanager;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class RecordServiceImpl implements RecordService{
    private final RecordRepository recordRepository;

    public RecordServiceImpl(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
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
    public void markPaid(String id) {
        recordRepository.updateIsPaidById(Long.valueOf(id));
        // TODO MODIFY BALANCE
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
