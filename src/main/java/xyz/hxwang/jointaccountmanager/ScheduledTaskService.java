package xyz.hxwang.jointaccountmanager;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ScheduledTaskService {
    private final RecordRepository recordRepository;
    private final BalanceRepository balanceRepository;
    private final AccountRepository accountRepository;

    public ScheduledTaskService(RecordRepository recordRepository, BalanceRepository balanceRepository, AccountRepository accountRepository) {
        this.recordRepository = recordRepository;
        this.balanceRepository = balanceRepository;
        this.accountRepository = accountRepository;
    }

    @Scheduled(cron = "0 0 5 * * ?") // Runs every day at 5:00 AM
    public void createNewRecords() {
        LocalDate today = LocalDate.now();
        accountRepository.findAccountsByDayOfMonthEquals(today.getDayOfMonth()).forEach(a -> {
            if (recordRepository.findAllByAcctNameEqualsAndDateEquals(a.getAcctName(), today).isEmpty()) {
                recordRepository.saveAndFlush(Record.builder()
                        .acctName(a.getAcctName())
                        .date(today.plusMonths(1))
                        .build());
            }
        });
    }

    @Scheduled(cron = "0 30 5 * * ?") // Runs every day at 5:30AM
    public void markRecordsAsPaid() {
        LocalDate todayDate = LocalDate.now();
        recordRepository.findAllByDateEquals(todayDate).forEach(r -> {
            if (!r.isPaid()) {
                balanceRepository.updateBalanceById(0L, balanceRepository.findBalanceById(0L).getAmount().subtract(r.getAmount()));
                recordRepository.updateIsPaidById(r.getId());
            }
        });
    }
}