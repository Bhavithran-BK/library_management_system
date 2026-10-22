package com.library.idgenerator;

public enum IdPrefix {
    BOOK("BK"),
    MEMBER("MEM"),
    LOAN("LN"),
    RESERVATION("RES"),
    TRANSACTION("TXN");

    private final String prefix;

    IdPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
