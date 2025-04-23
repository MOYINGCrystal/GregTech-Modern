package com.gregtechceu.gtceu.eco;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * 经济系统
 */
public class EcoSystem extends SavedData {
    private final Gson gson = new Gson();

    public static EcoSystem get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(EcoSystem::new,
                EcoSystem::new, "gtceu_ecosystem");
    }

    private final PriceSystem priceSystem = PriceSystem.INSTANCE;

    private EcoSystem() {
    }

    private EcoSystem(CompoundTag compoundTag) {
        String data = compoundTag.getString("data");
        Type type = new TypeToken<Int2ObjectOpenHashMap<GoodsInfo>>(){}.getType();
        goodsInfoMap = gson.fromJson(data, type);
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag compoundTag) {
        String json = gson.toJson(goodsInfoMap);
        compoundTag.putString("data", json);
        return compoundTag;
    }

    private Map<Integer, GoodsInfo> goodsInfoMap = new Int2ObjectOpenHashMap<>();

    private GoodsInfo getGoodsInfo(ItemLike item) {
        setDirty();
        int id = Item.getId(item.asItem());
        return goodsInfoMap.computeIfAbsent(id, key -> new GoodsInfo(id, priceSystem.getBasePrices(item)));
    }

    public long getPrice(ItemLike item) {
        GoodsInfo goodsInfo = getGoodsInfo(item);

        return goodsInfo.getPrice();
    }

    public void updatePrice() {
        // todo

        /*
         * 商品的价格会缓慢向基础价格移动
         *
         * 不在这个函数中实现的：当玩家出售该种商品时会商品价格会下跌
         * 相反的：购买时会上升
         */
    }
}
