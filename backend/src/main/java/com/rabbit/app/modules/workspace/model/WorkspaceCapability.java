package com.rabbit.app.modules.workspace.model;

public enum WorkspaceCapability {
    MEMBERS("members"),
    HOUSING("housing"),
    ANIMAL_RECORDS("animal-records"),
    BATCHES("batches"),
    FEED("feed"),
    HEALTH("health"),
    WEIGHT("weight"),
    INVENTORY("inventory"),
    SALES("sales"),
    EVENTS("events"),
    REPORTS("reports"),
    AUDIT("audit");

    private final String code;

    WorkspaceCapability(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
