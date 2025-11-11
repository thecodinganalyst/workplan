package com.hevlar.workplan.repository;

import com.hevlar.workplan.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findTopByOrderByIdAsc();

    boolean existsByNameIgnoreCase(String name);
}
