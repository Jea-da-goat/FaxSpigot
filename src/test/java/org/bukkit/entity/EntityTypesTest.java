package org.bukkit.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.support.AbstractTestingBase;
import org.junit.Assert;
import org.junit.Test;

public class EntityTypesTest extends AbstractTestingBase {

    @Test
    public void testMaps() {
        Set<EntityType> allBukkit = Arrays.stream(EntityType.values()).filter((b) -> b.getName() != null).collect(Collectors.toSet());

        for (net.minecraft.world.entity.EntityType<?> nms : BuiltInRegistries.ENTITY_TYPE) { // Paper - remap fix
            ResourceLocation key = net.minecraft.world.entity.EntityType.getKey(nms); // Paper - remap fix

            org.bukkit.entity.EntityType bukkit = org.bukkit.entity.EntityType.fromName(key.getPath());
            Assert.assertNotNull("Missing nms->bukkit " + key, bukkit);

            Assert.assertTrue("Duplicate entity nms->" + bukkit, allBukkit.remove(bukkit));
        }

        Assert.assertTrue("Unmapped bukkit entities " + allBukkit, allBukkit.isEmpty());
    }
}
