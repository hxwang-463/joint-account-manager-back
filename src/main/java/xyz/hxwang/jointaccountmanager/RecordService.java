package xyz.hxwang.jointaccountmanager;

import java.time.LocalDate;
import java.util.List;

public interface RecordService {
    List<RecordDTO> getAllRecordsAfterDate(LocalDate date);
    void changeAmount(long id);
    void markPaid(long id);
    void upDate(long id);
    void downDate(long id);
}
