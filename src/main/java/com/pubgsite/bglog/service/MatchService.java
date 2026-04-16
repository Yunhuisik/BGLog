package com.pubgsite.bglog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pubgsite.bglog.cache.BglogCacheService;
import com.pubgsite.bglog.dto.*;
import com.pubgsite.bglog.dto.pubgDTO.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchService {

        private final PubgApiService apiService;
        private final ObjectMapper mapper;
        private final BglogCacheService cacheService;
        private final PerformanceMetricsService perf;

        @Value("${bglog.match.parallelism:8}")
        private int parallelism;

        @Value("${bglog.match.max-detail-count:200}")
        private int maxDetailCount;

        @Value("${bglog.match.distinct-enabled:false}")
        private boolean distinctEnabled;

        public List<MatchSummary> getRecentMatchesCached(String platform, String name, Period period) {
                return getRecentMatchesCached(platform, name, period, true);
        }

        public List<MatchSummary> getRecentMatchesCached(
                        String platform,
                        String name,
                        Period period,
                        boolean useRecentMatchesCache) {
                return getRecentMatchesCached(platform, name, period, useRecentMatchesCache, true);
        }

        public List<MatchSummary> getRecentMatchesCached(
                        String platform,
                        String name,
                        Period period,
                        boolean useRecentMatchesCache,
                        boolean useMatchDetailCache) {
                String resolvedPlatform = (platform == null || platform.isBlank()) ? "steam" : platform.toLowerCase();
                String key = resolvedPlatform + ":" + name.toLowerCase() + ":" + period;

                return cacheService.getOrCompute(
                                "recentMatches",
                                key,
                                Duration.ofMinutes(2),
                                new TypeReference<List<MatchSummary>>() {
                                },
                                useRecentMatchesCache,
                                () -> getRecentMatches(resolvedPlatform, name, period, useMatchDetailCache));
        }

        public List<MatchSummary> getRecentMatches(String platform, String nickname, Period period) {
                return getRecentMatches(platform, nickname, period, true);
        }

        public List<MatchSummary> getRecentMatches(
                        String platform,
                        String nickname,
                        Period period,
                        boolean useMatchDetailCache) {
                long totalStart = perf.start();

                String resolvedPlatform = (platform == null || platform.isBlank()) ? "steam" : platform.toLowerCase();

                String playerId = apiService.getPlayerId(resolvedPlatform, nickname);
                List<String> matchIds = apiService.getMatchIds(resolvedPlatform, playerId);

                Instant cutoff = Instant.now().minus(period.getDays(), ChronoUnit.DAYS);

                List<String> limitedMatchIds = matchIds.stream()
                                .limit(maxDetailCount)
                                .toList();

                List<String> targetMatchIds = distinctEnabled
                                ? limitedMatchIds.stream().distinct().toList()
                                : limitedMatchIds;

                perf.add("match.input.count", limitedMatchIds.size());
                perf.add("match.target.count", targetMatchIds.size());
                perf.add("match.duplicate.removed.count", limitedMatchIds.size() - targetMatchIds.size());
                perf.increment("match.parallelism." + Math.max(1, parallelism));

                List<MatchSummary> parsedMatches;

                if (parallelism <= 1) {
                        parsedMatches = targetMatchIds.stream()
                                        .map(matchId -> parseMatch(resolvedPlatform, matchId, playerId, useMatchDetailCache))
                                        .toList();
                } else {
                        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
                        try {
                                List<CompletableFuture<MatchSummary>> futures = targetMatchIds.stream()
                                                .map(matchId -> CompletableFuture.supplyAsync(
                                                                () -> parseMatch(resolvedPlatform, matchId, playerId,
                                                                                useMatchDetailCache),
                                                                executor))
                                                .toList();

                                parsedMatches = futures.stream()
                                                .map(CompletableFuture::join)
                                                .toList();
                        } finally {
                        }
                }

                List<MatchSummary> result = parsedMatches.stream()
                                .filter(Objects::nonNull)
                                .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isAfter(cutoff))
                                .sorted(Comparator.comparing(MatchSummary::getCreatedAt).reversed())
                                .toList();

                perf.add("match.result.count", result.size());
                perf.recordTime("match.getRecentMatches.total", totalStart);

                long elapsedMs = Math.round((System.nanoTime() - totalStart) / 1_000_000.0);
                System.out.println("[MATCH_SERVICE] getRecentMatches platform=" + resolvedPlatform
                                + " name=" + nickname
                                + " period=" + period
                                + " inputMatchIds=" + limitedMatchIds.size()
                                + " targetMatchIds=" + targetMatchIds.size()
                                + " result=" + result.size()
                                + " parallelism=" + Math.max(1, parallelism)
                                + " distinctEnabled=" + distinctEnabled
                                + " useMatchDetailCache=" + useMatchDetailCache
                                + " elapsedMs=" + elapsedMs);

                return result;
        }

        private MatchSummary parseMatch(String platform, String matchId, String myPlayerId, boolean useMatchDetailCache) {
                long totalStart = perf.start();

                try {
                        long detailStart = perf.start();
                        Map raw = apiService.getMatchDetail(platform, matchId, useMatchDetailCache);
                        perf.recordTime("match.parse.fetchDetail", detailStart);

                        long convertStart = perf.start();
                        MatchResponse match = mapper.convertValue(raw, MatchResponse.class);
                        perf.recordTime("match.parse.convertValue", convertStart);

                        if (match.getIncluded() == null || match.getData() == null) {
                                perf.increment("match.parse.skipped.invalidPayload");
                                return null;
                        }

                        String mode = Optional.ofNullable(match.getData().getAttributes())
                                        .map(MatchAttributes::getGameMode)
                                        .orElse("unknown");

                        String map = Optional.ofNullable(match.getData().getAttributes())
                                        .map(MatchAttributes::getMapName)
                                        .orElse("unknown");

                        long findParticipantStart = perf.start();
                        Included myParticipant = match.getIncluded().stream()
                                        .filter(i -> "participant".equals(i.getType()))
                                        .filter(i -> Optional.ofNullable(i.getAttributes())
                                                        .map(Attributes::getStats)
                                                        .map(Stats::getPlayerId)
                                                        .map(pid -> pid.equals(myPlayerId))
                                                        .orElse(false))
                                        .findFirst()
                                        .orElse(null);
                        perf.recordTime("match.parse.findMyParticipant", findParticipantStart);

                        if (myParticipant == null
                                        || myParticipant.getAttributes() == null
                                        || myParticipant.getAttributes().getStats() == null) {
                                perf.increment("match.parse.skipped.noParticipant");
                                return null;
                        }

                        Stats stats = myParticipant.getAttributes().getStats();
                        String myParticipantId = myParticipant.getId();

                        long findRosterStart = perf.start();
                        Included myRoster = match.getIncluded().stream()
                                        .filter(i -> "roster".equals(i.getType()))
                                        .filter(r -> Optional.ofNullable(r.getRelationships())
                                                        .map(Relationships::getParticipants)
                                                        .map(Participants::getData)
                                                        .orElse(List.of())
                                                        .stream()
                                                        .anyMatch(d -> myParticipantId.equals(d.getId())))
                                        .findFirst()
                                        .orElse(null);
                        perf.recordTime("match.parse.findMyRoster", findRosterStart);

                        long teamNamesStart = perf.start();
                        List<String> teamNames = new ArrayList<>();

                        if (myRoster != null) {
                                List<RosterData> members = Optional.ofNullable(myRoster.getRelationships())
                                                .map(Relationships::getParticipants)
                                                .map(Participants::getData)
                                                .orElse(List.of());

                                for (RosterData member : members) {
                                        match.getIncluded().stream()
                                                        .filter(i -> "participant".equals(i.getType()))
                                                        .filter(i -> member.getId().equals(i.getId()))
                                                        .findFirst()
                                                        .ifPresent(p -> {
                                                                String name = Optional.ofNullable(p.getAttributes())
                                                                                .map(Attributes::getStats)
                                                                                .map(Stats::getName)
                                                                                .orElse("unknown");
                                                                teamNames.add(name);
                                                        });
                                }

                                teamNames.sort((a, b) -> {
                                        if (a.equalsIgnoreCase(stats.getName()))
                                                return -1;
                                        if (b.equalsIgnoreCase(stats.getName()))
                                                return 1;
                                        return a.compareToIgnoreCase(b);
                                });
                        }
                        perf.recordTime("match.parse.teamNames", teamNamesStart);

                        Instant createdAt = Optional.ofNullable(match.getData().getAttributes())
                                        .map(MatchAttributes::getCreatedAt)
                                        .map(Instant::parse)
                                        .orElse(Instant.now());

                        long rosterCountStart = perf.start();
                        int totalTeamCount = (int) match.getIncluded().stream()
                                        .filter(i -> "roster".equals(i.getType()))
                                        .count();
                        perf.recordTime("match.parse.countRosters", rosterCountStart);

                        perf.increment("match.parse.success");

                        return new MatchSummary(
                                        matchId,
                                        modeToKr(mode),
                                        mapToKr(map),
                                        stats.getWinPlace(),
                                        totalTeamCount,
                                        stats.getKills(),
                                        stats.getHeadshotKills(),
                                        stats.getDamageDealt(),
                                        stats.getAssists(),
                                        stats.getDBNOs(),
                                        stats.getLongestKill(),
                                        stats.getTimeSurvived(),
                                        teamNames,
                                        createdAt);
                } finally {
                        perf.recordTime("match.parse.total", totalStart);
                }
        }

        public Map<String, PlayerStatsSummary> calculateStats(List<MatchSummary> matches, Period period) {
                Instant cutoff = Instant.now().minus(period.getDays(), ChronoUnit.DAYS);

                List<MatchSummary> filtered = matches.stream()
                                .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isAfter(cutoff))
                                .toList();

                return filtered.stream()
                                .collect(Collectors.groupingBy(
                                                MatchSummary::getMode,
                                                Collectors.collectingAndThen(Collectors.toList(), this::buildStats)));
        }

        private PlayerStatsSummary buildStats(List<MatchSummary> list) {
                int games = list.size();
                if (games == 0) {
                        return new PlayerStatsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
                }

                int wins = (int) list.stream().filter(m -> m.getRank() == 1).count();
                int top10 = (int) list.stream().filter(m -> m.getRank() <= 10).count();

                int kills = list.stream().mapToInt(MatchSummary::getKills).sum();
                int deaths = Math.max(games - wins, 1);

                int headshots = list.stream().mapToInt(MatchSummary::getHeadshotKills).sum();
                double headshotRate = kills == 0 ? 0 : (headshots * 100.0 / kills);

                double avgDamage = list.stream().mapToDouble(MatchSummary::getDamage).average().orElse(0);
                double avgSurvival = list.stream().mapToDouble(MatchSummary::getSurvivalTime).average().orElse(0);

                int maxKills = list.stream().mapToInt(MatchSummary::getKills).max().orElse(0);
                double longestKill = list.stream().mapToDouble(MatchSummary::getLongestKill).max().orElse(0);

                return new PlayerStatsSummary(
                                games,
                                (double) kills / deaths,
                                100.0 * wins / games,
                                100.0 * top10 / games,
                                avgDamage,
                                avgSurvival,
                                maxKills,
                                headshots,
                                headshotRate,
                                longestKill);
        }

        public PlayerStatsSummary buildOverallStats(List<MatchSummary> matches) {
                return buildStats(matches);
        }

        public SummaryResponse buildSummaryCached(String platform, String nickname, Period period, int limit) {
                return buildSummaryCached(platform, nickname, period, limit, true, true);
        }

        public SummaryResponse buildSummaryCached(
                        String platform,
                        String nickname,
                        Period period,
                        int limit,
                        boolean useSummaryCache,
                        boolean useRecentMatchesCache) {
                String resolvedPlatform = (platform == null ? "steam" : platform.toLowerCase());
                String key = resolvedPlatform + ":RECENT:" + nickname.toLowerCase() + ":" + period + ":" + limit;

                return cacheService.getOrCompute(
                                "summary",
                                key,
                                Duration.ofMinutes(2),
                                SummaryResponse.class,
                                useSummaryCache,
                                () -> buildSummary(resolvedPlatform, nickname, period, limit, useRecentMatchesCache,
                                                true));
        }

        public SummaryResponse buildSummary(String platform, String nickname, Period period, int limit) {
                return buildSummary(platform, nickname, period, limit, true, true);
        }

        public SummaryResponse buildSummary(
                        String platform,
                        String nickname,
                        Period period,
                        int limit,
                        boolean useRecentMatchesCache,
                        boolean useMatchDetailCache) {
                List<MatchSummary> matches = getRecentMatchesCached(
                                platform,
                                nickname,
                                period,
                                useRecentMatchesCache,
                                useMatchDetailCache).stream()
                                                .filter(Objects::nonNull)
                                                .filter(m -> !"아케이드".equals(m.getMode()))
                                                .toList();

                if (matches.size() > limit) {
                        matches = matches.subList(0, limit);
                }

                int games = matches.size();

                if (games == 0) {
                        return new SummaryResponse(
                                        nickname,
                                        period,
                                        0,
                                        new OverallSummary(0, 0, 0, 0, 0),
                                        List.of(),
                                        Map.of(),
                                        Map.of(),
                                        List.of(),
                                        null);
                }

                double avgKills = matches.stream().mapToInt(MatchSummary::getKills).average().orElse(0);
                double avgDamage = matches.stream().mapToDouble(MatchSummary::getDamage).average().orElse(0);
                double avgSurvival = matches.stream().mapToDouble(MatchSummary::getSurvivalTime).average().orElse(0);
                long wins = matches.stream().filter(m -> m.getRank() == 1).count();

                OverallSummary overall = new OverallSummary(
                                games,
                                avgKills,
                                avgDamage,
                                avgSurvival,
                                100.0 * wins / games);

                List<Integer> recentPlacements = matches.stream()
                                .limit(20)
                                .map(MatchSummary::getRank)
                                .toList();

                Map<String, PlayerStatsSummary> byMode = matches.stream()
                                .collect(Collectors.groupingBy(
                                                MatchSummary::getMode,
                                                Collectors.collectingAndThen(Collectors.toList(), this::buildStats)));

                Map<String, PlayerStatsSummary> byMap = matches.stream()
                                .collect(Collectors.groupingBy(
                                                MatchSummary::getMap,
                                                Collectors.collectingAndThen(Collectors.toList(), this::buildStats)));

                return new SummaryResponse(
                                nickname,
                                period,
                                games,
                                overall,
                                recentPlacements,
                                byMode,
                                byMap,
                                matches,
                                null);
        }

        private static final Map<String, String> MAP_KR = Map.ofEntries(
                        Map.entry("Erangel_Main", "에란겔"),
                        Map.entry("Desert_Main", "미라마"),
                        Map.entry("Savage_Main", "사녹"),
                        Map.entry("Tiger_Main", "태이고"),
                        Map.entry("Heaven_Main", "카라킨"),
                        Map.entry("Summerland_Main", "카라킨"),
                        Map.entry("DihorOtok_Main", "비켄디"),
                        Map.entry("Kiki_Main", "데스턴"),
                        Map.entry("Neon_Main", "론도"),
                        Map.entry("Chimera_Main", "파라모"),
                        Map.entry("Baltic_Main", "에란겔"));

        private static final Map<String, String> MODE_KR = Map.of(
                        "solo", "솔로",
                        "duo", "듀오",
                        "squad", "스쿼드",
                        "ibr", "아케이드",
                        "squad-fpp", "경쟁전_스쿼드",
                        "duo-fpp", "경쟁전_듀오",
                        "tdm", "사용자설정게임_팀데스매치",
                        "normal-solo", "사용자설정게임_개인전");

        private String mapToKr(String map) {
                return MAP_KR.getOrDefault(map, map);
        }

        private String modeToKr(String mode) {
                return MODE_KR.getOrDefault(mode, mode);
        }

        public int getParallelism() {
                return parallelism;
        }

        public int getMaxDetailCount() {
                return maxDetailCount;
        }

        public boolean isDistinctEnabled() {
                return distinctEnabled;
        }
}