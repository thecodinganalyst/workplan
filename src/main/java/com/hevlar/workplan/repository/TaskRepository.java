package com.hevlar.workplan.repository;

import com.hevlar.workplan.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
