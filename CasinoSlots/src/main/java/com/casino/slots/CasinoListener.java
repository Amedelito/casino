package com.casino.slots;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class CasinoListener implements Listener {

    private final CasinoPlugin plugin;

    public CasinoListener(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ click derecho
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Machine m = plugin.machines().getAt(clicked);
        if (m != null) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) play(e.getPlayer(), m);
            return;
        }

        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack inHand = e.getItem();
        if (plugin.items().isMachine(inHand)) {
            e.setCancelled(true);
            place(e.getPlayer(), clicked.getRelative(e.getBlockFace()), inHand);
        }
    }

    private static boolean free(Block b) {
        return b.getType().isAir() || b.isReplaceable();
    }

    private void place(Player p, Block target, ItemStack inHand) {
        if (!p.hasPermission("casino.place")) {
            p.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (!free(target) || !free(target.getRelative(BlockFace.UP))) {
            p.sendMessage(plugin.msg("cannot-place"));
            return;
        }
        float yaw = Math.round(p.getLocation().getYaw() / 90f) * 90f + 180f;
        yaw = ((yaw % 360f) + 360f) % 360f;
        plugin.machines().create(target, yaw);
        if (p.getGameMode() != GameMode.CREATIVE) inHand.setAmount(inHand.getAmount() - 1);
        p.playSound(target.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.4f);
        p.sendMessage(plugin.msg("placed"));
    }

    // ------------------------------------------------------------ jugar
    private void play(Player p, Machine m) {
        if (!p.hasPermission("casino.play")) {
            p.sendMessage(plugin.msg("no-permission"));
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!plugin.items().isCoin(hand)) {
            p.sendActionBar(plugin.raw("need-coin"));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        if (m.isBusy()) {
            p.sendActionBar(plugin.raw("busy"));
            return;
        }

        m.ensureSpawned();
        // consume 1 moneda
        ItemStack rest = hand.clone();
        rest.setAmount(rest.getAmount() - 1);
        p.getInventory().setItemInMainHand(rest.getAmount() <= 0 ? new ItemStack(Material.AIR) : rest);
        m.setBusy(true);

        final boolean win = plugin.rollWin();
        final int[] result = plugin.rollSymbols(win);
        final ItemStack reward = win ? plugin.rewards().roll() : null;

        m.coinAnimation();
        plugin.later(6, m::leverPull);
        plugin.later(8, () -> m.spin(result, () -> finish(p, m, win, result[0], reward)));
    }

    private void finish(Player p, Machine m, boolean win, int symbol, ItemStack reward) {
        try {
            if (!win) {
                m.loseEffects();
                if (p.isOnline()) p.sendActionBar(plugin.raw("lose"));
                m.setBusy(false);
                return;
            }
            m.winEffects();
            Component itemName = reward.displayName();
            if (p.isOnline()) {
                p.sendActionBar(plugin.raw("win", Placeholder.component("item", itemName)));
            }
            if (plugin.getConfig().getBoolean("broadcast-wins", true)) {
                plugin.getServer().broadcast(plugin.msg("win-broadcast",
                        Placeholder.unparsed("player", p.getName()),
                        Placeholder.unparsed("symbol", CasinoPlugin.SYMBOL_NAMES[symbol]),
                        Placeholder.component("item", itemName)));
            }
            m.showReward(reward, () -> {
                give(p, m, reward);
                m.setBusy(false);
            });
        } catch (Exception ex) {
            m.setBusy(false);
            plugin.getLogger().warning("Error al terminar la partida: " + ex.getMessage());
            if (reward != null) give(p, m, reward);
        }
    }

    private void give(Player p, Machine m, ItemStack reward) {
        if (p.isOnline()) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(reward);
            for (ItemStack it : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), it);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            m.world.dropItemNaturally(m.front(), reward);
        }
    }

    // ------------------------------------------------------------ romper
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Machine m = plugin.machines().getAt(e.getBlock());
        if (m == null) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("casino.admin")) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        if (p.getGameMode() != GameMode.CREATIVE) {
            m.world.dropItemNaturally(m.front(), plugin.items().machine(1));
        }
        plugin.machines().remove(m);
        p.sendMessage(plugin.msg("removed"));
    }

    // ------------------------------------------------------------ carga de chunks / mundos
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        plugin.later(1, () -> plugin.machines().spawnForChunk(e.getChunk()));
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        plugin.machines().loadWorld(e.getWorld());
        plugin.later(20, () -> plugin.machines().spawnLoaded());
    }

    // ------------------------------------------------------------ resource pack
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (plugin.getConfig().getString("resource-pack.url", "").isBlank()) return;
        plugin.later(20, () -> sendPack(e.getPlayer()));
    }

    public void sendPack(Player p) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url.isBlank() || !p.isOnline()) return;
        String sha = plugin.getConfig().getString("resource-pack.sha1", "");
        byte[] hash = null;
        if (sha.length() == 40) {
            hash = new byte[20];
            for (int i = 0; i < 20; i++) hash[i] = (byte) Integer.parseInt(sha.substring(i * 2, i * 2 + 2), 16);
        }
        p.setResourcePack(url, hash, plugin.mini(plugin.getConfig().getString("resource-pack.prompt", "")),
                plugin.getConfig().getBoolean("resource-pack.force", false));
    }
}
