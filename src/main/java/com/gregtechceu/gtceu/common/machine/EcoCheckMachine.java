package com.gregtechceu.gtceu.common.machine;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.eco.EcoSystem;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class EcoCheckMachine extends MetaMachine implements IFancyUIMachine {
    public static final Logger LOGGER = LoggerFactory.getLogger("经济机器");

    public EcoCheckMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0,
                172,
                100);
        ComponentPanelWidget widget = new ComponentPanelWidget(4, 17, this::addDisplayText);
        group.addWidget(widget);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    private void addDisplayText(List<Component> textList) {
        textList.add(Component.translatable("gtceu.machine.eco.test1", EcoSystem.get().getPrice(Items.GOLD_INGOT)));
    }
}
