package xyz.hxwang.jointaccountmanager;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
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

    @Scheduled(cron = "0 0 0 * * ?") // Runs every day at 00:00
    public void createNewRecords() {
        log.info("start creating new records");
        LocalDate today = LocalDate.now();
        accountRepository.findAccountsByDayOfMonthEquals(today.getDayOfMonth()).forEach(a -> {
            if (recordRepository.findAllByAcctNameEqualsAndDateEquals(a.getAcctName(), today.plusMonths(1)).isEmpty()) {
                recordRepository.saveAndFlush(Record.builder()
                        .acctName(a.getAcctName())
                        .date(today.plusMonths(1))
                        .build());
                log.info("creating new record with acctName={} and date={}", a.getAcctName(), today.plusMonths(1));
            }
        });
        log.info("finish creating new records");
    }

    @Scheduled(cron = "0 30 0 * * ?") // Runs every day at 00:30
    @Transactional
    public void markRecordsAsPaid() {
        log.info("start marking records as paid");
        LocalDate today = LocalDate.now();
        recordRepository.findAllByDateEquals(today).forEach(r -> {
            if (!r.isPaid()) {
                balanceRepository.updateBalanceById(0L, balanceRepository.findBalanceById(0L).getAmount().subtract(r.getAmount()));
                recordRepository.updateIsPaidById(r.getId());
                log.info("marking records as paid with id={}", r.getId());
            }
        });
        log.info("finish marking records as paid");
    }
}