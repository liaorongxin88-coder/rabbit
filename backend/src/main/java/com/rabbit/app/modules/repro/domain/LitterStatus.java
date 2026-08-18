package com.rabbit.app.modules.repro.domain;

/** {@code litters.status}（设计 §4.3）。 */
public enum LitterStatus {
    NURSING("哺乳中"),
    WEANED("已断奶");

    private final String label;

    LitterStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
