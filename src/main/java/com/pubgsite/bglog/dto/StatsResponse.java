package com.pubgsite.bglog.dto;

import java.util.List;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatsResponse {

    private PlayerStatsSummary overall;   // ✅ 전체(기간 내 모든 매치)
    private PlayerStatsSummary solo;
    private PlayerStatsSummary duo;
    private PlayerStatsSummary squad;
    private PlayerStatsSummary arcade;    // ✅ 아케이드
    private List<MatchSummary> matches;   // ✅ 기간 내 전체 매치 리스트
}