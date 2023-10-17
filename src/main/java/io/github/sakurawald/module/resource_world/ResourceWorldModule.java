package io.github.sakurawald.module.resource_world;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Lifecycle;
import io.github.sakurawald.ServerMain;
import io.github.sakurawald.config.ConfigManager;
import io.github.sakurawald.mixin.resource_world.MinecraftServerAccessor;
import io.github.sakurawald.module.AbstractModule;
import io.github.sakurawald.module.newbie_welcome.RandomTeleport;
import io.github.sakurawald.module.resource_world.interfaces.DimensionOptionsMixinInterface;
import io.github.sakurawald.module.resource_world.interfaces.SimpleRegistryMixinInterface;
import io.github.sakurawald.util.MessageUtil;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.Util;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.RandomSupport;

import java.time.LocalTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static net.minecraft.commands.Commands.literal;

@Slf4j
public class ResourceWorldModule extends AbstractModule {

    private final String DEFAULT_RESOURCE_WORLD_NAMESPACE = "resource_world";
    private final String DEFAULT_THE_NETHER_PATH = "the_nether";
    private final String DEFAULT_THE_END_PATH = "the_end";
    private final String DEFAULT_OVERWORLD_PATH = "overworld";


    @Override
    public Supplier<Boolean> enableModule() {
        return () -> ConfigManager.configWrapper.instance().modules.resource_world.enable;
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(this::registerCommand);
        ServerWorldEvents.UNLOAD.register(this::onWorldUnload);
        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            this.loadWorlds(server);
            this.registerScheduleTask(server);
        });
    }

    public void registerScheduleTask(MinecraftServer server) {
        LocalTime now = LocalTime.now();
        long initialDelay = LocalTime.of(20, 0).toSecondOfDay() - now.toSecondOfDay();
        if (initialDelay < 0) {
            initialDelay += TimeUnit.DAYS.toSeconds(1);
        }
        ServerMain.getSCHEDULED_EXECUTOR_SERVICE().scheduleAtFixedRate(() -> {
            log.info("Start to reset resource worlds.");
            server.execute(() -> this.resetWorlds(server));
        }, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }

    @SuppressWarnings("unused")
    public void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(
                Commands.literal("rw")
                        .then(literal("reset").requires(source -> source.hasPermission(4)).executes(this::$reset))
                        .then(literal("delete").requires(source -> source.hasPermission(4)).then(literal(DEFAULT_OVERWORLD_PATH).executes(this::$delete))
                                .then(literal(DEFAULT_THE_NETHER_PATH).executes(this::$delete))
                                .then(literal(DEFAULT_THE_END_PATH).executes(this::$delete)))
                        .then(literal("tp").then(literal(DEFAULT_OVERWORLD_PATH).executes(this::$tp))
                                .then(literal(DEFAULT_THE_NETHER_PATH).executes(this::$tp))
                                .then(literal(DEFAULT_THE_END_PATH).executes(this::$tp))
                        )
        );
    }

    private int $reset(CommandContext<CommandSourceStack> ctx) {
        resetWorlds(ctx.getSource().getServer());
        return Command.SINGLE_SUCCESS;
    }

    private void resetWorlds(MinecraftServer server) {
        MessageUtil.sendBroadcast("resource_world.world.reset");
        ConfigManager.configWrapper.instance().modules.resource_world.seed = RandomSupport.generateUniqueSeed();
        ConfigManager.configWrapper.saveToDisk();
        deleteWorld(server, DEFAULT_OVERWORLD_PATH);
        deleteWorld(server, DEFAULT_THE_NETHER_PATH);
        deleteWorld(server, DEFAULT_THE_END_PATH);
    }

    public void loadWorlds(MinecraftServer server) {
        long seed = ConfigManager.configWrapper.instance().modules.resource_world.seed;
        createWorld(server, BuiltinDimensionTypes.OVERWORLD, DEFAULT_OVERWORLD_PATH, seed);
        createWorld(server, BuiltinDimensionTypes.NETHER, DEFAULT_THE_NETHER_PATH, seed);
        createWorld(server, BuiltinDimensionTypes.END, DEFAULT_THE_END_PATH, seed);
    }

    @SuppressWarnings("DataFlowIssue")
    private ChunkGenerator getChunkGenerator(MinecraftServer server, ResourceKey<DimensionType> dimensionTypeRegistryKey) {
        if (dimensionTypeRegistryKey == BuiltinDimensionTypes.OVERWORLD) {
            return server.getLevel(Level.OVERWORLD).getChunkSource().getGenerator();
        }
        if (dimensionTypeRegistryKey == BuiltinDimensionTypes.NETHER) {
            return server.getLevel(Level.NETHER).getChunkSource().getGenerator();
        }
        if (dimensionTypeRegistryKey == BuiltinDimensionTypes.END) {
            return server.getLevel(Level.END).getChunkSource().getGenerator();
        }
        return null;
    }

    private LevelStem createDimensionOptions(MinecraftServer server, ResourceKey<DimensionType> dimensionTypeRegistryKey) {
        Holder<DimensionType> dimensionTypeRegistryEntry = getDimensionTypeRegistryEntry(server, dimensionTypeRegistryKey);
        ChunkGenerator chunkGenerator = getChunkGenerator(server, dimensionTypeRegistryKey);
        return new LevelStem(dimensionTypeRegistryEntry, chunkGenerator);
    }

    private Holder<DimensionType> getDimensionTypeRegistryEntry(MinecraftServer server, ResourceKey<DimensionType> dimensionTypeRegistryKey) {
        return server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getHolder(dimensionTypeRegistryKey).orElse(null);
    }

    private ResourceKey<DimensionType> getDimensionTypeRegistryKeyByPath(String path) {
        if (path.equals(DEFAULT_OVERWORLD_PATH)) return BuiltinDimensionTypes.OVERWORLD;
        if (path.equals(DEFAULT_THE_NETHER_PATH)) return BuiltinDimensionTypes.NETHER;
        if (path.equals(DEFAULT_THE_END_PATH)) return BuiltinDimensionTypes.END;
        return null;
    }


    private MappedRegistry<LevelStem> getDimensionOptionsRegistry(MinecraftServer server) {
        RegistryAccess registryManager = server.registries().compositeAccess();
        return (MappedRegistry<LevelStem>) registryManager.registryOrThrow(Registries.LEVEL_STEM);
    }

    @SuppressWarnings("deprecation")
    private void createWorld(MinecraftServer server, ResourceKey<DimensionType> dimensionTypeRegistryKey, String path, long seed) {
        /* create the world */
        ResourceWorldProperties resourceWorldProperties = new ResourceWorldProperties(server.getWorldData(), seed);
        ResourceKey<Level> worldRegistryKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(DEFAULT_RESOURCE_WORLD_NAMESPACE, path));
        LevelStem dimensionOptions = createDimensionOptions(server, dimensionTypeRegistryKey);
        ServerLevel world = new ResourceWorld(server,
                Util.backgroundExecutor(),
                ((MinecraftServerAccessor) server).getStorageSource(),
                resourceWorldProperties,
                worldRegistryKey,
                dimensionOptions,
                VoidWorldGenerationProgressListener.INSTANCE,
                false,
                BiomeManager.obfuscateSeed(seed),
                ImmutableList.of(),
                true,
                null);

        if (dimensionTypeRegistryKey == BuiltinDimensionTypes.END) {
            world.setDragonFight(new EndDragonFight(world, world.getSeed(), EndDragonFight.Data.DEFAULT));
        }

        /* register the world */
        ((DimensionOptionsMixinInterface) (Object) dimensionOptions).sakurawald$setSaveProperties(false);

        MappedRegistry<LevelStem> dimensionsRegistry = getDimensionOptionsRegistry(server);
        boolean isFrozen = ((SimpleRegistryMixinInterface<?>) dimensionsRegistry).sakurawald$isFrozen();
        ((SimpleRegistryMixinInterface<?>) dimensionsRegistry).sakurawald$setFrozen(false);
        var dimensionOptionsRegistryKey = ResourceKey.create(Registries.LEVEL_STEM, worldRegistryKey.location());
        if (!dimensionsRegistry.containsKey(dimensionOptionsRegistryKey)) {
            dimensionsRegistry.register(dimensionOptionsRegistryKey, dimensionOptions, Lifecycle.stable());
        }
        ((SimpleRegistryMixinInterface<?>) dimensionsRegistry).sakurawald$setFrozen(isFrozen);

        ((MinecraftServerAccessor) server).getLevels().put(world.dimension(), world);
        ServerWorldEvents.LOAD.invoker().onWorldLoad(server, world);
        world.tick(() -> true);
        MessageUtil.sendBroadcast("resource_world.world.created", path);
    }

    private ServerLevel getResourceWorldByPath(MinecraftServer server, String path) {
        ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(DEFAULT_RESOURCE_WORLD_NAMESPACE, path));
        return server.getLevel(worldKey);
    }

    private int $tp(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String path = ctx.getNodes().get(2).getNode().getName();
        ServerLevel world = getResourceWorldByPath(ctx.getSource().getServer(), path);
        if (world == null) {
            MessageUtil.sendMessage(ctx.getSource(), "resource_world.world.not_found", path);
            return 0;
        }

        if (world.dimensionTypeId() == BuiltinDimensionTypes.END) {
            ServerLevel.makeObsidianPlatform(world);
            BlockPos endSpawnPos = ServerLevel.END_SPAWN_POINT;
            player.teleportTo(world, endSpawnPos.getX() + 0.5, endSpawnPos.getY(), endSpawnPos.getZ() + 0.5, 90, 0);
        } else {
            MessageUtil.sendActionBar(player, "resource_world.world.tp.tip");
            RandomTeleport.randomTeleport(player, world, false);
        }

        return Command.SINGLE_SUCCESS;
    }


    private void deleteWorld(MinecraftServer server, String path) {
        ServerLevel world = getResourceWorldByPath(server, path);
        if (world == null) return;

        ResourceWorldManager.enqueueWorldDeletion(world);
        MessageUtil.sendBroadcast("resource_world.world.deleted", path);
    }

    private int $delete(CommandContext<CommandSourceStack> ctx) {
        String path = ctx.getNodes().get(2).getNode().getName();
        deleteWorld(ctx.getSource().getServer(), path);
        return Command.SINGLE_SUCCESS;
    }

    public void onWorldUnload(MinecraftServer server, ServerLevel world) {
        if (server.isRunning()) {
            String namespace = world.dimension().location().getNamespace();
            String path = world.dimension().location().getPath();
            // IMPORTANT: only delete the world if it's a resource world
            if (!namespace.equals(DEFAULT_RESOURCE_WORLD_NAMESPACE)) return;

            log.info("onWorldUnload() -> Creating world {} ...", path);
            long seed = ConfigManager.configWrapper.instance().modules.resource_world.seed;
            this.createWorld(server, this.getDimensionTypeRegistryKeyByPath(path), path, seed);
        }
    }

}
