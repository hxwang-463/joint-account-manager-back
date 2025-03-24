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
@Table(name = "balance")
public class Balance {

    @Id
    private Long id;

    @Column
    private BigDecimal amount;
}