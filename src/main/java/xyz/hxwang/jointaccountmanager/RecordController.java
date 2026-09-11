package xyz.hxwang.jointaccountmanager;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/records")
public class RecordController {

    /** How far back the records list reaches when the caller does not say. */
    static final int DEFAULT_DAYS = 7;

    /** Ten years. Past this the window stops being a window and becomes the table. */
    static final int MAX_DAYS = 3650;

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    /**
     * The records the table shows: everything after {@code today - days}, and
     * everything scheduled ahead.
     *
     * <p>The window is open-ended into the future on purpose — upcoming bills are
     * the point of the list, and there is no sensible horizon at which to stop
     * showing them. Only the past end moves, widening by a week each time the
     * user asks for earlier records.
     *
     * <p>Anchored on the server's today rather than on a date the caller sends,
     * so a browser with a skewed clock cannot shift the window.
     */
    @GetMapping("")
    public List<RecordDTO> getRecords(@RequestParam(defaultValue = "" + DEFAULT_DAYS) int days){
        int window = Math.min(Math.max(days, 1), MAX_DAYS);
        LocalDate today = LocalDate.now();
        return recordService.getAllRecordsAfterDate(today.minusDays(window));
    }

    @GetMapping("/total/year/{year}/month/{month}")
    public BigDecimal getTotalAmountForMonth(@PathVariable String year, @PathVariable String month){
        return recordService.getTotalAmountForMonth(year, month);
    }

    @PutMapping("/{id}/amount")
    public void changeRecordAmount(@PathVariable String id, @RequestBody String amount){
        recordService.changeAmount(id, amount);
    }

    @PutMapping("/{id}/paid")
    public void markRecordPaid(@PathVariable String id){
        recordService.markPaid(id);
    }

    @PutMapping("/{id}/unpaid")
    public void revertRecordPaid(@PathVariable String id){
        recordService.revertPaid(id);
    }

    @PutMapping("/{id}/date")
    public void markRecordPaid(@PathVariable String id, @RequestBody String offset){
        recordService.changeDate(id, offset);
    }
}
