package com.learning.progress.repository;

import com.learning.progress.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LevelRepository extends JpaRepository<Level, Long> {

    boolean existsByLevelName(String levelName);

    boolean existsByLevelNameAndIdNot(String levelName, Long id);

    boolean existsByOrderNumber(Integer orderNumber);

    boolean existsByOrderNumberAndIdNot(Integer orderNumber, Long id);
}
