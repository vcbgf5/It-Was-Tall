package com.itwastall.base;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * What It knows about the player's base (MODPLAN §8.3): windows, doors, lights, chests —
 * stored as compact integer markers around the anchor (the bed the player slept in).
 *
 * <p><b>Encoding (one int per marker):</b> bits 0-3 type, bits 4-12 / 13-21 / 22-30 the
 * SIGNED 9-bit local x / y / z offsets relative to the anchor (range +-255 per axis).
 * The anchor itself is a {@code long} via {@link BlockPos#asLong()} (full world coords).
 * This keeps the whole map small enough to ride inside {@code ItWasTallState}'s NBT
 * (one persistent system — MODPLAN rule: no second saving system).
 */
public final class BaseMap {
    /** Marker types (bits 0-3). */
    public static final int TYPE_WINDOW = 0;
    /** Upper half of a door (its base cell is the stored offset; doors are two blocks). */
    public static final int TYPE_DOOR = 1;
    public static final int TYPE_LIGHT = 2;
    public static final int TYPE_CHEST = 3;

    private static final int TYPE_BITS = 4;
    private static final int COORD_MASK = 0x1FF; // signed 9 bits: -255..255

    private long anchor;
    /** Encoded markers, unordered. */
    private final List<Integer> markers = new ArrayList<>();

    public BaseMap(long anchor) {
        this.anchor = anchor;
    }

    public long anchor() {
        return anchor;
    }

    public void setAnchor(long anchor) {
        this.anchor = anchor;
    }

    public BlockPos anchorPos() {
        return BlockPos.fromLong(anchor);
    }

    /** Add marker from a world position, re-anchored relative to the current anchor. */
    public void add(BlockPos anchorPos, int type, BlockPos pos) {
        int lx = pos.getX() - anchorPos.getX();
        int ly = pos.getY() - anchorPos.getY();
        int lz = pos.getZ() - anchorPos.getZ();
        if (Math.abs(lx) > 255 || Math.abs(ly) > 255 || Math.abs(lz) > 255) return;
        markers.add(encode(type, lx, ly, lz));
    }

    public static int encode(int type, int lx, int ly, int lz) {
        return (type & 0x7)
                | ((lx & COORD_MASK) << TYPE_BITS)
                | ((ly & COORD_MASK) << (TYPE_BITS + 9))
                | ((lz & COORD_MASK) << (TYPE_BITS + 18));
    }

    public int type(int marker) {
        return marker & 0x7;
    }

    public int localX(int marker) {
        int v = (marker >> TYPE_BITS) & COORD_MASK;
        return v > 255 ? v - 512 : v;
    }

    public int localY(int marker) {
        int v = (marker >> (TYPE_BITS + 9)) & COORD_MASK;
        return v > 255 ? v - 512 : v;
    }

    public int localZ(int marker) {
        int v = (marker >> (TYPE_BITS + 18)) & COORD_MASK;
        return v > 255 ? v - 512 : v;
    }

    /** World position of a marker (relative to the CURRENT anchor). */
    public BlockPos pos(int marker) {
        BlockPos a = anchorPos();
        return new BlockPos(a.getX() + localX(marker), a.getY() + localY(marker), a.getZ() + localZ(marker));
    }

    public int count() {
        return markers.size();
    }

    public int count(int type) {
        int n = 0;
        for (int m : markers) if ((m & 0x7) == type) n++;
        return n;
    }

    /**
     * All markers of one type as world positions. Used by WindowKnocks (windows), future
     * SearchBaseGoal / HideAndSeek task (rooms, dark corners) and the debug 3D view.
     */
    public List<BlockPos> all(int type) {
        List<BlockPos> out = new ArrayList<>();
        for (int m : markers) if ((m & 0x7) == type) out.add(pos(m));
        return out;
    }

    /** Clear all markers (keep the anchor) — a fresh rescan starts arriving. */
    public void clearMarkers() {
        markers.clear();
    }

    /** Copy of the encoded marker ints (debug 3D payload; state file uses the same array). */
    public int[] encodedMarkers() {
        int[] arr = new int[markers.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = markers.get(i);
        return arr;
    }

    /** Shared high-level test used by the scanner and WindowKnocks' fallback: does this
     *  block let the world see a figure at the window (glass, stained glass, panes)? */
    public static boolean isGlassy(net.minecraft.block.BlockState state) {
        if (state.getBlock() instanceof net.minecraft.block.StainedGlassBlock
                || state.getBlock() instanceof net.minecraft.block.PaneBlock
                || state.getBlock() instanceof net.minecraft.block.TintedGlassBlock) return true;
        return state.isIn(net.minecraft.registry.tag.BlockTags.IMPERMEABLE);
    }

    // -------------------------------------------------------------- NBT ----

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putLong("anchor", anchor);
        int[] arr = new int[markers.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = markers.get(i);
        nbt.put("markers", new NbtIntArray(arr));
        return nbt;
    }

    public static BaseMap fromNbt(NbtCompound nbt) {
        BaseMap map = new BaseMap(nbt.getLong("anchor"));
        int[] arr = nbt.getIntArray("markers");
        for (int v : arr) map.markers.add(v);
        return map;
    }

    @Override
    public @NotNull String toString() {
        return "BaseMap{windows=" + count(TYPE_WINDOW) + ", doors=" + count(TYPE_DOOR)
                + ", lights=" + count(TYPE_LIGHT) + ", chests=" + count(TYPE_CHEST) + "}";
    }
}
