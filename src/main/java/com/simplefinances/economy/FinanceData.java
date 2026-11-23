package com.simplefinances.economy;

import com.simplefinances.SimpleFinances;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent economy state: accounts, settings and the transaction log.
 */
public final class FinanceData extends SavedData {
    public static final String DATA_NAME = "simplefinances";
    private static final int MAX_TRANSACTIONS = 400;

    public static final SavedData.Factory<FinanceData> FACTORY =
            new SavedData.Factory<>(FinanceData::new, FinanceData::load, DataFixTypes.LEVEL);

    private final Map<UUID, Account> accounts = new HashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<CollectionEntry> collection = new ArrayList<>();
    private int feePercent = 2;
    private int depositCooldownSeconds = 300;

    private FinanceData() {
    }

    public static FinanceData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Account getOrCreate(UUID uuid, String name) {
        Account account = accounts.get(uuid);
        if (account == null) {
            account = new Account(uuid, name);
            accounts.put(uuid, account);
            setDirty();
        } else {
            String previous = account.name();
            account.setName(name);
            if (name != null && !name.equals(previous)) {
                setDirty();
            }
        }
        return account;
    }

    public Account get(UUID uuid) {
        return accounts.get(uuid);
    }

    public Optional<Account> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Account best = null;
        for (Account account : accounts.values()) {
            if (!account.displayName().equalsIgnoreCase(name)) {
                continue;
            }
            if (account.displayName().equals(name)) {
                return Optional.of(account);
            }
            if (best == null || (!best.seen() && account.seen())) {
                best = account;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Offline-mode UUIDs are derived from the exact name spelling. If money was
     * sent to "alice" before the real player "Alice" ever joined, the value is
     * sitting on an unseen ghost account. Merge such accounts into the account
     * of the player who just joined. Accounts of players who have already
     * played are never merged, so two distinct players whose names differ only
     * by case keep separate balances.
     */
    public void absorbUnseenNameVariants(Account target) {
        List<Account> absorbed = new ArrayList<>();
        for (Account account : accounts.values()) {
            if (account == target || account.seen()) {
                continue;
            }
            if (account.displayName().equalsIgnoreCase(target.displayName())) {
                target.absorb(account);
                absorbed.add(account);
            }
        }
        if (absorbed.isEmpty()) {
            return;
        }
        for (Account account : absorbed) {
            accounts.remove(account.uuid());
        }
        setDirty();
    }

    public Collection<Account> allAccounts() {
        return accounts.values();
    }

    public List<Account> sortedAccounts() {
        List<Account> list = new ArrayList<>(accounts.values());
        list.sort(Comparator.comparing(Account::sortKey));
        return list;
    }

    public List<Account> blockedAccounts(long now) {
        List<Account> list = new ArrayList<>();
        for (Account account : accounts.values()) {
            if (account.isBlocked(now)) {
                list.add(account);
            }
        }
        list.sort(Comparator.comparing(Account::sortKey));
        return list;
    }

    public List<Transaction> recentTransactions(int limit) {
        int size = Math.min(limit, transactions.size());
        return new ArrayList<>(transactions.subList(0, size));
    }

    public void addTransaction(Transaction transaction) {
        transactions.add(0, transaction);
        while (transactions.size() > MAX_TRANSACTIONS) {
            transactions.remove(transactions.size() - 1);
        }
        setDirty();
    }

    /**
     * Adds a converted collectible to the server-wide, read-only collection.
     * Newest entries are first.
     */
    public void addCollectionEntry(CollectionEntry entry) {
        collection.add(0, entry);
        setDirty();
    }

    public List<CollectionEntry> collectionPage(int page, int pageSize) {
        if (page < 0 || pageSize <= 0) {
            return List.of();
        }
        int from = page * pageSize;
        if (from >= collection.size()) {
            return List.of();
        }
        int to = Math.min(collection.size(), from + pageSize);
        return new ArrayList<>(collection.subList(from, to));
    }

    public int collectionSize() {
        return collection.size();
    }

    public int feePercent() {
        return feePercent;
    }

    public void setFeePercent(int feePercent) {
        this.feePercent = Math.max(0, Math.min(50, feePercent));
        setDirty();
    }

    public int depositCooldownSeconds() {
        return depositCooldownSeconds;
    }

    public void setDepositCooldownSeconds(int seconds) {
        this.depositCooldownSeconds = Math.max(0, Math.min(86_400, seconds));
        setDirty();
    }

    public long feeFor(long amount) {
        if (feePercent <= 0 || amount <= 0) {
            return 0L;
        }
        return (amount * feePercent + 99L) / 100L;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        tag.putInt("fee_percent", feePercent);
        tag.putInt("deposit_cooldown_seconds", depositCooldownSeconds);

        ListTag accountList = new ListTag();
        for (Account account : accounts.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", account.uuid());
            entry.putString("name", account.displayName());
            entry.putLong("balance", account.balance());
            entry.putLong("blocked_until", account.blockedUntil());
            entry.putString("block_reason", account.blockReason());
            entry.putLong("last_deposit_at", account.lastDepositAt());
            entry.putBoolean("seen", account.seen());
            accountList.add(entry);
        }
        tag.put("accounts", accountList);

        ListTag transactionList = new ListTag();
        for (Transaction transaction : transactions) {
            CompoundTag entry = new CompoundTag();
            entry.putString("type", transaction.type().name());
            entry.putLong("time", transaction.time());
            entry.putString("from", transaction.from());
            entry.putString("to", transaction.to());
            entry.putLong("amount", transaction.amount());
            entry.putLong("fee", transaction.fee());
            entry.putString("note", transaction.note());
            transactionList.add(entry);
        }
        tag.put("transactions", transactionList);

        ListTag collectionList = new ListTag();
        for (CollectionEntry entry : collection) {
            CompoundTag item = new CompoundTag();
            item.putString("tier", entry.tier());
            item.putString("description", entry.description());
            item.putString("date", entry.date());
            item.putString("recipient", entry.recipient());
            item.putString("added_by", entry.addedBy());
            item.putLong("added_at", entry.addedAt());
            collectionList.add(item);
        }
        tag.put("collection", collectionList);

        return tag;
    }

    private static FinanceData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        FinanceData data = new FinanceData();
        data.feePercent = tag.contains("fee_percent") ? tag.getInt("fee_percent") : 2;
        data.depositCooldownSeconds = tag.contains("deposit_cooldown_seconds")
                ? tag.getInt("deposit_cooldown_seconds")
                : 300;

        ListTag accountList = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accountList.size(); i++) {
            CompoundTag entry = accountList.getCompound(i);
            UUID uuid;
            try {
                uuid = entry.getUUID("uuid");
            } catch (Exception exception) {
                SimpleFinances.LOGGER.warn("Skipping account with invalid uuid: {}", entry);
                continue;
            }
            if (uuid == null) {
                SimpleFinances.LOGGER.warn("Skipping account without uuid: {}", entry);
                continue;
            }
            Account account = new Account(uuid, entry.getString("name"));
            account.setBalance(entry.getLong("balance"));
            account.block(entry.getLong("blocked_until"), entry.getString("block_reason"));
            account.setLastDepositAt(entry.getLong("last_deposit_at"));
            // Data written before the seen flag existed belongs to real players.
            account.setSeen(!entry.contains("seen") || entry.getBoolean("seen"));
            data.accounts.put(uuid, account);
        }

        ListTag transactionList = tag.getList("transactions", Tag.TAG_COMPOUND);
        for (int i = 0; i < transactionList.size() && data.transactions.size() < MAX_TRANSACTIONS; i++) {
            CompoundTag entry = transactionList.getCompound(i);
            Transaction.Type type;
            try {
                type = Transaction.Type.valueOf(entry.getString("type").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                continue;
            }
            data.transactions.add(new Transaction(
                    type,
                    entry.getLong("time"),
                    entry.getString("from"),
                    entry.getString("to"),
                    entry.getLong("amount"),
                    entry.getLong("fee"),
                    entry.getString("note")
            ));
        }

        ListTag collectionList = tag.getList("collection", Tag.TAG_COMPOUND);
        for (int i = 0; i < collectionList.size(); i++) {
            CompoundTag entry = collectionList.getCompound(i);
            data.collection.add(new CollectionEntry(
                    entry.getString("tier"),
                    entry.getString("description"),
                    entry.getString("date"),
                    entry.getString("recipient"),
                    entry.getString("added_by"),
                    entry.getLong("added_at")
            ));
        }

        return data;
    }
}
