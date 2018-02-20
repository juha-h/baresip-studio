package com.tutpro.baresip;

public class Account {

    private String ua, aor, status;
    private int status_image;

    public Account(String ua, String aor) {
        this.ua = ua;
        this.aor = aor;
        this.status = "";
        this.status_image = 0;
    }

    public void setStatusImage(int status_image)  {
        this.status_image = status_image;
    }
    public int getStatusImage() {
        return status_image;
    }

    public String getUA() {
        return ua;
    }
    public String getAoR() {
        return aor;
    }

    public void setStatus(String status) { this.status = status; }
    public String getStatus() {
        return status;
    }

}
