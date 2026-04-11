package com.bengu.springblog.dto;

import java.util.List;

public class RecentTracksData {
    private List<TrackResponse> track;

    public RecentTracksData(List<TrackResponse> track) {
        this.track = track;
    }

    public List<TrackResponse> getTrack() {
        return track;
    }

    public void setTrack(List<TrackResponse> track) {
        this.track = track;
    }
}
