package srs.ui;

import java.awt.Rectangle;
import necesse.engine.modLoader.LoadedMod;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormComponentList;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.presets.ModsForm;
import necesse.gfx.ui.ButtonColor;
import srs.core.SRS;

/**
 * Adds a "Configurar randomizer" button to this mod's page in Necesse's mods menu.
 */
public final class ModsMenuHook {

    private ModsMenuHook() {
    }

    /** Called after ModsForm.setupInfo filled the info panel of the selected mod. */
    public static void onModInfo(ModsForm modsForm, Form modsMain, FormContentBox info, LoadedMod mod) {
        if (modsForm == null || modsMain == null || info == null || mod == null || !mod.id.equals(SRS.modID())) return;
        try {
            Rectangle box = info.getContentBox();
            int y = box.height + 5;
            FormTextButton button = info.addComponent(new FormTextButton("Configurar randomizer", "Abrir as opções do Super Randomizer Show",
                    5, y, info.getMinContentWidth() - 10, FormInputSize.SIZE_32, ButtonColor.BASE));
            // Not a temporary component: the screen must survive switching to a confirmation dialog and back.
            // SRSConfigForm removes its screen (the form and its tabs) when it closes.
            button.onClicked(e -> {
                FormComponentList screen = modsForm.addComponent(SRSConfigForm.createScreen(modsForm, modsMain));
                modsForm.makeCurrent(screen);
            });
            info.setContentBox(new Rectangle(box.width, y + 32 + 10));
        } catch (Exception e) {
            SRS.warn("Could not add the config button to the mods menu: " + e);
        }
    }

}
