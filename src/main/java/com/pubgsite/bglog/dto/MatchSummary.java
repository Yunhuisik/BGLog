package com.pubgsite.bglog.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class MatchSummary {

    private String matchId;
    private String mode;
    private String map;
    private int rank;
    private Integer totalRank;

    private int kills;
    private int headshotKills;
    private double damage;
    private int assists;
    private int dbnos;
    private double longestKill;
    private int survivalTime;

    @JsonProperty("team")
    private List<String> teamMembers;
    private Instant createdAt;
}

