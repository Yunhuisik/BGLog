package com.pubgsite.bglog.dto.pubgDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Included {

    private String type;
    private String id;
    private Attributes attributes;
    private Relationships relationships;
}

