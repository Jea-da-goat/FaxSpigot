package net.minecraft.world.level.storage.loot;

import com.google.common.collect.Lists;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.stream.Collectors;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.world.LootGenerateEvent;
// CraftBukkit end

public class LootTable {

    static final Logger LOGGER = LogUtils.getLogger();
    public static final LootTable EMPTY = new LootTable(LootContextParamSets.EMPTY, new LootPool[0], new LootItemFunction[0]);
    public static final LootContextParamSet DEFAULT_PARAM_SET = LootContextParamSets.ALL_PARAMS;
    final LootContextParamSet paramSet;
    final LootPool[] pools;
    final LootItemFunction[] functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;

    LootTable(LootContextParamSet type, LootPool[] pools, LootItemFunction[] functions) {
        this.paramSet = type;
        this.pools = pools;
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
    }

    // Paper start - preserve overstacked items
    @Deprecated
    public static Consumer<ItemStack> createStackSplitter(LootContext context, Consumer<ItemStack> consumer) {
        return createStackSplitter(context, consumer, null);
    }
    public static Consumer<ItemStack> createStackSplitter(LootContext context, Consumer<ItemStack> consumer, @org.jetbrains.annotations.Nullable net.minecraft.server.level.ServerLevel level) {
        boolean skipSplitter = level != null && !level.paperConfig().fixes.splitOverstackedLoot;
        // Paper end
        return (itemstack) -> {
            if (itemstack.isItemEnabled(context.getLevel().enabledFeatures())) {
                if (skipSplitter || itemstack.getCount() < itemstack.getMaxStackSize()) { // Paper - preserve overstacked items
                    consumer.accept(itemstack);
                } else {
                    int i = itemstack.getCount();


                    while (i > 0) {
                        ItemStack itemstack1 = itemstack.copy();

                        itemstack1.setCount(Math.min(itemstack.getMaxStackSize(), i));
                        i -= itemstack1.getCount();
                        consumer.accept(itemstack1);
                    }
                }

            }
        };
    }

    public void getRandomItemsRaw(LootContext context, Consumer<ItemStack> lootConsumer) {
        if (context.addVisitedTable(this)) {
            Consumer<ItemStack> consumer1 = LootItemFunction.decorate(this.compositeFunction, lootConsumer, context);
            LootPool[] alootselector = this.pools;
            int i = alootselector.length;

            for (int j = 0; j < i; ++j) {
                LootPool lootselector = alootselector[j];

                lootselector.addRandomItems(consumer1, context);
            }

            context.removeVisitedTable(this);
        } else {
            LootTable.LOGGER.warn("Detected infinite loop in loot tables");
        }

    }

    public void getRandomItems(LootContext context, Consumer<ItemStack> lootConsumer) {
        this.getRandomItemsRaw(context, LootTable.createStackSplitter(context, lootConsumer, context.getLevel())); // Paper - preserve overstacked items
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootContext context) {
        ObjectArrayList<ItemStack> objectarraylist = new ObjectArrayList();

        Objects.requireNonNull(objectarraylist);
        this.getRandomItems(context, objectarraylist::add);
        return objectarraylist;
    }

    public LootContextParamSet getParamSet() {
        return this.paramSet;
    }

    public void validate(ValidationContext reporter) {
        int i;

        for (i = 0; i < this.pools.length; ++i) {
            this.pools[i].validate(reporter.forChild(".pools[" + i + "]"));
        }

        for (i = 0; i < this.functions.length; ++i) {
            this.functions[i].validate(reporter.forChild(".functions[" + i + "]"));
        }

    }

    public void fill(Container inventory, LootContext context) {
        // CraftBukkit start
        this.fillInventory(inventory, context, false);
    }

    public void fillInventory(Container iinventory, LootContext loottableinfo, boolean plugin) {
        // CraftBukkit end
        ObjectArrayList<ItemStack> objectarraylist = this.getRandomItems(loottableinfo);
        RandomSource randomsource = loottableinfo.getRandom();
        // CraftBukkit start
        LootGenerateEvent event = CraftEventFactory.callLootGenerateEvent(iinventory, this, loottableinfo, objectarraylist, plugin);
        if (event.isCancelled()) {
            return;
        }
        objectarraylist = event.getLoot().stream().map(CraftItemStack::asNMSCopy).collect(ObjectArrayList.toList());
        // CraftBukkit end
        List<Integer> list = this.getAvailableSlots(iinventory, randomsource);

        this.shuffleAndSplitItems(objectarraylist, list.size(), randomsource);
        ObjectListIterator objectlistiterator = objectarraylist.iterator();

        while (objectlistiterator.hasNext()) {
            ItemStack itemstack = (ItemStack) objectlistiterator.next();

            if (list.isEmpty()) {
                LootTable.LOGGER.warn("Tried to over-fill a container");
                return;
            }

            if (itemstack.isEmpty()) {
                iinventory.setItem((Integer) list.remove(list.size() - 1), ItemStack.EMPTY);
            } else {
                iinventory.setItem((Integer) list.remove(list.size() - 1), itemstack);
            }
        }

    }

    private void shuffleAndSplitItems(ObjectArrayList<ItemStack> drops, int freeSlots, RandomSource random) {
        List<ItemStack> list = Lists.newArrayList();
        ObjectListIterator objectlistiterator = drops.iterator();

        while (objectlistiterator.hasNext()) {
            ItemStack itemstack = (ItemStack) objectlistiterator.next();

            if (itemstack.isEmpty()) {
                objectlistiterator.remove();
            } else if (itemstack.getCount() > 1) {
                list.add(itemstack);
                objectlistiterator.remove();
            }
        }

        while (freeSlots - drops.size() - list.size() > 0 && !list.isEmpty()) {
            ItemStack itemstack1 = (ItemStack) list.remove(Mth.nextInt(random, 0, list.size() - 1));
            int j = Mth.nextInt(random, 1, itemstack1.getCount() / 2);
            ItemStack itemstack2 = itemstack1.split(j);

            if (itemstack1.getCount() > 1 && random.nextBoolean()) {
                list.add(itemstack1);
            } else {
                drops.add(itemstack1);
            }

            if (itemstack2.getCount() > 1 && random.nextBoolean()) {
                list.add(itemstack2);
            } else {
                drops.add(itemstack2);
            }
        }

        drops.addAll(list);
        Util.shuffle(drops, random);
    }

    private List<Integer> getAvailableSlots(Container inventory, RandomSource random) {
        ObjectArrayList<Integer> objectarraylist = new ObjectArrayList();

        for (int i = 0; i < inventory.getContainerSize(); ++i) {
            if (inventory.getItem(i).isEmpty()) {
                objectarraylist.add(i);
            }
        }

        Util.shuffle(objectarraylist, random);
        return objectarraylist;
    }

    public static LootTable.Builder lootTable() {
        return new LootTable.Builder();
    }

    public static class Builder implements FunctionUserBuilder<LootTable.Builder> {

        private final List<LootPool> pools = Lists.newArrayList();
        private final List<LootItemFunction> functions = Lists.newArrayList();
        private LootContextParamSet paramSet;

        public Builder() {
            this.paramSet = LootTable.DEFAULT_PARAM_SET;
        }

        public LootTable.Builder withPool(LootPool.Builder poolBuilder) {
            this.pools.add(poolBuilder.build());
            return this;
        }

        public LootTable.Builder setParamSet(LootContextParamSet context) {
            this.paramSet = context;
            return this;
        }

        @Override
        public LootTable.Builder apply(LootItemFunction.Builder function) {
            this.functions.add(function.build());
            return this;
        }

        @Override
        public LootTable.Builder unwrap() {
            return this;
        }

        public LootTable build() {
            return new LootTable(this.paramSet, (LootPool[]) this.pools.toArray(new LootPool[0]), (LootItemFunction[]) this.functions.toArray(new LootItemFunction[0]));
        }
    }

    public static class Serializer implements JsonDeserializer<LootTable>, JsonSerializer<LootTable> {

        public Serializer() {}

        public LootTable deserialize(JsonElement jsonelement, Type type, JsonDeserializationContext jsondeserializationcontext) throws JsonParseException {
            JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "loot table");
            LootPool[] alootselector = (LootPool[]) GsonHelper.getAsObject(jsonobject, "pools", new LootPool[0], jsondeserializationcontext, LootPool[].class);
            LootContextParamSet lootcontextparameterset = null;

            if (jsonobject.has("type")) {
                String s = GsonHelper.getAsString(jsonobject, "type");

                lootcontextparameterset = LootContextParamSets.get(new ResourceLocation(s));
            }

            LootItemFunction[] alootitemfunction = (LootItemFunction[]) GsonHelper.getAsObject(jsonobject, "functions", new LootItemFunction[0], jsondeserializationcontext, LootItemFunction[].class);

            return new LootTable(lootcontextparameterset != null ? lootcontextparameterset : LootContextParamSets.ALL_PARAMS, alootselector, alootitemfunction);
        }

        public JsonElement serialize(LootTable loottable, Type type, JsonSerializationContext jsonserializationcontext) {
            JsonObject jsonobject = new JsonObject();

            if (loottable.paramSet != LootTable.DEFAULT_PARAM_SET) {
                ResourceLocation minecraftkey = LootContextParamSets.getKey(loottable.paramSet);

                if (minecraftkey != null) {
                    jsonobject.addProperty("type", minecraftkey.toString());
                } else {
                    LootTable.LOGGER.warn("Failed to find id for param set {}", loottable.paramSet);
                }
            }

            if (loottable.pools.length > 0) {
                jsonobject.add("pools", jsonserializationcontext.serialize(loottable.pools));
            }

            if (!ArrayUtils.isEmpty(loottable.functions)) {
                jsonobject.add("functions", jsonserializationcontext.serialize(loottable.functions));
            }

            return jsonobject;
        }
    }
}
