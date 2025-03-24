package xyz.hxwang.jointaccountmanager;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/balance")
public class BalanceController {
    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("")
    public Balance getBalance() {
        return balanceService.findBalanceById(0L);
    }

    @PutMapping("")
    public void updateBalance(@RequestBody String offset) {
        balanceService.updateBalance(0L, offset);
    }


}
