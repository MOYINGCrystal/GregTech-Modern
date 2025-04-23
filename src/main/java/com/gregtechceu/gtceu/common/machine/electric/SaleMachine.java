package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.WidgetUtils;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.editor.EditableUI;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.eco.EcoSystem;
import com.gregtechceu.gtceu.eco.GoodsInfo;
import com.gregtechceu.gtceu.eco.PriceSystem;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.item.ItemTransferHelper;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.utils.Position;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import static com.gregtechceu.gtceu.eco.Credit.toCredits;

public class SaleMachine extends TieredEnergyMachine
        implements IAutoOutputItem, IFancyUIMachine, IMachineLife, IWorkable {
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(SaleMachine.class,
            TieredEnergyMachine.MANAGED_FIELD_HOLDER);

    @Getter
    @Setter
    @Persisted
    protected boolean allowInputFromOutputSideItems;

    /**
     * 充电器库存
     */
    @Getter
    @Persisted
    protected final ItemStackTransfer chargerInventory;

    @Persisted
    protected final NotifiableItemStackHandler inHandler;

    @Persisted
    protected final NotifiableItemStackHandler cache;

    @Nullable
    protected TickableSubscription batterySubs;

    @Nullable
    protected TickableSubscription autoOutputSubs;

    @Nullable
    protected ISubscription energySubs;

    @Nullable
    protected ISubscription exportItemSubs;

    @Getter
    public final int maxProgress;

    @Getter
    @Persisted
    private boolean active = false;

    @Getter
    @Persisted
    @Setter
    @DescSynced
    private boolean isWorkingEnabled = true;

    @Getter
    @Persisted
    private int progress = 0;

    @Nullable
    protected ISubscription inSubs;

    @Nullable
    protected ISubscription cacheSubs;


    public SaleMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, args);
        maxProgress = 100 - tier * 20;
        inventorySize = (tier + 1) * (tier + 1);
        energyPerTick = GTValues.V[tier - 1];
        chargerInventory = createChargerItemHandler();
        cache = createCacheItemHandler();
        inHandler = createInItemHandler();
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (isRemote()) return;

        if (getLevel() instanceof ServerLevel serverLevel)
            serverLevel.getServer().tell(new TickTask(0, this::updateAutoOutputSubscription));

        exportItemSubs = cache.addChangedListener(this::updateAutoOutputSubscription);
        energySubs = energyContainer.addChangedListener(() -> {
            updateBatterySubscription();
            updateSaleUpdateSubscription();
        });
        inSubs = inHandler.addChangedListener(this::updateSaleUpdateSubscription);
        cacheSubs = cache.addChangedListener(this::updateSaleUpdateSubscription);
        chargerInventory.setOnContentsChanged(this::updateBatterySubscription);
        updateSaleUpdateSubscription();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (energySubs != null) {
            energySubs.unsubscribe();
            energySubs = null;
        }
        if (exportItemSubs != null) {
            exportItemSubs.unsubscribe();
            exportItemSubs = null;
        }
        if (inSubs != null) {
            inSubs.unsubscribe();
            inSubs = null;
        }
        if (cacheSubs != null) {
            cacheSubs.unsubscribe();
            cacheSubs = null;
        }
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(chargerInventory);
        clearInventory(cache.storage);
        clearInventory(inHandler.storage);
    }

    protected NotifiableItemStackHandler createInItemHandler() {
        var handler = new NotifiableItemStackHandler(this, 1, IO.BOTH, IO.IN);
        return handler;
    }

    private final int inventorySize;

    protected NotifiableItemStackHandler createCacheItemHandler() {
        return new NotifiableItemStackHandler(this, inventorySize, IO.BOTH, IO.OUT);
    }

    protected void updateBatterySubscription() {
        if (energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, true))
            batterySubs = subscribeServerTick(batterySubs, this::chargeBattery);
        else if (batterySubs != null) {
            batterySubs.unsubscribe();
            batterySubs = null;
        }
    }

    protected ItemStackTransfer createChargerItemHandler() {
        var transfer = new ItemStackTransfer();
        transfer.setFilter(item -> GTCapabilityHelper.getElectricItem(item) != null ||
                (ConfigHolder.INSTANCE.compat.energy.nativeEUToPlatformNative &&
                        GTCapabilityHelper.getForgeEnergyItem(item) != null));
        return transfer;
    }

    protected void chargeBattery() {
        if (!energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, false))
            updateBatterySubscription();
    }

    //////////////////////////////////////
    // ********* Logic **********//
    /// ///////////////////////////////////

    @Nullable
    protected TickableSubscription saleSubs;

    private final long energyPerTick;

    private static final PriceSystem priceSystem = PriceSystem.INSTANCE;

    public void updateSaleUpdateSubscription() {
        if (drainEnergy(true) && priceSystem.hasPrice(inHandler.getStackInSlot(0).getItem()) && canCache() && isWorkingEnabled) {
            saleSubs = subscribeServerTick(saleSubs, this::saleUpdate);
            active = true;
            return;
        } else if (saleSubs != null) {
            saleSubs.unsubscribe();
            saleSubs = null;
            active = false;
        }
        progress = 0;
    }

    private boolean canCache() {
        List<ItemStack> stack = getItemStack();
        return fillCache(stack, true);
    }

    private void saleUpdate() {
        drainEnergy(false);
        if (progress >= maxProgress) {
            List<ItemStack> stack = getItemStack();
            fillCache(stack, false);
            GoodsInfo goodsInfo = getEcoSystem().getGoodsInfo(inHandler.getStackInSlot(0).getItem());
            goodsInfo.setPrice(goodsInfo.getPrice() - 1);

            inHandler.storage.extractItem(0, 1, false);
            updateSaleUpdateSubscription();
            progress = -1;
        }
        progress++;
    }

    private @NotNull List<ItemStack> getItemStack() {
        Item item = inHandler.getStackInSlot(0).getItem();
        long prices = getEcoSystem().getPrice(item);
        return toCredits(prices);
    }

    private @NotNull EcoSystem getEcoSystem() {
        return EcoSystem.get();
    }

    /**
     * 填充缓存
     */
    private boolean fillCache(ItemStack stack, boolean simulate) {
        ItemStack residue = stack;
        for (int i = 0; i < cache.getSlots(); i++) {
            ItemStack old = residue;
            residue = cache.insertItemInternal(i, residue, simulate);
            if (residue.getCount() < old.getCount()) {
                if (residue.getCount() == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 填充缓存
     */
    private boolean fillCache(List<ItemStack> stack, boolean simulate) {
        boolean success = true;
        for (ItemStack itemStack : stack) {
            success &= fillCache(itemStack, simulate);
        }
        return success;
    }

    public boolean drainEnergy(boolean simulate) {
        long resultEnergy = energyContainer.getEnergyStored() - energyPerTick;
        if (resultEnergy >= 0L && resultEnergy <= energyContainer.getEnergyCapacity()) {
            if (!simulate)
                energyContainer.removeEnergy(energyPerTick);
            return true;
        }
        return false;
    }

    //////////////////////////////////////
    // ******* Auto Output *******//
    /// ///////////////////////////////////
    @Getter
    @Persisted
    @DescSynced
    @RequireRerender
    protected boolean autoOutputItems;

    @Getter
    @Persisted
    @DescSynced
    @RequireRerender
    protected Direction outputFacingItems;

    @Override
    public void setAutoOutputItems(boolean allow) {
        this.autoOutputItems = allow;
        updateAutoOutputSubscription();
    }

    @Override
    public void setOutputFacingItems(@Nullable Direction outputFacing) {
        this.outputFacingItems = outputFacing;
        updateAutoOutputSubscription();
    }

    protected void updateAutoOutputSubscription() {
        var outputFacing = getOutputFacingItems();
        if ((isAutoOutputItems() && !cache.isEmpty()) && outputFacing != null &&
                ItemTransferHelper.getItemTransfer(getLevel(), getPos().relative(outputFacing),
                        outputFacing.getOpposite()) != null)
            autoOutputSubs = subscribeServerTick(autoOutputSubs, this::checkAutoOutput);
        else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    protected void checkAutoOutput() {
        if (getOffsetTimer() % 5 == 0) {
            if (isAutoOutputItems() && getOutputFacingItems() != null)
                cache.exportToNearby(getOutputFacingItems());
            updateAutoOutputSubscription();
        }
    }

    @Override
    public boolean isFacingValid(Direction facing) {
        if (facing == getOutputFacingItems()) {
            return false;
        }
        return super.isFacingValid(facing);
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    /// ///////////////////////////////////

    public static BiFunction<ResourceLocation, Integer, EditableMachineUI> EDITABLE_UI_CREATOR = Util
            .memoize((path, inventorySize) ->
                    new EditableMachineUI("misc", path, () -> {
                        var template = createTemplate(inventorySize).createDefault();
                        var energyBar = createEnergyBar().createDefault();
                        var batterySlot = createBatterySlot().createDefault();
                        var energyGroup = new WidgetGroup(0, 0, energyBar.getSize().width, energyBar.getSize().height + 20);
                        batterySlot.setSelfPosition(
                                new Position((energyBar.getSize().width - 18) / 2, energyBar.getSize().height + 1));
                        energyGroup.addWidget(energyBar);
                        energyGroup.addWidget(batterySlot);
                        var group = new WidgetGroup(0, 0,
                                Math.max(energyGroup.getSize().width + template.getSize().width + 4 + 8, 172),
                                Math.max(template.getSize().height + 8, energyGroup.getSize().height + 8));
                        var size = group.getSize();
                        energyGroup.setSelfPosition(new Position(3, (size.height - energyGroup.getSize().height) / 2));

                        template.setSelfPosition(new Position(
                                (size.width - energyGroup.getSize().width - 4 - template.getSize().width) / 2 + 2 +
                                        energyGroup.getSize().width + 2,
                                (size.height - template.getSize().height) / 2));

                        group.addWidget(energyGroup);
                        group.addWidget(template);
                        return group;
                    }, (template, machine) -> {
                        if (machine instanceof SaleMachine saleMachine) {
                            createTemplate(inventorySize).setupUI(template, saleMachine);
                            createEnergyBar().setupUI(template, saleMachine);
                            createBatterySlot().setupUI(template, saleMachine);
                        }
                    }));

    protected static EditableUI<SlotWidget, SaleMachine> createBatterySlot() {
        return new EditableUI<>("battery_slot", SlotWidget.class, () -> {
            var slotWidget = new SlotWidget();
            slotWidget.setBackground(GuiTextures.SLOT, GuiTextures.CHARGER_OVERLAY);
            return slotWidget;
        }, (slotWidget, machine) -> {
            slotWidget.setHandlerSlot(machine.chargerInventory, 0);
            slotWidget.setCanPutItems(true);
            slotWidget.setCanTakeItems(true);
            slotWidget.setHoverTooltips(LangHandler.getMultiLang("gtceu.gui.charger_slot.tooltip",
                    GTValues.VNF[machine.getTier()], GTValues.VNF[machine.getTier()]).toArray(new MutableComponent[0]));
        });
    }

    protected static EditableUI<WidgetGroup, SaleMachine> createTemplate(int inventorySize) {
        return new EditableUI<>("functional_container", WidgetGroup.class, () -> {
            int rowSize = (int)Math.sqrt(inventorySize);
            WidgetGroup main = new WidgetGroup(0, 0, rowSize * 18 + 8 + 20, rowSize * 18 + 8);

            for (int y = 0; y < rowSize; y++) {
                for (int x = 0; x < rowSize; x++) {
                    int index = y * rowSize + x;
                    SlotWidget slotWidget = new SlotWidget();
                    slotWidget.initTemplate();
                    slotWidget.setSelfPosition(new Position(24 + x * 18, 4 + y * 18));
                    slotWidget.setBackground(GuiTextures.SLOT);
                    slotWidget.setId("slot_" + index);
                    main.addWidget(slotWidget);
                }
            }

            SlotWidget inSlotWidget = new SlotWidget();
            inSlotWidget.initTemplate();
            inSlotWidget
                    .setSelfPosition(new Position(4, (main.getSize().height - inSlotWidget.getSize().height) / 2));
            inSlotWidget.setBackground(GuiTextures.SLOT, GuiTextures.IN_SLOT_OVERLAY);
            inSlotWidget.setId("in_slot");
            main.addWidget(inSlotWidget);
            main.setBackground(GuiTextures.BACKGROUND_INVERSE);
            return main;
        }, (group, machine) -> {
            WidgetUtils.widgetByIdForEach(group, "^slot_[0-9]+$", SlotWidget.class, slot -> {
                var index = WidgetUtils.widgetIdIndex(slot);
                if (index >= 0 && index < machine.cache.getSlots()) {
                    slot.setHandlerSlot(machine.cache, index);
                    slot.setCanTakeItems(true);
                    slot.setCanPutItems(false);
                }
            });
            WidgetUtils.widgetByIdForEach(group, "^in_slot$", SlotWidget.class, slot -> {
                slot.setHandlerSlot(machine.inHandler.storage, 0);
                slot.setCanTakeItems(true);
                slot.setCanPutItems(true);
            });
        });
    }

    //////////////////////////////////////
    // ******* Rendering ********//

    /// ///////////////////////////////////
    @Override
    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes,
                                    Direction side) {
        if (toolTypes.contains(GTToolType.WRENCH)) {
            if (!player.isShiftKeyDown()) {
                if (!hasFrontFacing() || side != getFrontFacing()) {
                    return GuiTextures.TOOL_IO_FACING_ROTATION;
                }
            }
        } else if (toolTypes.contains(GTToolType.SCREWDRIVER)) {
            if (side == getOutputFacingItems()) {
                return GuiTextures.TOOL_ALLOW_INPUT;
            }
        } else if (toolTypes.contains(GTToolType.SOFT_MALLET)) {
            return this.isWorkingEnabled ? GuiTextures.TOOL_PAUSE : GuiTextures.TOOL_START;
        }
        return super.sideTips(player, pos, state, toolTypes, side);
    }

    //////////////////////////////////////
    // ******* Interactions ********//

    /// ///////////////////////////////////
    @Override
    protected InteractionResult onWrenchClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                              BlockHitResult hitResult) {
        if (!playerIn.isShiftKeyDown() && !isRemote()) {
            var tool = playerIn.getItemInHand(hand);
            if (tool.getDamageValue() >= tool.getMaxDamage()) return InteractionResult.PASS;
            if (hasFrontFacing() && gridSide == getFrontFacing()) return InteractionResult.PASS;

            // important not to use getters here, which have different logic
            Direction itemFacing = this.outputFacingItems;

            if (gridSide != itemFacing) {
                // if it is a new side, move it
                setOutputFacingItems(gridSide);
            } else {
                // remove the output facing when wrenching the current one to disable it
                setOutputFacingItems(null);
            }

            return InteractionResult.CONSUME;
        }

        return super.onWrenchClick(playerIn, hand, gridSide, hitResult);
    }
}
