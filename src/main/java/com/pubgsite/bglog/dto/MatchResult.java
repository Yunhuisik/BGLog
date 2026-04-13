package com.pubgsite.bglog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MatchResult {
    private String matchId;
    private int kills;
    private double damage;
    private int assists;
    private int survivalTime;
    
}
