package srs.patches;

import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.presets.ModsForm;
import net.bytebuddy.asm.Advice;
import srs.ui.ModsMenuHook;

/** Settings screen: adds the "Configurar randomizer" button to this mod's page in the mods menu. */
@ModMethodPatch(target = ModsForm.class, name = "setupInfo", arguments = {LoadedMod.class, boolean[].class, boolean[].class})
public class ModsFormSetupInfoPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This ModsForm form, @Advice.Argument(0) LoadedMod mod, @Advice.FieldValue("infoContent") FormContentBox info,
                       @Advice.FieldValue("main") Form main) {
        ModsMenuHook.onModInfo(form, main, info, mod);
    }

}
