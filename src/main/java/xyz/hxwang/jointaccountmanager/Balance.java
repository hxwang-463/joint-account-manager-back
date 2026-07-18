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
@Table(name = "balance")
public class Balance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private BigDecimal amount;

    @Column
    private BigDecimal delta;

    @Column
    private LocalDate date;

    @Column
    private String comment;

    /**
     * The record whose payment produced this row, or null for rows that did not
     * come from marking a record paid (manual adjustments, and anything created
     * before this column existed).
     */
    @Column(name = "record_id")
    private Long recordId;
}