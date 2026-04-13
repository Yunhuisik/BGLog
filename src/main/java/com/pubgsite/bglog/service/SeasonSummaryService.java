package com.pubgsite.bglog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pubgsite.bglog.cache.BglogCacheService;
import com.pubgsite.bglog.dto.PlayerStatsSummary;
import com.pubgsite.bglog.dto.PubgPlatform;
import com.pubgsite.bglog.dto.SeasonSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SeasonSummaryService {

    private final PubgApiService api;
    private final BglogCacheService cacheService;

    private static final Map<String, String> MODE_KR = Map.of(
            "solo", "솔로",
            "duo", "듀오",
            "squad", "스쿼드"
    );

    public SeasonSummaryResponse build(String platform, String name, String seasonId) {
        PubgPlatform p = PubgPlatform.from(platform);
        String shard = p.shard();

        String sid = (seasonId == null || seasonId.isBlank()) ? "CURRENT" : seasonId;
        String key = shard + ":SEASON_SUMMARY:" + name.toLowerCase() + ":" + sid;

        return cacheService.getOrCompute("seasonSummary", key, Duration.ofMinutes(1), SeasonSummaryResponse.class,
                () -> buildInternal(p, name, seasonId));
    }

    private SeasonSummaryResponse buildInternal(PubgPlatform platform, String nickname, String seasonId) {
        String shard = platform.shard();

        String accountId = api.getPlayerId(shard, nickname);
        String sid = (seasonId == null || seasonId.isBlank())
                ? api.getCurrentSeasonId(shard)
                : seasonId;

        JsonNode root = api.getSeasonStats(shard, accountId, sid);
        JsonNode gameModeStats = root.path("data").path("attributes").path("gameModeStats");

        Map<String, PlayerStatsSummary> byMode = new LinkedHashMap<>();

        for (String modeKey : MODE_KR.keySet()) {
            JsonNode m = gameModeStats.path(modeKey);
            if (m.isMissingNode() || m.isNull()) continue;

            int rounds = m.path("roundsPlayed").asInt(0);
            if (rounds <= 0) continue;

            int wins = m.path("wins").asInt(0);
            int kills = m.path("kills").asInt(0);
            double dmg = m.path("damageDealt").asDouble(0);
            int survived = m.path("timeSurvived").asInt(0);
            int top10 = m.path("top10s").asInt(0);

            int deaths = Math.max(rounds - wins, 1);

            double kd = (double) kills / deaths;
            double winRate = 100.0 * wins / rounds;
            double top10Rate = top10 > 0 ? (100.0 * top10 / rounds) : 0.0;

            double avgDamage = dmg / rounds;
            double avgSurvival = (double) survived / rounds;

            PlayerStatsSummary s = new PlayerStatsSummary(
                    rounds,
                    kd,
                    winRate,
                    top10Rate,
                    avgDamage,
                    avgSurvival,
                    0,
                    0,
                    0,
                    0
            );

            byMode.put(MODE_KR.get(modeKey), s);
        }

        int totalGames = 0;
        int totalWins = 0;
        int totalKills = 0;
        double totalDamage = 0;
        int totalSurvival = 0;

        for (PlayerStatsSummary s : byMode.values()) {
            totalGames += s.getGames();
            totalWins += (int) Math.round(s.getWinRate() * s.getGames() / 100.0);
            totalKills += (int) Math.round(
                    s.getKd() * Math.max(s.getGames() - (int) Math.round(s.getWinRate() * s.getGames() / 100.0), 1)
            );
            totalDamage += s.getAvgDamage() * s.getGames();
            totalSurvival += (int) Math.round(s.getAvgSurvival() * s.getGames());
        }

        SeasonSummaryResponse.OverallSummary overall = new SeasonSummaryResponse.OverallSummary(
                totalGames,
                0.0,
                totalGames == 0 ? 0 : (double) totalKills / totalGames,
                totalGames == 0 ? 0 : totalDamage / totalGames,
                totalGames == 0 ? 0 : (double) totalSurvival / totalGames,
                totalGames == 0 ? 0 : (100.0 * totalWins / totalGames)
        );

        return new SeasonSummaryResponse(sid, overall, byMode);
    }
}