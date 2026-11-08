package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.ChainBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * THE RITUAL'S END — what the players see at dawn, before the ending screen (about 16 seconds):
 * <ol>
 *   <li>from every pillar, one after another, a chain grows link by link to a point two blocks above the fire;</li>
 *   <li>there It appears, lying on its back in the air, arms, legs and head hanging down, held by the chains;</li>
 *   <li>It rises slowly into the morning sky, and the chains go with it;</li>
 *   <li>the fire goes out — but the ground around it glows red, and a pentagram turns on it;</li>
 *   <li>It is gone; the pentagram turns a while longer and fades; then the ending.</li>
 * </ol>
 */
public final class RitualFinale {
    private static final int SEGMENTS = 12;
    private static final int CHAIN_START = 10, CHAIN_GAP = 18, IT_AT = 95, RISE_FROM = 110, RISE_TO = 230,
            FIRE_OUT = 120, IT_GONE = 240, END = 330;
    private static final float CHAIN_WIDTH = 1.4f;

    private static boolean running;
    private static boolean forReal;
    private static int t;
    @Nullable private static ServerWorld world;
    private static BlockPos fire = BlockPos.ORIGIN;
    private static Vec3d center = Vec3d.ZERO;
    private static final List<List<DisplayEntity>> CHAINS = new ArrayList<>();
    private static final List<Vec3d> CHAIN_FROM = new ArrayList<>();
    private static final List<DisplayEntity> PENTAGRAM = new ArrayList<>();
    @Nullable private static TallOneEntity bound;
    private static float spin;
    @Nullable private static Runnable onEnd;

    private RitualFinale() {}

    public static boolean running() {
        return running;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (running) tick();
        });
    }

    /** Plays it at this fire; {@code then} runs at the end (the ending). {@code forReal} false = a test, no ending phase. */
    public static void start(ServerWorld w, BlockPos campfire, boolean real, Runnable then) {
        if (running) cleanup();
        running = true;
        forReal = real;
        t = 0;
        world = w;
        fire = campfire.toImmutable();
        center = Vec3d.ofCenter(fire).add(0, 2.0, 0);
        onEnd = then;
        spin = 0;
        CHAINS.clear();
        CHAIN_FROM.clear();
        for (Direction d : Direction.Type.HORIZONTAL) CHAIN_FROM.add(Vec3d.ofCenter(fire.offset(d, 3)).add(0, 0.75, 0));
        ItWasTall.LOGGER.info("The ritual's end begins at {}.", fire.toShortString());
    }

    private static void tick() {
        ServerWorld w = world;
        if (w == null) {
            running = false;
            return;
        }
        t++;
        // 1. the chains, one after another, link by link
        for (int k = 0; k < 4; k++) {
            int from = CHAIN_START + k * CHAIN_GAP;
            if (t == from) {
                List<DisplayEntity> chain = new ArrayList<>();
                for (int i = 0; i < SEGMENTS; i++) chain.add(spawnChainLink(w, CHAIN_FROM.get(k)));
                CHAINS.add(chain);
                w.playSound(null, BlockPos.ofFloored(CHAIN_FROM.get(k)), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1.5f, 0.6f);
            }
            if (k < CHAINS.size()) {
                int shown = MathHelper.clamp((t - from) * SEGMENTS / 16, 0, SEGMENTS);
                if (t % 2 == 0 || t - from <= 16) layChain(k, shown, t > RISE_FROM ? 2 : 0);
            }
        }
        Vec3d target = attach();
        // 2. It, lying on its back where the chains meet
        if (t == IT_AT) {
            TallOneEntity it = ModEntities.TALL_ONE.create(w);
            if (it != null) {
                it.makeBound();
                it.refreshPositionAndAngles(target.x, target.y - 0.3, target.z, 0f, 0f);
                it.setBodyYaw(0f);
                it.setHeadYaw(0f);
                w.spawnEntity(it);
                bound = it;
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, target.x, target.y, target.z, 60, 0.6, 0.6, 1.6, 0.02);
                w.playSound(null, BlockPos.ofFloored(target), ModSounds.SCREAM, SoundCategory.HOSTILE, 2f, 0.5f);
            }
        }
        // 3. it rises into the morning sky, the chains with it
        if (bound != null && t > RISE_FROM && t <= RISE_TO) {
            bound.refreshPositionAndAngles(target.x, target.y - 0.3, target.z, 0f, 0f);
            if (t % 40 == 0) w.playSound(null, BlockPos.ofFloored(target), ModSounds.GROWL, SoundCategory.HOSTILE, 1.6f, 0.5f);
        }
        // 4. the fire goes out, the ground glows red, the pentagram turns
        if (t == FIRE_OUT) {
            BlockState st = w.getBlockState(fire);
            if (st.isIn(BlockTags.CAMPFIRES)) w.setBlockState(fire, st.with(CampfireBlock.LIT, false));
            w.playSound(null, fire, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 2f, 0.6f);
            w.playSound(null, fire, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2f, 0.5f);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5, 40, 0.3, 0.4, 0.3, 0.02);
            spawnPentagram(w);
        }
        if (t >= FIRE_OUT && t < END) {
            spin += 2.5f;
            Vec3d c = Vec3d.ofBottomCenter(fire).add(0, 0.03, 0);
            for (DisplayEntity e : PENTAGRAM) e.refreshPositionAndAngles(c.x, c.y, c.z, spin, 0f);
            if (t % 2 == 0) {
                DustParticleEffect red = new DustParticleEffect(new Vector3f(0.9f, 0.05f, 0.03f), 1.6f);
                for (int i = 0; i < 6; i++) {
                    double a = w.random.nextDouble() * Math.PI * 2, r = 3.6 + w.random.nextGaussian() * 0.15;
                    w.spawnParticles(red, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0);
                }
                w.spawnParticles(ParticleTypes.CRIMSON_SPORE, c.x, c.y + 0.5, c.z, 8, 2.5, 0.3, 2.5, 0);
            }
        }
        // 5. It is gone
        if (t == IT_GONE) {
            if (bound != null) {
                Vec3d p = bound.getPos();
                w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                w.spawnParticles(ParticleTypes.SOUL, p.x, p.y, p.z, 80, 0.8, 1.6, 0.8, 0.05);
                w.playSound(null, bound.getBlockPos(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 2f, 0.5f);
                bound.discard();
                bound = null;
            }
            for (List<DisplayEntity> chain : CHAINS) for (DisplayEntity e : chain) e.discard();
            CHAINS.clear();
        }
        if (t >= END) {
            Runnable then = onEnd;
            cleanup();
            if (then != null) then.run();
        }
    }

    /** Where the chains meet: two blocks above the fire, and higher as It rises. */
    private static Vec3d attach() {
        double rise = t <= RISE_FROM ? 0 : Math.min(RISE_TO, t) - RISE_FROM;
        return center.add(0, rise * 0.06, 0);
    }

    // ------------------------------------------------------------------ the chains ----

    private static DisplayEntity spawnChainLink(ServerWorld w, Vec3d at) {
        DisplayEntity.BlockDisplayEntity e = EntityType.BLOCK_DISPLAY.create(w);
        e.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        NbtCompound nbt = e.writeNbt(new NbtCompound());
        nbt.put("block_state", NbtHelper.fromBlockState(Blocks.CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y)));
        nbt.put("transformation", transformation(new Vector3f(), new Quaternionf(), new Vector3f(0, 0, 0)));
        nbt.putFloat("view_range", 4f);
        e.readNbt(nbt);
        w.spawnEntity(e);
        return e;
    }

    /** Lays chain k from its pillar to where the chains meet; the first {@code shown} links are visible. */
    private static void layChain(int k, int shown, int interpolation) {
        Vec3d from = CHAIN_FROM.get(k), to = attach();
        Vector3f dir = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        float length = dir.length();
        if (length < 0.01f) return;
        dir.normalize();
        Quaternionf q = new Quaternionf().rotationTo(0, 1, 0, dir.x, dir.y, dir.z);
        float seg = length / SEGMENTS;
        List<DisplayEntity> chain = CHAINS.get(k);
        for (int i = 0; i < chain.size(); i++) {
            boolean visible = i < shown;
            Vector3f scale = visible ? new Vector3f(CHAIN_WIDTH, seg, CHAIN_WIDTH) : new Vector3f(0, 0, 0);
            Vector3f translation = q.transform(new Vector3f(-0.5f * CHAIN_WIDTH, i * seg, -0.5f * CHAIN_WIDTH));
            setTransformation(chain.get(i), translation, q, scale, interpolation);
        }
    }

    // ------------------------------------------------------------------ the pentagram ----

    private static void spawnPentagram(ServerWorld w) {
        PENTAGRAM.clear();
        double rStar = 3.2, rRing = 3.6;
        Vec3d[] pts = new Vec3d[5];
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(-90 + i * 72);
            pts[i] = new Vec3d(Math.cos(a) * rStar, 0, Math.sin(a) * rStar);
        }
        for (int i = 0; i < 5; i++) PENTAGRAM.add(spawnLine(w, pts[i], pts[(i + 2) % 5], 0.14f));
        int n = 28;
        for (int i = 0; i < n; i++) {
            double a1 = Math.PI * 2 * i / n, a2 = Math.PI * 2 * (i + 1) / n;
            PENTAGRAM.add(spawnLine(w, new Vec3d(Math.cos(a1) * rRing, 0, Math.sin(a1) * rRing),
                    new Vec3d(Math.cos(a2) * rRing, 0, Math.sin(a2) * rRing), 0.12f));
        }
    }

    /** A thin glowing red bar lying on the ground from p to q (relative to the fire), turning with the others. */
    private static DisplayEntity spawnLine(ServerWorld w, Vec3d p, Vec3d q, float width) {
        Vec3d c = Vec3d.ofBottomCenter(fire).add(0, 0.03, 0);
        DisplayEntity.BlockDisplayEntity e = EntityType.BLOCK_DISPLAY.create(w);
        e.refreshPositionAndAngles(c.x, c.y, c.z, 0f, 0f);
        double dx = q.x - p.x, dz = q.z - p.z;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        Quaternionf rot = new Quaternionf().rotationY((float) -Math.atan2(dz, dx));
        Vector3f translation = new Vector3f((float) p.x, 0, (float) p.z).add(rot.transform(new Vector3f(0, 0, -width / 2)));
        NbtCompound nbt = e.writeNbt(new NbtCompound());
        nbt.put("block_state", NbtHelper.fromBlockState(Blocks.REDSTONE_BLOCK.getDefaultState()));
        nbt.put("transformation", transformation(translation, rot, new Vector3f(len, 0.04f, width)));
        NbtCompound light = new NbtCompound();
        light.putInt("sky", 15);
        light.putInt("block", 15);
        nbt.put("brightness", light);
        nbt.putInt("glow_color_override", 0xFF1A10);
        nbt.putInt("teleport_duration", 2);
        e.readNbt(nbt);
        e.setGlowing(true);
        w.spawnEntity(e);
        return e;
    }

    // ------------------------------------------------------------------ helpers ----

    private static NbtCompound transformation(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        NbtCompound tr = new NbtCompound();
        tr.put("translation", floats(translation.x, translation.y, translation.z));
        tr.put("left_rotation", floats(rotation.x, rotation.y, rotation.z, rotation.w));
        tr.put("scale", floats(scale.x, scale.y, scale.z));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        return tr;
    }

    private static NbtList floats(float... v) {
        NbtList l = new NbtList();
        for (float f : v) l.add(NbtFloat.of(f));
        return l;
    }

    /** Like /data merge entity: a new transformation, animated over {@code interpolation} ticks on the client. */
    private static void setTransformation(DisplayEntity e, Vector3f translation, Quaternionf rotation, Vector3f scale, int interpolation) {
        NbtCompound nbt = e.writeNbt(new NbtCompound());
        nbt.put("transformation", transformation(translation, rotation, scale));
        nbt.putInt("interpolation_duration", interpolation);
        nbt.putInt("start_interpolation", 0);
        e.readNbt(nbt);
    }

    private static void cleanup() {
        for (List<DisplayEntity> chain : CHAINS) for (DisplayEntity e : chain) e.discard();
        CHAINS.clear();
        for (DisplayEntity e : PENTAGRAM) e.discard();
        PENTAGRAM.clear();
        if (bound != null) bound.discard();
        bound = null;
        running = false;
        onEnd = null;
    }
}
