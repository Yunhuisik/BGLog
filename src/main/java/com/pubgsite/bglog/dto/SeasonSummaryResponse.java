package com.pubgsite.bglog.dto;

import java.util.Map;

public record SeasonSummaryResponse(
                String seasonId,
                OverallSummary overall,
                Map<String, PlayerStatsSummary> byMode) {
        public record OverallSummary(
                        int games,
                        double avgPlace, // ✅ 추가
                        double avgKills,
                        double avgDamage,
                        double avgSurvival,
                        double winRate) {
        }
}