package com.pubgsite.bglog.util;

import java.util.concurrent.ConcurrentHashMap;

public class TtlCache<K, V> {
    private static class Entry<V> {
        final V value;
        final long expiresAt;
        Entry(V value, long expiresAt) { this.value = value; this.expiresAt = expiresAt; }
    }

    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();
    private final long ttlMs;

    public TtlCache(long ttlMs) { this.ttlMs = ttlMs; }

    public V get(K key) {
        Entry<V> e = map.get(key);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expiresAt) {
            map.remove(key);
            return null;
        }
        return e.value;
    }

    public void put(K key, V value) {
        map.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMs));
    }

    public V getOrCompute(K key, java.util.function.Supplier<V> supplier) {
        V v = get(key);
        if (v != null) return v;
        V computed = supplier.get();
        if (computed != null) put(key, computed);
        return computed;
    }
}