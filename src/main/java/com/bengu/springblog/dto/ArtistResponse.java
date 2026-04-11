package com.bengu.springblog.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArtistResponse {
    private String name;
    private String playcount;
    private String url;


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


}
