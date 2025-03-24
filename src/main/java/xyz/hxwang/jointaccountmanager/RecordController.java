package xyz.hxwang.jointaccountmanager;

import org.springframework.web.bind.annotation.*;

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

    @PutMapping("/{id}/amount")
    public void changeRecordAmount(@PathVariable String id, @RequestBody String amount){
        recordService.changeAmount(id, amount);
    }

    @PutMapping("/{id}/paid")
    public void markRecordPaid(@PathVariable String id){
        recordService.markPaid(id);
    }

    @PutMapping("/{id}/date")
    public void markRecordPaid(@PathVariable String id, @RequestBody String offset){
        recordService.changeDate(id, offset);
    }
}
