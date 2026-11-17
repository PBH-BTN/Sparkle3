package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.SupporterRoleMapper;
import com.ghostchu.btn.sparkle.service.membership.SupporterEligibilityChanged;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupporterRoleService {
    private static final String THRESHOLD_KEY = "sparkle.memberships.supporter-threshold-cny";
    private static final BigDecimal MAX_THRESHOLD = new BigDecimal("1000000000");
    private static final String EVENT_TASK = "supporter-role-event";
    private static final String SCAN_TASK = "supporter-role-reconcile";
    private static final long SCAN_INTERVAL = 15 * 60_000L;
    private static final long INITIAL_DELAY = 30_000L;
    private static final int BATCH_SIZE = 200;

    private final DatabaseConfigService configService;
    private final SupporterRoleMapper mapper;
    private final ExchangeRateService exchangeRateService;
    private final TaskStatusService tasks;

    // The reconciliation task also catches up on events dropped while the queue is full.
    private final ThreadPoolExecutor events = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(512),
            Thread.ofVirtual().name("supporter-role-events").factory(), new ThreadPoolExecutor.DiscardPolicy());

    @PostConstruct
    void registerTask() {
        tasks.ensure(TaskDefinition.event(EVENT_TASK, TaskGroup.MAINTENANCE,
                "admin.tasks.items." + EVENT_TASK + ".name",
                "admin.tasks.items." + EVENT_TASK + ".description"));
    }

    @PreDestroy
    void shutdownEvents() {
        events.shutdownNow();
    }

    public BigDecimal threshold() {
        String stored = configService.getRawValues(List.of(THRESHOLD_KEY)).getOrDefault(THRESHOLD_KEY, "0");
        String override = configService.getExternalOverride(THRESHOLD_KEY, stored);
        BigDecimal value = parseThreshold(override == null ? stored : override);
        return value == null ? BigDecimal.ZERO : value;
    }

    public void updateThreshold(String raw, Long operatorId) throws BusinessException {
        BigDecimal value = parseThreshold(raw);
        if (value == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Enter an amount from 0 to 1,000,000,000 CNY with at most two decimal places");
        }
        configService.update(THRESHOLD_KEY, value.toPlainString(), operatorId);
    }

    private static BigDecimal parseThreshold(String raw) {
        if (raw == null || !raw.trim().matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) {
            return null;
        }
        BigDecimal value = new BigDecimal(raw.trim());
        return value.compareTo(MAX_THRESHOLD) <= 0 ? value : null;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void eligibilityChanged(SupporterEligibilityChanged event) {
        events.execute(() -> checkEvent(event));
    }

    private void checkEvent(SupporterEligibilityChanged event) {
        var execution = tasks.begin(EVENT_TASK, "EVENT");
        try {
            BigDecimal required = threshold();
            if (required.signum() == 0) {
                execution.skipped("Automatic supporter grants are disabled");
                return;
            }
            Long userId = event.userId() != null ? event.userId()
                    : mapper.findBoundUser(event.provider(), event.externalUserId());
            if (userId == null || mapper.grantEligible(List.of(userId), required) == 0) {
                execution.skipped("No supporter role to grant");
                return;
            }
            execution.success("Supporter role granted");
        } catch (Exception exception) {
            execution.failure(exception);
            log.warn("Supporter role event failed", exception);
        }
    }

    @Scheduled(fixedDelay = SCAN_INTERVAL, initialDelay = INITIAL_DELAY)
    @MonitoredScheduledTask(id = SCAN_TASK, group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items." + SCAN_TASK + ".name",
            descriptionKey = "admin.tasks.items." + SCAN_TASK + ".description",
            scheduleType = TaskScheduleType.FIXED_DELAY, intervalMillis = SCAN_INTERVAL, initialDelayMillis = INITIAL_DELAY)
    public void reconcile() throws BusinessException {
        BigDecimal required = threshold();
        if (required.signum() == 0) {
            MonitoredTaskOutcome.skipped("Automatic supporter grants are disabled");
            return;
        }
        BusinessException rateFailure = null;
        if (mapper.needsCnyRates()) {
            try {
                exchangeRateService.ensureCnyRates();
            } catch (BusinessException exception) {
                rateFailure = exception;
            }
        }
        long after = 0;
        int granted = 0;
        List<Long> batch;
        do {
            batch = mapper.findCandidates(after, BATCH_SIZE);
            if (batch.isEmpty()) break;
            // Each conditional update commits immediately: do not hold several user rows
            // while points settlement or account maintenance updates the same users.
            for (Long userId : batch) {
                granted += mapper.grantEligible(List.of(userId), required);
            }
            after = batch.getLast();
        } while (batch.size() == BATCH_SIZE && !Thread.currentThread().isInterrupted());

        if (rateFailure != null) throw rateFailure;
        if (granted == 0) MonitoredTaskOutcome.skipped("No supporter roles to grant");
    }
}
