package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.config.ItWasTallConfig;
import com.itwastall.director.Director;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModBlocks;
import com.itwastall.registry.ModDimensions;
import com.itwastall.registry.ModEntities;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.BelowCaves;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * THE BELOW — what is under the bedrock: endless dark caves ({@link BelowCaves}).
 * <ul>
 *   <li>The way in: fall into the void under the Overworld (you do not die — you land down there), or break
 *   through CRACKED BEDROCK, which now and then appears in the floor of the deepest mines (stage 2+).</li>
 *   <li>Down there: dripping, footsteps that are not yours, breathing behind you, digging far away; bones, skulls,
 *   cobwebs and the cold camps of those who came before; and It, standing at the far end of a cave.</li>
 *   <li>The way out: live until dawn, or die. Either way you wake up in your bed, with everything you carried.</li>
 * </ul>
 */
public final class TheBelow {
    private static final Map<UUID, Long> NEXT_SOUND = new HashMap<>();
    private static final Map<UUID, Long> NEXT_IT = new HashMap<>();
    private static final Map<UUID, Long> NEXT_CRACK = new HashMap<>();
    private static final Set<Long> DECORATED = new HashSet<>();
    private static long lastDayTime = -1;

    private TheBelow() {}

    public static boolean inBelow(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey() == ModDimensions.BELOW;
    }

    public static void init() {
        // dying down there costs nothing: you wake up in your bed with everything you carried
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity sp && inBelow(sp)) {
                sp.setHealth(sp.getMaxHealth());
                sp.extinguish();
                sp.clearStatusEffects();
                sendHome(sp, true);
                return false;
            }
            return true;
        });
        ServerTickEvents.END_SERVER_TICK.register(TheBelow::tick);
    }

    public static void forget(UUID player) {
        NEXT_SOUND.remove(player);
        NEXT_IT.remove(player);
        NEXT_CRACK.remove(player);
    }

    // ------------------------------------------------------------------ ticking ----

    private static void tick(MinecraftServer server) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (!cfg.below) return;
        ServerWorld over = server.getOverworld();
        // the void: whoever falls out of the bottom of the Overworld lands in the caves instead of dying
        for (ServerPlayerEntity p : over.getPlayers()) {
            if (!p.isSpectator() && p.getY() < over.getBottomY() - 6) enter(p, true);
        }
        if (server.getTicks() % 20 != 0) return;
        long now = over.getTime();
        // dawn, somewhere far above: everyone down there wakes up at home
        long day = Math.floorMod(over.getTimeOfDay(), 24000L);
        boolean dawn = lastDayTime >= 0 && lastDayTime < 23500 && day >= 23500;
        lastDayTime = day;
        ServerWorld below = server.getWorld(ModDimensions.BELOW);
        if (below != null) {
            for (ServerPlayerEntity p : java.util.List.copyOf(below.getPlayers())) {
                if (dawn) {
                    sendHome(p, false);
                    continue;
                }
                ambience(p, now);
                maybeIt(p, now);
                decorateAround(below, p);
            }
        }
        for (ServerPlayerEntity p : over.getPlayers()) maybeCrack(p, now);
    }

    // ------------------------------------------------------------------ going down ----

    /** Drops the player into the caves (void = they fell out of the world). False when the dimension is missing. */
    public static boolean enter(ServerPlayerEntity player, boolean fromVoid) {
        MinecraftServer server = player.getServer();
        if (server == null || inBelow(player)) return false;
        ServerWorld below = server.getWorld(ModDimensions.BELOW);
        if (below == null) {
            ItWasTall.LOGGER.warn("The Below dimension is missing (datapack not loaded?).");
            return false;
        }
        BlockPos spot = landing(below, player.getBlockX(), player.getBlockZ(), player.getRandom());
        player.fallDistance = 0;
        player.setVelocity(Vec3d.ZERO);
        player.teleportTo(new TeleportTarget(below, Vec3d.ofBottomCenter(spot), Vec3d.ZERO, player.getYaw(), 0f, TeleportTarget.NO_OP));
        player.fallDistance = 0;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1, false, false));
        long now = server.getOverworld().getTime();
        NEXT_SOUND.put(player.getUuid(), now + 100);
        NEXT_IT.put(player.getUuid(), now + 20L * (45 + player.getRandom().nextInt(45)));
        ModNetworking.sendScareText(player, "there is something under everything.");
        com.itwastall.advancement.Achievements.grant(player, "below");
        JournalLog.add(player, fromVoid
                ? "I fell out of the world. I did not die. I landed in caves that should not be there."
                : "I broke through the bedrock. There was nothing under it, and then there were caves.");
        ItWasTall.LOGGER.info("{} went down into the Below ({}).", player.getName().getString(), fromVoid ? "void" : "cracked bedrock");
        return true;
    }

    /** A dry floor with room above it, near x, z (generated on demand); last resort: a small hollow carved out. */
    private static BlockPos landing(ServerWorld world, int x, int z, Random random) {
        for (int r = 0; r <= 64; r += 4) {
            for (int attempt = 0; attempt < (r == 0 ? 1 : 8); attempt++) {
                double a = random.nextDouble() * Math.PI * 2;
                int cx = x + (int) Math.round(Math.cos(a) * r), cz = z + (int) Math.round(Math.sin(a) * r);
                world.getChunk(cx >> 4, cz >> 4);
                BlockPos floor = floorIn(world, cx, cz, 100, 14);
                if (floor != null) return floor;
            }
        }
        BlockPos p = new BlockPos(x, 60, z);
        for (BlockPos q : BlockPos.iterate(p.add(-1, 0, -1), p.add(1, 2, 1))) world.setBlockState(q, Blocks.AIR.getDefaultState());
        return p;
    }

    /** The highest spot in this column between top and bottom: solid dry floor, three blocks of air above it. */
    private static BlockPos floorIn(ServerWorld world, int x, int z, int top, int bottom) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int y = top; y > bottom; y--) {
            m.set(x, y - 1, z);
            BlockState under = world.getBlockState(m);
            if (under.isAir() || !under.getFluidState().isEmpty() || under.getCollisionShape(world, m).isEmpty()) continue;
            boolean room = true;
            for (int dy = 0; dy < 3 && room; dy++) {
                BlockState s = world.getBlockState(m.set(x, y + dy, z));
                room = s.isAir() || s.getCollisionShape(world, m).isEmpty() && s.getFluidState().isEmpty();
            }
            if (room) return new BlockPos(x, y, z);
        }
        return null;
    }

    // ------------------------------------------------------------------ coming back ----

    /** Back to the bed (or the world spawn). {@code died} = it was death that sent them back. */
    public static void sendHome(ServerPlayerEntity player, boolean died) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        RegistryKey<World> dim = player.getSpawnPointPosition() != null ? player.getSpawnPointDimension() : World.OVERWORLD;
        ServerWorld world = server.getWorld(dim);
        if (world == null || world.getRegistryKey() == ModDimensions.BELOW) world = server.getOverworld();
        BlockPos bed = player.getSpawnPointPosition();
        Vec3d target = null;
        if (bed != null) {
            BlockState st = world.getBlockState(bed);
            if (st.getBlock() instanceof BedBlock) {
                Direction facing = st.get(BedBlock.FACING);
                target = BedBlock.findWakeUpPosition(EntityType.PLAYER, world, bed, facing, player.getYaw()).orElse(null);
            }
        }
        if (target == null) {
            BlockPos spawn = world.getSpawnPos();
            target = Vec3d.ofBottomCenter(world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn));
        }
        player.fallDistance = 0;
        player.teleportTo(new TeleportTarget(world, target, Vec3d.ZERO, player.getYaw(), 0f, TeleportTarget.NO_OP));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0, false, false));
        forget(player.getUuid());
        if (died) {
            player.sendMessage(Text.literal("Something found you in the dark. You woke up in your bed - with everything you carried.")
                    .formatted(Formatting.GRAY, Formatting.ITALIC), true);
            JournalLog.add(player, "Something found me in the dark under the world. I woke up in my bed. Everything was still in my pockets.");
        } else {
            player.sendMessage(Text.literal("Somewhere far above, the sun came up. You woke up in your bed.")
                    .formatted(Formatting.GRAY, Formatting.ITALIC), true);
            JournalLog.add(player, "Dawn came, somewhere far above. I woke up in my bed, covered in stone dust.");
            com.itwastall.advancement.Achievements.grant(player, "below_dawn");
        }
    }

    // ------------------------------------------------------------------ down there ----

    private static final SoundEvent[] FAR = {ModSounds.AMBIENT_CAVE_DRIP, ModSounds.AMBIENT_CAVE_DRIP, ModSounds.AMBIENT_ROCK_FALL,
            ModSounds.DIG, ModSounds.AMBIENT_CHAINS, ModSounds.AMBIENT_DISTANT_SCREAM, ModSounds.GROWL, ModSounds.AMBIENT_HUM};
    private static final SoundEvent[] NEAR = {ModSounds.SCARE_FOOTSTEPS, ModSounds.AMBIENT_BREATH, ModSounds.AMBIENT_WHISPER,
            ModSounds.SNIFF, ModSounds.SCARE_PEBBLE, ModSounds.SCARE_GIGGLE};

    /** Sounds of the caves, for this player only: mostly far away, sometimes right behind them. */
    private static void ambience(ServerPlayerEntity p, long now) {
        Long next = NEXT_SOUND.get(p.getUuid());
        if (next != null && now < next) return;
        Random r = p.getRandom();
        NEXT_SOUND.put(p.getUuid(), now + 20L * (6 + r.nextInt(14)));
        boolean near = r.nextFloat() < 0.3f;
        SoundEvent s = near ? NEAR[r.nextInt(NEAR.length)] : FAR[r.nextInt(FAR.length)];
        double yaw = near ? p.getYaw() * MathHelper.RADIANS_PER_DEGREE + Math.PI + (r.nextDouble() - 0.5)
                : r.nextDouble() * Math.PI * 2;
        double d = near ? 2.5 + r.nextDouble() * 2 : 12 + r.nextDouble() * 16;
        Vec3d at = new Vec3d(p.getX() - Math.sin(yaw) * d, p.getEyeY() + (r.nextDouble() - 0.5) * 4, p.getZ() + Math.cos(yaw) * d);
        MineEvents.playTo(p, s, at, near ? 0.8f : 1.4f, 0.7f + r.nextFloat() * 0.3f);
    }

    /** Now and then It stands at the far end of a cave, in clear sight, and watches. */
    private static void maybeIt(ServerPlayerEntity p, long now) {
        Long next = NEXT_IT.get(p.getUuid());
        if (next == null || now < next || p.isCreative()) return;
        Random r = p.getRandom();
        NEXT_IT.put(p.getUuid(), now + 20L * (60 + r.nextInt(90)));
        spawnItInSight(p, 12, 26);
    }

    /** It, standing on open ground minD..maxD blocks away, in clear sight of the player, watching (false = no spot). */
    public static boolean spawnItInSight(ServerPlayerEntity p, double minD, double maxD) {
        Random r = p.getRandom();
        MinecraftServer server = p.getServer();
        if (server == null || HauntDirector.countActive(server) >= Math.max(1, ItWasTallConfig.INSTANCE.maxActiveIts)) return false;
        ServerWorld world = p.getServerWorld();
        for (int attempt = 0; attempt < 30; attempt++) {
            double yaw = (p.getYaw() + (r.nextDouble() - 0.5) * 140) * MathHelper.RADIANS_PER_DEGREE;
            double d = minD + r.nextDouble() * (maxD - minD);
            int x = MathHelper.floor(p.getX() - Math.sin(yaw) * d), z = MathHelper.floor(p.getZ() + Math.cos(yaw) * d);
            BlockPos floor = floorIn(world, x, z, p.getBlockY() + 6, p.getBlockY() - 6);
            if (floor == null || !world.getBlockState(floor.up(3)).isAir()) continue;
            Vec3d head = new Vec3d(x + 0.5, floor.getY() + 2.8, z + 0.5);
            if (world.raycast(new RaycastContext(p.getEyePos(), head, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p)).getType() != HitResult.Type.MISS) continue;
            TallOneEntity it = ModEntities.TALL_ONE.create(world);
            if (it == null) return false;
            it.refreshPositionAndAngles(x + 0.5, floor.getY(), z + 0.5, 0f, 0f);
            if (!world.isSpaceEmpty(it)) continue;
            it.setVictim(p);
            return world.spawnEntity(it);
        }
        return false;
    }

    /** The chunks around the player get their bones, webs and cold camps once (the same ones every time). */
    private static void decorateAround(ServerWorld world, ServerPlayerEntity p) {
        ChunkPos c = p.getChunkPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int cx = c.x + dx, cz = c.z + dz;
                if (!DECORATED.add(ChunkPos.toLong(cx, cz)) || !world.isChunkLoaded(cx, cz)) continue;
                decorateChunk(world, cx, cz);
            }
        }
    }

    private static void decorateChunk(ServerWorld world, int cx, int cz) {
        Random r = Random.create(ChunkPos.toLong(cx, cz) * 31L + 6_413_113L);
        int n = r.nextInt(4);
        for (int i = 0; i < n; i++) {
            int x = (cx << 4) + r.nextInt(16), z = (cz << 4) + r.nextInt(16);
            BlockPos floor = floorIn(world, x, z, 110, 13);
            if (floor == null) continue;
            int kind = r.nextInt(10);
            if (kind < 4) {                                            // a heap of bones with a skull on it
                world.setBlockState(floor.down(), Blocks.BONE_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(floor, Blocks.SKELETON_SKULL.getDefaultState().with(SkullBlock.ROTATION, r.nextInt(16)), Block.NOTIFY_LISTENERS);
            } else if (kind < 7) {                                     // cobwebs under the ceiling
                BlockPos.Mutable m = floor.mutableCopy();
                while (m.getY() < 120 && world.getBlockState(m.up()).isAir()) m.move(Direction.UP);
                if (m.getY() < 120) world.setBlockState(m, Blocks.COBWEB.getDefaultState(), Block.NOTIFY_LISTENERS);
            } else if (kind < 9) {                                     // a candle someone left, long burnt out
                world.setBlockState(floor, Blocks.CANDLE.getDefaultState(), Block.NOTIFY_LISTENERS);
            } else {                                                   // the cold camp of someone who came before
                world.setBlockState(floor, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, false), Block.NOTIFY_LISTENERS);
                BlockPos chest = floor.east();
                if (world.getBlockState(chest).isAir() && !world.getBlockState(chest.down()).isAir()) {
                    world.setBlockState(chest, Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS);
                    if (world.getBlockEntity(chest) instanceof ChestBlockEntity be) {
                        be.setStack(r.nextInt(27), new ItemStack(Items.TORCH, 2 + r.nextInt(5)));
                        be.setStack(r.nextInt(27), new ItemStack(Items.BREAD, 1 + r.nextInt(3)));
                        if (r.nextInt(3) == 0) be.setStack(r.nextInt(27), new ItemStack(Items.IRON_INGOT, 1 + r.nextInt(4)));
                        if (r.nextInt(4) == 0) be.setStack(r.nextInt(27), new ItemStack(Items.BONE, 2 + r.nextInt(4)));
                    }
                }
                BlockPos skull = floor.west();
                if (world.getBlockState(skull).isAir()) {
                    world.setBlockState(skull, Blocks.SKELETON_SKULL.getDefaultState().with(SkullBlock.ROTATION, r.nextInt(16)), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the crack ----

    /** In the deepest mine, now and then (stage 2+), the bedrock near the player cracks. */
    private static void maybeCrack(ServerPlayerEntity p, long now) {
        ItWasTallConfig.Values cfg = ItWasTallConfig.INSTANCE;
        if (p.isCreative() || p.isSpectator() || Director.phase() != 1 || Director.stageIndex() < cfg.belowMinStage) return;
        ServerWorld world = p.getServerWorld();
        if (p.getY() > world.getBottomY() + 14 || !MineEvents.isUnderground(p)) return;
        int minutes = Math.max(1, cfg.crackedBedrockMinutes);
        Long next = NEXT_CRACK.get(p.getUuid());
        if (next == null) {
            NEXT_CRACK.put(p.getUuid(), now + 20L * 60 * (minutes + p.getRandom().nextInt(minutes + 1)) / 2);
            return;
        }
        if (now < next) return;
        NEXT_CRACK.put(p.getUuid(), now + 20L * 60 * (minutes + p.getRandom().nextInt(minutes + 1)));
        BlockPos at = crackNear(p, 3, 8);
        if (at != null) JournalLog.add(p, "I heard digging from UNDER the bedrock. Then the floor of the world cracked.");
    }

    /** Cracks a 2x2 of bedrock (all of its layers) a few blocks from the player. Returns where, or null. */
    public static BlockPos crackNear(ServerPlayerEntity p, int minDist, int maxDist) {
        ServerWorld world = p.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return null;
        Random r = p.getRandom();
        int bottom = world.getBottomY();
        for (int attempt = 0; attempt < 20; attempt++) {
            double a = r.nextDouble() * Math.PI * 2, d = minDist + r.nextDouble() * (maxDist - minDist);
            int x = MathHelper.floor(p.getX() + Math.cos(a) * d), z = MathHelper.floor(p.getZ() + Math.sin(a) * d);
            if (!world.getBlockState(new BlockPos(x, bottom, z)).isOf(Blocks.BEDROCK)) continue;
            for (int ox = 0; ox < 2; ox++) {
                for (int oz = 0; oz < 2; oz++) {
                    for (int y = bottom; y <= bottom + 5; y++) {
                        BlockPos q = new BlockPos(x + ox, y, z + oz);
                        if (world.getBlockState(q).isOf(Blocks.BEDROCK)) world.setBlockState(q, ModBlocks.CRACKED_BEDROCK.getDefaultState());
                    }
                }
            }
            BlockPos at = new BlockPos(x, bottom + 4, z);
            MineEvents.playTo(p, ModSounds.DIG, Vec3d.ofCenter(at), 1.2f, 0.6f);
            MineEvents.playTo(p, ModSounds.AMBIENT_ROCK_FALL, Vec3d.ofCenter(at), 1.0f, 0.8f);
            return at;
        }
        return null;
    }
}
