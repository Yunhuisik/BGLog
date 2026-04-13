package com.pubgsite.bglog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlayerStatsSummary {


    private int games;
    private double kd;
    private double winRate;
    private double top10Rate;

    private double avgDamage;
    private double avgSurvival;

    private int maxKills;
    private int headshots;
    private double headshotRate;
    private double longestKill;
}
