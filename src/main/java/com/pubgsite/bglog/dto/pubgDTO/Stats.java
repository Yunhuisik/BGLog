package com.pubgsite.bglog.dto.pubgDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stats {

    private int kills;
    private int headshotKills;
    private double damageDealt;
    private int assists;
    @JsonProperty("DBNOs")
    private int DBNOs;
    private double longestKill;
    private int winPlace;
    private int timeSurvived;
    private String name;
    private String playerId;

}
