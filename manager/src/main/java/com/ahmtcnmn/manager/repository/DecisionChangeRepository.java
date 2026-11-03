package com.ahmtcnmn.manager.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ahmtcnmn.manager.model.DecisionChange;

public interface DecisionChangeRepository extends JpaRepository<DecisionChange, Long> {

    // Cursor'dan sonraki değişiklikler; decision'ı da birlikte yükler (N+1 sorgu olmasın)
    @Query("select c from DecisionChange c join fetch c.decision d where c.id > :after order by c.id")
    List<DecisionChange> findAfter(@Param("after") long after, Limit limit);

    @Query("select coalesce(max(c.id), 0) from DecisionChange c")
    long findMaxId();
}
