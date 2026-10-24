package com.casino.slots;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Una tragaperras: 2 bloques de barrera (hitbox) + 5 ItemDisplay (cuerpo, 3 rodillos, palanca).
 * Las coordenadas "modelo" (0..16 por bloque) son las del resource pack.
 * Frente del modelo = +Z (sur) con yaw 0.
 */
public final class Machine {

    /** Escala visual de todo el modelo (1.25 = algo más grande que 1 bloque). */
    public static final float S = 1.25f;

    private static final double[] REEL_X = {3.1, 8.0, 12.9};
    private static final double REEL_Y = 19.0, REEL_Z = 9.0;
    private static final double LEVER_X = 16.75, LEVER_Y = 19.0, LEVER_Z = 9.0;
    private static final float LEVER_REST = -25f, LEVER_PULL = 55f;
    private static final int[] DECEL = {3, 4, 6, 8, 10};

    private final CasinoPlugin plugin;
    public final World world;
    public final int x, y, z;
    public final float yaw;

    private ItemDisplay body, lever;
    private final ItemDisplay[] reels = new ItemDisplay[3];
    private final int[] quarter = new int[3]; // cara frontal de cada rodillo (0..3)
    private boolean busy;

    public Machine(CasinoPlugin plugin, World world, int x, int y, int z, float yaw) {
        this.plugin = plugin;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public String id() { return world.getName() + ";" + x + ";" + y + ";" + z; }
    public boolean isBusy() { return busy; }
    public void setBusy(boolean busy) { this.busy = busy; }

    // ------------------------------------------------------------ geometría
    /** Punto del mundo para unas coordenadas del modelo (0..16), con yaw y escala. */
    public Location point(double mx, double my, double mz) {
        double yr = Math.toRadians(yaw);
        double lx = (mx - 8) / 16.0 * S;
        double lz = (mz - 8) / 16.0 * S;
        double wx = x + 0.5 + lx * Math.cos(yr) + lz * -Math.sin(yr);
        double wz = z + 0.5 + lx * Math.sin(yr) + lz * Math.cos(yr);
        double wy = y + my / 16.0 * S;
        return new Location(world, wx, wy, wz, yaw, 0f);
    }

    /** Sitio delante de la máquina donde dejar cosas. */
    public Location front() {
        return point(8, 4, 16 + 10);
    }

    private static Transformation rotX(float deg) {
        return new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(deg), 1f, 0f, 0f),
                new Vector3f(S, S, S), new AxisAngle4f());
    }

    private static Transformation yTf(float deg, float scale) {
        return new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(deg), 0f, 1f, 0f),
                new Vector3f(scale, scale, scale), new AxisAngle4f());
    }

    private static Transformation coinTf(float tz, float scale) {
        return new Transformation(new Vector3f(0f, 0f, tz), new AxisAngle4f((float) Math.toRadians(90), 0f, 1f, 0f),
                new Vector3f(scale, scale, scale), new AxisAngle4f());
    }

    // ------------------------------------------------------------ spawn
    private ItemDisplay display(Location loc, ItemStack item, Transformation tf, ItemDisplay.ItemDisplayTransform ctx) {
        return world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ctx);
            d.setTransformation(tf);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2f);
            d.setPersistent(false);   // no se guarda en el chunk: se vuelve a crear al cargar
            d.setInvulnerable(true);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(0);
        });
    }

    private ItemDisplay display(Location loc, ItemStack item, Transformation tf) {
        return display(loc, item, tf, ItemDisplay.ItemDisplayTransform.NONE);
    }

    private static boolean ok(ItemDisplay d) { return d != null && d.isValid(); }

    private boolean allValid() {
        if (!ok(body) || !ok(lever)) return false;
        for (ItemDisplay r : reels) if (!ok(r)) return false;
        return true;
    }

    public void ensureSpawned() {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
        Block b0 = world.getBlockAt(x, y, z);
        Block b1 = world.getBlockAt(x, y + 1, z);
        if (b0.getType() != Material.BARRIER) b0.setType(Material.BARRIER, false);
        if (b1.getType() != Material.BARRIER) b1.setType(Material.BARRIER, false);
        if (allValid()) return;
        despawn();
        CasinoItems it = plugin.items();
        body = display(point(8, 8, 8), it.model(CasinoItems.CMD_BODY), rotX(0));
        lever = display(point(LEVER_X, LEVER_Y, LEVER_Z), it.model(CasinoItems.CMD_LEVER), rotX(LEVER_REST));
        for (int i = 0; i < 3; i++) {
            reels[i] = display(point(REEL_X[i], REEL_Y, REEL_Z), it.model(CasinoItems.CMD_REEL), rotX(quarter[i] * 90f));
        }
    }

    public void despawn() {
        if (body != null) body.remove();
        if (lever != null) lever.remove();
        for (int i = 0; i < 3; i++) {
            if (reels[i] != null) reels[i].remove();
            reels[i] = null;
        }
        body = null;
        lever = null;
    }

    // ------------------------------------------------------------ animaciones
    private void rot(ItemDisplay d, float deg, int duration) {
        if (!ok(d)) return;
        d.setTransformation(rotX(deg));
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, duration));
    }

    private void sched(long ticks, Runnable r) {
        plugin.later(ticks, r);
    }

    /** La moneda vuela hacia la ranura y desaparece. */
    public void coinAnimation() {
        Location l = point(8, 8.5, 15.6);
        final float sc = 0.22f;
        ItemDisplay c = display(l, plugin.items().coinModel(), coinTf(0.9f, sc));
        sched(1, () -> {
            if (!c.isValid()) return;
            c.setTransformation(coinTf(-0.02f, sc));
            c.setInterpolationDelay(0);
            c.setInterpolationDuration(6);
        });
        sched(8, c::remove);
        world.playSound(l, Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.8f, 1.3f);
        sched(7, () -> world.playSound(l, Sound.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1f, 1.6f));
    }

    /** Tirón de la palanca. */
    public void leverPull() {
        Location l = point(LEVER_X, LEVER_Y, LEVER_Z);
        rot(lever, LEVER_PULL, 5);
        world.playSound(l, Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1f, 0.8f);
        sched(9, () -> rot(lever, LEVER_REST, 8));
        sched(17, () -> world.playSound(l, Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.7f, 1.2f));
    }

    /**
     * Gira los 3 rodillos y los deja parados en 'target' (índices de símbolo 0..3).
     * Cada rodillo gira más tiempo que el anterior, frena en pasos y rebota al parar.
     */
    public void spin(int[] target, Runnable done) {
        Location sndLoc = point(8, 19, 14);
        int maxEnd = 0;
        for (int i = 0; i < 3; i++) {
            final int reel = i;
            final int cur = ((quarter[i] % 4) + 4) % 4;
            final int q = 16 + 8 * i + (((target[i] - cur) % 4) + 4) % 4; // cuartos de vuelta
            float angle = cur * 90f;
            int t = 4;
            for (int step = 0; step < q; step++) {
                final boolean decel = step >= q - DECEL.length;
                final int dur = decel ? DECEL[step - (q - DECEL.length)] : 2;
                angle += 90f;
                final boolean last = step == q - 1;
                final float a = last ? angle + 9f : angle;       // pasa un pelín y rebota
                final boolean click = decel || (reel == 0 && step % 2 == 0);
                final float pitch = decel ? 1.2f + 0.1f * (step - (q - DECEL.length)) : 1.7f;
                sched(t, () -> {
                    rot(reels[reel], a, dur);
                    if (click) world.playSound(sndLoc, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.BLOCKS, 0.5f, pitch);
                });
                t += dur;
            }
            final float finalAngle = angle;
            sched(t, () -> rot(reels[reel], finalAngle, 4));      // rebote hacia atrás
            t += 4;
            sched(t, () -> {
                rot(reels[reel], finalAngle % 360f, 0);           // normaliza el ángulo
                quarter[reel] = target[reel];
                world.playSound(sndLoc, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.9f, 0.9f + 0.15f * reel);
            });
            maxEnd = Math.max(maxEnd, t);
        }
        sched(maxEnd + 8, done);
    }

    public void loseEffects() {
        Location l = point(8, 19, 14);
        world.playSound(l, Sound.ENTITY_VILLAGER_NO, SoundCategory.BLOCKS, 0.8f, 1f);
        world.spawnParticle(Particle.SMOKE, l, 8, 0.4, 0.3, 0.2, 0.01);
    }

    public void winEffects() {
        Location l = point(8, 20, 14);
        world.spawnParticle(Particle.FIREWORK, l, 60, 0.7, 0.8, 0.7, 0.08);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, l, 40, 0.6, 0.8, 0.6, 0.3);
        world.playSound(l, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 1.5f, 1f);
        world.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.BLOCKS, 1f, 1f);
        sched(10, () -> world.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.BLOCKS, 1f, 1f));
        for (int i = 0; i < 6; i++) {
            final float p = 0.8f + 0.15f * i;
            sched(4L * i, () -> world.playSound(l, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.BLOCKS, 1f, p));
        }
    }

    /** El premio aparece girando encima de la máquina durante ~4 s. */
    public void showReward(ItemStack reward, Runnable onEnd) {
        Location l = point(8, 34, 8);
        ItemDisplay d = display(l, reward, yTf(0, 0.1f), ItemDisplay.ItemDisplayTransform.GROUND);
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (!d.isValid()) {
                    cancel();
                    onEnd.run();
                    return;
                }
                n++;
                if (n > 16) {
                    d.remove();
                    cancel();
                    onEnd.run();
                    return;
                }
                d.setTransformation(yTf(n * 90f, 0.9f));
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(5);
                if (n % 2 == 0) world.spawnParticle(Particle.END_ROD, l, 3, 0.4, 0.4, 0.4, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}
