package com.bengu.springblog.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArtistResponse {
    String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlaycount() {
        return playcount;
    }

    public void setPlaycount(String playcount) {
        this.playcount = playcount;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    String playcount;
    String url;
}
