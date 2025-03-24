package xyz.hxwang.jointaccountmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    public void changeAmount(long id) {

    }

    @Override
    public void markPaid(long id) {

    }

    @Override
    public void upDate(long id) {

    }

    @Override
    public void downDate(long id) {

    }
}
