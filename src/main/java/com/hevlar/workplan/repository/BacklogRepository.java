package com.hevlar.workplan.repository;

import com.hevlar.workplan.domain.Backlog;
import com.hevlar.workplan.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BacklogRepository extends JpaRepository<Backlog, Long> {
    Optional<Backlog> findByProject(Project project);
}
