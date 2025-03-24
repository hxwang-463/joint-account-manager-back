package xyz.hxwang.jointaccountmanager;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/records")
public class RecordController {
    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping("")
    public List<RecordDTO> getRecords(){
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysBefore = today.minusDays(7);
        return recordService.getAllRecordsAfterDate(sevenDaysBefore);
    }
}
