package io.github.sakurawald.module.initializer.biome_lookup_cache;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Utility methods for getting chunks.
 *
 * @author Wesley1808
 */
public class ChunkManager {

    @NotNull
    public static Holder<Biome> getRoughBiome(Level level, BlockPos pos) {
        ChunkAccess chunk = getChunkNow(level, pos);
        int x = pos.getX() >> 2;
        int y = pos.getY() >> 2;
        int z = pos.getZ() >> 2;

        return chunk != null ? chunk.getNoiseBiome(x, y, z) : level.getUncachedNoiseBiome(x, y, z);
    }

    @Nullable
    public static ChunkAccess getChunkNow(LevelReader levelReader, BlockPos pos) {
        return getChunkNow(levelReader, pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Nullable
    public static ChunkAccess getChunkNow(LevelReader levelReader, int chunkX, int chunkZ) {
        if (levelReader instanceof ServerLevel level) {
            return getChunkFromHolder(getChunkHolder(level, chunkX, chunkZ));
        } else {
            return levelReader.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        }
    }

    @Nullable
    public static LevelChunk getChunkFromFuture(CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> chunkFuture) {
        Either<LevelChunk, ChunkHolder.ChunkLoadingFailure> either;
        if (chunkFuture == ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE || (either = chunkFuture.getNow(null)) == null) {
            return null;
        }

        return either.left().orElse(null);
    }

    @Nullable
    public static LevelChunk getChunkFromHolder(ChunkHolder holder) {
        return holder != null ? getChunkFromFuture(holder.getFullChunkFuture()) : null;
    }

    @Nullable
    private static ChunkHolder getChunkHolder(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
    }
}