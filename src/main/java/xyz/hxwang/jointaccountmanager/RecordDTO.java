package xyz.hxwang.jointaccountmanager;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.math.BigDecimal;

@Data
@Builder
public class RecordDTO {
    private long id;
    private String acctName;
    private LocalDate date;
    private BigDecimal amount;
    private boolean isPaid;
}
