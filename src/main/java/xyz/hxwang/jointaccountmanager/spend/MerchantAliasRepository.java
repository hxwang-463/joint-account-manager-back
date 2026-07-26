package xyz.hxwang.jointaccountmanager.spend;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantAliasRepository extends JpaRepository<MerchantAlias, Long> {
    Optional<MerchantAlias> findByLookupKey(String lookupKey);
}
