package com.itwastall.client.mixin;

import com.itwastall.client.hud.MenuBackground;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The selected row of every list (worlds, servers, resource packs, languages) is outlined in ink red instead of
 * vanilla's white / grey line, and filled with a dark leather colour, to match the It Was Tall UI.
 */
@Mixin(EntryListWidget.class)
public abstract class EntryListWidgetMixin {
    @ModifyVariable(method = "drawSelectionHighlight", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private int itwastall$borderColor(int color) {
        if (!MenuBackground.enabled()) return color;
        return color == -1 ? 0xFFA0241B : 0xFF5A1610;   // focused : unfocused
    }

    @ModifyVariable(method = "drawSelectionHighlight", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int itwastall$fillColor(int color) {
        return MenuBackground.enabled() ? 0xFF120A07 : color;
    }
}
