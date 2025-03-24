package xyz.hxwang.jointaccountmanager;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "records")
public class Record {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="acct-name", nullable = false)
    private String acctName;

    @Column(name="date", nullable = false)
    private LocalDate date;

    @Column(name="amount")
    private BigDecimal amount;

    @Column(name="is-paid")
    private boolean isPaid;
}
