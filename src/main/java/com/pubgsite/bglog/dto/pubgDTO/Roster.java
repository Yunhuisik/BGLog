package com.pubgsite.bglog.dto.pubgDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Roster {

    private RosterData data;
}
