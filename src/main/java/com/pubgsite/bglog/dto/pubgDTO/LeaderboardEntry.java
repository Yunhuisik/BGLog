package com.pubgsite.bglog.dto.pubgDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LeaderboardEntry {

    private int rank;
    private String name;

    private int rp;
    private String tier;
    private String subTier;

    private int roundsPlayed;
    private int wins;

    private double winRate;
    private double avgDamage;
    private double avgKill;
}