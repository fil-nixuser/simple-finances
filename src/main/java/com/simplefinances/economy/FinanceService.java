package com.simplefinances.economy;

import com.simplefinances.item.CollectibleCoinItem;
import com.simplefinances.item.CollectibleInfo;
import com.simplefinances.item.CollectibleTier;
import com.simplefinances.net.ActionPayload;
import com.simplefinances.net.CollectionPayload;
import com.simplefinances.net.ModNetwork;
import com.simplefinances.net.NotifyPayload;
import com.simplefinances.net.StatePayload;
import com.simplefinances.registry.ModComponents;
import com.simplefinances.registry.ModItems;
import com.simplefinances.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * All economy operations happen on the server thread. The client only sends requests.
 */
public final class FinanceService {
    public static final long MAX_AMOUNT = 1_000_000L;
    private static final long MAX_BLOCK_MINUTES = 525_600L;
    private static final int COLLECTION_PAGE_SIZE = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private FinanceService() {
    }

    public static void onJoin(ServerPlayer player) {
        FinanceData data = data(player);
        Account account = account(data, player);
        long now = System.currentTimeMillis();

        // Transfers/blocks may have created a ghost account under a differently
        // cased name before this player ever joined. Merge it now.
        data.absorbUnseenNameVariants(account);
        account.markSeen();
        account.clearExpiredBlock(now);
        data.setDirty();

        if (account.isBlocked(now)) {
            ModNetwork.sendToPlayer(player, new NotifyPayload(NotifyPayload.Kind.BLOCKED, 0L,
                    account.blockReason(), account.blockedUntil(), 0));
        }
        sendState(player, false);
    }

    public static void openWallet(ServerPlayer player) {
        sendState(player, true);
        sendCollection(player);
    }

    public static void sendBalance(ServerPlayer player) {
        Account account = account(data(player), player);
        player.sendSystemMessage(Component.translatable("message.simplefinances.balance",
                        Money.format(account.balance()))
                .withStyle(ChatFormatting.GOLD));
    }

    public static void handleAction(ServerPlayer player, ActionPayload payload) {
        switch (payload.action()) {
            case REQUEST_OPEN -> openWallet(player);
            case DEPOSIT -> deposit(player, payload.amount());
            case WITHDRAW -> withdraw(player, payload.amount());
            case TRANSFER -> transfer(player, payload.target(), payload.amount());
            case ADMIN_BLOCK -> block(player, payload.target(), payload.duration(), payload.text());
            case ADMIN_UNBLOCK -> unblock(player, payload.target());
            case ADMIN_COLLECTIBLE -> giveCollectible(player, payload.tier(), payload.text(),
                    payload.target(), payload.flag() == 1);
            case ADMIN_CONFIG -> configure(player, (int) payload.amount(), (int) payload.duration());
            case CONVERT_COLLECTIBLE -> convertCollectible(player, (int) payload.amount());
            case REQUEST_COLLECTION -> sendCollection(player, (int) payload.amount());
        }
    }

    public static void sendCollection(ServerPlayer player) {
        sendCollection(player, 0);
    }

    public static void sendCollection(ServerPlayer player, int page) {
        FinanceData data = data(player);
        int total = data.collectionSize();
        int pageCount = Math.max(1, (total + COLLECTION_PAGE_SIZE - 1) / COLLECTION_PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, pageCount - 1));
        List<CollectionPayload.EntryView> views = data.collectionPage(safePage, COLLECTION_PAGE_SIZE).stream()
                .map(CollectionPayload.EntryView::from)
                .collect(Collectors.toList());
        ModNetwork.sendToPlayer(player, new CollectionPayload(safePage, pageCount, total, views));
    }

    public static void sendState(ServerPlayer player, boolean open) {
        FinanceData data = data(player);
        Account account = account(data, player);
        long now = System.currentTimeMillis();

        if (account.clearExpiredBlock(now)) {
            data.setDirty();
        }

        boolean admin = player.hasPermissions(2);
        List<StatePayload.TxView> transactions = List.of();
        List<StatePayload.AccountView> blocked = List.of();

        if (admin) {
            transactions = data.recentTransactions(150).stream()
                    .map(StatePayload.TxView::from)
                    .collect(Collectors.toList());
            blocked = data.blockedAccounts(now).stream()
                    .limit(200)
                    .map(StatePayload.AccountView::from)
                    .collect(Collectors.toList());
        }

        long depositReadyAt = 0L;
        if (account.lastDepositAt() > 0L && data.depositCooldownSeconds() > 0) {
            depositReadyAt = account.lastDepositAt() + data.depositCooldownSeconds() * 1000L;
        }

        ModNetwork.sendToPlayer(player, new StatePayload(
                open,
                admin,
                account.balance(),
                account.blockedUntil(),
                depositReadyAt,
                data.feePercent(),
                data.depositCooldownSeconds(),
                transactions,
                blocked
        ));
    }

    // ------------------------------------------------------------------
    // Wallet operations
    // ------------------------------------------------------------------

    public static void deposit(ServerPlayer player, long requested) {
        FinanceData data = data(player);
        Account account = account(data, player);
        long now = System.currentTimeMillis();
        account.clearExpiredBlock(now);

        if (account.isBlocked(now)) {
            error(player, "message.simplefinances.error.blocked", formatDate(account.blockedUntil()));
            sendState(player, false);
            return;
        }

        int cooldown = data.depositCooldownSeconds();
        if (cooldown > 0 && account.lastDepositAt() > 0L) {
            long readyAt = account.lastDepositAt() + cooldown * 1000L;
            if (now < readyAt) {
                long seconds = Math.max(1L, (readyAt - now + 999L) / 1000L);
                error(player, "message.simplefinances.error.deposit_cooldown", seconds);
                sendState(player, false);
                return;
            }
        }

        long available = countCash(player.getInventory());
        if (available <= 0L) {
            error(player, "message.simplefinances.error.no_cash");
            sendState(player, false);
            return;
        }

        long amount = requested <= 0L ? available : requested;
        if (amount <= 0L || amount > MAX_AMOUNT) {
            error(player, "message.simplefinances.error.invalid_amount", Money.format(MAX_AMOUNT));
            sendState(player, false);
            return;
        }
        if (amount > available) {
            error(player, "message.simplefinances.error.not_enough_cash", Money.format(available));
            sendState(player, false);
            return;
        }

        consumeCash(player, amount);
        account.setBalance(account.balance() + amount);
        account.setLastDepositAt(now);
        data.addTransaction(new Transaction(Transaction.Type.DEPOSIT, now, account.displayName(), "", amount, 0L, ""));
        data.setDirty();

        player.displayClientMessage(Component.translatable("message.simplefinances.deposited", Money.format(amount))
                .withStyle(ChatFormatting.GREEN), true);
        ModNetwork.sendToPlayer(player, new NotifyPayload(NotifyPayload.Kind.DEPOSIT, amount, "", 0L, 0));
        sendState(player, false);
    }

    public static void withdraw(ServerPlayer player, long amount) {
        FinanceData data = data(player);
        Account account = account(data, player);
        long now = System.currentTimeMillis();
        account.clearExpiredBlock(now);

        if (account.isBlocked(now)) {
            error(player, "message.simplefinances.error.blocked", formatDate(account.blockedUntil()));
            sendState(player, false);
            return;
        }
        if (amount <= 0L || amount > MAX_AMOUNT) {
            error(player, "message.simplefinances.error.invalid_amount", Money.format(MAX_AMOUNT));
            sendState(player, false);
            return;
        }
        if (account.balance() < amount) {
            error(player, "message.simplefinances.error.not_enough_money", Money.format(account.balance()));
            sendState(player, false);
            return;
        }

        account.setBalance(account.balance() - amount);
        giveCash(player, amount);
        data.addTransaction(new Transaction(Transaction.Type.WITHDRAW, now, account.displayName(), "", amount, 0L, ""));
        data.setDirty();

        player.displayClientMessage(Component.translatable("message.simplefinances.withdrawn", Money.format(amount))
                .withStyle(ChatFormatting.GREEN), true);
        sendState(player, false);
    }

    public static void transfer(ServerPlayer player, String targetName, long amount) {
        FinanceData data = data(player);
        Account account = account(data, player);
        long now = System.currentTimeMillis();
        account.clearExpiredBlock(now);

        if (account.isBlocked(now)) {
            error(player, "message.simplefinances.error.blocked", formatDate(account.blockedUntil()));
            sendState(player, false);
            return;
        }

        String target = targetName == null ? "" : targetName.trim();
        if (!OfflineUuids.validName(target)) {
            error(player, "message.simplefinances.error.invalid_player");
            sendState(player, false);
            return;
        }
        if (target.equalsIgnoreCase(account.displayName())) {
            error(player, "message.simplefinances.error.self_transfer");
            sendState(player, false);
            return;
        }
        if (amount <= 0L || amount > MAX_AMOUNT) {
            error(player, "message.simplefinances.error.invalid_amount", Money.format(MAX_AMOUNT));
            sendState(player, false);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        ServerPlayer onlineTarget = server.getPlayerList().getPlayerByName(target);
        Account targetAccount;
        if (onlineTarget != null) {
            targetAccount = data.getOrCreate(onlineTarget.getUUID(), onlineTarget.getGameProfile().getName());
        } else {
            targetAccount = data.findByName(target)
                    .orElseGet(() -> data.getOrCreate(OfflineUuids.of(target), target));
        }

        if (targetAccount.uuid().equals(account.uuid())) {
            error(player, "message.simplefinances.error.self_transfer");
            sendState(player, false);
            return;
        }
        targetAccount.clearExpiredBlock(now);
        if (targetAccount.isBlocked(now)) {
            error(player, "message.simplefinances.error.target_blocked");
            sendState(player, false);
            return;
        }

        long fee = data.feeFor(amount);
        long total = amount + fee;
        if (account.balance() < total) {
            error(player, "message.simplefinances.error.not_enough_money_fee",
                    Money.format(account.balance()), Money.format(total));
            sendState(player, false);
            return;
        }

        account.setBalance(account.balance() - total);
        targetAccount.setBalance(targetAccount.balance() + amount);
        data.addTransaction(new Transaction(Transaction.Type.TRANSFER, now,
                account.displayName(), targetAccount.displayName(), amount, fee, ""));
        data.setDirty();

        player.displayClientMessage(Component.translatable("message.simplefinances.transferred",
                        Money.format(amount), targetAccount.displayName(), Money.format(fee))
                .withStyle(ChatFormatting.GREEN), true);

        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(Component.translatable("message.simplefinances.received",
                            Money.format(amount), account.displayName())
                    .withStyle(ChatFormatting.GOLD));
            ModNetwork.sendToPlayer(onlineTarget,
                    new NotifyPayload(NotifyPayload.Kind.RECEIVED, amount, account.displayName(), 0L, 0));
            sendState(onlineTarget, false);
        }

        sendState(player, false);
    }

    /**
     * Converts one collectible coin from the player's inventory into account money.
     * The coin itself is removed forever and a copy of its award data is added to
     * the shared server collection.
     */
    public static void convertCollectible(ServerPlayer player, int slot) {
        FinanceData data = data(player);
        Account account = account(data, player);
        long now = System.currentTimeMillis();
        account.clearExpiredBlock(now);

        if (account.isBlocked(now)) {
            error(player, "message.simplefinances.error.blocked", formatDate(account.blockedUntil()));
            sendState(player, false);
            return;
        }

        Conversion conversion = applyConversion(data, account, player, slot, now);
        if (conversion == null) {
            error(player, "message.simplefinances.error.no_collectible");
            sendState(player, false);
            return;
        }

        player.displayClientMessage(Component.translatable("message.simplefinances.converted",
                        Money.format(conversion.value()), conversion.tier().displayName())
                .withStyle(ChatFormatting.GREEN), true);
        sendState(player, false);
        sendCollection(player);
    }

    static Conversion applyConversion(FinanceData data, Account account, ServerPlayer player, int slot, long now) {
        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return null;
        }

        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof CollectibleCoinItem coin)) {
            return null;
        }

        CollectibleTier tier = coin.tier();
        CollectibleInfo info = stack.get(ModComponents.COLLECTIBLE_INFO);
        inventory.removeItem(slot, 1);

        long value = tier.conversionValue();
        long newBalance = account.balance() + value;
        if (newBalance < account.balance()) {
            newBalance = Long.MAX_VALUE;
        }
        account.setBalance(newBalance);

        String playerName = player.getGameProfile().getName();
        String description = info == null ? "" : info.description().trim();
        if (description.length() > 200) {
            description = description.substring(0, 200);
        }
        String date = info == null ? "" : info.date().trim();
        if (date.isEmpty()) {
            date = LocalDate.now().format(DATE_FORMAT);
        }
        if (date.length() > 64) {
            date = date.substring(0, 64);
        }
        String recipient = info == null ? "" : info.recipient().trim();
        if (recipient.isEmpty()) {
            recipient = playerName;
        }
        if (recipient.length() > 64) {
            recipient = recipient.substring(0, 64);
        }

        data.addCollectionEntry(new CollectionEntry(tier.id(), description, date, recipient, playerName, now));
        String note = tier.id() + (description.isEmpty() ? "" : ": " + description);
        data.addTransaction(new Transaction(Transaction.Type.CONVERT, now,
                playerName, "", value, 0L, note));
        data.setDirty();

        return new Conversion(tier, value);
    }

    record Conversion(CollectibleTier tier, long value) {
    }

    // ------------------------------------------------------------------
    // Admin operations
    // ------------------------------------------------------------------

    public static void block(ServerPlayer admin, String targetName, long minutes, String reason) {
        if (!requireAdmin(admin)) {
            return;
        }
        FinanceData data = data(admin);
        String target = targetName == null ? "" : targetName.trim();
        if (!OfflineUuids.validName(target)) {
            error(admin, "message.simplefinances.error.invalid_player");
            sendState(admin, false);
            return;
        }

        long duration = Math.max(1L, Math.min(MAX_BLOCK_MINUTES, minutes));
        long now = System.currentTimeMillis();
        long until = now + duration * 60_000L;

        MinecraftServer server = admin.getServer();
        if (server == null) {
            return;
        }

        ServerPlayer online = server.getPlayerList().getPlayerByName(target);
        Account account = online != null
                ? data.getOrCreate(online.getUUID(), online.getGameProfile().getName())
                : data.findByName(target).orElseGet(() -> data.getOrCreate(OfflineUuids.of(target), target));

        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.length() > 100) {
            cleanReason = cleanReason.substring(0, 100);
        }
        account.block(until, cleanReason);
        data.addTransaction(new Transaction(Transaction.Type.BLOCK, now,
                admin.getGameProfile().getName(), account.displayName(), duration, 0L, cleanReason));
        data.setDirty();

        admin.displayClientMessage(Component.translatable("message.simplefinances.blocked_target",
                        account.displayName(), formatDate(until))
                .withStyle(ChatFormatting.YELLOW), true);

        if (online != null) {
            online.sendSystemMessage(Component.translatable("message.simplefinances.blocked_self",
                            formatDate(until))
                    .withStyle(ChatFormatting.RED));
            ModNetwork.sendToPlayer(online,
                    new NotifyPayload(NotifyPayload.Kind.BLOCKED, 0L, cleanReason, until, 0));
            sendState(online, false);
        }

        sendState(admin, false);
    }

    public static void unblock(ServerPlayer admin, String targetName) {
        if (!requireAdmin(admin)) {
            return;
        }
        FinanceData data = data(admin);
        String target = targetName == null ? "" : targetName.trim();
        Account account = data.findByName(target).orElse(null);
        if (account == null) {
            error(admin, "message.simplefinances.error.target_not_found");
            sendState(admin, false);
            return;
        }

        boolean wasBlocked = account.blockedUntil() > 0L;
        account.unblock();
        data.addTransaction(new Transaction(Transaction.Type.UNBLOCK, System.currentTimeMillis(),
                admin.getGameProfile().getName(), account.displayName(), 0L, 0L, ""));
        data.setDirty();

        admin.displayClientMessage(Component.translatable("message.simplefinances.unblocked_target",
                        account.displayName())
                .withStyle(ChatFormatting.YELLOW), true);

        MinecraftServer server = admin.getServer();
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(account.displayName());
            if (online != null) {
                if (wasBlocked) {
                    online.sendSystemMessage(Component.translatable("message.simplefinances.unblocked_self")
                            .withStyle(ChatFormatting.GREEN));
                    ModNetwork.sendToPlayer(online,
                            new NotifyPayload(NotifyPayload.Kind.UNBLOCKED, 0L, "", 0L, 0));
                }
                sendState(online, false);
            }
        }

        sendState(admin, false);
    }

    public static void giveCollectible(ServerPlayer admin, int tierIndex, String description,
                                       String recipientsRaw, boolean all) {
        if (!requireAdmin(admin)) {
            return;
        }
        MinecraftServer server = admin.getServer();
        if (server == null) {
            return;
        }

        CollectibleTier tier = CollectibleTier.byIndex(tierIndex);
        String cleanDescription = description == null ? "" : description.trim();
        if (cleanDescription.isEmpty()) {
            error(admin, "message.simplefinances.error.empty_description");
            sendState(admin, false);
            return;
        }
        if (cleanDescription.length() > 200) {
            cleanDescription = cleanDescription.substring(0, 200);
        }

        List<ServerPlayer> recipients = new ArrayList<>();
        if (all) {
            recipients.addAll(server.getPlayerList().getPlayers());
        } else {
            Set<String> missing = new LinkedHashSet<>();
            for (String raw : (recipientsRaw == null ? "" : recipientsRaw).split(",")) {
                String name = raw.trim();
                if (name.isEmpty()) {
                    continue;
                }
                if (!OfflineUuids.validName(name)) {
                    missing.add(name);
                    continue;
                }
                ServerPlayer online = server.getPlayerList().getPlayerByName(name);
                if (online == null) {
                    missing.add(name);
                } else if (!recipients.contains(online)) {
                    recipients.add(online);
                }
            }
            if (!missing.isEmpty()) {
                error(admin, "message.simplefinances.error.unknown_players", String.join(", ", missing));
                sendState(admin, false);
                return;
            }
        }

        if (recipients.isEmpty()) {
            error(admin, "message.simplefinances.error.no_recipients");
            sendState(admin, false);
            return;
        }

        String date = LocalDate.now().format(DATE_FORMAT);
        String adminName = admin.getGameProfile().getName();
        long now = System.currentTimeMillis();
        FinanceData data = data(admin);

        for (ServerPlayer recipient : recipients) {
            String recipientName = recipient.getGameProfile().getName();
            ItemStack stack = new ItemStack(tier.item());
            stack.set(ModComponents.COLLECTIBLE_INFO,
                    new CollectibleInfo(tier.id(), cleanDescription, date, recipientName));
            recipient.getInventory().placeItemBackInInventory(stack);

            data.addTransaction(new Transaction(Transaction.Type.COLLECTIBLE, now,
                    adminName, recipientName, 0L, 0L, tier.id() + ": " + cleanDescription));

            recipient.sendSystemMessage(Component.translatable("message.simplefinances.collectible_received",
                            tier.displayName())
                    .withStyle(ChatFormatting.GOLD));
            ModNetwork.sendToPlayer(recipient,
                    new NotifyPayload(NotifyPayload.Kind.COLLECTIBLE, 0L, adminName, 0L, tier.ordinal()));
            sendState(recipient, false);
        }

        Component broadcast;
        if (all) {
            broadcast = Component.translatable("message.simplefinances.collectible.broadcast_all",
                    adminName, tier.displayName());
        } else {
            String names = recipients.stream()
                    .map(recipient -> recipient.getGameProfile().getName())
                    .collect(Collectors.joining(", "));
            broadcast = Component.translatable("message.simplefinances.collectible.broadcast",
                    adminName, tier.displayName(), names);
        }
        server.getPlayerList().broadcastSystemMessage(broadcast, false);

        admin.displayClientMessage(Component.translatable("message.simplefinances.collectible_given",
                        recipients.size(), tier.displayName())
                .withStyle(ChatFormatting.GREEN), true);
        sendState(admin, false);
    }

    public static void configure(ServerPlayer admin, int feePercent, int cooldownSeconds) {
        if (!requireAdmin(admin)) {
            return;
        }
        FinanceData data = data(admin);
        data.setFeePercent(feePercent);
        data.setDepositCooldownSeconds(cooldownSeconds);
        data.addTransaction(new Transaction(Transaction.Type.CONFIG, System.currentTimeMillis(),
                admin.getGameProfile().getName(), "", data.feePercent(), 0L,
                "fee=" + data.feePercent() + "%, cooldown=" + data.depositCooldownSeconds() + "s"));

        admin.displayClientMessage(Component.translatable("message.simplefinances.config_saved",
                        data.feePercent(), data.depositCooldownSeconds())
                .withStyle(ChatFormatting.GREEN), true);
        sendState(admin, false);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static FinanceData data(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            throw new IllegalStateException("Player is not on a server");
        }
        return FinanceData.get(server);
    }

    private static Account account(FinanceData data, ServerPlayer player) {
        return data.getOrCreate(player.getUUID(), player.getGameProfile().getName());
    }

    private static boolean requireAdmin(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            error(player, "message.simplefinances.error.no_permission");
            return false;
        }
        return true;
    }

    private static void error(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }

    private static String formatDate(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT));
    }

    public static long countCash(Inventory inventory) {
        long total = 0L;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.COIN_1)) {
                total += stack.getCount();
            } else if (stack.is(ModItems.COIN_50)) {
                total += stack.getCount() * 50L;
            }
        }
        return total;
    }

    private static void consumeCash(ServerPlayer player, long amount) {
        Inventory inventory = player.getInventory();
        long remaining = amount;

        remaining = consumeItem(inventory, ModItems.COIN_1, remaining, 1L);
        remaining = consumeItem(inventory, ModItems.COIN_50, remaining, 50L);

        if (remaining > 0L) {
            int slot = findItem(inventory, ModItems.COIN_50);
            if (slot >= 0) {
                inventory.removeItem(slot, 1);
                long change = 50L - remaining;
                if (change > 0L) {
                    giveCash(player, change);
                }
                remaining = 0L;
            }
        }

        if (remaining > 0L) {
            // Should never happen: amount is checked against countCash before consuming.
            com.simplefinances.SimpleFinances.LOGGER.warn("Failed to consume {} coins from {}", remaining, player.getGameProfile().getName());
        }
    }

    private static long consumeItem(Inventory inventory, Item item, long remaining, long unit) {
        for (int slot = 0; slot < inventory.getContainerSize() && remaining >= unit; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            long canTake = Math.min(stack.getCount(), remaining / unit);
            if (canTake <= 0L) {
                continue;
            }
            stack.shrink((int) canTake);
            remaining -= canTake * unit;
        }
        return remaining;
    }

    private static int findItem(Inventory inventory, Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    public static void giveCash(ServerPlayer player, long amount) {
        long fifties = amount / 50L;
        int ones = (int) (amount % 50L);

        while (fifties > 0L) {
            int count = (int) Math.min(64L, fifties);
            player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.COIN_50, count));
            fifties -= count;
        }
        if (ones > 0) {
            player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.COIN_1, ones));
        }
    }
}
