package com.tutpro.baresip;

public class HistoryRow {

    private String peer_uri;
    private Integer direction;
    private String time;

    public HistoryRow(String peer_uri, Integer direction, String time) {
        this.peer_uri = peer_uri;
        this.direction = direction;
        this.time = time;
    }

    public String getPeerURI() { return peer_uri; }
    public Integer getDirection() { return direction; }
    public String getTime() { return time; }

}
