package com.simplefinances.client.gui;

import com.simplefinances.item.CoinView;
import com.simplefinances.item.CollectibleInfo;
import com.simplefinances.item.CollectibleTier;
import com.simplefinances.net.ActionPayload;
import com.simplefinances.net.CollectionPayload;
import com.simplefinances.net.StatePayload;
import com.simplefinances.util.Money;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Wallet and admin panel. One screen with tabs: the admin panel simply has more tabs.
 * Everything is validated on the server, this screen only sends requests and renders the snapshot.
 */
public final class FinanceScreen extends Screen {
    private static final int MAX_PANEL_W = 480;
    private static final int MAX_PANEL_H = 276;
    private static final int TX_ROW_H = 22;
    private static final int BLOCK_ROW_H = 32;
    private static final int CONVERT_ROW_H = 28;
    private static final int COLLECTION_CELL = 24;
    private static final long[] PRESET_MINUTES = {60L, 1440L, 10080L, 43200L};
    private static final String[] PRESET_KEYS = {
            "screen.simplefinances.blocks.hour",
            "screen.simplefinances.blocks.day",
            "screen.simplefinances.blocks.week",
            "screen.simplefinances.blocks.month"
    };
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private StatePayload state;
    private Tab tab = Tab.WALLET;
    private CollectionPayload collection = new CollectionPayload(0, 1, 0, List.of());
    private int convertScroll = 0;
    private int selectedConvertSlot = -1;
    private Button convertButton;
    private int collectionScroll = 0;
    private Button prevPageButton;
    private Button nextPageButton;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private int tabColumns = 1;
    private int tabRows = 1;
    private int tabWidthPx = 0;

    private EditBox depositAmount;
    private EditBox withdrawAmount;
    private EditBox transferName;
    private EditBox transferAmount;

    private EditBox collectibleDescription;
    private EditBox collectibleRecipients;
    private Button recipientsToggle;
    private int selectedTier = 0;
    private boolean allRecipients = false;

    private EditBox blockName;
    private EditBox blockDuration;
    private EditBox blockReason;
    private int blockScroll = 0;
    private int presetIndex = 0;

    private EditBox feeField;
    private EditBox cooldownField;

    private int txScroll = 0;
    private long now = System.currentTimeMillis();

    public FinanceScreen(StatePayload state) {
        super(Component.translatable(state.admin()
                ? "screen.simplefinances.admin"
                : "screen.simplefinances.wallet"));
        this.state = state;
    }

    public static void handleState(StatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FinanceScreen screen) {
            screen.updateState(payload);
        } else if (payload.open()) {
            minecraft.setScreen(new FinanceScreen(payload));
        }
    }

    public static void handleCollection(CollectionPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FinanceScreen screen) {
            if (screen.collection.page() != payload.page()) {
                screen.collectionScroll = 0;
            }
            screen.collection = payload;
            screen.updatePageButtons();
        }
    }

    public void updateState(StatePayload newState) {
        boolean wasAdmin = state != null && state.admin();
        this.state = newState;

        if (!newState.admin() && tab.adminOnly) {
            tab = Tab.WALLET;
            rebuildWidgets();
            return;
        }
        if (wasAdmin != newState.admin()) {
            rebuildWidgets();
            return;
        }
        if (feeField != null && !feeField.isFocused()) {
            feeField.setValue(Integer.toString(newState.feePercent()));
        }
        if (cooldownField != null && !cooldownField.isFocused()) {
            cooldownField.setValue(Integer.toString(newState.depositCooldownSeconds()));
        }
    }

    @Override
    protected void init() {
        super.init();

        panelW = Math.min(MAX_PANEL_W, width - 12);
        panelH = Math.min(MAX_PANEL_H, height - 12);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        addTabs();

        switch (tab) {
            case WALLET -> initWallet();
            case CONVERSION -> initConversion();
            case COLLECTION -> initCollection();
            case TRANSACTIONS -> initTransactions();
            case COLLECTIBLES -> initCollectibles();
            case BLOCKS -> initBlocks();
            case SETTINGS -> initSettings();
        }

        addButton(panelX + panelW - 92, panelY + panelH - 26, 80, 18,
                Component.translatable("button.simplefinances.close"), button -> onClose());
    }

    // ------------------------------------------------------------------
    // Widget setup
    // ------------------------------------------------------------------

    private void addTabs() {
        List<Tab> tabs = visibleTabs();
        int gap = 4;

        // Localized (especially Russian) tab names are wider than English ones.
        // Pick as many columns as can show the longest label in full, then wrap
        // the remaining tabs onto extra rows instead of letting labels overlap.
        int widest = 0;
        for (Tab value : tabs) {
            widest = Math.max(widest, font.width(Component.translatable(value.key).getString()));
        }
        int needed = widest + 8;
        int maxPerRow = Math.max(1, (panelW - 24 + gap) / Math.max(1, needed));
        int rows = (tabs.size() + maxPerRow - 1) / maxPerRow;
        tabColumns = (tabs.size() + rows - 1) / rows;
        tabRows = rows;
        int tabW = (panelW - 24 - (tabColumns - 1) * gap) / tabColumns;
        tabWidthPx = tabW;

        for (int i = 0; i < tabs.size(); i++) {
            Tab value = tabs.get(i);
            int x = panelX + 12 + (i % tabColumns) * (tabW + gap);
            int y = panelY + 28 + (i / tabColumns) * 20;
            Button button = addButton(x, y, tabW, 18,
                    Component.translatable(value.key), b -> {
                        if (tab != value) {
                            tab = value;
                            rebuildWidgets();
                        }
                    });
            button.active = value != tab;
        }
    }

    private void initWallet() {
        int x = panelX + 16;
        int y = contentTop();

        depositAmount = addEditBox(x, y + 41, 110, 18, "wallet.simplefinances.amount", 9, DIGITS);
        addButton(x + 116, y + 41, 44, 18, Component.translatable("wallet.simplefinances.all"),
                button -> send(ActionPayload.deposit(0L)));
        addButton(x + 166, y + 41, 84, 18, Component.translatable("wallet.simplefinances.deposit_button"),
                button -> {
                    send(ActionPayload.deposit(parseLong(depositAmount.getValue())));
                    depositAmount.setValue("");
                });

        withdrawAmount = addEditBox(x, y + 76, 110, 18, "wallet.simplefinances.amount", 9, DIGITS);
        addButton(x + 116, y + 76, 44, 18, Component.translatable("wallet.simplefinances.all"),
                button -> send(ActionPayload.withdraw(state.balance())));
        addButton(x + 166, y + 76, 84, 18, Component.translatable("wallet.simplefinances.withdraw_button"),
                button -> {
                    send(ActionPayload.withdraw(parseLong(withdrawAmount.getValue())));
                    withdrawAmount.setValue("");
                });

        transferName = addEditBox(x, y + 111, 110, 18, "wallet.simplefinances.player", 16, PLAYER_NAME);
        transferAmount = addEditBox(x + 116, y + 111, 70, 18, "wallet.simplefinances.amount", 9, DIGITS);
        addButton(x + 192, y + 111, 90, 18, Component.translatable("wallet.simplefinances.transfer_button"),
                button -> {
                    send(ActionPayload.transfer(transferName.getValue(),
                            parseLong(transferAmount.getValue())));
                    transferAmount.setValue("");
                });
    }

    private void initConversion() {
        convertButton = addButton(panelX + 16, panelY + panelH - 26, 150, 18,
                Component.translatable("screen.simplefinances.conversion.convert"), button -> {
                    if (selectedConvertSlot >= 0) {
                        send(ActionPayload.convertCollectible(selectedConvertSlot));
                        selectedConvertSlot = -1;
                    }
                });
        addButton(panelX + 172, panelY + panelH - 26, 110, 18,
                Component.translatable("screen.simplefinances.conversion.convert_all"), button -> {
                    send(ActionPayload.convertAllCollectibles());
                    selectedConvertSlot = -1;
                });
        convertButton.active = selectedConvertSlot >= 0;
    }

    private void initCollection() {
        prevPageButton = addButton(panelX + 16, panelY + panelH - 26, 28, 18,
                Component.literal("<"), button ->
                        send(ActionPayload.requestCollection(collection.page() - 1)));
        nextPageButton = addButton(panelX + 48, panelY + panelH - 26, 28, 18,
                Component.literal(">"), button ->
                        send(ActionPayload.requestCollection(collection.page() + 1)));
        addButton(panelX + 80, panelY + panelH - 26, 100, 18,
                Component.translatable("screen.simplefinances.collection.refresh"),
                button -> send(ActionPayload.requestCollection(collection.page())));
        updatePageButtons();
    }

    private void updatePageButtons() {
        if (prevPageButton != null) {
            prevPageButton.active = collection.page() > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = collection.page() + 1 < collection.pageCount();
        }
    }

    private void initTransactions() {
        // The list is drawn manually; there are no widgets besides the tabs and the close button.
    }

    private void initCollectibles() {
        int x = panelX + 16;
        int y = contentTop();
        int totalW = panelW - 32;
        int tierW = (totalW - 8) / 3;

        for (int i = 0; i < CollectibleTier.values().length; i++) {
            final int index = i;
            addButton(x + i * (tierW + 4), y + 12, tierW, 20,
                    CollectibleTier.values()[i].displayName(), button -> selectedTier = index);
        }

        collectibleDescription = addEditBox(x, y + 49, totalW - 92, 18,
                "screen.simplefinances.collectibles.description", 200, ANY);
        addButton(x + totalW - 86, y + 49, 86, 18,
                Component.translatable("screen.simplefinances.collectibles.today"), button -> {
                    String date = LocalDate.now().format(DATE_FORMAT);
                    String current = collectibleDescription.getValue();
                    String updated = current.isBlank() ? date : current + " " + date;
                    if (updated.length() > 200) {
                        updated = updated.substring(0, 200);
                    }
                    collectibleDescription.setValue(updated);
                });

        collectibleRecipients = addEditBox(x, y + 84, totalW - 116, 18,
                "screen.simplefinances.collectibles.recipients", 200, RECIPIENTS);
        recipientsToggle = addButton(x + totalW - 110, y + 84, 110, 18,
                Component.translatable(allRecipients
                        ? "screen.simplefinances.collectibles.all_players"
                        : "screen.simplefinances.collectibles.list"), button -> {
                    allRecipients = !allRecipients;
                    button.setMessage(Component.translatable(allRecipients
                            ? "screen.simplefinances.collectibles.all_players"
                            : "screen.simplefinances.collectibles.list"));
                    collectibleRecipients.setEditable(!allRecipients);
                });
        collectibleRecipients.setEditable(!allRecipients);

        addButton(x, y + 110, 170, 18,
                Component.translatable("screen.simplefinances.collectibles.create"), button ->
                        send(ActionPayload.adminCollectible(selectedTier,
                                collectibleDescription.getValue(),
                                collectibleRecipients.getValue(),
                                allRecipients)));
    }

    private void initBlocks() {
        int x = panelX + 16;
        int y = contentTop();

        blockName = addEditBox(x, y + 11, 150, 18, "screen.simplefinances.blocks.player", 16, PLAYER_NAME);
        blockDuration = addEditBox(x, y + 47, 60, 18, "screen.simplefinances.blocks.duration", 6, DIGITS);
        blockDuration.setValue(Long.toString(PRESET_MINUTES[presetIndex]));
        addButton(x + 66, y + 47, 84, 18, Component.translatable(PRESET_KEYS[presetIndex]), button -> {
            presetIndex = (presetIndex + 1) % PRESET_MINUTES.length;
            blockDuration.setValue(Long.toString(PRESET_MINUTES[presetIndex]));
            button.setMessage(Component.translatable(PRESET_KEYS[presetIndex]));
        });
        blockReason = addEditBox(x, y + 83, 150, 18, "screen.simplefinances.blocks.reason", 100, ANY);
        addButton(x, y + 110, 150, 18,
                Component.translatable("screen.simplefinances.blocks.block"), button ->
                        send(ActionPayload.adminBlock(blockName.getValue(),
                                parseLong(blockDuration.getValue()),
                                blockReason.getValue())));
    }

    private void initSettings() {
        int x = panelX + 16;
        int y = contentTop();

        feeField = addEditBox(x, y + 12, 60, 18, "screen.simplefinances.settings.fee", 2, DIGITS);
        feeField.setValue(Integer.toString(state.feePercent()));
        cooldownField = addEditBox(x, y + 50, 80, 18, "screen.simplefinances.settings.cooldown", 5, DIGITS);
        cooldownField.setValue(Integer.toString(state.depositCooldownSeconds()));

        addButton(x, y + 78, 120, 18,
                Component.translatable("screen.simplefinances.settings.save"), button ->
                        send(ActionPayload.adminConfig(parseInt(feeField.getValue()),
                                parseInt(cooldownField.getValue()))));
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xE6121212);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 24, 0xFF1E1E1E);
        graphics.hLine(panelX, panelX + panelW, panelY + 24, 0xFF4A4A4A);
        graphics.renderOutline(panelX - 2, panelY - 2, panelW + 4, panelH + 4, 0xFF4A4A4A);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        now = System.currentTimeMillis();
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font, title, panelX + 12, panelY + 8, 0xFFFFFFFF, false);

        int tabIndex = visibleTabs().indexOf(tab);
        if (tabIndex >= 0 && tabColumns > 0) {
            int x = panelX + 12 + (tabIndex % tabColumns) * (tabWidthPx + 4);
            int underlineY = panelY + 46 + (tabIndex / tabColumns) * 20;
            graphics.fill(x, underlineY, x + tabWidthPx, underlineY + 2, 0xFFFFD700);
        }

        switch (tab) {
            case WALLET -> renderWallet(graphics);
            case CONVERSION -> renderConversion(graphics, mouseX, mouseY);
            case COLLECTION -> renderCollection(graphics, mouseX, mouseY);
            case TRANSACTIONS -> renderTransactions(graphics);
            case COLLECTIBLES -> renderCollectibles(graphics);
            case BLOCKS -> renderBlocks(graphics);
            case SETTINGS -> renderSettings(graphics);
        }
    }

    private void renderWallet(GuiGraphics graphics) {
        int x = panelX + 16;
        int y = contentTop();

        graphics.drawString(font, Component.translatable("wallet.simplefinances.balance",
                Money.format(state.balance())), x, y, 0xFFFFD700, false);

        if (state.blockedUntil() > now) {
            graphics.drawString(font, Component.translatable("wallet.simplefinances.blocked",
                    formatDateTime(state.blockedUntil())), x, y + 12, 0xFFFF5555, false);
        } else if (state.depositReadyAt() > now) {
            graphics.drawString(font, Component.translatable("wallet.simplefinances.deposit_ready",
                    formatDuration(state.depositReadyAt() - now)), x, y + 12, 0xFFAAAAAA, false);
        }

        graphics.drawString(font, Component.translatable("wallet.simplefinances.deposit"),
                x, y + 30, 0xFFCCCCCC, false);
        graphics.drawString(font, Component.translatable("wallet.simplefinances.withdraw"),
                x, y + 65, 0xFFCCCCCC, false);
        graphics.drawString(font, Component.translatable("wallet.simplefinances.transfer"),
                x, y + 100, 0xFFCCCCCC, false);

        graphics.drawString(font, Component.translatable("wallet.simplefinances.fee",
                state.feePercent()), x, y + 135, 0xFFAAAAAA, false);

        int hintY = drawWrapped(graphics, Component.translatable("wallet.simplefinances.hint_cash"),
                x, y + 149, panelW - 32, 0xFF777777);
        drawWrapped(graphics, Component.translatable("wallet.simplefinances.hint_withdraw"),
                x, hintY + 2, panelW - 32, 0xFF777777);
    }

    private void renderConversion(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelX + 16;
        int y = contentTop();
        int totalW = panelW - 32;

        graphics.drawString(font, Component.translatable("screen.simplefinances.conversion.title"),
                x, y, 0xFFFFD700, false);
        drawWrapped(graphics, Component.translatable("screen.simplefinances.conversion.hint"),
                x, y + 12, totalW, 0xFF777777);

        List<OwnedCoin> owned = ownedCollectibles();
        if (selectedConvertSlot >= 0) {
            boolean found = false;
            for (OwnedCoin collectible : owned) {
                if (collectible.slot() == selectedConvertSlot) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                selectedConvertSlot = -1;
            }
        }
        if (convertButton != null) {
            convertButton.active = selectedConvertSlot >= 0;
        }

        int top = conversionTop();
        int bottom = conversionBottom();

        if (owned.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.simplefinances.conversion.empty"),
                    panelX + panelW / 2, top + 12, 0xFF888888);
            return;
        }

        int rows = Math.max(1, (bottom - top) / CONVERT_ROW_H);
        int maxScroll = Math.max(0, owned.size() - rows);
        convertScroll = Mth.clamp(convertScroll, 0, maxScroll);

        OwnedCoin hovered = null;
        graphics.enableScissor(x, top, x + totalW, bottom);
        for (int row = 0; row < rows; row++) {
            int index = convertScroll + row;
            if (index >= owned.size()) {
                break;
            }
            OwnedCoin collectible = owned.get(index);
            int rowY = top + row * CONVERT_ROW_H;
            boolean selected = collectible.slot() == selectedConvertSlot;

            graphics.fill(x, rowY, x + totalW, rowY + CONVERT_ROW_H - 2,
                    selected ? 0x553377CC : (row % 2 == 0 ? 0x20FFFFFF : 0x10FFFFFF));
            if (selected) {
                graphics.renderOutline(x, rowY, totalW, CONVERT_ROW_H - 2, 0xFF55AAFF);
            }

            graphics.renderFakeItem(collectible.view().icon(), x + 4, rowY + 5);
            graphics.drawString(font, collectible.view().name().getString(),
                    x + 24, rowY + 3, 0xFFFFFFFF, false);

            String description = collectible.view().info() != null ? collectible.view().info().description() : "";
            if (description.isBlank()) {
                description = Component.translatable("screen.simplefinances.collection.no_description").getString();
            }
            graphics.drawString(font, trim(description, totalW - 150), x + 24, rowY + 14, 0xFFAAAAAA, false);

            String value = "+" + Money.format(collectible.view().value());
            graphics.drawString(font, value, x + totalW - font.width(value) - 6, rowY + 3,
                    0xFFFFD700, false);

            String date = collectible.view().info() != null ? collectible.view().info().date() : "";
            if (!date.isBlank()) {
                String trimmed = trim(date, 80);
                graphics.drawString(font, trimmed, x + totalW - font.width(trimmed) - 6,
                        rowY + 14, 0xFF888888, false);
            }

            if (mouseX >= x && mouseX <= x + totalW && mouseY >= rowY && mouseY < rowY + CONVERT_ROW_H - 2) {
                hovered = collectible;
            }
        }
        graphics.disableScissor();

        drawScrollbar(graphics, panelX + panelW - 14, top, bottom, convertScroll, maxScroll,
                rows, owned.size());

        if (selectedConvertSlot >= 0) {
            for (OwnedCoin collectible : owned) {
                if (collectible.slot() == selectedConvertSlot) {
                    graphics.drawString(font, Component.translatable("screen.simplefinances.conversion.selected",
                                    collectible.view().name(),
                                    Money.format(collectible.view().value())),
                            x + 160, panelY + panelH - 21, 0xFFAAAAAA, false);
                    break;
                }
            }
        }

        if (hovered != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(hovered.view().name());
            CollectibleInfo info = hovered.view().info();
            if (info != null) {
                if (!info.description().isBlank()) {
                    tooltip.add(Component.translatable("tooltip.simplefinances.collectible.description",
                            info.description()));
                }
                if (!info.date().isBlank()) {
                    tooltip.add(Component.translatable("tooltip.simplefinances.collectible.date",
                            info.date()));
                }
                if (!info.recipient().isBlank()) {
                    tooltip.add(Component.translatable("tooltip.simplefinances.collectible.recipient",
                            info.recipient()));
                }
            } else {
                tooltip.add(Component.translatable("tooltip.simplefinances.collectible.blank"));
            }
            tooltip.add(Component.translatable("screen.simplefinances.conversion.value",
                    Money.format(hovered.view().value())));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void renderCollection(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelX + 16;
        int y = contentTop();
        int totalW = panelW - 32;

        graphics.drawString(font, Component.translatable("screen.simplefinances.collection.title",
                collection.total()), x, y, 0xFFFFD700, false);
        graphics.drawString(font, Component.translatable("screen.simplefinances.collection.hint"),
                x, y + 12, 0xFF777777, false);
        updatePageButtons();

        int top = collectionTop();
        int bottom = collectionBottom();
        List<CollectionPayload.EntryView> entries = collection.entries();

        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.simplefinances.collection.empty"),
                    panelX + panelW / 2, top + 12, 0xFF888888);
            return;
        }

        int cols = Math.max(1, totalW / COLLECTION_CELL);
        int rows = Math.max(1, (bottom - top) / COLLECTION_CELL);
        int totalRows = (entries.size() + cols - 1) / cols;
        int maxScroll = Math.max(0, totalRows - rows);
        collectionScroll = Mth.clamp(collectionScroll, 0, maxScroll);

        CollectionPayload.EntryView hovered = null;
        int start = collectionScroll * cols;

        graphics.enableScissor(x, top, x + totalW, bottom);
        for (int i = 0; i < cols * rows; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }
            int cellX = x + (i % cols) * COLLECTION_CELL;
            int cellY = top + (i / cols) * COLLECTION_CELL;
            CollectionPayload.EntryView entry = entries.get(index);
            CoinView view = CoinView.fromAlbumKey(entry.tier());

            graphics.fill(cellX + 1, cellY + 1, cellX + COLLECTION_CELL - 1,
                    cellY + COLLECTION_CELL - 1, 0x20FFFFFF);
            graphics.renderFakeItem(view.icon(), cellX + 4, cellY + 4);

            if (mouseX >= cellX && mouseX < cellX + COLLECTION_CELL
                    && mouseY >= cellY && mouseY < cellY + COLLECTION_CELL) {
                hovered = entry;
            }
        }
        graphics.disableScissor();

        drawScrollbar(graphics, panelX + panelW - 14, top, bottom, collectionScroll, maxScroll,
                rows, totalRows);

        graphics.drawString(font, Component.translatable("screen.simplefinances.collection.page",
                        collection.page() + 1, collection.pageCount()),
                panelX + 190, panelY + panelH - 21, 0xFFAAAAAA, false);

        if (hovered != null) {
            CoinView view = CoinView.fromAlbumKey(hovered.tier());
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(view.name());
            tooltip.add(Component.translatable("screen.simplefinances.collection.added_by", hovered.addedBy()));
            tooltip.add(Component.translatable("screen.simplefinances.collection.description",
                    hovered.description().isBlank()
                            ? Component.translatable("screen.simplefinances.collection.no_description")
                            : hovered.description()));
            tooltip.add(Component.translatable("screen.simplefinances.collection.date", hovered.date()));
            if (!hovered.recipient().isBlank()) {
                tooltip.add(Component.translatable("screen.simplefinances.collection.recipient",
                        hovered.recipient()));
            }
            tooltip.add(Component.translatable("screen.simplefinances.collection.added_at",
                    formatDateTime(hovered.addedAt())));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void renderTransactions(GuiGraphics graphics) {
        int x = panelX + 16;
        int top = contentTop() + 4;
        int bottom = panelY + panelH - 30;
        int width = panelW - 32;

        if (state.transactions().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.simplefinances.transactions.empty"),
                    panelX + panelW / 2, top + 10, 0xFF888888);
            return;
        }

        int rows = Math.max(1, (bottom - top) / TX_ROW_H);
        int maxScroll = Math.max(0, state.transactions().size() - rows);
        txScroll = Mth.clamp(txScroll, 0, maxScroll);

        graphics.enableScissor(x, top, x + width, bottom);
        for (int row = 0; row < rows; row++) {
            int index = txScroll + row;
            if (index >= state.transactions().size()) {
                break;
            }
            StatePayload.TxView tx = state.transactions().get(index);
            int rowY = top + row * TX_ROW_H;

            String type = Component.translatable("transaction.simplefinances."
                    + tx.type().toLowerCase(Locale.ROOT)).getString();
            graphics.drawString(font, type, x, rowY + 1, txColor(tx.type()), false);

            String time = TIME_FORMAT.format(Instant.ofEpochMilli(tx.time()).atZone(ZoneId.systemDefault()));
            graphics.drawString(font, time, x + width - font.width(time), rowY + 1, 0xFF888888, false);

            graphics.drawString(font, trim(describe(tx), width), x, rowY + 11, 0xFFCCCCCC, false);
        }
        graphics.disableScissor();

        drawScrollbar(graphics, panelX + panelW - 14, top, bottom, txScroll, maxScroll,
                rows, state.transactions().size());

        graphics.drawString(font, Component.translatable("screen.simplefinances.transactions.hint"),
                x, bottom + 4, 0xFF666666, false);
    }

    private void renderCollectibles(GuiGraphics graphics) {
        int x = panelX + 16;
        int y = contentTop();
        int totalW = panelW - 32;
        int tierW = (totalW - 8) / 3;

        graphics.drawString(font, Component.translatable("screen.simplefinances.collectibles.tier"),
                x, y, 0xFFCCCCCC, false);

        for (int i = 0; i < CollectibleTier.values().length; i++) {
            int bx = x + i * (tierW + 4);
            if (i == selectedTier) {
                graphics.renderOutline(bx, y + 12, tierW, 20, 0xFFFFD700);
            }
        }

        graphics.drawString(font, Component.translatable("screen.simplefinances.collectibles.description"),
                x, y + 38, 0xFFCCCCCC, false);
        graphics.drawString(font, Component.translatable("screen.simplefinances.collectibles.recipients"),
                x, y + 73, 0xFFCCCCCC, false);

        drawWrapped(graphics, Component.translatable("screen.simplefinances.collectibles.hint"),
                x, y + 134, totalW, 0xFF777777);
    }

    private void renderBlocks(GuiGraphics graphics) {
        int x = panelX + 16;
        int y = contentTop();

        graphics.drawString(font, Component.translatable("screen.simplefinances.blocks.player"),
                x, y, 0xFFCCCCCC, false);
        graphics.drawString(font, Component.translatable("screen.simplefinances.blocks.duration"),
                x, y + 36, 0xFFCCCCCC, false);
        graphics.drawString(font, Component.translatable("screen.simplefinances.blocks.reason"),
                x, y + 72, 0xFFCCCCCC, false);

        int rightX = blockListX();
        int rightW = blockListW();
        int top = blockListTop();
        int bottom = blockListBottom();

        graphics.drawString(font, Component.translatable("screen.simplefinances.blocks.list"),
                rightX, y, 0xFFCCCCCC, false);

        List<StatePayload.AccountView> accounts = state.blockedAccounts();
        if (accounts.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.simplefinances.blocks.empty"),
                    rightX, top + 4, 0xFF888888, false);
            return;
        }

        int rows = Math.max(1, (bottom - top) / BLOCK_ROW_H);
        int maxScroll = Math.max(0, accounts.size() - rows);
        blockScroll = Mth.clamp(blockScroll, 0, maxScroll);

        graphics.enableScissor(rightX, top, rightX + rightW, bottom);
        for (int row = 0; row < rows; row++) {
            int index = blockScroll + row;
            if (index >= accounts.size()) {
                break;
            }
            StatePayload.AccountView account = accounts.get(index);
            int rowY = top + row * BLOCK_ROW_H;

            graphics.fill(rightX, rowY, rightX + rightW, rowY + BLOCK_ROW_H - 2, 0x30FFFFFF);
            graphics.drawString(font, trim(account.name(), rightW - 22), rightX + 4, rowY + 2,
                    0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("screen.simplefinances.blocks.until",
                            formatDateTime(account.blockedUntil())),
                    rightX + 4, rowY + 11, 0xFFFFAA55, false);
            if (!account.reason().isBlank()) {
                graphics.drawString(font, trim(Component.translatable("screen.simplefinances.blocks.reason_short",
                                account.reason()).getString(), rightW - 22),
                        rightX + 4, rowY + 20, 0xFF999999, false);
            }
            graphics.drawString(font, "×", rightX + rightW - 12, rowY + 4, 0xFFFF5555, false);
        }
        graphics.disableScissor();

        drawScrollbar(graphics, rightX + rightW - 4, top, bottom, blockScroll, maxScroll,
                rows, accounts.size());
    }

    private void renderSettings(GuiGraphics graphics) {
        int x = panelX + 16;
        int y = contentTop();

        graphics.drawString(font, Component.translatable("screen.simplefinances.settings.fee"),
                x, y, 0xFFCCCCCC, false);
        graphics.drawString(font, Component.translatable("screen.simplefinances.settings.cooldown"),
                x, y + 38, 0xFFCCCCCC, false);

        drawWrapped(graphics, Component.translatable("screen.simplefinances.settings.hint"),
                x, y + 104, panelW - 32, 0xFF777777);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.CONVERSION) {
            int rows = Math.max(1, (conversionBottom() - conversionTop()) / CONVERT_ROW_H);
            int maxScroll = Math.max(0, ownedCollectibles().size() - rows);
            convertScroll = Mth.clamp(convertScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        if (tab == Tab.COLLECTION && !collection.entries().isEmpty()) {
            int totalW = panelW - 32;
            int cols = Math.max(1, totalW / COLLECTION_CELL);
            int rows = Math.max(1, (collectionBottom() - collectionTop()) / COLLECTION_CELL);
            int totalRows = (collection.entries().size() + cols - 1) / cols;
            int maxScroll = Math.max(0, totalRows - rows);
            collectionScroll = Mth.clamp(collectionScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        if (tab == Tab.TRANSACTIONS && !state.transactions().isEmpty()) {
            int top = contentTop() + 4;
            int bottom = panelY + panelH - 30;
            int rows = Math.max(1, (bottom - top) / TX_ROW_H);
            int maxScroll = Math.max(0, state.transactions().size() - rows);
            txScroll = Mth.clamp(txScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        if (tab == Tab.BLOCKS && !state.blockedAccounts().isEmpty()) {
            int rows = Math.max(1, (blockListBottom() - blockListTop()) / BLOCK_ROW_H);
            int maxScroll = Math.max(0, state.blockedAccounts().size() - rows);
            blockScroll = Mth.clamp(blockScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && tab == Tab.CONVERSION) {
            int x = panelX + 16;
            int totalW = panelW - 32;
            int top = conversionTop();
            int bottom = conversionBottom();
            if (mouseX >= x && mouseX <= x + totalW && mouseY >= top && mouseY < bottom) {
                List<OwnedCoin> owned = ownedCollectibles();
                int row = (int) ((mouseY - top) / CONVERT_ROW_H) + convertScroll;
                if (row >= 0 && row < owned.size()) {
                    selectedConvertSlot = owned.get(row).slot();
                    return true;
                }
            }
        }
        if (button == 0 && tab == Tab.BLOCKS && !state.blockedAccounts().isEmpty()) {
            int rightX = blockListX();
            int rightW = blockListW();
            int top = blockListTop();
            int bottom = blockListBottom();
            if (mouseX >= rightX + rightW - 18 && mouseX <= rightX + rightW
                    && mouseY >= top && mouseY <= bottom) {
                int row = (int) ((mouseY - top) / BLOCK_ROW_H) + blockScroll;
                if (row >= 0 && row < state.blockedAccounts().size()) {
                    ClientPlayNetworking.send(ActionPayload.adminUnblock(
                            state.blockedAccounts().get(row).name()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------
    // Layout helpers
    // ------------------------------------------------------------------

    private int contentTop() {
        return panelY + 24 + tabRows * 20 + 8;
    }

    private List<Tab> visibleTabs() {
        List<Tab> tabs = new ArrayList<>();
        for (Tab value : Tab.values()) {
            if (!value.adminOnly || state.admin()) {
                tabs.add(value);
            }
        }
        return tabs;
    }

    private int conversionTop() {
        return contentTop() + 34;
    }

    private int conversionBottom() {
        return panelY + panelH - 32;
    }

    private int collectionTop() {
        return contentTop() + 26;
    }

    private int collectionBottom() {
        return panelY + panelH - 32;
    }

    private List<OwnedCoin> ownedCollectibles() {
        List<OwnedCoin> result = new ArrayList<>();
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return result;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            CoinView view = CoinView.of(inventory.getItem(slot));
            if (view != null) {
                result.add(new OwnedCoin(slot, view));
            }
        }
        return result;
    }

    private int blockListX() {
        return panelX + 186;
    }

    private int blockListW() {
        return panelW - 202;
    }

    private int blockListTop() {
        return contentTop() + 14;
    }

    private int blockListBottom() {
        return panelY + panelH - 30;
    }

    private EditBox addEditBox(int x, int y, int width, int height, String hintKey,
                               int maxLength, Predicate<String> filter) {
        EditBox box = new EditBox(font, x, y, width, height, Component.translatable(hintKey));
        box.setMaxLength(maxLength);
        box.setFilter(filter);
        box.setHint(Component.translatable(hintKey).withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(box);
        return box;
    }

    private Button addButton(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        return addRenderableWidget(Button.builder(label, onPress).bounds(x, y, width, height).build());
    }

    private void send(ActionPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    // ------------------------------------------------------------------
    // Small utilities
    // ------------------------------------------------------------------

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private Component formatDuration(long millis) {
        if (millis <= 0L) {
            return Component.translatable("time.simplefinances.now");
        }
        long seconds = (millis + 999L) / 1000L;
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long secs = seconds % 60L;

        MutableComponent result = Component.empty();
        if (days > 0) {
            result.append(Component.translatable("time.simplefinances.day", days)).append(" ");
        }
        if (hours > 0) {
            result.append(Component.translatable("time.simplefinances.hour", hours)).append(" ");
        }
        if (minutes > 0) {
            result.append(Component.translatable("time.simplefinances.minute", minutes)).append(" ");
        }
        if (secs > 0 || result.getString().isEmpty()) {
            result.append(Component.translatable("time.simplefinances.second", secs));
        }
        return result;
    }

    private static String formatDateTime(long epochMillis) {
        return DATE_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    private String trim(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String result = text;
        while (!result.isEmpty() && font.width(result + "…") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private static String describe(StatePayload.TxView tx) {
        String arrow = " → ";
        return switch (tx.type()) {
            case "TRANSFER" -> tx.from() + arrow + tx.to() + "  +" + Money.format(tx.amount())
                    + (tx.fee() > 0L
                    ? "  (" + Component.translatable("screen.simplefinances.transactions.fee",
                    Money.format(tx.fee())).getString() + ")"
                    : "");
            case "DEPOSIT" -> tx.from() + "  +" + Money.format(tx.amount());
            case "WITHDRAW" -> tx.from() + "  -" + Money.format(tx.amount());
            case "COLLECTIBLE" -> tx.from() + arrow + tx.to() + "  " + tx.note();
            case "CONVERT" -> tx.from() + "  +" + Money.format(tx.amount()) + "  " + tx.note();
            case "BLOCK" -> tx.from() + arrow + tx.to() + "  " + tx.note();
            case "UNBLOCK" -> tx.from() + arrow + tx.to();
            case "CONFIG" -> tx.from() + "  " + tx.note();
            default -> "";
        };
    }

    private static int txColor(String type) {
        return switch (type) {
            case "TRANSFER" -> 0xFF55FF55;
            case "DEPOSIT" -> 0xFF55FFFF;
            case "WITHDRAW" -> 0xFFFFAA00;
            case "COLLECTIBLE" -> 0xFFCC88FF;
            case "CONVERT" -> 0xFFFFAA55;
            case "BLOCK" -> 0xFFFF5555;
            case "UNBLOCK" -> 0xFF55FF55;
            case "CONFIG" -> 0xFFAAAAAA;
            default -> 0xFFFFFFFF;
        };
    }

    private static void drawScrollbar(GuiGraphics graphics, int x, int top, int bottom,
                                      int offset, int maxScroll, int rows, int total) {
        if (maxScroll <= 0) {
            return;
        }
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(12, trackHeight * rows / total);
        int thumbY = top + (trackHeight - thumbHeight) * offset / maxScroll;

        graphics.fill(x, top, x + 3, bottom, 0x40FFFFFF);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xFFFFFFFF);
    }

    private static final Predicate<String> DIGITS = value -> value.isEmpty() || value.matches("\\d{0,9}");
    private static final Predicate<String> PLAYER_NAME = value -> value.matches("[A-Za-z0-9_]{0,16}");
    private static final Predicate<String> RECIPIENTS = value -> value.matches("[A-Za-z0-9_, ]{0,200}");
    private static final Predicate<String> ANY = value -> true;

    private record OwnedCoin(int slot, CoinView view) {
    }

    private enum Tab {
        WALLET("screen.simplefinances.tab.wallet", false),
        CONVERSION("screen.simplefinances.tab.conversion", false),
        COLLECTION("screen.simplefinances.tab.collection", false),
        TRANSACTIONS("screen.simplefinances.tab.transactions", true),
        COLLECTIBLES("screen.simplefinances.tab.collectibles", true),
        BLOCKS("screen.simplefinances.tab.blocks", true),
        SETTINGS("screen.simplefinances.tab.settings", true);

        private final String key;
        private final boolean adminOnly;

        Tab(String key, boolean adminOnly) {
            this.key = key;
            this.adminOnly = adminOnly;
        }
    }
}
