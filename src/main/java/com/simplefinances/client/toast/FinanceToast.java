package com.simplefinances.client.toast;

import com.simplefinances.item.CollectibleTier;
import com.simplefinances.net.NotifyPayload;
import com.simplefinances.registry.ModItems;
import com.simplefinances.util.Money;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Advancement-style toast for incoming money, blocked accounts and collectible coins.
 */
public final class FinanceToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Component title;
    private final Component subtitle;
    private final ItemStack icon;

    private FinanceToast(Component title, Component subtitle, ItemStack icon) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
    }

    public static void show(NotifyPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Component title;
        Component subtitle;
        ItemStack icon;

        switch (payload.kind()) {
            case RECEIVED -> {
                title = Component.translatable("toast.simplefinances.received.title");
                subtitle = Component.translatable("toast.simplefinances.received.desc",
                        Money.format(payload.amount()), payload.name());
                icon = new ItemStack(ModItems.COIN_1);
            }
            case DEPOSIT -> {
                title = Component.translatable("toast.simplefinances.deposit.title");
                subtitle = Component.translatable("toast.simplefinances.deposit.desc", Money.format(payload.amount()));
                icon = new ItemStack(ModItems.COIN_1);
            }
            case BLOCKED -> {
                title = Component.translatable("toast.simplefinances.blocked.title");
                String until = DATE_TIME.format(Instant.ofEpochMilli(payload.until())
                        .atZone(ZoneId.systemDefault()));
                subtitle = payload.name().isBlank()
                        ? Component.translatable("toast.simplefinances.blocked.desc", until)
                        : Component.translatable("toast.simplefinances.blocked.desc_reason", until, payload.name());
                icon = new ItemStack(Items.BARRIER);
            }
            case UNBLOCKED -> {
                title = Component.translatable("toast.simplefinances.unblocked.title");
                subtitle = Component.translatable("toast.simplefinances.unblocked.desc");
                icon = new ItemStack(Items.LIME_DYE);
            }
            case COLLECTIBLE -> {
                CollectibleTier tier = CollectibleTier.byIndex(payload.tier());
                title = Component.translatable("toast.simplefinances.collectible.title");
                subtitle = Component.translatable("toast.simplefinances.collectible.desc", tier.displayName());
                icon = new ItemStack(tier.item());
            }
            default -> {
                return;
            }
        }

        minecraft.getToasts().addToast(new FinanceToast(title, subtitle, icon));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F, 1.0F));
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        graphics.blitSprite(BACKGROUND, 0, 0, this.width(), this.height());
        Font font = toastComponent.getMinecraft().font;
        drawLine(graphics, font, title, 30, 7, 0xFFFF00);
        drawLine(graphics, font, subtitle, 30, 18, 0xFFFFFF);
        graphics.renderFakeItem(icon, 8, 8);

        return timeSinceLastVisible >= 5000L * toastComponent.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE
                : Visibility.SHOW;
    }

    private static void drawLine(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        List<FormattedCharSequence> lines = font.split(text, 124);
        if (!lines.isEmpty()) {
            graphics.drawString(font, lines.get(0), x, y, color, false);
        }
    }
}
