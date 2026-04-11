package com.bengu.springblog.dto;

public class RecentTracksResponse {
    private RecentTracksData recenttracks;

    public RecentTracksResponse(RecentTracksData recenttracks) {
        this.recenttracks = recenttracks;
    }

    public RecentTracksData getRecenttracks() {
        return recenttracks;
    }

    public void setResenttracks(RecentTracksData recenttracks) {
        this.recenttracks = recenttracks;
    }
}
