package org.bukkit.support;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.biome.Biome;
import org.bukkit.Material;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.junit.Assert;

/**
 *  If you are getting: java.lang.ExceptionInInitializerError
 *    at net.minecraft.server.StatisticList.&lt;clinit&gt;(SourceFile:58)
 *    at net.minecraft.server.Item.&lt;clinit&gt;(SourceFile:252)
 *    at net.minecraft.server.Block.&lt;clinit&gt;(Block.java:577)
 *
 *  extend this class to solve it.
 */
public abstract class AbstractTestingBase {
    // Materials that only exist in block form (or are legacy)
    public static final List<Material> INVALIDATED_MATERIALS;

    public static final ReloadableServerResources DATA_PACK;
    public static final RegistryAccess.Frozen REGISTRY_CUSTOM;
    public static final Registry<Biome> BIOMES;

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Set up resource manager
        MultiPackResourceManager resourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, Collections.singletonList(new ServerPacksSource().getVanillaPack()));
        // add tags and loot tables for unit tests
        LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
        layers = WorldLoader.loadAndReplaceLayer(resourceManager, layers, RegistryLayer.WORLDGEN, RegistryDataLoader.WORLDGEN_REGISTRIES);
        REGISTRY_CUSTOM = layers.compositeAccess().freeze();
        io.papermc.paper.testing.DummyServer.setup(); // Paper
        // Paper start
        try {
            java.lang.reflect.Field field = io.papermc.paper.registry.PaperRegistry.class.getDeclaredField("REGISTRY_ACCESS");
            field.trySetAccessible();
            field.set(null, com.google.common.base.Suppliers.ofInstance(REGISTRY_CUSTOM));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not reflectively set RegistryAccess in PaperRegistry", ex);
        }
        // Paper end
        // Register vanilla pack
        DATA_PACK = ReloadableServerResources.loadResources(resourceManager, REGISTRY_CUSTOM, FeatureFlags.REGISTRY.allFlags(), Commands.CommandSelection.DEDICATED, 0, MoreExecutors.directExecutor(), MoreExecutors.directExecutor()).join();
        // Bind tags
        DATA_PACK.updateRegistryTags(REGISTRY_CUSTOM);
        // Biome shortcut
        BIOMES = REGISTRY_CUSTOM.registryOrThrow(Registries.BIOME);

        DummyEnchantments.setup();
        io.papermc.paper.configuration.GlobalConfigTestingBase.setupGlobalConfigForTest(); // Paper

        ImmutableList.Builder<Material> builder = ImmutableList.builder();
        for (Material m : Material.values()) {
            if (m.isLegacy() || CraftMagicNumbers.getItem(m) == null) {
                builder.add(m);
            }
        }
        INVALIDATED_MATERIALS = builder.build();
        Assert.assertEquals("Expected 604 invalidated materials (got " + INVALIDATED_MATERIALS.size() + ")", 604, INVALIDATED_MATERIALS.size());
    }
}
