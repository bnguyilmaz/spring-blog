package com.bengu.springblog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecentTrackArtistResponse {
    @JsonProperty("#text")
    private String name;

    public RecentTrackArtistResponse(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
