package com.simplefinances.economy;

public record Transaction(Type type, long time, String from, String to, long amount, long fee, String note) {
    public enum Type {
        TRANSFER,
        DEPOSIT,
        WITHDRAW,
        COLLECTIBLE,
        CONVERT,
        BLOCK,
        UNBLOCK,
        CONFIG
    }
}
