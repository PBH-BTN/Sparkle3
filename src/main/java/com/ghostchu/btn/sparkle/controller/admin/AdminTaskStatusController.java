package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.TaskStatusModels.TaskStatusResponse;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskStatusController {
    private final TaskStatusService taskStatusService;

    @GetMapping
    public TaskStatusResponse status() {
        return taskStatusService.snapshot();
    }
}
