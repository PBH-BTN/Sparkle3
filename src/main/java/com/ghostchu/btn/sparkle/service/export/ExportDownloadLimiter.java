package com.ghostchu.btn.sparkle.service.export;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ExportDownloadLimiter {
    static final int MAX_CONNECTIONS = 32;
    static final int CHUNK_BYTES = 256 * 1024;
    private static final int LEASE_MILLIS = 120_000;

    private static final DefaultRedisScript<Long> ACQUIRE = script("""
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then return 0 end
            redis.call('ZADD', KEYS[1], now + tonumber(ARGV[3]), ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """);

    // All ranges and files for this account debit the same byte bucket.
    private static final DefaultRedisScript<Long> PACE = script("""
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            local lease = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not lease or tonumber(lease) <= now then return -1 end
            redis.call('ZADD', KEYS[1], now + tonumber(ARGV[5]), ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            local bucket = redis.call('HMGET', KEYS[2], 'time', 'bytes')
            local rate = tonumber(ARGV[3])
            local elapsed = math.max(0, now - (tonumber(bucket[1]) or now))
            local available = math.min(tonumber(ARGV[4]), (tonumber(bucket[2]) or 0) + elapsed * rate)
            local cost = tonumber(ARGV[2])
            local wait = 0
            if available >= cost then
                available = available - cost
            else
                wait = math.ceil((cost - available) / rate)
            end
            redis.call('HSET', KEYS[2], 'time', now, 'bytes', available)
            redis.call('PEXPIRE', KEYS[2], ARGV[5])
            return wait
            """);

    private final RedisTemplate<String, String> redis;

    public ExportDownloadLimiter(@Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    String acquire(long uid) {
        String lease = UUID.randomUUID().toString();
        Long granted = redis.execute(ACQUIRE, List.of(key(uid, "leases")), lease,
                Integer.toString(MAX_CONNECTIONS), Integer.toString(LEASE_MILLIS));
        if (granted == null) throw new IllegalStateException("Download limiter unavailable");
        return granted == 1 ? lease : null;
    }

    long waitMillis(long uid, String lease, int bytes, int mbps) {
        // One Mbps is 125 bytes per millisecond.
        Long wait = redis.execute(PACE, List.of(key(uid, "leases"), key(uid, "rate")), lease,
                Integer.toString(bytes), Long.toString(125L * mbps), Integer.toString(CHUNK_BYTES),
                Integer.toString(LEASE_MILLIS));
        if (wait == null) throw new IllegalStateException("Download limiter unavailable");
        return wait;
    }

    void release(long uid, String lease) {
        redis.opsForZSet().remove(key(uid, "leases"), lease);
    }

    private static String key(long uid, String suffix) {
        return "sparkle:exports:{" + uid + "}:" + suffix;
    }

    private static DefaultRedisScript<Long> script(String lua) {
        return new DefaultRedisScript<>(lua, Long.class);
    }
}
