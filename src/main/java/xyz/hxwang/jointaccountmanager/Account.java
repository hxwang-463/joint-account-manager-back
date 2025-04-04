package xyz.hxwang.jointaccountmanager;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "account")
public class Account {
    @Id
    private Long id;

    @Column(name="acct-name", nullable = false)
    private String acctName;

    @Column(name="day-of-month", nullable = false)
    private int dayOfMonth;

    @Column(name="default-amount")
    private BigDecimal defaultAmount;
}