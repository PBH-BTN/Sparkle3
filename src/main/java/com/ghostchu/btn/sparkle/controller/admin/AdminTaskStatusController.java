package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.TaskStatusModels.TaskStatusView;
import com.ghostchu.btn.sparkle.controller.admin.dto.TaskStatusModels.TaskStatusResponse;
import com.ghostchu.btn.sparkle.controller.admin.dto.TaskStatusModels.TaskTriggerResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.AnalyseModuleScheduler;
import com.ghostchu.btn.sparkle.service.tasks.TaskManualExecutionService;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskStatusController {
    private final TaskStatusService taskStatusService;
    private final TaskManualExecutionService manualExecutionService;
    private final AnalyseModuleScheduler analyseModuleScheduler;

    @GetMapping
    public TaskStatusResponse status() {
        return taskStatusService.snapshot();
    }

    @PostMapping("/{id}/run")
    public TaskTriggerResponse run(@PathVariable String id) throws BusinessException {
        TaskStatusView task = taskStatusService.task(id);
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Task not found");
        }
        if ("EVENT".equals(task.scheduleType()) || "STARTUP".equals(task.scheduleType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only scheduled tasks can run manually");
        }
        if (!task.enabled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Task is disabled");
        }
        if (!task.manualExecutable()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Task cannot be run manually");
        }
        if (task.runningCount() > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Task is already running");
        }
        if (task.moduleId() != null) {
            analyseModuleScheduler.triggerNow(task.moduleId());
            return new TaskTriggerResponse(id, true);
        }
        return manualExecutionService.trigger(id);
    }
}
