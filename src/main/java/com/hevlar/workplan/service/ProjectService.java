package com.hevlar.workplan.service;

import com.hevlar.workplan.domain.Project;
import com.hevlar.workplan.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public boolean projectExists() {
        return projectRepository.count() > 0;
    }

    @Transactional(readOnly = true)
    public Optional<Project> getProject() {
        return projectRepository.findTopByOrderByIdAsc().map(project -> {
            project.getUsers().size();
            return project;
        });
    }

    @Transactional
    public Project createProject(String name) {
        if (projectExists()) {
            throw new IllegalStateException("A project already exists");
        }
        if (projectRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalStateException("A project with this name already exists");
        }
        Project project = new Project();
        project.setName(name);
        return projectRepository.save(project);
    }
}
