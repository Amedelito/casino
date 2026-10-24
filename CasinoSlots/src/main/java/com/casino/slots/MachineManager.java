package com.casino.slots;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MachineManager {

    private final CasinoPlugin plugin;
    private final File file;
    private final Map<String, String> raw = new LinkedHashMap<>();       // id -> "mundo;x;y;z;yaw"
    private final Map<String, Machine> machines = new LinkedHashMap<>(); // id -> máquina cargada
    private final Map<String, Machine> byBlock = new HashMap<>();        // bloque -> máquina

    public MachineManager(CasinoPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "machines.yml");
    }

    private static String key(World w, int x, int y, int z) {
        return w.getName() + ";" + x + ";" + y + ";" + z;
    }

    public void load() {
        raw.clear();
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String s : yml.getStringList("machines")) {
                int cut = s.lastIndexOf(';');
                if (cut > 0) raw.put(s.substring(0, cut), s);
            }
        }
        for (World w : Bukkit.getWorlds()) loadWorld(w);
    }

    /** Registra las máquinas de ese mundo (se llama al arrancar y en WorldLoadEvent). */
    public void loadWorld(World w) {
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String[] p = e.getValue().split(";");
            if (p.length < 5 || !p[0].equals(w.getName()) || machines.containsKey(e.getKey())) continue;
            try {
                Machine m = new Machine(plugin, w, Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), Float.parseFloat(p[4]));
                register(m);
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("Entrada corrupta en machines.yml: " + e.getValue());
            }
        }
    }

    private void register(Machine m) {
        machines.put(m.id(), m);
        byBlock.put(key(m.world, m.x, m.y, m.z), m);
        byBlock.put(key(m.world, m.x, m.y + 1, m.z), m);
    }

    public Machine create(Block base, float yaw) {
        Machine m = new Machine(plugin, base.getWorld(), base.getX(), base.getY(), base.getZ(), yaw);
        register(m);
        raw.put(m.id(), m.id() + ";" + yaw);
        save();
        m.ensureSpawned();
        return m;
    }

    public void remove(Machine m) {
        m.despawn();
        machines.remove(m.id());
        byBlock.remove(key(m.world, m.x, m.y, m.z));
        byBlock.remove(key(m.world, m.x, m.y + 1, m.z));
        raw.remove(m.id());
        m.world.getBlockAt(m.x, m.y, m.z).setType(Material.AIR, false);
        m.world.getBlockAt(m.x, m.y + 1, m.z).setType(Material.AIR, false);
        save();
    }

    public Machine getAt(Block b) {
        return byBlock.get(key(b.getWorld(), b.getX(), b.getY(), b.getZ()));
    }

    public Machine nearest(Location loc, double radius) {
        Machine best = null;
        double bestDist = radius * radius;
        for (Machine m : machines.values()) {
            if (m.world != loc.getWorld()) continue;
            double d = loc.distanceSquared(new Location(m.world, m.x + 0.5, m.y + 1, m.z + 0.5));
            if (d <= bestDist) {
                bestDist = d;
                best = m;
            }
        }
        return best;
    }

    public java.util.Collection<Machine> all() {
        return machines.values();
    }

    public void spawnLoaded() {
        for (Machine m : machines.values()) m.ensureSpawned();
    }

    public void spawnForChunk(Chunk c) {
        for (Machine m : machines.values()) {
            if (m.world == c.getWorld() && (m.x >> 4) == c.getX() && (m.z >> 4) == c.getZ()) m.ensureSpawned();
        }
    }

    public void despawnAll() {
        for (Machine m : machines.values()) m.despawn();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("machines", new ArrayList<>(raw.values()));
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar machines.yml: " + e.getMessage());
        }
    }
}
