package com.simplefinances.command;

import com.simplefinances.economy.FinanceData;
import com.simplefinances.economy.FinanceService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class FinanceCommands {
    private FinanceCommands() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("wallet")
                    .executes(context -> {
                        FinanceService.openWallet(context.getSource().getPlayerOrException());
                        return 1;
                    }));

            dispatcher.register(Commands.literal("balance")
                    .executes(context -> {
                        FinanceService.sendBalance(context.getSource().getPlayerOrException());
                        return 1;
                    }));

            dispatcher.register(Commands.literal("pay")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .then(Commands.argument("amount", LongArgumentType.longArg(1L, FinanceService.MAX_AMOUNT))
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        FinanceService.transfer(player,
                                                StringArgumentType.getString(context, "player"),
                                                LongArgumentType.getLong(context, "amount"));
                                        return 1;
                                    }))));

            dispatcher.register(Commands.literal("finances")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("admin")
                            .executes(context -> {
                                FinanceService.openWallet(context.getSource().getPlayerOrException());
                                return 1;
                            }))
                    .then(Commands.literal("fee")
                            .then(Commands.argument("percent", IntegerArgumentType.integer(0, 50))
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        int cooldown = FinanceData.get(player.getServer()).depositCooldownSeconds();
                                        FinanceService.configure(player,
                                                IntegerArgumentType.getInteger(context, "percent"), cooldown);
                                        return 1;
                                    })))
                    .then(Commands.literal("cooldown")
                            .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86_400))
                                    .executes(context -> {
                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                        int fee = FinanceData.get(player.getServer()).feePercent();
                                        FinanceService.configure(player, fee,
                                                IntegerArgumentType.getInteger(context, "seconds"));
                                        return 1;
                                    })))
                    .then(Commands.literal("block")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .then(Commands.argument("minutes", LongArgumentType.longArg(1L, 525_600L))
                                            .executes(context -> block(context, ""))
                                            .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                    .executes(context -> block(context,
                                                            StringArgumentType.getString(context, "reason")))))))
                    .then(Commands.literal("unblock")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(context -> {
                                        FinanceService.unblock(context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "player"));
                                        return 1;
                                    }))));
        });
    }

    private static int block(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
                             String reason) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        FinanceService.block(context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, "player"),
                LongArgumentType.getLong(context, "minutes"),
                reason);
        return 1;
    }
}
