package com.pubgsite.bglog.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pubgsite.bglog.dto.pubgDTO.Included;
import com.pubgsite.bglog.dto.pubgDTO.MatchData;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchResponse {
    private MatchData data;
    private List<Included> included;
}
