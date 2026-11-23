package com.simplefinances.economy;

import java.util.Locale;
import java.util.UUID;

public final class Account {
    private final UUID uuid;
    private String name;
    private long balance;
    private long blockedUntil;
    private String blockReason = "";
    private long lastDepositAt;
    private boolean seen;

    public Account(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public long balance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = Math.max(0L, balance);
    }

    public long blockedUntil() {
        return blockedUntil;
    }

    public String blockReason() {
        return blockReason;
    }

    public void block(long until, String reason) {
        this.blockedUntil = until;
        this.blockReason = reason == null ? "" : reason;
    }

    public void unblock() {
        this.blockedUntil = 0L;
        this.blockReason = "";
    }

    public boolean isBlocked(long now) {
        return blockedUntil > now;
    }

    public boolean clearExpiredBlock(long now) {
        if (blockedUntil > 0L && blockedUntil <= now) {
            unblock();
            return true;
        }
        return false;
    }

    public long lastDepositAt() {
        return lastDepositAt;
    }

    public void setLastDepositAt(long lastDepositAt) {
        this.lastDepositAt = lastDepositAt;
    }

    /**
     * True once the real player has joined the server at least once.
     * Accounts created for offline transfer/block targets stay unseen until then.
     */
    public boolean seen() {
        return seen;
    }

    public void markSeen() {
        this.seen = true;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }

    /**
     * Moves value from a ghost account created by a differently-cased name
     * onto the account of the player who has just joined.
     */
    public void absorb(Account other) {
        long sum = this.balance + other.balance;
        this.balance = sum < 0L ? Long.MAX_VALUE : sum;
        if (other.blockedUntil > this.blockedUntil) {
            this.blockedUntil = other.blockedUntil;
            this.blockReason = other.blockReason;
        }
        if (other.lastDepositAt > this.lastDepositAt) {
            this.lastDepositAt = other.lastDepositAt;
        }
    }

    public String displayName() {
        return name == null || name.isBlank() ? uuid.toString() : name;
    }

    public String sortKey() {
        return displayName().toLowerCase(Locale.ROOT);
    }
}
