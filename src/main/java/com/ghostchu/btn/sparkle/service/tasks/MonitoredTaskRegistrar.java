package com.ghostchu.btn.sparkle.service.tasks;

import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

@Component
@RequiredArgsConstructor
public class MonitoredTaskRegistrar {
    private final ApplicationContext applicationContext;
    private final TaskStatusService taskStatusService;

    @EventListener(ApplicationReadyEvent.class)
    public void registerScheduledTasks() {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType;
            try {
                beanType = applicationContext.getType(beanName);
            } catch (Exception ignored) {
                continue;
            }
            if (beanType == null) {
                continue;
            }
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception ignored) {
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            ReflectionUtils.doWithMethods(targetClass, method -> {
                MonitoredScheduledTask annotation = findAnnotation(method);
                if (annotation != null) {
                    taskStatusService.ensure(annotation);
                }
            });
        }
    }

    private MonitoredScheduledTask findAnnotation(Method method) {
        MonitoredScheduledTask annotation = AnnotationUtils.findAnnotation(
                method,
                MonitoredScheduledTask.class
        );
        if (annotation != null || method.getDeclaringClass() == null) {
            return annotation;
        }
        try {
            Method implementation = method.getDeclaringClass()
                    .getDeclaredMethod(method.getName(), method.getParameterTypes());
            return AnnotationUtils.findAnnotation(
                    implementation,
                    MonitoredScheduledTask.class
            );
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }
}
