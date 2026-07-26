package xyz.hxwang.jointaccountmanager;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import xyz.hxwang.jointaccountmanager.spend.StatementFormat;

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

    /**
     * Whether this account arrives with an itemised statement, and whose format
     * it is. NONE means there is no export to import, so marking the bill paid
     * generates a single transaction instead.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statement_format", nullable = false, length = 20)
    @Builder.Default
    private StatementFormat statementFormat = StatementFormat.NONE;

    /**
     * Category for the transaction generated when this bill is paid. Only
     * meaningful when {@link #statementFormat} is NONE — and generation
     * requires it, which is what keeps a not-yet-configured card inert.
     */
    @Column(name = "default_category", length = 40)
    private String defaultCategory;
}