package com.ahmtcnmn.manager.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ahmtcnmn.manager.model.Agent;
import com.ahmtcnmn.manager.model.Decision;

public interface DecisionRepository extends JpaRepository<Decision, Long>, JpaSpecificationExecutor<Decision> {

    // Aynı değer için aktif karar (mükerrer ban açmamak için)
    List<Decision> findByValueAndRevokedAtIsNull(String value);

    // Süresi dolmuş ama henüz kapatılmamış kararlar (DecisionExpiryJob)
    List<Decision> findByRevokedAtIsNullAndExpiresAtBefore(Instant now);

    // Bir Agent'ı ilgilendiren aktif kararlar: GLOBAL olanlar + yalnız ona yönelik olanlar
    @Query("""
        select d from Decision d
        where d.revokedAt is null and d.simulated = false
          and (d.scope = com.ahmtcnmn.manager.model.DecisionScope.GLOBAL or d.targetAgent = :agent)
        order by d.id
        """)
    List<Decision> findActiveForAgent(@Param("agent") Agent agent);

    List<Decision> findByRevokedAtIsNull();

    long countByRevokedAtIsNull();
}
