package com.ghostchu.btn.sparkle.service.moderation;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.ContentModerationMapper;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentModerationService implements ApplicationRunner {
    private static final int MAX_LENGTH = 1_000_000;
    private static final long REFRESH_MILLIS = 15_000;
    public static final String CONTENT_REJECTED = "content_moderation_rejected";

    private final ContentModerationMapper mapper;
    private final Object updates = new Object();
    private final LongAdder swarmHits = new LongAdder();
    private final LongAdder banHits = new LongAdder();
    private volatile Snapshot current = new Snapshot(-1, null);

    @Override
    @MonitoredScheduledTask(id = "content-moderation-load", group = TaskGroup.MAINTENANCE,
            scheduleType = TaskScheduleType.STARTUP,
            nameKey = "admin.tasks.items.content-moderation-load.name",
            descriptionKey = "admin.tasks.items.content-moderation-load.description")
    public void run(ApplicationArguments args) throws BusinessException {
        synchronized (updates) {
            load();
        }
    }

    @Scheduled(fixedDelay = REFRESH_MILLIS, initialDelay = REFRESH_MILLIS)
    @MonitoredScheduledTask(id = "content-moderation-refresh", group = TaskGroup.MAINTENANCE,
            scheduleType = TaskScheduleType.FIXED_DELAY,
            intervalMillis = REFRESH_MILLIS, initialDelayMillis = REFRESH_MILLIS,
            nameKey = "admin.tasks.items.content-moderation-refresh.name",
            descriptionKey = "admin.tasks.items.content-moderation-refresh.description")
    public void refresh() throws BusinessException {
        synchronized (updates) {
            if (mapper.version() <= current.version()) {
                MonitoredTaskOutcome.skipped("Dictionary is current");
                return;
            }
            load();
        }
    }

    private void load() throws BusinessException {
        var dictionary = mapper.read();
        if (dictionary.version() > current.version()) {
            current = compile(dictionary.version(), dictionary.enabled(), dictionary.words());
        }
    }

    public Snapshot snapshot() {
        return current;
    }

    public void recordSwarmHit() {
        swarmHits.increment();
    }

    public void recordBanHit() {
        banHits.increment();
    }

    public Settings settings() {
        var dictionary = mapper.read();
        return new Settings(Long.toString(dictionary.version()), dictionary.enabled(), dictionary.words(),
                Long.toString(current.version()), swarmHits.sum(), banHits.sum());
    }

    public void save(String expectedVersion, Boolean enabled, String words, Long actor) throws BusinessException {
        long version;
        try {
            version = Long.parseLong(expectedVersion);
        } catch (NumberFormatException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid dictionary version");
        }
        if (enabled == null || words == null || words.length() > MAX_LENGTH || words.indexOf(0) >= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid dictionary text or settings");
        }
        synchronized (updates) {
            if (mapper.version() != version) {
                throw new BusinessException(HttpStatus.CONFLICT, "The dictionary was updated. Reload it before saving.");
            }
            Snapshot replacement = compile(version + 1, enabled, words);
            // The mapper runs outside a surrounding transaction and commits before returning.
            if (mapper.save(version, enabled, words) != 1) {
                throw new BusinessException(HttpStatus.CONFLICT, "The dictionary was updated. Reload it before saving.");
            }
            current = replacement;
            log.info("Content moderation dictionary updated by user {}, version {}, enabled {}",
                    actor, replacement.version(), enabled);
        }
    }

    private Snapshot compile(long version, boolean enabled, String words) throws BusinessException {
        if (!enabled) {
            return new Snapshot(version, null);
        }
        var matcher = SensitiveWordMatcher.compile(words.startsWith("\uFEFF") ? words.substring(1) : words);
        if (matcher.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Add at least one word before enabling moderation");
        }
        return new Snapshot(version, matcher);
    }

    public record Settings(String version, boolean enabled, String words, String activeVersion,
                           long swarmHits, long banHits) {}

    public record Snapshot(long version, SensitiveWordMatcher matcher) {
        public boolean enabled() {
            return matcher != null;
        }

        public boolean matches(String text) {
            return matcher != null && matcher.contains(text);
        }

        public boolean matchesJson(Object value) {
            if (value instanceof String text) {
                return matches(text);
            }
            if (value instanceof Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    if (matches((String) entry.getKey()) || matchesJson(entry.getValue())) {
                        return true;
                    }
                }
            }
            if (value instanceof List<?> list) {
                for (var item : list) {
                    if (matchesJson(item)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
