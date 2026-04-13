package com.pubgsite.bglog.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RankedCardResponse {

    private String playerName;
    private String platform;     // steam/kakao
    private String seasonId;

    // rankedGameModeStats 키 기준: "squad" / "duo"
    private String mode;

    private boolean unranked;    // 경쟁전 기록 없음

    private String tier;         // Platinum
    private String subTier;      // 2
    private int rp;              // currentRankPoint

    private String bestTier;     // "Platinum 1" 같은 문자열
    private int bestRp;          // bestRankPoint

    // ✅ 화면용 요약 지표
    private int roundsPlayed;    // 판수
    private double avgRank;      // 평균등수 (낮을수록 좋음)

    // ✅ raw는 0~1 ratio → 여기서는 %로 내려줌(0~100)
    private double top10Rate;    // 탑10 비율(%)
    private double winRate;      // 1등 비율(%)

    private double avgKill;      // 평균킬
    private double avgDamage;    // 평균딜 (damageDealt / roundsPlayed)

    // (디버깅/확장용) 총딜
    private double damageDealt;

    // 프론트에서 모드 선택 UI 만들 때 유용
    private List<String> availableModes;

    private String message;      // unranked일 때 안내 문구
}