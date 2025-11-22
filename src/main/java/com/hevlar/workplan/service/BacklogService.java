package com.hevlar.workplan.service;

import com.hevlar.workplan.domain.AppUser;
import com.hevlar.workplan.domain.Backlog;
import com.hevlar.workplan.domain.Feature;
import com.hevlar.workplan.domain.Project;
import com.hevlar.workplan.domain.ProjectModule;
import com.hevlar.workplan.domain.Task;
import com.hevlar.workplan.domain.WorkItemStatus;
import com.hevlar.workplan.repository.BacklogRepository;
import com.hevlar.workplan.repository.FeatureRepository;
import com.hevlar.workplan.repository.ProjectModuleRepository;
import com.hevlar.workplan.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BacklogService {

    private final BacklogRepository backlogRepository;
    private final ProjectModuleRepository moduleRepository;
    private final FeatureRepository featureRepository;
    private final TaskRepository taskRepository;

    public BacklogService(BacklogRepository backlogRepository,
                          ProjectModuleRepository moduleRepository,
                          FeatureRepository featureRepository,
                          TaskRepository taskRepository) {
        this.backlogRepository = backlogRepository;
        this.moduleRepository = moduleRepository;
        this.featureRepository = featureRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public Backlog ensureBacklog(Project project) {
        return backlogRepository.findByProject(project)
            .orElseGet(() -> {
                Backlog backlog = new Backlog();
                backlog.setProject(project);
                backlog.setName(project.getName() + " Backlog");
                project.setBacklog(backlog);
                return backlogRepository.save(backlog);
            });
    }

    public Backlog getBacklog(Project project) {
        return backlogRepository.findByProject(project)
            .orElseThrow(() -> new IllegalStateException("Backlog is not configured"));
    }

    public List<ProjectModule> modules(Project project) {
        return ensureBacklog(project).getModules();
    }

    @Transactional
    public ProjectModule addModule(Project project, String name, String description) {
        Backlog backlog = ensureBacklog(project);
        ProjectModule module = new ProjectModule();
        module.setBacklog(backlog);
        module.setName(name);
        module.setDescription(description);
        return moduleRepository.save(module);
    }

    @Transactional
    public Feature addFeature(Long moduleId, String name, String description) {
        ProjectModule module = moduleRepository.findById(moduleId)
            .orElseThrow(() -> new IllegalArgumentException("Module not found"));
        Feature feature = new Feature();
        feature.setModule(module);
        feature.setName(name);
        feature.setDescription(description);
        feature.setStatus(WorkItemStatus.PLANNED);
        return featureRepository.save(feature);
    }

    @Transactional
    public Task addTask(Long featureId,
                        String name,
                        String description,
                        Integer estimatedHours,
                        AppUser assignee) {
        Feature feature = featureRepository.findById(featureId)
            .orElseThrow(() -> new IllegalArgumentException("Feature not found"));
        Task task = new Task();
        task.setFeature(feature);
        task.setName(name);
        task.setDescription(description);
        task.setEstimatedHours(estimatedHours);
        task.setStatus(WorkItemStatus.PLANNED);
        task.setAssignee(assignee);
        return taskRepository.save(task);
    }
}
