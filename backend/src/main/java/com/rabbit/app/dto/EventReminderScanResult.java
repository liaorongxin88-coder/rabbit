package com.rabbit.app.dto;

public class EventReminderScanResult {
    private int prodLogged;
    private int repLogged;
    private int prodMarked;
    private int repMarked;

    public int getProdLogged() {
        return prodLogged;
    }

    public void setProdLogged(int prodLogged) {
        this.prodLogged = prodLogged;
    }

    public int getRepLogged() {
        return repLogged;
    }

    public void setRepLogged(int repLogged) {
        this.repLogged = repLogged;
    }

    public int getProdMarked() {
        return prodMarked;
    }

    public void setProdMarked(int prodMarked) {
        this.prodMarked = prodMarked;
    }

    public int getRepMarked() {
        return repMarked;
    }

    public void setRepMarked(int repMarked) {
        this.repMarked = repMarked;
    }
}

