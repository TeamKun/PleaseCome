package net.teamfruit.pleasecome;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import org.apache.commons.lang.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class PleaseCome extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static class TeleportTicket {
        public final CommandSender sender;
        public final Consumer<Player> action;
        public final long expireAt;
        public final List<Player> targets;

        public TeleportTicket(CommandSender sender, Consumer<Player> action, long expireAt, List<Player> targets) {
            this.sender = sender;
            this.action = action;
            this.expireAt = expireAt;
            this.targets = targets;
        }
    }

    private Map<String, TeleportTicket> tickets = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1)
            return false;

        String cname = command.getName();
        if ("come-accept".equals(cname) || "come-deny".equals(cname)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(new ComponentBuilder()
                        .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                        .append("プレイヤー専用コマンドです").color(ChatColor.RED)
                        .create()
                );
                return true;
            }
            Player player = (Player) sender;

            String token = args[0];
            TeleportTicket ticket = tickets.get(token);
            if (ticket == null) {
                sender.sendMessage(new ComponentBuilder()
                        .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                        .append("トークンが無効です").color(ChatColor.RED)
                        .create()
                );
                return true;
            }
            if (ticket.expireAt < System.currentTimeMillis()) {
                sender.sendMessage(new ComponentBuilder()
                        .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                        .append("招待が期限切れです").color(ChatColor.RED)
                        .create()
                );
                return true;
            }
            if (!ticket.targets.contains(player)) {
                sender.sendMessage(new ComponentBuilder()
                        .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                        .append("招待を受けていません").color(ChatColor.RED)
                        .create()
                );
                return true;
            }

            ticket.targets.remove(player);
            if ("come-deny".equals(cname)) {
                sender.sendMessage(new ComponentBuilder()
                        .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                        .append("TPを拒否しました").color(ChatColor.RED)
                        .create()
                );
            } else {
                sendActionBarOrMessage(ticket.sender, sender.getName() + " がTPしてきた");
                ticket.action.accept(player);
                sender.sendMessage(new ComponentBuilder()
                        .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                        .append("TPしました").color(ChatColor.GREEN)
                        .create()
                );
            }
        } else if ("come".equals(cname) || "come-optional".equals(cname)) {
            Player to;
            if (args.length < 2) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(new ComponentBuilder()
                            .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                            .append("コマブロやコンソールからの場合は送り先の人も指定してね").color(ChatColor.RED)
                            .create()
                    );
                    return true;
                }
                to = (Player) sender;
            } else {
                Optional<Player> toOptional = Bukkit.selectEntities(sender, args[1]).stream()
                        .filter(Player.class::isInstance)
                        .map(Player.class::cast)
                        .findFirst();
                if (!toOptional.isPresent()) {
                    sender.sendMessage(new ComponentBuilder()
                            .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                            .append("ターゲットが見つかりません").color(ChatColor.RED)
                            .create()
                    );
                    return true;
                }
                to = toOptional.get();
            }

            String targetName = args[0];
            List<Player> targets = Bukkit.selectEntities(sender, targetName).stream()
                    .filter(Player.class::isInstance)
                    .map(Player.class::cast)
                    .filter(p -> !p.equals(to))
                    .collect(Collectors.toList());
            if (targets.isEmpty()) {
                sender.sendMessage(new ComponentBuilder()
                        .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                        .append("ターゲットが見つかりません").color(ChatColor.RED)
                        .create()
                );
                return true;
            }

            String token = RandomStringUtils.random(8, true, true);
            Location toLocation = to.getLocation();
            TeleportTicket ticket = new TeleportTicket(
                    sender,
                    p -> p.teleport(toLocation),
                    System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5),
                    targets
            );
            tickets.put(token, ticket);
            boolean opt = "come-optional".equals(cname);
            BaseComponent[] invite = new ComponentBuilder(
                    new TextComponent(new ComponentBuilder()
                            .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                            .append(sender.getName()).color(ChatColor.WHITE)
                            .append(sender.equals(to)
                                    ? new ComponentBuilder("から").color(ChatColor.GREEN).create()
                                    : new ComponentBuilder("から").color(ChatColor.GREEN)
                                    .append(to.getName()).color(ChatColor.WHITE)
                                    .append("への").color(ChatColor.GREEN).create()
                            )
                            .append(opt ? "TP招待が来た。" : "TP命令が来た。10秒にTPされる。").color(ChatColor.GREEN)
                            .append(opt ? "[クリックでTPする！]" : "[クリックでTPを拒否する]").color(ChatColor.GOLD)
                            .create())
            )
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder()
                            .append(opt ? "クリックでTPする" : "クリックでTPを拒否する")
                            .create()
                    ))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, (opt ? "/come-accept " : "/come-deny ") + token))
                    .create();
            targets.forEach(p -> p.sendMessage(invite));
            sender.sendMessage(new ComponentBuilder()
                    .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                    .append(targetName).color(ChatColor.WHITE)
                    .append("にTP招待を送りつけた").color(ChatColor.GREEN)
                    .create()
            );

            if (!opt) {
                new BukkitRunnable() {
                    private final long tpAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);

                    @Override
                    public void run() {
                        long remainMillis = tpAt - System.currentTimeMillis();
                        long remainSec = TimeUnit.MILLISECONDS.toSeconds(remainMillis);
                        sendActionBarOrMessage(ticket.sender, remainSec + "秒後にTPしてきます");
                        ticket.targets.forEach(p -> p.sendActionBar(remainSec + "秒後にTPします"));
                        if (remainMillis <= 0) {
                            cancel();
                            sendActionBarOrMessage(ticket.sender, "TPしてきた");
                            ticket.targets.forEach(p -> {
                                ticket.action.accept(p);
                                p.sendMessage(new ComponentBuilder()
                                        .append("[かめすたプラグイン] ").color(ChatColor.LIGHT_PURPLE)
                                        .append("TPしました").color(ChatColor.GREEN)
                                        .create()
                                );
                            });
                        }
                    }
                }.runTaskTimer(this, 0, 20);
            }
        }

        return true;
    }

    private static void sendActionBarOrMessage(CommandSender sender, String s) {
        if (sender instanceof Player)
            ((Player) sender).sendActionBar(s);
        else
            sender.sendMessage(s);
    }
}
