package com.pubgsite.bglog.dto;

public record OverallSummary(
        int games,
        double avgKills,
        double avgDamage,
        double avgSurvival,
        double winRate
) {}