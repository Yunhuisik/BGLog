package com.pubgsite.bglog.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PerformanceMetricsService {

    private final ConcurrentHashMap<String, TimerMetric> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public long start() {
        return System.nanoTime();
    }

    public void recordTime(String name, long startNanos) {
        recordDurationNanos(name, System.nanoTime() - startNanos);
    }

    public void recordDurationNanos(String name, long nanos) {
        if (nanos < 0) {
            return;
        }
        timers.computeIfAbsent(name, k -> new TimerMetric()).record(nanos);
    }

    public void increment(String name) {
        add(name, 1);
    }

    public void add(String name, long delta) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", Instant.now().toString());

        Map<String, Long> counterSnapshot = new LinkedHashMap<>();
        counters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> counterSnapshot.put(e.getKey(), e.getValue().get()));

        Map<String, Map<String, Object>> timerSnapshot = new LinkedHashMap<>();
        timers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> timerSnapshot.put(e.getKey(), e.getValue().snapshot()));

        out.put("counters", counterSnapshot);
        out.put("timers", timerSnapshot);
        return out;
    }

    public void reset() {
        timers.clear();
        counters.clear();
    }

    private static class TimerMetric {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong maxNanos = new AtomicLong();

        void record(long nanos) {
            count.incrementAndGet();
            totalNanos.addAndGet(nanos);
            maxNanos.accumulateAndGet(nanos, Math::max);
        }

        Map<String, Object> snapshot() {
            long c = count.get();
            long total = totalNanos.get();
            long max = maxNanos.get();

            double totalMs = total / 1_000_000.0;
            double avgMs = c == 0 ? 0.0 : (total / 1_000_000.0) / c;
            double maxMs = max / 1_000_000.0;

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("count", c);
            out.put("totalMs", round(totalMs));
            out.put("avgMs", round(avgMs));
            out.put("maxMs", round(maxMs));
            return out;
        }

        private double round(double value) {
            return Math.round(value * 1000.0) / 1000.0;
        }
    }
}