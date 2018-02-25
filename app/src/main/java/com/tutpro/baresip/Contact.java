package com.tutpro.baresip;

public class Contact {

    private String name, uri;

    public Contact(String name, String uri) {
        this.name = name;
        this.uri = uri;
    }

    public String getName() {
            return name;
        }

    public String getURI() {
            return uri;
        }

}
