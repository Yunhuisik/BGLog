package com.pubgsite.bglog.dto.pubgDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchAttributes {

    private String gameMode;
    private String mapName;
    private String matchType;
    private String shardId;
    private String createdAt;
    private int duration;
    @JsonProperty("totalTeamCount")
    private Integer totalTeamCount;
}
