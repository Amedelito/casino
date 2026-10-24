package com.casino.slots;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class RewardManager {

    private record Reward(Material material, int min, int max, int weight, Map<Enchantment, Integer> enchants, String name) {}

    private final CasinoPlugin plugin;
    private final List<Reward> rewards = new ArrayList<>();
    private int totalWeight;

    public RewardManager(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rewards.clear();
        totalWeight = 0;
        for (Map<?, ?> map : plugin.getConfig().getMapList("rewards")) {
            try {
                Material material = Material.matchMaterial(String.valueOf(map.get("material")));
                if (material == null || material.isAir() || !material.isItem()) {
                    plugin.getLogger().warning("Premio ignorado, material inválido: " + map.get("material"));
                    continue;
                }
                int min = Math.max(1, toInt(map.get("min"), 1));
                int max = Math.max(min, toInt(map.get("max"), min));
                int weight = Math.max(1, toInt(map.get("weight"), 1));
                Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
                Object eo = map.get("enchants");
                if (eo instanceof Map<?, ?> em) {
                    for (Map.Entry<?, ?> en : em.entrySet()) {
                        String key = String.valueOf(en.getKey()).toLowerCase(Locale.ROOT);
                        Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
                        if (ench == null) {
                            plugin.getLogger().warning("Encantamiento desconocido: " + key);
                            continue;
                        }
                        enchants.put(ench, Math.max(1, toInt(en.getValue(), 1)));
                    }
                }
                String name = map.get("name") == null ? null : String.valueOf(map.get("name"));
                rewards.add(new Reward(material, min, max, weight, enchants, name));
                totalWeight += weight;
            } catch (Exception ex) {
                plugin.getLogger().warning("Premio ignorado por error: " + ex.getMessage());
            }
        }
        if (rewards.isEmpty()) {
            rewards.add(new Reward(Material.DIAMOND, 1, 1, 1, Map.of(), null));
            totalWeight = 1;
        }
        plugin.getLogger().info(rewards.size() + " premios cargados.");
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try {
            return o == null ? def : Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Elige UN premio al azar (ponderado). */
    public ItemStack roll() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int pick = r.nextInt(totalWeight);
        Reward chosen = rewards.get(0);
        for (Reward rw : rewards) {
            pick -= rw.weight();
            if (pick < 0) {
                chosen = rw;
                break;
            }
        }
        int amount = chosen.min() + r.nextInt(chosen.max() - chosen.min() + 1);
        amount = Math.min(amount, chosen.material().getMaxStackSize());
        ItemStack item = new ItemStack(chosen.material(), amount);
        if (chosen.material() == Material.ENCHANTED_BOOK) {
            EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) item.getItemMeta();
            for (Map.Entry<Enchantment, Integer> e : chosen.enchants().entrySet()) {
                bookMeta.addStoredEnchant(e.getKey(), e.getValue(), true);
            }
            item.setItemMeta(bookMeta);
        } else {
            for (Map.Entry<Enchantment, Integer> e : chosen.enchants().entrySet()) {
                item.addUnsafeEnchantment(e.getKey(), e.getValue());
            }
        }
        if (chosen.name() != null) {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(plugin.mini(chosen.name()).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }
}
