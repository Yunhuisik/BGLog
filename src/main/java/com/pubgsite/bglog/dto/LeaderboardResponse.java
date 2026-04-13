package com.pubgsite.bglog.dto;

import java.util.List;

import com.pubgsite.bglog.dto.pubgDTO.LeaderboardEntry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LeaderboardResponse {
    private String platform;     // steam
    private String seasonId;
    private String mode;         // squad-fpp

    // PUBG 문서상 “리더보드 업데이트 주기”가 있으니 표시용으로 두면 좋음
    private List<LeaderboardEntry> entries;
}