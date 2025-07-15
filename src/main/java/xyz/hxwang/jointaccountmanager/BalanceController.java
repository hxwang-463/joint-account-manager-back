package xyz.hxwang.jointaccountmanager;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/balance")
public class BalanceController {
    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("")
    public Balance getBalance() {
        return balanceService.findLatestBalance();
    }

    @PostMapping("")
    public Balance updateBalance(@RequestBody UpdateBalanceRequest request) {
        return balanceService.updateBalance(BigDecimal.valueOf(Double.parseDouble(request.getOffset())), request.getComment());
    }

    @GetMapping("/history")
    public ResponseEntity<List<Balance>> getBalanceHistory(@RequestParam(name="limit" ,defaultValue = "10") int limit) {
        List<Balance> histories = balanceService.getLatestHistories(limit);
        return ResponseEntity.ok(histories);
    }


}
