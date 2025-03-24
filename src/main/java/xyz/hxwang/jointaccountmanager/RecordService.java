package xyz.hxwang.jointaccountmanager;

import java.time.LocalDate;
import java.util.List;

public interface RecordService {
    List<RecordDTO> getAllRecordsAfterDate(LocalDate date);
    void changeAmount(String id, String amount);
    void markPaid(String id);
    void changeDate(String id, String offset);
}
