package com.casino.slots;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class CasinoItems {

    // custom_model_data (deben coincidir con el resource pack)
    public static final int CMD_MACHINE = 1001; // paper: icono de inventario
    public static final int CMD_BODY = 1002;    // paper: cuerpo de la máquina
    public static final int CMD_REEL = 1003;    // paper: rodillo
    public static final int CMD_LEVER = 1004;   // paper: palanca
    public static final int CMD_COIN = 1010;    // gold_nugget: moneda

    private final NamespacedKey coinKey;
    private final NamespacedKey machineKey;

    public CasinoItems(CasinoPlugin plugin) {
        this.coinKey = new NamespacedKey(plugin, "coin");
        this.machineKey = new NamespacedKey(plugin, "machine");
    }

    private static Component name(String text, NamedTextColor color) {
        return Component.text(text, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);
    }

    private static Component lore(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack coin(int amount) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name("Moneda del Casino", NamedTextColor.GOLD));
        meta.lore(List.of(lore("Haz click derecho en una"), lore("tragaperras para jugar.")));
        meta.setCustomModelData(CMD_COIN);
        meta.getPersistentDataContainer().set(coinKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack machine(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name("Tragaperras", NamedTextColor.RED));
        meta.lore(List.of(lore("Click derecho en el suelo"), lore("para colocarla.")));
        meta.setCustomModelData(CMD_MACHINE);
        meta.getPersistentDataContainer().set(machineKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCoin(ItemStack item) {
        return item != null && item.getType() == Material.GOLD_NUGGET && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(coinKey, PersistentDataType.BYTE);
    }

    public boolean isMachine(ItemStack item) {
        return item != null && item.getType() == Material.PAPER && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(machineKey, PersistentDataType.BYTE);
    }

    /** Item "limpio" que usan los ItemDisplay para mostrar un modelo 3D. */
    public ItemStack model(int cmd) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(cmd);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack coinModel() {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(CMD_COIN);
        item.setItemMeta(meta);
        return item;
    }
}
