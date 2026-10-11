package srs.patches;

import java.util.List;
import necesse.engine.localization.fileLanguage.FileLanguage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import net.bytebuddy.asm.Advice;
import srs.random.TranslationRandomizer;

/** Feature 12: re-applies the shuffle whenever a language reloads its files. */
@ModMethodPatch(target = FileLanguage.class, name = "reload", arguments = {List.class})
public class FileLanguageReloadPatch {

    @Advice.OnMethodExit
    static void onExit(@Advice.This FileLanguage language) {
        TranslationRandomizer.randomize(language);
    }

}
