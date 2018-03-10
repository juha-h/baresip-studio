package com.tutpro.baresip;

import java.io.Serializable;
import java.util.GregorianCalendar;

public class History implements Serializable {

    private static final long serialVersionUID = -299482035708790407L;

    private String aor, peer_uri, direction;
    private GregorianCalendar time;

    public History(String aor, String peer_uri, String direction) {
            this.aor = aor;
            this.peer_uri = peer_uri;
            this.direction = direction;
            this.time = new GregorianCalendar();
    }

    public String getAoR() { return aor; }
    public String getPeerURI() { return peer_uri; }
    public String getDirection() { return direction; }
    public GregorianCalendar getTime() { return time; }
}
