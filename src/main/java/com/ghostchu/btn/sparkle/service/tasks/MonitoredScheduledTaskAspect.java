package com.ghostchu.btn.sparkle.service.tasks;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class MonitoredScheduledTaskAspect {
    private final TaskStatusService taskStatusService;

    @Around("@annotation(monitoredTask)")
    public Object monitor(
            ProceedingJoinPoint joinPoint,
            MonitoredScheduledTask monitoredTask
    ) throws Throwable {
        taskStatusService.ensure(monitoredTask);
        TaskStatusService.TaskExecution execution = taskStatusService.begin(
                monitoredTask.id(),
                taskStatusService.consumeScheduledTrigger(monitoredTask.id())
        );
        try {
            Object result = joinPoint.proceed();
            execution.success();
            return result;
        } catch (Throwable throwable) {
            execution.failure(throwable);
            throw throwable;
        }
    }
}
