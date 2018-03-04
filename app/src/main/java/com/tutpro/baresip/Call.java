package com.tutpro.baresip;

public class Call {

    private String ua, call, peer_uri, status;
    Boolean hold;

    public Call(String ua, String call, String peer_uri, String status) {
        this.ua = ua;
        this.call = call;
        this.peer_uri = peer_uri;
        this.status = status;
    }

    public String getUA() {
        return ua;
    }

    public String getCall() {
        return call;
    }

    public String getPeerURI() {
        return peer_uri;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public String getStatus() {
        return status;
    }

    public void setHold(Boolean hold) {
        this.hold = hold;
    }
    public Boolean getHold() {
        return hold;
    }
}
