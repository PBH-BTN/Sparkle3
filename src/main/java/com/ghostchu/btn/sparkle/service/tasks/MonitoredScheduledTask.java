package com.ghostchu.btn.sparkle.service.tasks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MonitoredScheduledTask {
    String id();

    TaskGroup group();

    String nameKey();

    String descriptionKey() default "";

    TaskScheduleType scheduleType() default TaskScheduleType.CRON;

    String expression() default "";

    long intervalMillis() default 0;

    long initialDelayMillis() default 0;

    boolean enabled() default true;
}
