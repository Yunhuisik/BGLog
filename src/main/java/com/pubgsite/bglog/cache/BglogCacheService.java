package com.pubgsite.bglog.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Value("${bglog.redis.enabled:false}")
    private boolean redisEnabled;

    private final Map<String, LocalEntry> localCache = new ConcurrentHashMap<>();

    public <T> T getOrCompute(String namespace, String key, Duration ttl, Class<T> type, Supplier<T> supplier) {
        String fullKey = buildKey(namespace, key);

        T cached = read(fullKey, type);
        if (cached != null) {
            return cached;
        }

        T computed = supplier.get();
        if (computed != null) {
            write(fullKey, computed, ttl);
        }
        return computed;
    }

    public <T> T getOrCompute(String namespace, String key, Duration ttl, TypeReference<T> type, Supplier<T> supplier) {
        String fullKey = buildKey(namespace, key);

        T cached = read(fullKey, type);
        if (cached != null) {
            return cached;
        }

        T computed = supplier.get();
        if (computed != null) {
            write(fullKey, computed, ttl);
        }
        return computed;
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
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                return redis.opsForValue().get(key);
            } catch (Exception ignored) {
            }
        }

        LocalEntry entry = localCache.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            localCache.remove(key);
            return null;
        }

        return entry.payload();
    }

    private void write(String key, Object value, Duration ttl) {
        try {
            String payload = objectMapper.writeValueAsString(value);

            StringRedisTemplate redis = redis();
            if (redis != null) {
                try {
                    redis.opsForValue().set(key, payload, ttl);
                    return;
                } catch (Exception ignored) {
                }
            }

            localCache.put(key, new LocalEntry(payload, System.currentTimeMillis() + ttl.toMillis()));
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

    private record LocalEntry(String payload, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}