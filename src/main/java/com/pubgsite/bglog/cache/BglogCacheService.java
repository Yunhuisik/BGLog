package com.pubgsite.bglog.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pubgsite.bglog.service.PerformanceMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class BglogCacheService {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;
    private final PerformanceMetricsService perf;

    @Value("${bglog.redis.enabled:false}")
    private boolean redisEnabled;

    private final Map<String, LocalEntry> localCache = new ConcurrentHashMap<>();

    public <T> T getOrCompute(String namespace, String key, Duration ttl, Class<T> type, Supplier<T> supplier) {
        return getOrCompute(namespace, key, ttl, type, true, supplier);
    }

    public <T> T getOrCompute(
            String namespace,
            String key,
            Duration ttl,
            Class<T> type,
            boolean useCache,
            Supplier<T> supplier
    ) {
        String fullKey = buildKey(namespace, key);

        if (!useCache) {
            perf.increment("cache.bypass." + namespace);
            long computeStart = perf.start();
            T computed = supplier.get();
            perf.recordTime("cache.compute." + namespace, computeStart);
            return computed;
        }

        long readStart = perf.start();
        T cached = read(fullKey, type);
        perf.recordTime("cache.read." + namespace, readStart);

        if (cached != null) {
            perf.increment("cache.hit." + namespace);
            return cached;
        }

        perf.increment("cache.miss." + namespace);

        long computeStart = perf.start();
        T computed = supplier.get();
        perf.recordTime("cache.compute." + namespace, computeStart);

        if (computed != null) {
            long writeStart = perf.start();
            write(fullKey, computed, ttl);
            perf.recordTime("cache.write." + namespace, writeStart);
        }

        return computed;
    }

    public <T> T getOrCompute(String namespace, String key, Duration ttl, TypeReference<T> type, Supplier<T> supplier) {
        return getOrCompute(namespace, key, ttl, type, true, supplier);
    }

    public <T> T getOrCompute(
            String namespace,
            String key,
            Duration ttl,
            TypeReference<T> type,
            boolean useCache,
            Supplier<T> supplier
    ) {
        String fullKey = buildKey(namespace, key);

        if (!useCache) {
            perf.increment("cache.bypass." + namespace);
            long computeStart = perf.start();
            T computed = supplier.get();
            perf.recordTime("cache.compute." + namespace, computeStart);
            return computed;
        }

        long readStart = perf.start();
        T cached = read(fullKey, type);
        perf.recordTime("cache.read." + namespace, readStart);

        if (cached != null) {
            perf.increment("cache.hit." + namespace);
            return cached;
        }

        perf.increment("cache.miss." + namespace);

        long computeStart = perf.start();
        T computed = supplier.get();
        perf.recordTime("cache.compute." + namespace, computeStart);

        if (computed != null) {
            long writeStart = perf.start();
            write(fullKey, computed, ttl);
            perf.recordTime("cache.write." + namespace, writeStart);
        }

        return computed;
    }

    public void clearAll() {
        localCache.clear();

        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            } catch (Exception ignored) {
                perf.increment("cache.clear.redis.error");
            }
        }

        perf.increment("cache.clear.all");
    }

    private String buildKey(String namespace, String key) {
        return "bglog:" + namespace + ":" + key;
    }

    private <T> T read(String key, Class<T> type) {
        String raw = readRaw(key);
        if (raw == null) {
            return null;
        }

        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            evict(key);
            return null;
        }
    }

    private <T> T read(String key, TypeReference<T> type) {
        String raw = readRaw(key);
        if (raw == null) {
            return null;
        }

        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            evict(key);
            return null;
        }
    }

    private String readRaw(String key) {
        String namespace = extractNamespace(key);

        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                String value = redis.opsForValue().get(key);
                if (value != null) {
                    perf.increment("cache.source.redis.hit." + namespace);
                    return value;
                }
                perf.increment("cache.source.redis.miss." + namespace);
            } catch (Exception ignored) {
                perf.increment("cache.source.redis.error." + namespace);
            }
        }

        LocalEntry entry = localCache.get(key);
        if (entry == null) {
            perf.increment("cache.source.local.miss." + namespace);
            return null;
        }

        if (entry.isExpired()) {
            localCache.remove(key);
            perf.increment("cache.source.local.expired." + namespace);
            return null;
        }

        perf.increment("cache.source.local.hit." + namespace);
        return entry.payload();
    }

    private void write(String key, Object value, Duration ttl) {
        String namespace = extractNamespace(key);

        try {
            String payload = objectMapper.writeValueAsString(value);

            StringRedisTemplate redis = redis();
            if (redis != null) {
                try {
                    redis.opsForValue().set(key, payload, ttl);
                    perf.increment("cache.write.redis." + namespace);
                    return;
                } catch (Exception ignored) {
                    perf.increment("cache.write.redis.error." + namespace);
                }
            }

            localCache.put(key, new LocalEntry(payload, System.currentTimeMillis() + ttl.toMillis()));
            perf.increment("cache.write.local." + namespace);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("캐시 직렬화 실패", e);
        }
    }

    private void evict(String key) {
        localCache.remove(key);

        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.delete(key);
            } catch (Exception ignored) {
            }
        }
    }

    private StringRedisTemplate redis() {
        if (!redisEnabled) {
            return null;
        }
        return stringRedisTemplateProvider.getIfAvailable();
    }

    private String extractNamespace(String fullKey) {
        String prefix = "bglog:";
        if (!fullKey.startsWith(prefix)) {
            return "unknown";
        }

        String remain = fullKey.substring(prefix.length());
        int idx = remain.indexOf(':');
        if (idx < 0) {
            return "unknown";
        }
        return remain.substring(0, idx);
    }

    private record LocalEntry(String payload, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}