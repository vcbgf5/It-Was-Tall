package com.itwastall.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Chunk generator of THE BELOW, the caves under the bedrock: every block comes from {@link BelowCaves}.
 * No features, no structures, no mobs — only the dark.
 */
public class BelowChunkGenerator extends ChunkGenerator {
    public static final MapCodec<BelowChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.LONG.optionalFieldOf("seed", 6_413_113L).forGetter(g -> g.seed)
    ).apply(instance, BelowChunkGenerator::new));

    private final long seed;

    public BelowChunkGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource);
        this.seed = seed;
    }

    public long seed() {
        return seed;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        Heightmap ocean = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        int baseX = chunk.getPos().getStartX(), baseZ = chunk.getPos().getStartZ();
        int bottom = Math.max(chunk.getBottomY(), 0), top = Math.min(chunk.getTopY(), BelowCaves.HEIGHT);
        BlockState[] column = new BlockState[BelowCaves.HEIGHT];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                BelowCaves.column(seed, baseX + lx, baseZ + lz, column);
                for (int y = bottom; y < top; y++) {
                    BlockState st = column[y];
                    if (st.isAir()) continue;
                    chunk.setBlockState(p.set(lx, y, lz), st, false);
                    ocean.trackUpdate(lx, y, lz, st);
                    surface.trackUpdate(lx, y, lz, st);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
                      StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
    }

    @Override
    public void populateEntities(ChunkRegion region) {
    }

    @Override
    public int getWorldHeight() {
        return BelowCaves.HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinimumY() {
        return 0;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        BlockState[] column = new BlockState[BelowCaves.HEIGHT];
        BelowCaves.column(seed, x, z, column);
        for (int y = Math.min(world.getTopY(), BelowCaves.HEIGHT) - 1; y >= Math.max(0, world.getBottomY()); y--) {
            if (heightmap.getBlockPredicate().test(column[y])) return y + 1;
        }
        return world.getBottomY();
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        BlockState[] full = new BlockState[BelowCaves.HEIGHT];
        BelowCaves.column(seed, x, z, full);
        BlockState[] column = new BlockState[world.getHeight()];
        for (int i = 0; i < column.length; i++) {
            int y = world.getBottomY() + i;
            column[i] = y >= 0 && y < full.length ? full[y] : net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        return new VerticalBlockSample(world.getBottomY(), column);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        text.add("The Below");
    }
}
