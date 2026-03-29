package com.bengu.springblog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;


public class TopArtistsResponse {
    TopArtistsData topartists;

    public TopArtistsData getTopartists() {
        return topartists;
    }

    public void setTopartists(TopArtistsData topartists) {
        this.topartists = topartists;
    }
}
