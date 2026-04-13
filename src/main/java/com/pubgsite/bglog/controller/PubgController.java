package com.pubgsite.bglog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.pubgsite.bglog.dto.*;
import com.pubgsite.bglog.service.MatchService;
import com.pubgsite.bglog.service.PubgApiService;
import com.pubgsite.bglog.service.SeasonSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pubg")
public class PubgController {

        private final MatchService matchService;
        private final SeasonSummaryService seasonSummaryService;
        private final PubgApiService pubgApiService;

        @GetMapping("/health")
        public String health() {
                return "ok";
        }

        /**
         * 실제 검색 진입점
         * - 프론트 첫 검색은 가능하면 이 엔드포인트 사용
         * - 검색 성공 시 recent search 기록
         */
        @GetMapping("/search")
        public SummaryResponse search(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam("name") String name,
                        @RequestParam(value = "period", defaultValue = "WEEK") Period period,
                        @RequestParam(value = "limit", defaultValue = "20") int limit,
                        @RequestParam(value = "seasonId", required = false) String seasonId) {
                SummaryResponse base = matchService.buildSummaryCached(platform, name, period, limit);
                SeasonSummaryResponse season = seasonSummaryService.build(platform, name, seasonId);

                return new SummaryResponse(
                                base.name(),
                                base.period(),
                                base.matchCount(),
                                base.overall(),
                                base.recentPlacements(),
                                base.byMode(),
                                base.byMap(),
                                base.recentMatches(),
                                season);
        }

        @GetMapping("/season-raw")
        public JsonNode seasonRaw(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam("name") String name,
                        @RequestParam(value = "seasonId", required = false) String seasonId) {
                String accountId = pubgApiService.getPlayerId(platform, name);

                String sid = (seasonId == null || seasonId.isBlank())
                                ? pubgApiService.getCurrentSeasonId(platform)
                                : seasonId;

                return pubgApiService.getSeasonStats(platform, accountId, sid);
        }

        @GetMapping("/latest-matches")
        public List<MatchSummary> latestMatches(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam("name") String name,
                        @RequestParam(value = "period", required = false, defaultValue = "DAY") Period period,
                        @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
                List<MatchSummary> matches = matchService.getRecentMatches(platform, name, period);

                if (limit < 1)
                        limit = 1;
                if (matches.size() > limit) {
                        return matches.subList(0, limit);
                }
                return matches;
        }

        @GetMapping("/matches")
        public List<MatchSummary> matches(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam("name") String name,
                        @RequestParam("period") Period period) {
                return matchService.getRecentMatches(platform, name, period);
        }

        @GetMapping("/stats")
        public StatsResponse stats(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam("name") String name,
                        @RequestParam("period") Period period) {
                List<MatchSummary> matches = matchService.getRecentMatchesCached(platform, name, period);

                var byMode = matchService.calculateStats(matches, period);
                PlayerStatsSummary overall = matchService.buildOverallStats(matches);

                return new StatsResponse(
                                overall,
                                byMode.getOrDefault("솔로", new PlayerStatsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
                                byMode.getOrDefault("듀오", new PlayerStatsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
                                byMode.getOrDefault("스쿼드", new PlayerStatsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
                                byMode.getOrDefault("아케이드", new PlayerStatsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
                                matches);
        }

        @GetMapping("/summary")
        public SummaryResponse summary(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam("name") String name,
                        @RequestParam(value = "period", defaultValue = "WEEK") Period period,
                        @RequestParam(value = "limit", defaultValue = "20") int limit,
                        @RequestParam(value = "scope", defaultValue = "RECENT") SummaryScope scope,
                        @RequestParam(value = "seasonId", required = false) String seasonId) {
                SummaryResponse base = matchService.buildSummaryCached(platform, name, period, limit);
                SeasonSummaryResponse season = seasonSummaryService.build(platform, name, seasonId);

                return new SummaryResponse(
                                base.name(),
                                base.period(),
                                base.matchCount(),
                                base.overall(),
                                base.recentPlacements(),
                                base.byMode(),
                                base.byMap(),
                                base.recentMatches(),
                                season);
        }

        @GetMapping("/ranked-card")
        public RankedCardResponse rankedCard(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam("name") String name,
                        @RequestParam(value = "mode", required = false, defaultValue = "squad-fpp") String mode) {
                return pubgApiService.getRankedCard(platform, name, mode);
        }

        @GetMapping("/leaderboard")
        public LeaderboardResponse leaderboard(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam(value = "seasonId", required = false) String seasonId,
                        @RequestParam(value = "mode", required = false, defaultValue = "squad-fpp") String mode) {
                return pubgApiService.getLeaderboard(platform, seasonId, mode);
        }

        @GetMapping("/ranked-raw")
        public JsonNode rankedRaw(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam("name") String name,
                        @RequestParam(value = "seasonId", required = false) String seasonId) {
                String accountId = pubgApiService.getPlayerId(platform, name);

                String sid = (seasonId == null || seasonId.isBlank())
                                ? pubgApiService.getCurrentSeasonId(platform)
                                : seasonId;

                return pubgApiService.getRankedStats(platform, accountId, sid);
        }

        @GetMapping("/leaderboard-raw")
        public JsonNode leaderboardRaw(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam(value = "seasonId", required = false) String seasonId,
                        @RequestParam(value = "mode", required = false, defaultValue = "squad-fpp") String mode) {
                return pubgApiService.getLeaderboardRaw(platform, seasonId, mode);
        }

        @GetMapping("/leaderboard-debug")
        public List<Map<String, Object>> leaderboardDebug(
                        @RequestParam(value = "platform", required = false, defaultValue = "steam") String platform,
                        @RequestParam(value = "seasonId", required = false) String seasonId,
                        @RequestParam(value = "mode", required = false, defaultValue = "squad-fpp") String mode) {
                JsonNode root = pubgApiService.getLeaderboardRaw(platform, seasonId, mode);
                JsonNode included = root.path("included");

                List<Map<String, Object>> out = new ArrayList<>();

                if (included != null && included.isArray()) {
                        for (JsonNode node : included) {
                                if (!"player".equals(node.path("type").asText("")))
                                        continue;

                                JsonNode stats = node.path("attributes").path("stats");

                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("name", node.path("attributes").path("name").asText(""));
                                row.put("tier", stats.path("tier")
                                                .asText(stats.path("currentTier").path("tier").asText("")));
                                row.put("subTier", stats.path("subTier")
                                                .asText(stats.path("currentTier").path("subTier").asText("")));
                                row.put("rankPoints", stats.path("rankPoints").asInt(
                                                stats.path("currentRankPoint").asInt(
                                                                stats.path("rankPoint").asInt(0))));
                                row.put("roundsPlayed", stats.path("roundsPlayed").asInt(stats.path("games").asInt(0)));
                                row.put("wins", stats.path("wins").asInt(0));
                                row.put("kills", stats.path("kills").asInt(0));
                                row.put("deaths", stats.path("deaths").asInt(0));
                                row.put("kda", stats.path("kda").asDouble(0.0));
                                row.put("kdr", stats.path("kdr").asDouble(0.0));
                                row.put("avgKill", stats.path("avgKill").asDouble(0.0));
                                row.put("damageDealt", stats.path("damageDealt").asDouble(0.0));
                                row.put("avgDamage", stats.path("avgDamage").asDouble(0.0));
                                row.put("winRatio", stats.path("winRatio").asDouble(0.0));
                                row.put("top10Ratio", stats.path("top10Ratio").asDouble(0.0));

                                out.add(row);

                                if (out.size() >= 20)
                                        break;
                        }
                }

                return out;
        }
}