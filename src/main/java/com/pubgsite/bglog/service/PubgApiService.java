package com.pubgsite.bglog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pubgsite.bglog.cache.BglogCacheService;
import com.pubgsite.bglog.dto.LeaderboardResponse;
import com.pubgsite.bglog.dto.PubgPlatform;
import com.pubgsite.bglog.dto.RankedCardResponse;
import com.pubgsite.bglog.dto.pubgDTO.LeaderboardEntry;
import com.pubgsite.bglog.exception.MatchNotFoundException;
import com.pubgsite.bglog.exception.PlayerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class PubgApiService {

    private static final Logger log = LoggerFactory.getLogger(PubgApiService.class);

    private static final AtomicLong CALL_PLAYER_LOOKUP = new AtomicLong(0);
    private static final AtomicLong CALL_PLAYER_MATCHES = new AtomicLong(0);
    private static final AtomicLong CALL_MATCH_DETAIL = new AtomicLong(0);
    private static final AtomicLong CALL_SEASONS = new AtomicLong(0);
    private static final AtomicLong CALL_SEASON_STATS = new AtomicLong(0);
    private static final AtomicLong CALL_RANKED_STATS = new AtomicLong(0);
    private static final AtomicLong CALL_LEADERBOARD = new AtomicLong(0);

    private final BglogCacheService cacheService;
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${pubg.api-key}")
    private String apiKey;

    @Value("${pubg.base-url}")
    private String baseUrl;

    private static void logCounter(String label, AtomicLong counter, String extra) {
        long n = counter.incrementAndGet();
        if (extra == null || extra.isBlank()) {
            log.info("[PUBG_API_CALL] {} count={}", label, n);
        } else {
            log.info("[PUBG_API_CALL] {} count={} {}", label, n, extra);
        }
    }

    private HttpEntity<String> createEntity() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("PUBG_API_KEY 환경변수가 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Accept", "application/vnd.api+json");
        return new HttpEntity<>(headers);
    }

    // -------------------------
    // Player
    // -------------------------
    public String getPlayerId(String name) {
        return getPlayerId("steam", name);
    }

    public String getPlayerId(String platform, String name) {
        PubgPlatform p = PubgPlatform.from(platform);
        String shard = p.shard();

        String key = shard + ":playerId:" + normalizeNameKey(name);
        return cacheService.getOrCompute("playerId", key, Duration.ofHours(12), String.class,
                () -> getPlayerIdFromApi(p, name));
    }

    private String getPlayerIdFromApi(PubgPlatform platform, String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new PlayerNotFoundException(playerName);
        }

        try {
            return requestPlayer(platform, playerName);
        } catch (PlayerNotFoundException e) {
            if (playerName.isEmpty()) {
                throw e;
            }
            String normalized = playerName.substring(0, 1).toUpperCase() + playerName.substring(1);
            if (normalized.equals(playerName)) {
                throw e;
            }
            return requestPlayer(platform, normalized);
        }
    }

    private String requestPlayer(PubgPlatform platform, String name) {
        String shard = platform.shard();

        logCounter("players_lookup", CALL_PLAYER_LOOKUP, "platform=" + shard + " name=" + name);

        String encoded = UriUtils.encodeQueryParam(name, StandardCharsets.UTF_8);
        String url = baseUrl + "/shards/" + shard + "/players?filter[playerNames]=" + encoded;

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, createEntity(), Map.class);

            List<?> data = Optional.ofNullable(response.getBody())
                    .map(b -> (List<?>) b.get("data"))
                    .orElseThrow(() -> new PlayerNotFoundException(name));

            if (data.isEmpty()) {
                throw new PlayerNotFoundException(name);
            }

            Map<?, ?> player = (Map<?, ?>) data.get(0);
            return String.valueOf(player.get("id"));
        } catch (HttpClientErrorException.NotFound e) {
            throw new PlayerNotFoundException(name);
        }
    }

    // -------------------------
    // Matches
    // -------------------------
    public List<String> getMatchIds(String platform, String playerId) {
        PubgPlatform p = PubgPlatform.from(platform);
        String shard = p.shard();

        String key = shard + ":playerMatches:" + playerId;
        return cacheService.getOrCompute("playerMatches", key, Duration.ofMinutes(1), List.class,
                () -> getMatchIdsFromApi(p, playerId));
    }

    private List<String> getMatchIdsFromApi(PubgPlatform platform, String playerId) {
        String shard = platform.shard();

        logCounter("player_matches", CALL_PLAYER_MATCHES, "platform=" + shard + " playerId=" + playerId);

        String url = baseUrl + "/shards/" + shard + "/players/" + playerId;

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, createEntity(), Map.class);

            Map<?, ?> body = Optional.ofNullable(response.getBody())
                    .orElseThrow(() -> new PlayerNotFoundException(playerId));

            Map<?, ?> data = (Map<?, ?>) body.get("data");
            Map<?, ?> relationships = (Map<?, ?>) data.get("relationships");
            Map<?, ?> matches = (Map<?, ?>) relationships.get("matches");

            List<Map<?, ?>> matchData = (List<Map<?, ?>>) matches.get("data");

            return matchData.stream()
                    .map(m -> String.valueOf(m.get("id")))
                    .toList();

        } catch (HttpClientErrorException.NotFound e) {
            throw new PlayerNotFoundException(playerId);
        }
    }

    public Map getMatchDetail(String platform, String matchId) {
        PubgPlatform p = PubgPlatform.from(platform);
        String shard = p.shard();

        String key = shard + ":match:" + matchId;

        return cacheService.getOrCompute("matchDetail", key, Duration.ofHours(24), Map.class, () -> {
            logCounter("match_detail", CALL_MATCH_DETAIL, "platform=" + shard + " matchId=" + matchId);

            String url = baseUrl + "/shards/" + shard + "/matches/" + matchId;

            try {
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, createEntity(), Map.class);
                return Optional.ofNullable(response.getBody())
                        .orElseThrow(() -> new MatchNotFoundException(matchId));
            } catch (HttpClientErrorException.NotFound e) {
                throw new MatchNotFoundException(matchId);
            }
        });
    }

    // -------------------------
    // Seasons
    // -------------------------
    public String getCurrentSeasonId(String platform) {
        PubgPlatform p = PubgPlatform.from(platform);
        String shard = p.shard();

        String key = shard + ":currentSeasonId";
        return cacheService.getOrCompute("currentSeason", key, Duration.ofHours(1), String.class, () -> {
            logCounter("seasons", CALL_SEASONS, "platform=" + shard);

            String url = baseUrl + "/shards/" + shard + "/seasons";
            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, createEntity(), String.class);

            try {
                JsonNode root = objectMapper.readTree(res.getBody());
                for (JsonNode s : root.path("data")) {
                    boolean isCurrent = s.path("attributes").path("isCurrentSeason").asBoolean(false);
                    if (isCurrent) {
                        return s.path("id").asText();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse seasons", e);
            }

            throw new IllegalStateException("Current season not found");
        });
    }

    public JsonNode getSeasonStats(String platform, String accountId, String seasonId) {
        PubgPlatform p = PubgPlatform.from(platform);
        String shard = p.shard();

        String key = shard + ":seasonStats:" + accountId + ":" + seasonId;
        return cacheService.getOrCompute("seasonStats", key, Duration.ofMinutes(5), JsonNode.class,
                () -> getSeasonStatsFromApi(p, accountId, seasonId));
    }

    private JsonNode getSeasonStatsFromApi(PubgPlatform platform, String accountId, String seasonId) {
        String shard = platform.shard();

        logCounter("season_stats", CALL_SEASON_STATS,
                "platform=" + shard + " accountId=" + accountId + " seasonId=" + seasonId);

        String url = baseUrl + "/shards/" + shard + "/players/" + accountId + "/seasons/" + seasonId;
        ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, createEntity(), String.class);

        try {
            return objectMapper.readTree(res.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse season stats", e);
        }
    }

    // -------------------------
    // Ranked
    // -------------------------
    public JsonNode getRankedStats(String platform, String accountId, String seasonId) {
        PubgPlatform p = PubgPlatform.from(platform);
        String shard = p.shard();

        String key = shard + ":rankedStats:" + accountId + ":" + seasonId;
        return cacheService.getOrCompute("rankedStats", key, Duration.ofMinutes(5), JsonNode.class,
                () -> getRankedStatsFromApi(p, accountId, seasonId));
    }

    private JsonNode getRankedStatsFromApi(PubgPlatform platform, String accountId, String seasonId) {
        String shard = platform.shard();

        logCounter("ranked_stats", CALL_RANKED_STATS,
                "platform=" + shard + " accountId=" + accountId + " seasonId=" + seasonId);

        String url = baseUrl + "/shards/" + shard + "/players/" + accountId + "/seasons/" + seasonId + "/ranked";
        ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, createEntity(), String.class);

        try {
            return objectMapper.readTree(res.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ranked stats", e);
        }
    }

    public RankedCardResponse getRankedCard(String platform, String name, String mode) {
        String accountId = getPlayerId(platform, name);
        String seasonId = getCurrentSeasonId(platform);

        JsonNode ranked = getRankedStats(platform, accountId, seasonId);
        JsonNode statsRoot = ranked.path("data").path("attributes").path("rankedGameModeStats");

        List<String> availableModes = new ArrayList<>();
        if (statsRoot != null && statsRoot.isObject()) {
            statsRoot.fieldNames().forEachRemaining(availableModes::add);
        }

        String requested = (mode == null || mode.isBlank()) ? "squad" : mode.trim().toLowerCase();
        String normalized = normalizeRankedMode(requested);

        String targetMode = normalized;
        JsonNode m = (statsRoot == null) ? null : statsRoot.path(targetMode);

        if (m == null || m.isMissingNode() || m.isNull() || (m.isObject() && m.size() == 0)) {
            String fallback = pickRankedFallbackMode(availableModes);
            if (fallback != null) {
                targetMode = fallback;
                m = statsRoot.path(targetMode);
            }
        }

        if (m == null || m.isMissingNode() || m.isNull() || (m.isObject() && m.size() == 0)) {
            return RankedCardResponse.builder()
                    .playerName(name)
                    .platform(platform)
                    .seasonId(seasonId)
                    .mode(targetMode)
                    .unranked(true)
                    .availableModes(availableModes)
                    .message("이번 시즌 경쟁전 기록이 없습니다.")
                    .build();
        }

        String tier = m.path("currentTier").path("tier").asText("");
        String subTier = m.path("currentTier").path("subTier").asText("");
        int rp = m.path("currentRankPoint").asInt(0);

        String bestTier = "";
        String bt = m.path("bestTier").path("tier").asText("");
        String bst = m.path("bestTier").path("subTier").asText("");
        if (!bt.isBlank()) {
            bestTier = bt + (bst.isBlank() ? "" : (" " + bst));
        }

        int bestRp = m.path("bestRankPoint").asInt(0);
        if (bestRp == 0) {
            bestRp = m.path("bestRankPoints").asInt(0);
        }

        int roundsPlayed = m.path("roundsPlayed").asInt(m.path("games").asInt(0));
        double avgRank = m.path("avgRank").asDouble(0.0);
        double top10Ratio = m.path("top10Ratio").asDouble(0.0);
        double winRatio = m.path("winRatio").asDouble(0.0);
        double avgKill = m.path("avgKill").asDouble(0.0);
        double damageDealt = m.path("damageDealt").asDouble(0.0);

        double top10Rate = top10Ratio * 100.0;
        double winRate = winRatio * 100.0;
        double avgDamage = (roundsPlayed > 0) ? (damageDealt / roundsPlayed) : 0.0;

        return RankedCardResponse.builder()
                .playerName(name)
                .platform(platform)
                .seasonId(seasonId)
                .mode(targetMode)
                .unranked(false)
                .tier(tier)
                .subTier(subTier)
                .rp(rp)
                .bestTier(bestTier)
                .bestRp(bestRp)
                .roundsPlayed(roundsPlayed)
                .avgRank(avgRank)
                .top10Rate(top10Rate)
                .winRate(winRate)
                .avgKill(avgKill)
                .avgDamage(avgDamage)
                .damageDealt(damageDealt)
                .availableModes(availableModes)
                .build();
    }

    private String normalizeRankedMode(String mode) {
        if (mode == null) return "squad";
        String m = mode.toLowerCase();

        if (m.equals("squad-fpp") || m.equals("squadfpp") || m.equals("squad_fpp")) return "squad";
        if (m.equals("duo-fpp") || m.equals("duofpp") || m.equals("duo_fpp")) return "duo";

        if (m.startsWith("squad")) return "squad";
        if (m.startsWith("duo")) return "duo";

        return m;
    }

    private String pickRankedFallbackMode(List<String> availableModes) {
        if (availableModes == null || availableModes.isEmpty()) return null;
        if (availableModes.contains("squad")) return "squad";
        if (availableModes.contains("duo")) return "duo";
        return availableModes.get(0);
    }

    // -------------------------
    // Leaderboard
    // -------------------------
    public JsonNode getLeaderboardRaw(String platform, String seasonId, String mode) {
        PubgPlatform p = PubgPlatform.from(platform);

        String sid = (seasonId == null || seasonId.isBlank()) ? getCurrentSeasonId(platform) : seasonId;
        String m = (mode == null || mode.isBlank()) ? "squad-fpp" : mode;

        RuntimeException last = null;

        for (String lbShard : p.leaderboardShards()) {
            String key = lbShard + ":leaderboard:" + sid + ":" + m;

            try {
                return cacheService.getOrCompute("leaderboard", key, Duration.ofMinutes(15), JsonNode.class,
                        () -> getLeaderboardRawFromApi(lbShard, sid, m));
            } catch (RuntimeException e) {
                last = e;
                log.warn("[LEADERBOARD_FALLBACK] tryShard={} platform={} seasonId={} mode={} reason={}",
                        lbShard, p.shard(), sid, m, e.getMessage());
            }
        }

        throw (last != null) ? last : new RuntimeException("Leaderboard fetch failed");
    }

    private JsonNode getLeaderboardRawFromApi(String leaderboardShard, String seasonId, String mode) {
        logCounter("leaderboard", CALL_LEADERBOARD,
                "platform=" + leaderboardShard + " seasonId=" + seasonId + " mode=" + mode);

        String url = baseUrl + "/shards/" + leaderboardShard + "/leaderboards/" + seasonId + "/" + mode;
        ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, createEntity(), String.class);

        try {
            return objectMapper.readTree(res.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse leaderboard", e);
        }
    }

    public LeaderboardResponse getLeaderboard(String platform, String seasonId, String mode) {
        PubgPlatform p = PubgPlatform.from(platform);

        String sid = (seasonId == null || seasonId.isBlank()) ? getCurrentSeasonId(platform) : seasonId;
        String m = (mode == null || mode.isBlank()) ? "squad-fpp" : mode;

        JsonNode root = getLeaderboardRaw(platform, sid, m);

        List<LeaderboardEntry> entries = new ArrayList<>();
        JsonNode included = root.path("included");

        if (included != null && included.isArray()) {
            for (JsonNode node : included) {
                if (!"player".equals(node.path("type").asText(""))) continue;

                String name = node.path("attributes").path("name").asText("");
                JsonNode stats = node.path("attributes").path("stats");

                int rp = stats.path("rankPoints").asInt(
                        stats.path("currentRankPoint").asInt(
                                stats.path("rankPoint").asInt(0)));

                String tier = stats.path("tier").asText(
                        stats.path("currentTier").path("tier").asText(""));
                String subTier = stats.path("subTier").asText(
                        stats.path("currentTier").path("subTier").asText(""));

                int roundsPlayed = stats.path("games").asInt(
                        stats.path("roundsPlayed").asInt(0));

                int wins = stats.path("wins").asInt(0);
                double winRatio = stats.path("winRatio").asDouble(0.0);
                double winRate = winRatio * 100.0;

                double avgDamage = stats.path("averageDamage").asDouble(0.0);
                double avgKill = stats.path("averageKill").asDouble(0.0);

                entries.add(LeaderboardEntry.builder()
                        .rank(0)
                        .name(name)
                        .rp(rp)
                        .tier(tier)
                        .subTier(subTier)
                        .roundsPlayed(roundsPlayed)
                        .wins(wins)
                        .winRate(winRate)
                        .avgDamage(avgDamage)
                        .avgKill(avgKill)
                        .build());
            }
        }

        entries.sort(
                Comparator.comparingInt(LeaderboardEntry::getRp).reversed()
                        .thenComparingInt(LeaderboardEntry::getRoundsPlayed).reversed()
                        .thenComparingInt(LeaderboardEntry::getWins).reversed()
                        .thenComparing(LeaderboardEntry::getName, String.CASE_INSENSITIVE_ORDER));

        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry e = entries.get(i);
            entries.set(i, LeaderboardEntry.builder()
                    .rank(i + 1)
                    .name(e.getName())
                    .rp(e.getRp())
                    .tier(e.getTier())
                    .subTier(e.getSubTier())
                    .roundsPlayed(e.getRoundsPlayed())
                    .wins(e.getWins())
                    .winRate(e.getWinRate())
                    .avgDamage(e.getAvgDamage())
                    .avgKill(e.getAvgKill())
                    .build());
        }

        return LeaderboardResponse.builder()
                .platform(p.shard())
                .seasonId(sid)
                .mode(m)
                .entries(entries)
                .build();
    }

    private String normalizeNameKey(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}