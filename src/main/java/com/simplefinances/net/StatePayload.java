package com.simplefinances.net;

import com.simplefinances.SimpleFinances;
import com.simplefinances.economy.Account;
import com.simplefinances.economy.Transaction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Full wallet snapshot sent by the server. Admin-only data is empty for regular players.
 */
public record StatePayload(boolean open, boolean admin, long balance, long blockedUntil, long depositReadyAt,
                           int feePercent, int depositCooldownSeconds,
                           List<TxView> transactions, List<AccountView> blockedAccounts)
        implements CustomPacketPayload {
    public static final Type<StatePayload> TYPE = new Type<>(SimpleFinances.id("state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.open);
                buf.writeBoolean(payload.admin);
                buf.writeVarLong(payload.balance);
                buf.writeLong(payload.blockedUntil);
                buf.writeLong(payload.depositReadyAt);
                buf.writeVarInt(payload.feePercent);
                buf.writeVarInt(payload.depositCooldownSeconds);

                buf.writeVarInt(payload.transactions.size());
                for (TxView tx : payload.transactions) {
                    buf.writeUtf(tx.type, 24);
                    buf.writeLong(tx.time);
                    buf.writeUtf(tx.from, 64);
                    buf.writeUtf(tx.to, 64);
                    buf.writeVarLong(tx.amount);
                    buf.writeVarLong(tx.fee);
                    buf.writeUtf(tx.note, 512);
                }

                buf.writeVarInt(payload.blockedAccounts.size());
                for (AccountView account : payload.blockedAccounts) {
                    buf.writeUtf(account.name, 64);
                    buf.writeVarLong(account.balance);
                    buf.writeLong(account.blockedUntil);
                    buf.writeUtf(account.reason, 256);
                }
            },
            buf -> {
                boolean open = buf.readBoolean();
                boolean admin = buf.readBoolean();
                long balance = buf.readVarLong();
                long blockedUntil = buf.readLong();
                long depositReadyAt = buf.readLong();
                int feePercent = buf.readVarInt();
                int cooldown = buf.readVarInt();

                int txCount = Math.min(buf.readVarInt(), 500);
                List<TxView> transactions = new ArrayList<>(txCount);
                for (int i = 0; i < txCount; i++) {
                    transactions.add(new TxView(
                            buf.readUtf(24),
                            buf.readLong(),
                            buf.readUtf(64),
                            buf.readUtf(64),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readUtf(512)
                    ));
                }

                int accountCount = Math.min(buf.readVarInt(), 500);
                List<AccountView> accounts = new ArrayList<>(accountCount);
                for (int i = 0; i < accountCount; i++) {
                    accounts.add(new AccountView(
                            buf.readUtf(64),
                            buf.readVarLong(),
                            buf.readLong(),
                            buf.readUtf(256)
                    ));
                }

                return new StatePayload(open, admin, balance, blockedUntil, depositReadyAt,
                        feePercent, cooldown, transactions, accounts);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record TxView(String type, long time, String from, String to, long amount, long fee, String note) {
        public static TxView from(Transaction transaction) {
            return new TxView(transaction.type().name(), transaction.time(), transaction.from(),
                    transaction.to(), transaction.amount(), transaction.fee(), transaction.note());
        }
    }

    public record AccountView(String name, long balance, long blockedUntil, String reason) {
        public static AccountView from(Account account) {
            return new AccountView(account.displayName(), account.balance(), account.blockedUntil(), account.blockReason());
        }
    }
}
