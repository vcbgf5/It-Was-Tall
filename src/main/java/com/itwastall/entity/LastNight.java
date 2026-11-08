package com.itwastall.entity;

import com.itwastall.ItWasTall;
import com.itwastall.director.Director;
import com.itwastall.net.EndingPayload;
import com.itwastall.net.ModNetworking;
import com.itwastall.registry.ModBlocks;
import com.itwastall.registry.ModItems;
import com.itwastall.registry.ModSounds;
import com.itwastall.world.JournalLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CandleBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE LAST NIGHT — the way to end it. The Ritual Plan (in the archives of the Below) shows how:
 * <ul>
 *   <li>a burning campfire on a 3x3 floor of Oldrock Bricks;</li>
 *   <li>four Ritual Pillars, two high, three blocks from the fire (north, south, east, west), a lit candle on each;</li>
 *   <li>at night, the Hollow Heart (from the old altars deep in the Below) is given to the fire.</li>
 * </ul>
 * Then It comes, again and again, and blows the candles out. If any part of the ritual stays broken for 15 seconds,
 * it fails. If it still stands at dawn: the ending — It is gone, for good (the Director's phase becomes ENDED).
 */
public final class LastNight {
    private static final int GRACE_TICKS = 300;
    private static boolean active;
    @Nullable private static BlockPos fire;
    @Nullable private static RegistryKey<World> fireWorld;
    private static long brokenSince = -1, nextCandle, nextWhisper;
    private static final Map<UUID, Long> NEXT_IT = new HashMap<>();
    private static final String[] WHISPERS = {"keep it burning.", "it is close.", "don't let the fire die.",
            "it wants the candles.", "not long now.", "don't look at it.", "the sun is far away."};

    private LastNight() {}

    public static boolean active() {
        return active;
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(EndingPayload.ID, EndingPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(LastNight::tick);
    }

    /** The Hollow Heart: given to the burning fire of a complete ritual at night, it begins the last night. */
    public static class HollowHeartItem extends Item {
        public HollowHeartItem(Settings settings) {
            super(settings);
        }

        @Override
        public ActionResult useOnBlock(ItemUsageContext context) {
            if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
                return context.getWorld().getBlockState(context.getBlockPos()).isIn(BlockTags.CAMPFIRES) ? ActionResult.SUCCESS : ActionResult.PASS;
            }
            BlockPos pos = context.getBlockPos();
            if (!context.getWorld().getBlockState(pos).isIn(BlockTags.CAMPFIRES)) return ActionResult.PASS;
            return tryStart(player, pos, context.getStack()) ? ActionResult.CONSUME : ActionResult.FAIL;
        }
    }

    /** What is still missing at this fire, in the words of the plan (null = the ritual is complete). */
    @Nullable
    public static String missing(World w, BlockPos c) {
        BlockState f = w.getBlockState(c);
        if (!f.isIn(BlockTags.CAMPFIRES)) return "the fire is gone";
        if (!f.get(CampfireBlock.LIT)) return "the fire must burn";
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!w.getBlockState(c.add(dx, -1, dz)).isOf(ModBlocks.OLDROCK_BRICKS)) return "a 3x3 floor of Oldrock Bricks under the fire";
            }
        }
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos p = c.offset(d, 3);
            if (!w.getBlockState(p.down()).isOf(ModBlocks.RITUAL_PILLAR) || !w.getBlockState(p).isOf(ModBlocks.RITUAL_PILLAR)) {
                return "four Ritual Pillars, two high, three blocks from the fire";
            }
            BlockState candle = w.getBlockState(p.up());
            if (!candle.isIn(BlockTags.CANDLES) || !candle.get(CandleBlock.LIT)) return "a lit candle on every pillar";
        }
        return null;
    }

    public static boolean tryStart(ServerPlayerEntity player, BlockPos c, ItemStack heart) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        if (active) return say(player, "The last night has already begun.");
        if (Director.phase() == 2) return say(player, "There is nothing left to send away.");
        if (Director.phase() != 1) return say(player, "Nothing is listening. Not yet.");
        String miss = missing(player.getWorld(), c);
        if (miss != null) return say(player, "The ritual is not complete: " + miss + ".");
        long t = server.getOverworld().getTimeOfDay() % 24000L;
        if (t < 13000 || t > 21500) return say(player, "It only listens at night.");
        if (!player.isCreative()) heart.decrement(1);
        start(server, player.getServerWorld(), c);
        return true;
    }

    private static boolean say(ServerPlayerEntity p, String text) {
        p.sendMessage(Text.literal(text).formatted(Formatting.GRAY, Formatting.ITALIC), true);
        return false;
    }

    private static void start(MinecraftServer server, ServerWorld world, BlockPos c) {
        active = true;
        fire = c.toImmutable();
        fireWorld = world.getRegistryKey();
        brokenSince = -1;
        long now = server.getOverworld().getTime();
        nextCandle = now + 20L * 90;
        nextWhisper = now + 20L * 30;
        NEXT_IT.clear();
        world.playSound(null, c, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.AMBIENT, 1.5f, 0.6f);
        FogNight.start(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            title(p, "THE LAST NIGHT", Formatting.DARK_RED, "Keep the fire burning until dawn.");
            JournalLog.add(p, "The heart went into the fire. The last night began. Everything came for the fire.");
            com.itwastall.advancement.Achievements.grant(p, "last_night");
        }
        ItWasTall.LOGGER.info("The last night has begun at {}.", c.toShortString());
    }

    private static void title(ServerPlayerEntity p, String title, Formatting color, String sub) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 80, 30));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(sub).formatted(Formatting.GRAY)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title).formatted(color, Formatting.BOLD)));
    }

    private static void tick(MinecraftServer server) {
        if (!active || server.getTicks() % 20 != 0) return;
        ServerWorld world = fireWorld == null ? null : server.getWorld(fireWorld);
        if (world == null || fire == null) {
            active = false;
            return;
        }
        long now = server.getOverworld().getTime();
        long t = server.getOverworld().getTimeOfDay() % 24000L;
        if (t >= 23000 || t < 12000) {
            succeed(server);
            return;
        }
        List<ServerPlayerEntity> near = world.getPlayers(p -> !p.isSpectator() && p.getBlockPos().isWithinDistance(fire, 80));
        if (world.isChunkLoaded(fire.getX() >> 4, fire.getZ() >> 4)) {
            String miss = missing(world, fire);
            if (miss == null) {
                brokenSince = -1;
            } else {
                if (brokenSince < 0) brokenSince = now;
                long left = (GRACE_TICKS - (now - brokenSince)) / 20;
                for (ServerPlayerEntity p : near) {
                    p.sendMessage(Text.literal("The ritual is breaking: " + miss + "! " + Math.max(0, left) + " s")
                            .formatted(Formatting.RED, Formatting.BOLD), true);
                }
                if (now - brokenSince >= GRACE_TICKS) {
                    fail(server, near);
                    return;
                }
            }
        }
        Random r = world.getRandom();
        // It blows a candle out now and then
        if (now >= nextCandle) {
            nextCandle = now + 20L * (70 + r.nextInt(70));
            Direction d = Direction.Type.HORIZONTAL.random(r);
            BlockPos candle = fire.offset(d, 3).up();
            BlockState st = world.getBlockState(candle);
            if (st.isIn(BlockTags.CANDLES) && st.get(CandleBlock.LIT)) {
                world.setBlockState(candle, st.with(CandleBlock.LIT, false));
                world.playSound(null, candle, SoundEvents.BLOCK_CANDLE_EXTINGUISH, SoundCategory.BLOCKS, 1.5f, 0.6f);
                world.playSound(null, candle, ModSounds.AMBIENT_BREATH, SoundCategory.AMBIENT, 1.5f, 0.7f);
            }
        }
        // It comes for the people at the fire, again and again
        for (ServerPlayerEntity p : near) {
            Long next = NEXT_IT.get(p.getUuid());
            if (next == null) {
                NEXT_IT.put(p.getUuid(), now + 20L * (15 + r.nextInt(20)));
            } else if (now >= next && !p.isCreative()) {
                NEXT_IT.put(p.getUuid(), now + 20L * (45 + r.nextInt(40)));
                TallOneEntity it = TheBelow.spawnItInSight(p, 10, 20);
                if (it != null) it.setForced(true);
            }
        }
        if (now >= nextWhisper) {
            nextWhisper = now + 20L * (40 + r.nextInt(40));
            for (ServerPlayerEntity p : near) ModNetworking.sendScareText(p, WHISPERS[r.nextInt(WHISPERS.length)]);
        }
    }

    private static void fail(MinecraftServer server, List<ServerPlayerEntity> near) {
        active = false;
        FogNight.stop(server);
        for (ServerPlayerEntity p : near) ModNetworking.sendJumpscare(p);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            title(p, "IT WAS NOT ENOUGH", Formatting.DARK_RED, "The fire died. It is still here.");
            JournalLog.add(p, "The ritual broke. The fire died, and it laughed. The heart is gone. I will need another.");
        }
        ItWasTall.LOGGER.info("The last night failed.");
    }

    private static void succeed(MinecraftServer server) {
        active = false;
        FogNight.stop(server);
        HauntDirector.clearForCommand(server);
        Director.end(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            JournalLog.add(p, "The sun came up. The fire was still burning. The edge of the forest was empty. It was gone.");
            com.itwastall.advancement.Achievements.grant(p, "banished");
            if (ServerPlayNetworking.canSend(p, EndingPayload.ID)) ServerPlayNetworking.send(p, EndingPayload.INSTANCE);
        }
        ItWasTall.LOGGER.info("The last night is over. It is gone.");
    }

    /** Test: the whole ritual built a few blocks in front of the player, candles lit, and a heart in their hand. */
    public static boolean buildInFront(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        BlockPos c = p.getBlockPos().offset(p.getHorizontalFacing(), 6);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy <= 3; dy++) {
            w.setBlockState(c.add(dx, dy, dz), Blocks.AIR.getDefaultState());
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.setBlockState(c.add(dx, -1, dz), ModBlocks.OLDROCK_BRICKS.getDefaultState());
        w.setBlockState(c, Blocks.CAMPFIRE.getDefaultState());
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos q = c.offset(d, 3);
            w.setBlockState(q.down(), ModBlocks.RITUAL_PILLAR.getDefaultState());
            w.setBlockState(q, ModBlocks.RITUAL_PILLAR.getDefaultState());
            w.setBlockState(q.up(), Blocks.CANDLE.getDefaultState().with(CandleBlock.LIT, true));
        }
        p.getInventory().offerOrDrop(new ItemStack(ModItems.HOLLOW_HEART));
        return true;
    }

    /** Test: only the ending screen, for this player (nothing changes in the world). */
    public static void showEnding(ServerPlayerEntity p) {
        if (ServerPlayNetworking.canSend(p, EndingPayload.ID)) ServerPlayNetworking.send(p, EndingPayload.INSTANCE);
    }
}
