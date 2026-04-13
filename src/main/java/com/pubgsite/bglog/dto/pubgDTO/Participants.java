package com.pubgsite.bglog.dto.pubgDTO;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Participants {

    private List<RosterData> data;
}
