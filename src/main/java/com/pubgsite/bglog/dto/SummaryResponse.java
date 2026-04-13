package com.pubgsite.bglog.dto;

import java.util.List;
import java.util.Map;

public record SummaryResponse(
        String name,
        Period period,
        int matchCount,

        OverallSummary overall,
        List<Integer> recentPlacements,
        Map<String, PlayerStatsSummary> byMode,
        Map<String, PlayerStatsSummary> byMap,

        List<MatchSummary> recentMatches,   // ✅ 추가

        // 기존 필드들 ...,
        SeasonSummaryResponse season
) {}