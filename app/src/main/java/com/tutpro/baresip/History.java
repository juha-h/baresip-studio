package com.tutpro.baresip;

import java.io.Serializable;
import java.util.GregorianCalendar;

public class History implements Serializable {

    private static final long serialVersionUID = -299482035708790407L;

    private String ua, call, aor, peer_uri, direction;
    private GregorianCalendar time;
    private Boolean connected;

    public History(String ua, String call, String aor, String peer_uri, String direction,
                   Boolean connected) {
        this.ua = ua;
        this.call = call;
        this.aor = aor;
        this.peer_uri = peer_uri;
        this.direction = direction;
        this.time = new GregorianCalendar();
        this.connected = connected;
    }

    public String getUA() { return ua; }
    public String getCall() { return call; }
    public String getAoR() { return aor; }
    public String getPeerURI() { return peer_uri; }
    public String getDirection() { return direction; }
    public GregorianCalendar getTime() { return time; }
    public Boolean getConnected() { return connected; }
}
