package com.casino.slots;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CasinoCommand implements CommandExecutor, TabCompleter {

    private final CasinoPlugin plugin;

    public CasinoCommand(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("casino.admin")) {
            s.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (a.length == 0) {
            help(s, label);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(s, label, a);
            case "remove" -> remove(s);
            case "list" -> {
                s.sendMessage(plugin.mini("<gold>Tragaperras: <white>" + plugin.machines().all().size()));
                for (Machine m : plugin.machines().all()) {
                    s.sendMessage(plugin.mini("<gray> - " + m.world.getName() + " " + m.x + " " + m.y + " " + m.z));
                }
            }
            case "reload" -> {
                plugin.reload();
                s.sendMessage(plugin.msg("reloaded"));
            }
            case "pack" -> {
                CasinoListener l = new CasinoListener(plugin);
                if (a.length > 1) {
                    Player t = Bukkit.getPlayerExact(a[1]);
                    if (t != null) l.sendPack(t);
                } else if (s instanceof Player p) {
                    l.sendPack(p);
                } else {
                    Bukkit.getOnlinePlayers().forEach(l::sendPack);
                }
            }
            default -> help(s, label);
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        s.sendMessage(plugin.mini("<gold><bold>CasinoSlots"));
        s.sendMessage(plugin.mini("<yellow>/" + label + " give <coin|machine> [jugador] [cantidad]"));
        s.sendMessage(plugin.mini("<yellow>/" + label + " remove <gray>(la que estés mirando / la más cercana)"));
        s.sendMessage(plugin.mini("<yellow>/" + label + " list"));
        s.sendMessage(plugin.mini("<yellow>/" + label + " reload"));
        s.sendMessage(plugin.mini("<yellow>/" + label + " pack [jugador] <gray>(reenviar resource pack)"));
    }

    private void give(CommandSender s, String label, String[] a) {
        if (a.length < 2) {
            help(s, label);
            return;
        }
        String what = a[1].toLowerCase(Locale.ROOT);
        if (!what.equals("coin") && !what.equals("machine")) {
            help(s, label);
            return;
        }
        Player target = s instanceof Player p ? p : null;
        int amount = 1;
        if (a.length > 2) {
            Integer n = parse(a[2]);
            if (n != null) {
                amount = n;            // /casino give coin 5
            } else {
                target = Bukkit.getPlayerExact(a[2]);
                if (target == null) {
                    s.sendMessage(plugin.mini("<red>Jugador no encontrado: " + a[2]));
                    return;
                }
                if (a.length > 3) {
                    Integer n2 = parse(a[3]);
                    if (n2 == null) {
                        s.sendMessage(plugin.mini("<red>Cantidad inválida."));
                        return;
                    }
                    amount = n2;
                }
            }
        }
        if (target == null) {
            s.sendMessage(plugin.mini("<red>Indica un jugador."));
            return;
        }
        amount = Math.max(1, Math.min(2304, amount));
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(64, remaining);
            ItemStack item = what.equals("coin") ? plugin.items().coin(stack) : plugin.items().machine(stack);
            for (ItemStack left : target.getInventory().addItem(item).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), left);
            }
            remaining -= stack;
        }
        String label2 = what.equals("coin") ? "monedas" : "tragaperras";
        s.sendMessage(plugin.msg("given", Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("what", label2), Placeholder.unparsed("player", target.getName())));
        if (target != s) {
            target.sendMessage(plugin.msg("received", Placeholder.unparsed("amount", String.valueOf(amount)),
                    Placeholder.unparsed("what", label2)));
        }
    }

    private void remove(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage(plugin.mini("<red>Solo jugadores."));
            return;
        }
        Machine m = null;
        Block target = p.getTargetBlockExact(6);
        if (target != null) m = plugin.machines().getAt(target);
        if (m == null) m = plugin.machines().nearest(p.getLocation(), 4);
        if (m == null) {
            s.sendMessage(plugin.msg("no-machine"));
            return;
        }
        plugin.machines().remove(m);
        p.getInventory().addItem(plugin.items().machine(1)).values()
                .forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        s.sendMessage(plugin.msg("removed"));
    }

    private static Integer parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("casino.admin")) return out;
        if (a.length == 1) {
            for (String o : List.of("give", "remove", "list", "reload", "pack")) if (o.startsWith(a[0].toLowerCase())) out.add(o);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            for (String o : List.of("coin", "machine")) if (o.startsWith(a[1].toLowerCase())) out.add(o);
        } else if ((a.length == 3 && a[0].equalsIgnoreCase("give")) || (a.length == 2 && a[0].equalsIgnoreCase("pack"))) {
            String prefix = a[a.length - 1].toLowerCase();
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(prefix)) out.add(p.getName());
            });
        }
        return out;
    }
}
