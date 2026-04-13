package com.pubgsite.bglog.dto;

import java.util.List;

public enum PubgPlatform {
    STEAM("steam", "pc-krjp"),
    KAKAO("kakao", "pc-kakao");

    private final String shard; // players/matches 기본
    private final String leaderboardShard; // leaderboards 용 (platform-region)

    PubgPlatform(String shard, String leaderboardShard) {
        this.shard = shard;
        this.leaderboardShard = leaderboardShard;
    }

    public String shard() {
        return shard;
    }

    public String leaderboardShard() {
        return leaderboardShard;
    }

    public static PubgPlatform from(String v) {
        if (v == null)
            return STEAM;
        return switch (v.trim().toLowerCase()) {
            case "kakao" -> KAKAO;
            case "steam" -> STEAM;
            default -> STEAM;
        };
    }

    // ✅ 리더보드 shard 후보(steam은 krjp 실패 시 pc-as 폴백)
    public List<String> leaderboardShards() {
        return switch (this) {
            case STEAM -> List.of("pc-krjp", "pc-as");
            case KAKAO -> List.of("pc-kakao"); // 필요하면 List.of("pc-kakao","pc-as")로 확장 가능
        };
    }
}