package com.ghostchu.btn.sparkle.service.tasks;

import com.ghostchu.btn.sparkle.controller.admin.dto.TaskStatusModels.TaskTriggerResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskManualExecutionService implements SmartInitializingSingleton {
    private final ApplicationContext applicationContext;
    private final TaskStatusService taskStatusService;
    private final ConcurrentMap<String, Runnable> runners = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicBoolean> running = new ConcurrentHashMap<>();

    @Override
    public void afterSingletonsInstantiated() {
        registerAnnotatedTasks();
    }

    public TaskTriggerResponse trigger(String taskId) throws BusinessException {
        Runnable runner = runners.get(taskId);
        if (runner == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Task cannot be run manually");
        }
        AtomicBoolean taskRunning = running.computeIfAbsent(
                taskId,
                ignored -> new AtomicBoolean()
        );
        if (!taskRunning.compareAndSet(false, true)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Task is already running");
        }

        Thread.startVirtualThread(() -> {
            try {
                taskStatusService.markManualExecution(taskId);
                runner.run();
            } catch (Throwable throwable) {
                log.warn("Manually triggered task {} failed", taskId, throwable);
            } finally {
                taskStatusService.clearManualExecution(taskId);
                taskRunning.set(false);
            }
        });
        return new TaskTriggerResponse(taskId, true);
    }

    public void registerRunner(String taskId, Runnable runner) {
        if (taskId == null || taskId.isBlank() || runner == null) {
            throw new IllegalArgumentException("Task id and runner are required");
        }
        if (runners.putIfAbsent(taskId, runner) == null) {
            taskStatusService.setManualExecutable(taskId, true);
        }
    }

    private void registerAnnotatedTasks() {
        for (String beanName : applicationContext.getBeanNamesForAnnotation(Component.class)) {
            Object bean = applicationContext.getBean(beanName);
            registerBean(bean);
        }
    }

    private void registerBean(Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        Map<Method, MonitoredScheduledTask> methods = MethodIntrospector.selectMethods(
                targetClass,
                (MethodIntrospector.MetadataLookup<MonitoredScheduledTask>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(
                                method,
                                MonitoredScheduledTask.class
                        )
        );
        for (Map.Entry<Method, MonitoredScheduledTask> entry : methods.entrySet()) {
            registerMethod(bean, targetClass, entry.getKey(), entry.getValue());
        }
    }

    private void registerMethod(
            Object bean,
            Class<?> targetClass,
            Method targetMethod,
            MonitoredScheduledTask annotation
    ) {
        taskStatusService.configure(annotation);
        if (!isScheduled(annotation.scheduleType())) {
            return;
        }
        if (targetMethod.getParameterCount() != 0) {
            log.warn(
                    "Scheduled task {} cannot be run manually because {}#{} has parameters",
                    annotation.id(),
                    targetClass.getName(),
                    targetMethod.getName()
            );
            return;
        }

        Method invocableMethod = AopUtils.selectInvocableMethod(targetMethod, bean.getClass());
        ReflectionUtils.makeAccessible(invocableMethod);
        Runnable runner = new ReflectiveTaskRunner(annotation.id(), bean, invocableMethod);
        if (runners.containsKey(annotation.id())) {
            throw new IllegalStateException(
                    "Duplicate manually executable task id: " + annotation.id()
            );
        }
        registerRunner(annotation.id(), runner);
    }

    private boolean isScheduled(TaskScheduleType scheduleType) {
        return scheduleType == TaskScheduleType.CRON
                || scheduleType == TaskScheduleType.FIXED_RATE
                || scheduleType == TaskScheduleType.FIXED_DELAY
                || scheduleType == TaskScheduleType.INTERVAL;
    }

    @RequiredArgsConstructor
    private static final class ReflectiveTaskRunner implements Runnable {
        private final String taskId;
        private final Object bean;
        private final Method method;

        @Override
        public void run() {
            try {
                method.invoke(bean);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(
                        "Task " + taskId + " failed: "
                                + (cause == null ? "unknown error" : cause.getMessage()),
                        cause
                );
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to invoke task " + taskId, exception);
            }
        }
    }
}
