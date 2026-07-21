package xyz.hxwang.jointaccountmanager;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class RecordServiceImpl implements RecordService{
    private final RecordRepository recordRepository;
    private final BalanceService balanceService;

    public RecordServiceImpl(RecordRepository recordRepository, BalanceService balanceService) {
        this.recordRepository = recordRepository;
        this.balanceService = balanceService;
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
    @Transactional
    public void changeAmount(String id, String amount) {
        Long recordId = Long.valueOf(id);
        BigDecimal newAmount = new BigDecimal(amount);

        Record record = recordRepository.getRecordById(recordId);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No record with id " + recordId);
        }
        BigDecimal oldAmount = record.getAmount();
        boolean paid = record.isPaid();

        recordRepository.updateAmountById(recordId, newAmount);

        // An unpaid record has not touched the ledger yet, so there is nothing to
        // correct. A paid one already moved the balance by its old amount.
        if (paid) {
            balanceService.adjustForRecordAmountChange(recordId, oldAmount, newAmount);
        }
    }

    @Override
    @Transactional
    public void markPaid(String id) {
        Long recordId = Long.valueOf(id);
        Record record = recordRepository.getRecordById(recordId);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No record with id " + recordId);
        }
        // Without this guard a repeated call would deduct the amount from the
        // balance twice and leave two ledger rows claiming the same record.
        if (record.isPaid()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Record " + recordId + " is already marked paid");
        }

        recordRepository.updateIsPaidById(recordId);
        String comment = "Mark " + record.getAcctName() + " paid successfully, id: " + id;
        balanceService.updateBalance(record.getAmount().negate(), comment, recordId);
    }

    @Override
    @Transactional
    public void revertPaid(String id) {
        Long recordId = Long.valueOf(id);
        Record record = recordRepository.getRecordById(recordId);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No record with id " + recordId);
        }
        if (!record.isPaid()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Record " + recordId + " is not paid, so there is nothing to revert");
        }

        recordRepository.updateUnpaidById(recordId);
        // Removes the linked ledger row and heals the running totals after it. A
        // record paid before ledger linking existed has no row, so this is a no-op
        // and only the paid flag is cleared.
        balanceService.removeForRevertedRecord(recordId);
    }

    @Override
    public void changeDate(String id, String offset) {
        LocalDate date = recordRepository.getRecordById(Long.valueOf(id)).getDate();
        LocalDate newDate;
        int offsetInt = Integer.parseInt(offset);
        if (offsetInt > 0) {
            newDate = date.plusDays(offsetInt);
        } else {
            newDate = date.minusDays(offsetInt * -1);
        }
        recordRepository.updateDateById(Long.valueOf(id), newDate);
    }

    @Override
    public BigDecimal getTotalAmountForMonth(String year, String month) {
        int yearInt = Integer.parseInt(year);
        int monthInt = Integer.parseInt(month);
        
        LocalDate startDate = LocalDate.of(yearInt, monthInt, 1);
        LocalDate endDate = startDate.plusMonths(1);
        
        return recordRepository.sumAmountByMonthRange(startDate, endDate);
    }

}
