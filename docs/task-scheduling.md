# Sparkle3 后台任务系统使用与接入指南

本文说明“任务状态与计划”页面中的计划任务、触发任务和启动任务如何工作，以及新增后台任务时如何注册、展示执行状态和配置 Cron。

后台入口：`/admin/tasks`

主要实现代码：

- `src/main/java/com/ghostchu/btn/sparkle/service/tasks/`
- `src/main/java/com/ghostchu/btn/sparkle/service/AnalyseModuleScheduler.java`
- `src/main/java/com/ghostchu/btn/sparkle/service/AnalyseModuleConfigService.java`
- `src/main/java/com/ghostchu/btn/sparkle/service/AnalyseModuleExecutionService.java`
- `frontend/src/views/admin/AdminTaskStatusView.vue`

## 1. 三种任务类型

任务页按执行方式分为四类筛选：全部、计划任务、触发任务、启动任务。任务所属业务分组会作为额外的 tag 展示，例如“分析任务”“统计任务”“维护任务”“同步任务”。

| 类型 | 对应调度方式 | 是否有下次执行时间 | 典型用途 |
| --- | --- | --- | --- |
| 计划任务 | `CRON`、`FIXED_RATE`、`FIXED_DELAY`、`INTERVAL` | 有 | 定时清理、统计刷新、积分结算、Peer 分布快照 |
| 触发任务 | `EVENT` | 无 | 用户请求、数据提交或业务状态变化后异步执行 |
| 启动任务 | `STARTUP` | 无 | 应用启动完成后执行一次初始化 |

“计划任务”不只有 Cron。固定频率、固定延迟和普通间隔任务也属于计划任务，但只有受后台模块配置管理的 Cron 任务可以在任务页直接修改执行计划。

“触发任务”不会等待某个固定时间。它只表示该任务由业务事件驱动，真正的执行发生在代码调用对应事件入口时。

## 2. 在后台页面中使用

### 2.1 查看任务

进入“后台管理 → 任务状态与计划”后，可以：

1. 使用“全部”“计划任务”“触发任务”“启动任务”筛选任务。
2. 根据任务名称旁的业务分组 tag 判断任务用途。
3. 查看下次执行时间、当前运行时长、最近开始时间、最近耗时、触发方式和最近结果。
4. 展开最近运行记录，确认任务成功、失败、跳过或错误信息。

任务状态每 5 秒自动刷新。

支持手动执行的计划任务会显示“立即执行”按钮。点击后需要再次确认，任务会在后台启动，触发方式记录为“管理员触发”（`MANUAL`），不会改变原有 Cron 或下一次计划执行时间。

### 2.2 计划任务

计划任务会显示下一次预计执行时间。当前支持：

- Cron：按日历表达式执行。
- 固定频率：按照固定间隔安排下一次执行，不等上一次执行结束。
- 固定延迟：上一次执行结束后再等待指定间隔。
- 普通间隔：由任务自身维护下一次执行时间。

对于由“积分模块管理”或“封禁模块配置”统一管理的任务，任务卡片会额外显示：

- “启用模块”开关。
- “Cron 执行计划”输入框和保存按钮。

保存后无需重启应用，后台会重建相关调度。关闭模块后，对应计划会停止，已隐藏的积分面板或图表也不会继续展示。

固定频率、固定延迟、事件任务和启动任务通常不能从任务页直接改写时间，因为它们的调度方式不是可热更新的模块 Cron。

计划任务只要具备可调用的执行入口，都可以使用“立即执行”。管理员触发不会抢占或替换正常调度，但任务已经在运行时会拒绝重复启动。

对应管理接口为：

```http
POST /api/admin/tasks/{task-id}/run
```

### 2.3 触发任务

触发任务不会显示固定的“下次执行时间”。当业务代码接收到事件并调用任务执行入口后，页面才会更新本次运行状态。

常见操作方式是：

1. 在业务请求中只完成必要校验和轻量操作。
2. 将耗时处理提交到后台线程。
3. 在后台线程中调用 `TaskStatusService.begin(...)`。
4. 根据处理结果调用 `success()`、`skipped()` 或 `failure()`。

不要因为页面显示“触发任务”就认为任务会自动监听所有请求。触发条件必须由业务代码明确调用。

“立即执行”只面向计划任务。事件任务必须由真实业务事件驱动，避免管理员绕过业务前置条件直接执行。

### 2.4 启动任务

启动任务在应用完成启动后执行。它没有 Cron，也不在普通请求期间重复执行。适合注册缓存、预热配置或检查运行环境。

## 3. 注册自动执行的计划任务

### 3.1 使用 `@MonitoredScheduledTask`

普通 Spring 定时任务可以同时添加 `@Scheduled` 和 `@MonitoredScheduledTask`。切面会自动上报开始、成功和异常状态。

固定频率示例：

```java
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExampleMetricsTask {

    @Scheduled(fixedRate = 60_000, initialDelay = 10_000)
    @MonitoredScheduledTask(
            id = "example-metrics-sample",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.example-metrics-sample.name",
            descriptionKey = "admin.tasks.items.example-metrics-sample.description",
            scheduleType = TaskScheduleType.FIXED_RATE,
            intervalMillis = 60_000,
            initialDelayMillis = 10_000
    )
    public void sampleMetrics() {
        // 实际任务逻辑。
        // 抛出异常时，切面会自动将该次执行标记为失败。
    }
}
```

Cron 示例：

```java
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExampleCleanupTask {

    @Scheduled(cron = "${sparkle.example.cleanup-cron:0 30 3 * * *}")
    @MonitoredScheduledTask(
            id = "example-cleanup",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.example-cleanup.name",
            descriptionKey = "admin.tasks.items.example-cleanup.description",
            expression = "${sparkle.example.cleanup-cron:0 30 3 * * *}"
    )
    public void cleanup() {
        // 实际任务逻辑。
    }
}
```

注意事项：

- `id` 必须全局唯一且长期稳定。修改 `id` 会让页面把它识别为一个新任务。
- `@Scheduled` 的执行配置必须与 `@MonitoredScheduledTask` 中的 `scheduleType`、`expression`、`intervalMillis` 和 `initialDelayMillis` 保持一致。
- Cron 任务必须填写 `expression`，即使 `@Scheduled` 本身已经配置了 Cron；否则任务页无法正确计算下次运行时间。
- `@MonitoredScheduledTask.enabled` 只是状态展示元数据，不会真正关闭 Spring 的 `@Scheduled` 方法。
- 无参数的 `@MonitoredScheduledTask` 方法会在启动时自动注册手动执行入口，任务页会显示“立即执行”按钮。有参数的方法无法安全手动调用，因此不会注册该入口。
- 需要后台即时启停或修改 Cron 的任务，应使用下一节的模块化任务方式。
- 方法内部应记录必要的业务日志。抛出异常可以让任务状态显示为失败，但不要吞掉所有异常后仍无条件调用成功。

### 3.2 注册运行时可控的 Cron 模块

需要管理员在任务页即时启用、停用或修改 Cron 时，使用现有分析模块调度体系。

第一步，在 `AnalyseModuleConfigService` 的 `MODULES` 中注册模块定义：

```java
module("example-sync", "example-sync",
        bool("enabled", "enabled", true),
        cron("schedule", "schedule", "0 */10 * * * *")
),
```

第二步，在 `AnalyseModuleExecutionService` 中注册执行方法：

```java
runners.put("example-sync", exampleSyncService::runScheduled);
```

第三步，为模块名称和描述添加中英文国际化：

```ts
'example-sync': {
  name: '示例同步',
  description: '按计划同步示例数据。',
},
```

调度器会自动创建任务 `analysis-example-sync`，并在“任务状态与计划”页面提供：

- 模块启用开关。
- Cron 修改与保存。
- 最近运行结果。
- 下次运行时间。
- 手动触发后的运行记录。

模块化任务还具备以下行为：

- 同一模块上一次运行尚未结束时，会跳过下一次重叠执行。
- 模块关闭后，调度器会取消当前计划。
- Cron 保存后会自动重建调度，不需要重启应用。
- 动态积分等模块如果所有来源均已关闭，即使模块本身仍为启用状态，其有效调度也可能显示为停用。

模块执行方法应保持幂等，并自行控制单次处理的数据量，避免长时间占用数据库连接或阻塞调度线程。

模块化 Cron 任务同样出现在“计划任务”分类中，并支持从任务页立即执行。手动触发会复用模块执行器；模块已停用或执行器正在运行时，请求会被拒绝。

## 4. 注册事件触发任务

事件任务需要在应用初始化时注册任务定义，然后在业务事件发生时手动上报执行状态。

### 4.1 注册任务定义

```java
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExampleEventService {
    private static final String TASK_ID = "example-event";
    private final TaskStatusService taskStatusService;

    @PostConstruct
    void registerTask() {
        taskStatusService.ensure(TaskDefinition.event(
                TASK_ID,
                TaskGroup.MAINTENANCE,
                "admin.tasks.items.example-event.name",
                "admin.tasks.items.example-event.description"
        ));
    }
}
```

`ensure(...)` 只会在任务尚未注册时创建定义，适合在构造器或 `@PostConstruct` 中调用。

### 4.2 在事件入口中执行

```java
public void handleBusinessEvent(long userId) {
    Thread.startVirtualThread(() -> {
        TaskStatusService.TaskExecution execution =
                taskStatusService.begin(TASK_ID, "EVENT");
        try {
            process(userId);
            execution.success("Example event processed");
        } catch (Exception exception) {
            execution.failure(exception);
            log.warn("Unable to process example event", exception);
        }
    });
}
```

如果事件因业务条件正常无需处理，应标记为跳过，而不是失败：

```java
if (user == null) {
    execution.skipped("User does not exist");
    return;
}
```

要求：

- 每次实际事件处理只能调用一次 `success()`、`skipped()` 或 `failure()`。
- 如果可能长时间运行，应放入后台线程，不要阻塞 HTTP 请求。
- 如果多个业务操作属于同一个事件，应合并处理后只记录一次任务运行。
- 自定义触发方式应使用已有标签，或在 `admin.tasks.triggers` 下补充中英文翻译。
- 任务异常需要记录日志，同时调用 `failure(...)`，方便后台排查。

## 5. 注册启动任务

启动任务同样先注册定义，再监听应用启动完成事件。

```java
@PostConstruct
void registerStartupTask() {
    taskStatusService.ensure(TaskDefinition.startup(
            "example-startup",
            TaskGroup.STARTUP,
            "admin.tasks.items.example-startup.name",
            "admin.tasks.items.example-startup.description"
    ));
}

@EventListener(ApplicationReadyEvent.class)
void initialize() {
    TaskStatusService.TaskExecution execution =
            taskStatusService.begin("example-startup", "STARTUP");
    try {
        initializeExampleCache();
        execution.success("Example cache initialized");
    } catch (Exception exception) {
        execution.failure(exception);
        throw exception;
    }
}
```

启动任务应尽量快速、可重试且具备明确失败日志。不要在启动任务中执行没有时间上限的全表扫描。

## 6. Cron 格式

Sparkle3 使用 Spring `CronExpression`，后台 Cron 输入为 6 段：

```text
秒 分 时 日 月 星期
```

示例：

| Cron | 含义 |
| --- | --- |
| `0 */10 * * * *` | 每 10 分钟执行一次 |
| `0 5 * * * *` | 每小时的第 5 分钟执行 |
| `0 20 2 * * *` | 每天 02:20 执行 |
| `0 0 4 * * MON` | 每周一 04:00 执行 |
| `0 0 0 1 * *` | 每月 1 日 00:00 执行 |

Cron 使用 JVM 的系统时区计算。部署时应确认容器的 `TZ` 与预期时区一致。

新增或修改模块化任务后，后台会校验 Cron。无效表达式会导致计划创建失败，并在任务卡片中显示错误信息；修正后保存即可重新调度。

## 7. 国际化

任务名称和描述使用 i18n key。新增任务时至少补充以下两个文件：

- `frontend/src/i18n/locales/zh-CN.ts`
- `frontend/src/i18n/locales/en-US.ts`

示例：

```ts
admin: {
  tasks: {
    items: {
      'example-event': {
        name: '示例事件处理',
        description: '业务事件发生后异步处理示例数据。',
      },
    },
  },
}
```

如果缺少翻译，页面会直接显示 key，影响可读性和故障判断。

## 8. 执行结果与触发方式

任务执行结果包括：

- `SUCCESS`：处理成功。
- `FAILED`：发生异常或明确失败。
- `SKIPPED`：由于条件不满足而正常跳过。

常用触发方式包括：

- `SCHEDULED`：由调度器触发。
- `MANUAL`：管理员手动触发。
- `EVENT`：业务事件触发。
- `STARTUP`：应用启动触发。
- `AFTER_DELETE`：删除后的后续刷新。

最近运行记录保存在内存中，每个任务最多保留 8 条，应用重启后会清空。该页面用于观察运行状态，不是持久化审计日志。

## 9. 接入检查清单

新增任务后依次检查：

1. 任务 ID 是否唯一且稳定。
2. `TaskGroup` 是否正确。
3. `scheduleType` 是否与真实执行方式一致。
4. 自动执行配置是否与 `@Scheduled` 一致。
5. 事件任务是否已通过 `ensure(...)` 注册。
6. 每次事件处理是否只结束一次，并正确选择成功、跳过或失败。
7. 中英文任务名称和描述是否已补充。
8. 任务是否避免阻塞 HTTP 请求和调度线程。
9. 大批量数据处理是否分批、可中断并控制数据库压力。
10. 在 `/admin/tasks` 中确认任务能出现在正确分类，并完成一次实际运行。

建议验证命令：

```bash
pnpm --dir frontend typecheck
./gradlew test
```

Windows 环境可使用：

```powershell
.\gradlew.bat test --no-daemon
```
