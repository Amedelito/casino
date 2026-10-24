package com.casino.slots;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class CasinoPlugin extends JavaPlugin {

    public static final String[] SYMBOL_NAMES = {"cerezas", "limones", "diamantes", "sietes"};

    private final MiniMessage mm = MiniMessage.miniMessage();
    private CasinoItems items;
    private RewardManager rewards;
    private MachineManager machines;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new CasinoItems(this);
        rewards = new RewardManager(this);
        rewards.load();
        machines = new MachineManager(this);
        machines.load();

        getServer().getPluginManager().registerEvents(new CasinoListener(this), this);
        CasinoCommand cmd = new CasinoCommand(this);
        getCommand("casino").setExecutor(cmd);
        getCommand("casino").setTabCompleter(cmd);

        // un tick después, cuando los mundos y chunks ya están listos
        Bukkit.getScheduler().runTask(this, machines::spawnLoaded);
    }

    @Override
    public void onDisable() {
        if (machines != null) machines.despawnAll();
    }

    public void reload() {
        reloadConfig();
        rewards.load();
    }

    public CasinoItems items() { return items; }
    public RewardManager rewards() { return rewards; }
    public MachineManager machines() { return machines; }

    public void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(this, r, Math.max(0L, ticks));
    }

    /** Mensaje con prefijo (chat). */
    public Component msg(String key, TagResolver... resolvers) {
        return mm.deserialize(getConfig().getString("prefix", "") + getConfig().getString("messages." + key, key), resolvers);
    }

    /** Mensaje sin prefijo (action bar). */
    public Component raw(String key, TagResolver... resolvers) {
        return mm.deserialize(getConfig().getString("messages." + key, key), resolvers);
    }

    public Component mini(String text) {
        return mm.deserialize(text);
    }

    /** Devuelve {a,b,c} con los índices de símbolo (0 cereza, 1 limón, 2 diamante, 3 siete). */
    public int[] rollSymbols(boolean win) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (win) {
            int s = r.nextInt(4);
            return new int[]{s, s, s};
        }
        int[] res;
        do {
            res = new int[]{r.nextInt(4), r.nextInt(4), r.nextInt(4)};
        } while (res[0] == res[1] && res[1] == res[2]);
        return res;
    }

    public boolean rollWin() {
        double chance = Math.max(0.0, Math.min(1.0, getConfig().getDouble("win-chance", 0.08)));
        return ThreadLocalRandom.current().nextDouble() < chance;
    }
}
