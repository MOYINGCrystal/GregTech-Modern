package com.gregtechceu.gtceu.eco;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

public class PriceSystem {
    public static final PriceSystem INSTANCE = new PriceSystem();

    private PriceSystem() {
    }

    private final Object2LongMap<Item> prices = new Object2LongOpenHashMap<>();

    public boolean hasPrice(ItemLike item) {
        return prices.containsKey(item.asItem());
    }

    public void setBasePrices(ItemLike item, long price) {
        prices.put(item.asItem(), price);
    }

    public long getBasePrices(ItemLike item) {
        return prices.getLong(item);
    }
}
