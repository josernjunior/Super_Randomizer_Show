package srs.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormBreakLine;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.FormComponentList;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormSlider;
import necesse.gfx.forms.components.FormTabTextComponent;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.presets.ConfirmationForm;
import necesse.gfx.forms.presets.ModsForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import srs.SRSSettings;
import srs.core.Reflect;
import srs.core.SRS;

/**
 * The randomizer settings screen, opened from the mod's page in Necesse's mods menu.
 * It edits a copy of the stored settings and writes the same cfg file the /srs command and manual edits use.
 */
public class SRSConfigForm extends Form {

    private static final int WIDTH = 700;
    private static final int HEIGHT = 580;
    private static final int INDENT = 24;

    // Slider value formats
    private static final int MULTIPLIER = 0;
    private static final int PERCENT = 1;
    private static final int PERCENT_DECIMAL = 2;

    private static final String[][] VISUAL_FOLDERS = {
            {"items", "Ícones de itens"}, {"mobs", "Mobs e NPCs"}, {"player/armor", "Armaduras e cosméticos"},
            {"player/weapons", "Armas (na mão)"}, {"player/holditems", "Itens segurados"}, {"player/hair", "Cabelos"},
            {"projectiles", "Projéteis"}};
    private static final String[][] SOUND_CATEGORIES = {
            {"music", "Músicas"}, {"animals", "Animais"}, {"creatures", "Criaturas"}, {"environment", "Ambiente"},
            {"weather", "Clima"}, {"weapons", "Armas"}, {"impacts", "Impactos e magias"}, {"interface", "Interface e baús"},
            {"bosses", "Vozes de chefes e mobs"}, {"notes", "Notas musicais"}, {"other", "Outros"}};

    private final ModsForm modsForm;
    private final Form modsMain;
    private SRSSettings edit;

    private final FormContentBox content;
    private final FormTextInput seedInput;
    private final FormLabel status;
    private final FormTextButton restartButton;
    private final List<Runnable> refreshers = new ArrayList<>();
    private final List<Runnable> pullers = new ArrayList<>();
    private FormFlow flow;
    private ConfirmationForm ownDialog;

    /** The component shown in the mods menu: this form plus its tabs. */
    private final FormComponentList screen;
    // Tabs: one per section. buildContent() walks every section and only adds the components of the selected one.
    private final List<String> sectionTitles = new ArrayList<>();
    private int selectedTab = -1;
    private int sectionIndex;
    private boolean visible;
    private int visibleEnd;

    /** Builds the screen (the form with its tabs on top) to add to the mods menu. */
    public static FormComponentList createScreen(ModsForm modsForm, Form modsMain) {
        FormComponentList screen = new FormComponentList();
        SRSConfigForm form = screen.addComponent(new SRSConfigForm(modsForm, modsMain, screen));
        form.addTabs();
        return screen;
    }

    private SRSConfigForm(ModsForm modsForm, Form modsMain, FormComponentList screen) {
        super("srsconfig", WIDTH, HEIGHT);
        this.modsForm = modsForm;
        this.modsMain = modsMain;
        this.screen = screen;
        this.edit = copy(SRS.stored());

        addComponent(new FormLabel("Super Randomizer Show", new FontOptions(20), 0, WIDTH / 2, 8));
        addComponent(new FormLabel("A mesma seed sempre gera o mesmo resultado. Mudanças valem depois de reiniciar o jogo.",
                new FontOptions(12).color(getInterfaceStyle().inactiveTextColor), 0, WIDTH / 2, 34, WIDTH - 20));

        addComponent(new FormLabel("Seed:", new FontOptions(16), -1, 10, 60));
        seedInput = addComponent(new FormTextInput(60, 56, FormInputSize.SIZE_24, 200, 20));
        seedInput.setRegexMatchFull("-?[0-9]*");
        seedInput.placeHolder = new StaticMessage("0 = sortear no próximo início");
        seedInput.setText(String.valueOf(edit.seed));
        addComponent(new FormTextButton("Sortear", 268, 56, 86, FormInputSize.SIZE_24, ButtonColor.BASE)).onClicked(e -> askRandomSeed());
        addComponent(new FormLabel("No multiplayer, a seed de todos deve ser a mesma", new FontOptions(12).color(getInterfaceStyle().inactiveTextColor),
                -1, 362, 62, WIDTH - 372));

        content = addComponent(new FormContentBox(5, 90, WIDTH - 10, HEIGHT - 90 - 85));
        addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 5, 89, WIDTH - 10, true));
        addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 5, HEIGHT - 84, WIDTH - 10, true));

        status = addComponent(new FormLabel("", new FontOptions(14), 0, WIDTH / 2, HEIGHT - 74, WIDTH - 20));

        int buttonWidth = (WIDTH - 25) / 4;
        int buttonY = HEIGHT - 45;
        addComponent(new FormTextButton("Salvar", 5, buttonY, buttonWidth, FormInputSize.SIZE_32, ButtonColor.BASE)).onClicked(e -> save());
        addComponent(new FormTextButton("Restaurar padrão", 10 + buttonWidth, buttonY, buttonWidth, FormInputSize.SIZE_32, ButtonColor.BASE)).onClicked(e -> resetDefaults());
        boolean canRestart = ModsForm.restartGameRunnable() != null;
        restartButton = addComponent(new FormTextButton(canRestart ? "Reiniciar jogo" : "Fechar jogo", 15 + buttonWidth * 2, buttonY, buttonWidth,
                FormInputSize.SIZE_32, ButtonColor.BASE));
        restartButton.onClicked(e -> restart());
        addComponent(new FormTextButton("Voltar", 20 + buttonWidth * 3, buttonY, buttonWidth, FormInputSize.SIZE_32, ButtonColor.BASE)).onClicked(e -> back());

        buildContent();     // nothing selected yet: only collects the section titles for the tabs
        selectedTab = 0;
        buildContent();
        if (needsRestart()) {
            setStatus("Há mudanças salvas que ainda não estão em uso. Reinicie o jogo para aplicar.", false);
        }
        GameWindow window = WindowManager.getWindow();
        if (window != null) setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
    }

    @Override
    public void onWindowResized(GameWindow window) {
        super.onWindowResized(window);
        setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
    }

    // ===== Tabs =====

    private static final int TAB_GAP = 2;
    private static final int NUMBER_TAB_WIDTH = 31;

    /**
     * One Necesse tab per section along the top edge of the form: its number as the label ("Avançado" has none),
     * its name as the tooltip. They're drawn outside the form, so they live in the screen next to it.
     */
    private void addTabs() {
        int numbered = 0;
        for (String title : sectionTitles) {
            if (title.contains(" - ")) numbered++;
        }
        int others = sectionTitles.size() - numbered;
        int otherWidth = others == 0 ? 0 : (WIDTH - 8 - TAB_GAP * (sectionTitles.size() - 1) - NUMBER_TAB_WIDTH * numbered) / others;
        int x = 4;
        for (int i = 0; i < sectionTitles.size(); i++) {
            final int index = i;
            String title = sectionTitles.get(i);
            int split = title.indexOf(" - ");
            String label = split < 0 ? title : title.substring(0, split);
            String tooltip = split < 0 ? null : title.substring(split + 3);
            int width = split < 0 ? otherWidth : NUMBER_TAB_WIDTH;
            FormTabTextComponent tab = screen.addComponent(new FormTabTextComponent(label, tooltip, this, x, FormInputSize.SIZE_20, width) {
                @Override
                public boolean isSelected() {
                    return selectedTab == index;
                }
            });
            tab.onClicked(e -> selectTab(index));
            x += width + TAB_GAP;
        }
    }

    private void selectTab(int index) {
        if (index == selectedTab) return;
        pull();
        selectedTab = index;
        buildContent();
    }

    // ===== Content =====

    private void buildContent() {
        content.clearComponents();
        refreshers.clear();
        pullers.clear();
        sectionTitles.clear();
        sectionIndex = -1;
        visible = false;
        visibleEnd = 0;
        flow = new FormFlow(5);

        section("1 - Aparecimento de mobs");
        check(0, "Trocar onde inimigos, bichos, NPCs e chefes aparecem", s -> s.mobSpawns, (s, v) -> s.mobSpawns = v, null);
        BooleanSupplier mobs = () -> edit.mobSpawns;
        check(1, "Inimigos que nascem naturalmente", s -> s.mobSpawnHostiles, (s, v) -> s.mobSpawnHostiles = v, mobs);
        check(1, "Bichos (criaturas passivas)", s -> s.mobSpawnCritters, (s, v) -> s.mobSpawnCritters = v, mobs);
        check(1, "Animais (ovelhas, vacas, porcos, galinhas, avestruzes, pinguins...) em mundos e áreas novas", s -> s.mobSpawnAnimals,
                (s, v) -> s.mobSpawnAnimals = v, mobs);
        check(1, "NPCs de vilas e estruturas", s -> s.mobSpawnTownNPCs, (s, v) -> s.mobSpawnTownNPCs = v, mobs);
        flow.next(4);
        label(1, "1.1 - NPCs que podem trocar de lugar entre si:");
        listChecks(2, MobChoices.townNPCs(edit.townNPCPool), s -> s.townNPCPool, (s, v) -> s.townNPCPool = v, () -> edit.mobSpawns && edit.mobSpawnTownNPCs);
        flow.next(4);
        label(1, "1.2 - Chefes que podem trocar entre si (vale também para a troca de drops):");
        listChecks(2, numbered(MobChoices.bosses(edit.bossPool)), s -> s.bossPool, (s, v) -> s.bossPool = v, () -> edit.mobSpawns || edit.drops);

        section("2 - Drops");
        check(0, "Trocar quem dropa o quê (inimigos, chefes, bichos, rochas, árvores e plantas)", s -> s.drops, (s, v) -> s.drops = v, null);
        check(0, "Randomizar a quantidade de cada item dropado", s -> s.dropQuantities, (s, v) -> s.dropQuantities = v, null);
        slider(1, "Quantidade mínima", 0.0F, 5.0F, 0.1F, MULTIPLIER, s -> s.dropQuantityMin, (s, v) -> s.dropQuantityMin = v);
        slider(1, "Quantidade máxima", 0.0F, 5.0F, 0.1F, MULTIPLIER, s -> s.dropQuantityMax, (s, v) -> s.dropQuantityMax = v);
        check(0, "Chance de dropar também um item aleatório ao matar um mob", s -> s.randomItemDrop, (s, v) -> s.randomItemDrop = v, null);
        slider(1, "Chance do item aleatório", 0.005F, 1.0F, 0.005F, PERCENT_DECIMAL, s -> s.randomItemDropChance, (s, v) -> s.randomItemDropChance = v);

        section("3 - Receitas");
        check(0, "Embaralhar os ingredientes das receitas", s -> s.recipes, (s, v) -> s.recipes = v, null);
        check(1, "Só trocar entre receitas da mesma estação de trabalho (mais jogável)", s -> s.recipesSameStation,
                (s, v) -> s.recipesSameStation = v, () -> edit.recipes);

        section("4 - Lojas");
        check(0, "Embaralhar os itens que os NPCs de cidade vendem e compram", s -> s.shops, (s, v) -> s.shops = v, null);

        section("5 - Danos");
        check(0, "5.1 - Armas", s -> s.weaponDamage, (s, v) -> s.weaponDamage = v, null);
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("multiplier", "Multiplicador aleatório");
        modes.put("shuffle", "Trocar entre armas de força parecida");
        modes.put("chaos", "Caos: trocar com qualquer arma do mesmo tipo");
        dropdown(1, "Modo:", modes, s -> s.weaponDamageMode, (s, v) -> s.weaponDamageMode = v, () -> edit.weaponDamage);
        slider(1, "Multiplicador mínimo (modo multiplicador)", 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.weaponDamageMin, (s, v) -> s.weaponDamageMin = v);
        slider(1, "Multiplicador máximo (modo multiplicador)", 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.weaponDamageMax, (s, v) -> s.weaponDamageMax = v);

        flow.next(4);
        check(0, "5.2 - Dano dos inimigos", s -> s.enemyDamage, (s, v) -> s.enemyDamage = v, null);
        Map<String, String> enemyModes = new LinkedHashMap<>();
        enemyModes.put("multiplier", "Multiplicador aleatório");
        enemyModes.put("preswap", "Usar o dano do inimigo antes da troca");
        dropdown(1, "Modo:", enemyModes, s -> s.enemyDamageMode, (s, v) -> s.enemyDamageMode = v, () -> edit.enemyDamage);
        label(1, "As barras valem nos dois modos: no \"antes da troca\" elas multiplicam o dano do inimigo original (x1,0 e x1,0 = dano igual ao dele).");
        slider(1, "Multiplicador mínimo", 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.enemyDamageMin, (s, v) -> s.enemyDamageMin = v);
        slider(1, "Multiplicador máximo", 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.enemyDamageMax, (s, v) -> s.enemyDamageMax = v);

        flow.next(4);
        check(0, "5.3 - Dano dos chefes", s -> s.bossDamage, (s, v) -> s.bossDamage = v, null);
        Map<String, String> bossModes = new LinkedHashMap<>();
        bossModes.put("multiplier", "Multiplicador aleatório");
        bossModes.put("preswap", "Usar o dano do chefe antes da troca");
        dropdown(1, "Modo:", bossModes, s -> s.bossDamageMode, (s, v) -> s.bossDamageMode = v, () -> edit.bossDamage);
        label(1, "As barras valem nos dois modos: no \"antes da troca\" elas multiplicam o dano do chefe original (x1,0 e x1,0 = dano igual ao dele).");
        slider(1, "Multiplicador mínimo", 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.bossDamageMin, (s, v) -> s.bossDamageMin = v);
        slider(1, "Multiplicador máximo", 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.bossDamageMax, (s, v) -> s.bossDamageMax = v);

        section("6 - Vida");
        label(0, "Em todos os itens abaixo, as barras valem nos dois modos: no \"antes da troca\" elas multiplicam a vida do mob original (x1,0 e x1,0 = vida igual à dele).");
        healthBlock("6.1 - Vida dos inimigos", "Usar a vida do inimigo antes da troca", s -> s.enemyHealth, (s, v) -> s.enemyHealth = v,
                s -> s.enemyHealthMode, (s, v) -> s.enemyHealthMode = v, s -> s.enemyHealthMin, (s, v) -> s.enemyHealthMin = v,
                s -> s.enemyHealthMax, (s, v) -> s.enemyHealthMax = v);
        healthBlock("6.2 - Vida dos aldeões", "Usar a vida do aldeão antes da troca", s -> s.villagerHealth, (s, v) -> s.villagerHealth = v,
                s -> s.villagerHealthMode, (s, v) -> s.villagerHealthMode = v, s -> s.villagerHealthMin, (s, v) -> s.villagerHealthMin = v,
                s -> s.villagerHealthMax, (s, v) -> s.villagerHealthMax = v);
        healthBlock("6.3 - Vida dos bichos", "Usar a vida do bicho antes da troca", s -> s.critterHealth, (s, v) -> s.critterHealth = v,
                s -> s.critterHealthMode, (s, v) -> s.critterHealthMode = v, s -> s.critterHealthMin, (s, v) -> s.critterHealthMin = v,
                s -> s.critterHealthMax, (s, v) -> s.critterHealthMax = v);
        healthBlock("6.4 - Vida dos animais", "Usar a vida do animal antes da troca", s -> s.animalHealth, (s, v) -> s.animalHealth = v,
                s -> s.animalHealthMode, (s, v) -> s.animalHealthMode = v, s -> s.animalHealthMin, (s, v) -> s.animalHealthMin = v,
                s -> s.animalHealthMax, (s, v) -> s.animalHealthMax = v);
        healthBlock("6.5 - Vida dos chefes", "Usar a vida do chefe antes da troca", s -> s.bossHealth, (s, v) -> s.bossHealth = v,
                s -> s.bossHealthMode, (s, v) -> s.bossHealthMode = v, s -> s.bossHealthMin, (s, v) -> s.bossHealthMin = v,
                s -> s.bossHealthMax, (s, v) -> s.bossHealthMax = v);

        section("7 - Defesa");
        label(0, "A defesa é a armadura: cada golpe perde metade da armadura em dano. As barras valem nos dois modos: no \"antes da troca\" "
                + "elas multiplicam a defesa do mob original (x1,0 e x1,0 = defesa igual à dele).");
        statBlocks("7", "Defesa", "Armor", "a defesa");

        section("8 - Empurrão");
        label(0, "O empurrão é o quanto o mob é jogado para trás ao levar um golpe (normal = x1,0; 0 = não se move). As barras valem nos dois modos: "
                + "no \"antes da troca\" elas multiplicam o empurrão do mob original.");
        statBlocks("8", "Empurrão", "Knockback", "o empurrão");

        section("9 - Chance de crítico");
        check(0, "Randomizar o crítico de armas e equipamentos", s -> s.critChance, (s, v) -> s.critChance = v, null);
        slider(1, "Crítico extra mínimo em cada arma", 0.0F, 1.0F, 0.01F, PERCENT, s -> s.weaponCritMin, (s, v) -> s.weaponCritMin = v);
        slider(1, "Crítico extra máximo em cada arma", 0.0F, 1.0F, 0.01F, PERCENT, s -> s.weaponCritMax, (s, v) -> s.weaponCritMax = v);
        slider(1, "Multiplicador mínimo do crítico dos equipamentos", 0.0F, 5.0F, 0.1F, MULTIPLIER, s -> s.equipmentCritMultMin, (s, v) -> s.equipmentCritMultMin = v);
        slider(1, "Multiplicador máximo do crítico dos equipamentos", 0.0F, 5.0F, 0.1F, MULTIPLIER, s -> s.equipmentCritMultMax, (s, v) -> s.equipmentCritMultMax = v);
        slider(1, "Crítico extra máximo por armadura/acessório", 0.0F, 0.5F, 0.01F, PERCENT, s -> s.equipmentCritBonusMax, (s, v) -> s.equipmentCritBonusMax = v);

        section("10 - Bônus de conjunto");
        check(0, "Trocar os bônus de conjunto entre as armaduras", s -> s.armorSetBonuses, (s, v) -> s.armorSetBonuses = v, null);

        section("11 - Efeitos de equipamentos");
        check(0, "Trocar os efeitos entre acessórios e entre peças de armadura do mesmo slot", s -> s.equipmentEffects, (s, v) -> s.equipmentEffects = v, null);

        section("12 - % de dano dos equipamentos");
        check(0, "Randomizar o bônus de dano dos equipamentos", s -> s.equipmentDamage, (s, v) -> s.equipmentDamage = v, null);
        slider(1, "Multiplicador mínimo", 0.0F, 5.0F, 0.1F, MULTIPLIER, s -> s.equipmentDamageMultMin, (s, v) -> s.equipmentDamageMultMin = v);
        slider(1, "Multiplicador máximo", 0.0F, 5.0F, 0.1F, MULTIPLIER, s -> s.equipmentDamageMultMax, (s, v) -> s.equipmentDamageMultMax = v);

        section("13 - Visuais");
        check(0, "Trocar as texturas (um mob vira outro mob inteiro, um capacete vira outro capacete...)", s -> s.visuals, (s, v) -> s.visuals = v, null);
        label(1, "O que trocar:");
        listChecks(2, VISUAL_FOLDERS, s -> s.visualFolders, (s, v) -> s.visualFolders = v, () -> edit.visuals);
        flow.next(4);
        check(0, "Troca Bugada Divertida: as partes dos mobs trocam separadas (ex.: slime com braços de zumbi). Funciona mesmo com os visuais desligados.",
                s -> s.funnyBuggedSwap, (s, v) -> s.funnyBuggedSwap = v, null);

        section("14 - Projéteis");
        check(0, "Trocar os projéteis de armas, inimigos e chefes", s -> s.projectiles, (s, v) -> s.projectiles = v, null);

        section("15 - Traduções");
        check(0, "Embaralhar os textos do jogo", s -> s.translations, (s, v) -> s.translations = v, null);
        Map<String, String> translationModes = new LinkedHashMap<>();
        translationModes.put("entries", "Trocar textos inteiros (nomes continuam legíveis)");
        translationModes.put("words", "Trocar palavras de lugar (caótico)");
        dropdown(1, "Modo:", translationModes, s -> s.translationMode, (s, v) -> s.translationMode = v, () -> edit.translations);

        section("16 - Sons");
        check(0, "Trocar os sons dentro de cada categoria", s -> s.sounds, (s, v) -> s.sounds = v, null);
        label(1, "Categorias:");
        listChecks(2, SOUND_CATEGORIES, s -> s.soundCategories, (s, v) -> s.soundCategories = v, () -> edit.sounds);

        section("17 - Recompensas");
        check(0, "Recompensas aleatórias: as quests dão itens aleatórios de recompensa", s -> s.questRewards, (s, v) -> s.questRewards = v, null);
        check(1, "17.1 - Recompensas do Mestre Ancião", s -> s.questRewardsElder, (s, v) -> s.questRewardsElder = v, () -> edit.questRewards);
        check(1, "17.2 - Recompensas dos desafios do diário de aventura", s -> s.questRewardsJournal,
                (s, v) -> s.questRewardsJournal = v, () -> edit.questRewards);

        section("18 - Pesca");
        check(0, "Randomizar o poder de pesca das iscas", s -> s.fishingBaitPower, (s, v) -> s.fishingBaitPower = v, null);
        slider(1, "Multiplicador mínimo", 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.fishingBaitPowerMin, (s, v) -> s.fishingBaitPowerMin = v);
        slider(1, "Multiplicador máximo", 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.fishingBaitPowerMax, (s, v) -> s.fishingBaitPowerMax = v);
        check(0, "Randomizar o poder de pesca das varas", s -> s.fishingRodPower, (s, v) -> s.fishingRodPower = v, null);
        slider(1, "Multiplicador mínimo", 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.fishingRodPowerMin, (s, v) -> s.fishingRodPowerMin = v);
        slider(1, "Multiplicador máximo", 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.fishingRodPowerMax, (s, v) -> s.fishingRodPowerMax = v);
        check(0, "Randomizar a pescaria", s -> s.fishingCatches, (s, v) -> s.fishingCatches = v, null);
        Map<String, String> fishingModes = new LinkedHashMap<>();
        fishingModes.put("fish", "Peixes (cada peixe vira outro peixe)");
        fishingModes.put("all", "Todos os itens do jogo");
        dropdown(1, "Modo:", fishingModes, s -> s.fishingCatchesMode, (s, v) -> s.fishingCatchesMode = v, () -> edit.fishingCatches);

        section("Avançado");
        check(0, "Gravar o arquivo de spoiler com todas as trocas", s -> s.spoilerLog, (s, v) -> s.spoilerLog = v, null);
        text(0, "Mobs que nunca devem ser trocados (IDs separados por vírgula):", s -> s.excludeMobs, (s, v) -> s.excludeMobs = v);
        text(0, "Itens que nunca devem ser trocados (IDs separados por vírgula):", s -> s.excludeItems, (s, v) -> s.excludeItems = v);

        if (visible) visibleEnd = flow.next();
        content.setContentBox(new java.awt.Rectangle(content.getWidth(), visibleEnd + 10));
        content.setScrollY(0);
        refresh();
    }

    /** Groups of a per-mob stat: settings field prefix, plural and singular name. */
    private static final String[][] STAT_GROUPS = {{"enemy", "inimigos", "inimigo"}, {"villager", "aldeões", "aldeão"},
            {"critter", "bichos", "bicho"}, {"animal", "animais", "animal"}, {"boss", "chefes", "chefe"}};

    /** The 5 options (enemies, villagers, critters, animals, bosses) of a stat whose fields are named like enemyArmor. */
    private void statBlocks(String number, String title, String key, String what) {
        for (int i = 0; i < STAT_GROUPS.length; i++) {
            String f = STAT_GROUPS[i][0] + key;
            healthBlock(number + "." + (i + 1) + " - " + title + " dos " + STAT_GROUPS[i][1],
                    "Usar " + what + " do " + STAT_GROUPS[i][2] + " antes da troca",
                    s -> (Boolean) fieldValue(s, f), (s, v) -> setFieldValue(s, f, v),
                    s -> (String) fieldValue(s, f + "Mode"), (s, v) -> setFieldValue(s, f + "Mode", v),
                    s -> (Float) fieldValue(s, f + "Min"), (s, v) -> setFieldValue(s, f + "Min", v),
                    s -> (Float) fieldValue(s, f + "Max"), (s, v) -> setFieldValue(s, f + "Max", v));
        }
    }

    private static Object fieldValue(SRSSettings s, String name) {
        try {
            return SRSSettings.class.getField(name).get(s);
        } catch (Exception e) {
            throw new IllegalStateException("Missing setting " + name, e);
        }
    }

    private static void setFieldValue(SRSSettings s, String name, Object value) {
        try {
            SRSSettings.class.getField(name).set(s, value);
        } catch (Exception e) {
            throw new IllegalStateException("Missing setting " + name, e);
        }
    }

    /** One per-mob stat option (health, defense, knockback): on/off, mode and the min/max multiplier sliders. */
    private void healthBlock(String title, String preswapLabel, Predicate<SRSSettings> enabledGet, BiConsumer<SRSSettings, Boolean> enabledSet,
                             Function<SRSSettings, String> modeGet, BiConsumer<SRSSettings, String> modeSet,
                             Function<SRSSettings, Float> minGet, BiConsumer<SRSSettings, Float> minSet,
                             Function<SRSSettings, Float> maxGet, BiConsumer<SRSSettings, Float> maxSet) {
        flow.next(4);
        check(0, title, enabledGet, enabledSet, null);
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("multiplier", "Multiplicador aleatório");
        modes.put("preswap", preswapLabel);
        dropdown(1, "Modo:", modes, modeGet, modeSet, () -> enabledGet.test(edit));
        slider(1, "Multiplicador mínimo", 0.1F, 5.0F, 0.1F, MULTIPLIER, minGet, minSet);
        slider(1, "Multiplicador máximo", 0.1F, 5.0F, 0.1F, MULTIPLIER, maxGet, maxSet);
    }

    private int x(int indent) {
        return 10 + indent * INDENT;
    }

    private int width(int indent) {
        return content.getMinContentWidth() - x(indent) - 10;
    }

    /** Starts a section (one tab). Only the selected section's components are added; the helpers skip the rest. */
    private void section(String title) {
        if (visible) visibleEnd = flow.next();
        sectionIndex++;
        sectionTitles.add(title);
        visible = sectionIndex == selectedTab;
        if (!visible) return;
        flow = new FormFlow(5);
        content.addComponent(new FormLabel(title, new FontOptions(18), -1, 8, flow.next(24)));
        content.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 8, flow.next(6), content.getMinContentWidth() - 16, true));
    }

    private void label(int indent, String text) {
        if (!visible) return;
        FormLabel label = content.addComponent(new FormLabel(text, new FontOptions(14), -1, x(indent), flow.next(), width(indent)));
        flow.next(Math.max(18, label.getHeight()) + 4);
    }

    private FormCheckBox check(int indent, String text, Predicate<SRSSettings> getter, BiConsumer<SRSSettings, Boolean> setter, BooleanSupplier enabledWhen) {
        if (!visible) return null;
        FormCheckBox box = content.addComponent(new FormCheckBox(text, x(indent), flow.next(), width(indent), getter.test(edit)));
        flow.next(Math.max(20, box.getBoundingBox().height) + 4);
        box.onClicked(e -> {
            setter.accept(edit, box.checked);
            refresh();
        });
        if (enabledWhen != null) refreshers.add(() -> box.setActive(enabledWhen.getAsBoolean()));
        return box;
    }

    private void slider(int indent, String text, float min, float max, float step, int format,
                        Function<SRSSettings, Float> getter, BiConsumer<SRSSettings, Float> setter) {
        if (!visible) return;
        int ticks = Math.round((max - min) / step);
        int start = Math.max(0, Math.min(ticks, Math.round((getter.apply(edit) - min) / step)));
        FormSlider slider = new FormSlider(text, x(indent), 0, start, 0, ticks, Math.min(420, width(indent))) {
            @Override
            public String getValueText() {
                return format(min + getValue() * step, format);
            }
        };
        slider.drawValue = true;
        content.addComponent(slider);
        slider.setPosition(x(indent), flow.next(slider.getTotalHeight() + 8));
        slider.onChanged(e -> setter.accept(edit, round(min + slider.getValue() * step)));
    }

    private void dropdown(int indent, String text, Map<String, String> options, Function<SRSSettings, String> getter,
                          BiConsumer<SRSSettings, String> setter, BooleanSupplier enabledWhen) {
        if (!visible) return;
        int y = flow.next(32);
        content.addComponent(new FormLabel(text, new FontOptions(14), -1, x(indent), y + 5));
        FormDropdownSelectionButton<String> dropdown = content.addComponent(
                new FormDropdownSelectionButton<String>(x(indent) + 60, y, FormInputSize.SIZE_24, ButtonColor.BASE, Math.min(380, width(indent) - 60)));
        for (Map.Entry<String, String> option : options.entrySet()) {
            dropdown.options.add(option.getKey(), new StaticMessage(option.getValue()));
        }
        String current = getter.apply(edit) == null ? "" : getter.apply(edit).toLowerCase();
        if (!options.containsKey(current)) current = options.keySet().iterator().next();
        dropdown.setSelected(current, new StaticMessage(options.get(current)));
        dropdown.onSelected(e -> setter.accept(edit, e.value));
        if (enabledWhen != null) refreshers.add(() -> dropdown.setActive(enabledWhen.getAsBoolean()));
    }

    /** One checkbox per known entry of a comma separated list. Unknown entries typed in the cfg are kept. */
    private void listChecks(int indent, String[][] entries, Function<SRSSettings, String> getter, BiConsumer<SRSSettings, String> setter,
                            BooleanSupplier enabledWhen) {
        if (!visible) return;
        int columns = 2;
        int columnWidth = width(indent) / columns;
        for (int i = 0; i < entries.length; i++) {
            final String key = entries[i][0];
            int y = i % columns == 0 ? flow.next(22) : flow.next() - 22;
            Set<String> selected = SRS.parseList(getter.apply(edit));
            FormCheckBox box = content.addComponent(new FormCheckBox(entries[i][1], x(indent) + (i % columns) * columnWidth, y, columnWidth - 10,
                    selected.contains(key)));
            box.onClicked(e -> {
                Set<String> current = new LinkedHashSet<>(SRS.parseList(getter.apply(edit)));
                if (box.checked) {
                    current.add(key);
                } else {
                    current.remove(key);
                }
                setter.accept(edit, orderedList(entries, current));
            });
            refreshers.add(() -> box.setActive(enabledWhen.getAsBoolean()));
        }
    }

    private void text(int indent, String text, Function<SRSSettings, String> getter, BiConsumer<SRSSettings, String> setter) {
        if (!visible) return;
        label(indent, text);
        FormTextInput input = content.addComponent(new FormTextInput(x(indent), flow.next(30), FormInputSize.SIZE_24, width(indent), 2000));
        input.setText(getter.apply(edit) == null ? "" : getter.apply(edit));
        pullers.add(() -> setter.accept(edit, input.getText().trim()));
    }

    /** Prefixes each label with its position ("1. Evil's Protector"), used to show the boss progression. */
    private static String[][] numbered(String[][] entries) {
        String[][] out = new String[entries.length][];
        for (int i = 0; i < entries.length; i++) out[i] = new String[]{entries[i][0], (i + 1) + ". " + entries[i][1]};
        return out;
    }

    private static String orderedList(String[][] entries, Set<String> values) {
        List<String> out = new ArrayList<>();
        for (String[] entry : entries) {
            if (values.contains(entry[0])) out.add(entry[0]);
        }
        for (String v : values) {
            if (!out.contains(v)) out.add(v);
        }
        return String.join(",", out);
    }

    private static String format(float value, int format) {
        if (format == PERCENT) return Math.round(value * 100) + "%";
        if (format == PERCENT_DECIMAL) return String.format(java.util.Locale.ROOT, "%.1f", value * 100).replace('.', ',') + "%";
        return "x" + String.format(java.util.Locale.ROOT, "%.1f", value).replace('.', ',');
    }

    private static float round(float value) {
        return Math.round(value * 10000) / 10000.0F;
    }

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    // ===== Actions =====

    private void pull() {
        for (Runnable r : pullers) r.run();
        String seedText = seedInput.getText().trim();
        try {
            edit.seed = seedText.isEmpty() || seedText.equals("-") ? 0 : Long.parseLong(seedText);
        } catch (NumberFormatException e) {
            edit.seed = 0;
        }
    }

    private boolean isDirty() {
        pull();
        return !edit.fingerprint().equals(SRS.stored().fingerprint());
    }

    private boolean needsRestart() {
        return !SRS.stored().fingerprint().equals(SRS.settings().fingerprint());
    }

    private boolean save() {
        pull();
        SRS.stored().applyScript(edit.toScript());
        boolean saved = SRS.saveConfig();
        if (saved) {
            setStatus(needsRestart() ? "Configuração salva! Reinicie o jogo para aplicar." : "Configuração salva. Nada mudou em relação ao que está em uso.", true);
        } else {
            setStatus("Não foi possível salvar o arquivo de configuração.", false);
        }
        return saved;
    }

    private void resetDefaults() {
        pull();
        long seed = edit.seed;
        edit = new SRSSettings();
        edit.seed = seed;
        buildContent();
        setStatus("Valores padrão carregados (a seed foi mantida). Clique em Salvar para gravar.", false);
    }

    private void restart() {
        if (isDirty() && !save()) return;
        Runnable restart = ModsForm.restartGameRunnable();
        if (restart != null) {
            restart.run();
        } else {
            WindowManager.getWindow().requestClose();
        }
    }

    private void askRandomSeed() {
        confirm("Sortear uma nova seed? Toda a randomização muda (depois de salvar e reiniciar o jogo).", "Sim", "Não", () -> {
            long seed = 0;
            while (seed == 0) seed = new Random().nextLong();
            seedInput.setText(String.valueOf(seed));
            setStatus("Nova seed sorteada. Clique em Salvar para gravar.", false);
            modsForm.makeCurrent(screen);
        }, () -> modsForm.makeCurrent(screen));
    }

    private void back() {
        if (!isDirty()) {
            close();
            return;
        }
        confirm("Você tem alterações não salvas no Super Randomizer Show.", "Salvar", "Descartar", () -> {
            save();
            close();
        }, this::close);
    }

    /** Leaves this screen and removes it from the mods menu. */
    private void close() {
        modsForm.makeCurrent(modsMain);
        modsForm.removeComponent(screen);
    }

    /** Shows a yes/no dialog. Each action decides which screen comes next. */
    private void confirm(String message, String yes, String no, Runnable onYes, Runnable onNo) {
        ConfirmationForm dialog = confirmationForm();
        dialog.setupConfirmation(new StaticMessage(message), new StaticMessage(yes), new StaticMessage(no), onYes, onNo);
        modsForm.makeCurrent(dialog);
    }

    /** The mods menu's own dialog, so it looks and behaves like the rest of the menu. */
    private ConfirmationForm confirmationForm() {
        try {
            ConfirmationForm dialog = Reflect.get(ModsForm.class, modsForm, "confirmationForm");
            if (dialog != null) return dialog;
        } catch (Exception ignored) {
        }
        if (ownDialog == null) {
            ownDialog = modsForm.addComponent(new ConfirmationForm("srsconfirm") {
                @Override
                public void onWindowResized(GameWindow window) {
                    super.onWindowResized(window);
                    setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
                }
            });
            GameWindow window = WindowManager.getWindow();
            if (window != null) ownDialog.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
        }
        return ownDialog;
    }

    private void setStatus(String text, boolean success) {
        status.setText(text);
        status.setColor(success ? getInterfaceStyle().successTextColor : getInterfaceStyle().warningTextColor);
    }

    private static SRSSettings copy(SRSSettings s) {
        SRSSettings out = new SRSSettings();
        out.applyScript(s.toScript());
        return out;
    }

}
