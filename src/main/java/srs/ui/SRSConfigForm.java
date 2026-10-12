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
 * Every text comes in Portuguese and English ({@link Lang}): the screen follows the game's language.
 */
public class SRSConfigForm extends Form {

    private static final int WIDTH = 700;
    private static final int HEIGHT = 580;
    private static final int INDENT = 24;
    /** Width of the gray note to the right of some checkboxes. */
    private static final int NOTE_WIDTH = 170;

    // Slider value formats
    private static final int MULTIPLIER = 0;
    private static final int PERCENT = 1;
    private static final int PERCENT_DECIMAL = 2;

    // {key, Portuguese, English}
    private static final String[][] VISUAL_FOLDERS = {
            {"items", "Ícones de itens", "Item icons"}, {"mobs", "Mobs e NPCs", "Mobs and NPCs"},
            {"player/armor", "Armaduras e cosméticos", "Armor and cosmetics"}, {"player/weapons", "Armas (na mão)", "Weapons (in hand)"},
            {"player/holditems", "Itens segurados", "Held items"}, {"player/hair", "Cabelos", "Hair"}, {"projectiles", "Projéteis", "Projectiles"}};
    private static final String[][] DROP_GROUPS = {
            {"enemies", "Inimigos", "Enemies"}, {"bosses", "Chefes", "Bosses"}, {"critters", "Bichos", "Critters"},
            {"animals", "Animais", "Animals"}, {"rocks", "Rochas", "Rocks"}, {"trees", "Árvores", "Trees"},
            {"plants", "Plantas", "Plants"}, {"crops", "Plantações", "Crops"}, {"walls", "Paredes", "Walls"}};
    private static final String[][] SOUND_CATEGORIES = {
            {"music", "Músicas", "Music"}, {"animals", "Animais", "Animals"}, {"creatures", "Criaturas", "Creatures"},
            {"environment", "Ambiente", "Environment"}, {"weather", "Clima", "Weather"}, {"weapons", "Armas", "Weapons"},
            {"impacts", "Impactos e magias", "Impacts and magic"}, {"interface", "Interface e baús", "Interface and chests"},
            {"bosses", "Vozes de chefes e mobs", "Boss and mob voices"}, {"notes", "Notas musicais", "Musical notes"},
            {"other", "Outros", "Other"}};

    // Texts used in many places, in the language the game had when the screen opened
    private final String modeText = t("Modo:", "Mode:");
    private final String randomMultiplierText = t("Multiplicador aleatório", "Random multiplier");
    private final String minMultiplierText = t("Multiplicador mínimo", "Minimum multiplier");
    private final String maxMultiplierText = t("Multiplicador máximo", "Maximum multiplier");
    private final String performanceNote = t("ativar pode afetar o desempenho do jogo", "turning this on may affect game performance");

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
        addComponent(new FormLabel(t("A mesma seed sempre gera o mesmo resultado. Mudanças valem depois de reiniciar o jogo.",
                "The same seed always gives the same result. Changes apply after restarting the game."),
                new FontOptions(12).color(getInterfaceStyle().inactiveTextColor), 0, WIDTH / 2, 34, WIDTH - 20));

        addComponent(new FormLabel("Seed:", new FontOptions(16), -1, 10, 60));
        seedInput = addComponent(new FormTextInput(60, 56, FormInputSize.SIZE_24, 200, 20));
        seedInput.setRegexMatchFull("-?[0-9]*");
        seedInput.placeHolder = new StaticMessage(t("0 = sortear no próximo início", "0 = roll one on next start"));
        seedInput.setText(String.valueOf(edit.seed));
        addComponent(new FormTextButton(t("Sortear", "Roll"), 268, 56, 86, FormInputSize.SIZE_24, ButtonColor.BASE)).onClicked(e -> askRandomSeed());
        addComponent(new FormLabel(t("No multiplayer, a seed de todos deve ser a mesma", "In multiplayer, everyone must use the same seed"),
                new FontOptions(12).color(getInterfaceStyle().inactiveTextColor), -1, 362, 62, WIDTH - 372));

        content = addComponent(new FormContentBox(5, 90, WIDTH - 10, HEIGHT - 90 - 85));
        addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 5, 89, WIDTH - 10, true));
        addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 5, HEIGHT - 84, WIDTH - 10, true));

        status = addComponent(new FormLabel("", new FontOptions(14), 0, WIDTH / 2, HEIGHT - 74, WIDTH - 20));

        int buttonWidth = (WIDTH - 25) / 4;
        int buttonY = HEIGHT - 45;
        addComponent(new FormTextButton(t("Salvar", "Save"), 5, buttonY, buttonWidth, FormInputSize.SIZE_32, ButtonColor.BASE)).onClicked(e -> save());
        addComponent(new FormTextButton(t("Restaurar padrão", "Restore defaults"), 10 + buttonWidth, buttonY, buttonWidth, FormInputSize.SIZE_32,
                ButtonColor.BASE)).onClicked(e -> resetDefaults());
        boolean canRestart = ModsForm.restartGameRunnable() != null;
        restartButton = addComponent(new FormTextButton(canRestart ? t("Reiniciar jogo", "Restart game") : t("Fechar jogo", "Close game"),
                15 + buttonWidth * 2, buttonY, buttonWidth, FormInputSize.SIZE_32, ButtonColor.BASE));
        restartButton.onClicked(e -> restart());
        addComponent(new FormTextButton(t("Voltar", "Back"), 20 + buttonWidth * 3, buttonY, buttonWidth, FormInputSize.SIZE_32, ButtonColor.BASE))
                .onClicked(e -> back());

        buildContent();     // nothing selected yet: only collects the section titles for the tabs
        selectedTab = 0;
        buildContent();
        if (needsRestart()) {
            setStatus(t("Há mudanças salvas que ainda não estão em uso. Reinicie o jogo para aplicar.",
                    "There are saved changes that aren't in use yet. Restart the game to apply them."), false);
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
     * One Necesse tab per section along the top edge of the form: its number as the label ("Avançado"/"Advanced" has none),
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

        section(t("1 - Aparecimento de mobs", "1 - Mob spawns"));
        check(0, t("Trocar onde inimigos, bichos, NPCs e chefes aparecem", "Swap where enemies, critters, NPCs and bosses spawn"),
                s -> s.mobSpawns, (s, v) -> s.mobSpawns = v, null);
        BooleanSupplier mobs = () -> edit.mobSpawns;
        check(1, t("Inimigos que nascem naturalmente", "Naturally spawning enemies"), s -> s.mobSpawnHostiles, (s, v) -> s.mobSpawnHostiles = v, mobs);
        check(1, t("Bichos (criaturas passivas)", "Critters (passive creatures)"), s -> s.mobSpawnCritters, (s, v) -> s.mobSpawnCritters = v, mobs);
        check(1, t("Animais (ovelhas, vacas, porcos, galinhas, avestruzes, pinguins...) em mundos e áreas novas",
                "Animals (sheep, cows, pigs, chickens, ostriches, penguins...) in new worlds and areas"), s -> s.mobSpawnAnimals,
                (s, v) -> s.mobSpawnAnimals = v, mobs);
        check(1, t("NPCs de vilas e estruturas", "Village and structure NPCs"), s -> s.mobSpawnTownNPCs, (s, v) -> s.mobSpawnTownNPCs = v, mobs);
        flow.next(4);
        label(1, t("1.1 - NPCs que podem trocar de lugar entre si:", "1.1 - NPCs that can swap places with each other:"));
        listChecks(2, MobChoices.townNPCs(edit.townNPCPool), s -> s.townNPCPool, (s, v) -> s.townNPCPool = v, () -> edit.mobSpawns && edit.mobSpawnTownNPCs);
        flow.next(4);
        label(1, t("1.2 - Chefes que podem trocar entre si (vale também para a troca de drops):",
                "1.2 - Bosses that can swap with each other (also used by the drop swap):"));
        listChecks(2, numbered(MobChoices.bosses(edit.bossPool)), s -> s.bossPool, (s, v) -> s.bossPool = v,
                () -> edit.mobSpawns || edit.drops && SRS.parseList(edit.dropGroups).contains("bosses"));

        section("2 - Drops");
        check(0, t("Trocar quem dropa o quê", "Swap who drops what"), s -> s.drops, (s, v) -> s.drops = v, null);
        label(1, t("Grupos que participam:", "Groups that take part:"));
        listChecks(2, 3, Lang.pairs(DROP_GROUPS), s -> s.dropGroups, (s, v) -> s.dropGroups = v, () -> edit.drops);
        Map<String, String> dropModes = new LinkedHashMap<>();
        dropModes.put("group", t("Troca entre o mesmo grupo/classificação", "Swap within the same group/category"));
        dropModes.put("all", t("Troca totalmente aleatória", "Fully random swap"));
        dropdown(1, modeText, dropModes, s -> s.dropsMode, (s, v) -> s.dropsMode = v, () -> edit.drops);
        label(1, t("Na troca totalmente aleatória, os grupos marcados viram um grupo só: um zumbi pode dropar como uma árvore, e uma rocha "
                        + "como um chefe. Quem dropa algo só troca com quem também dropa. Objetos colocados por você devolvem o próprio item.",
                "In the fully random swap, the checked groups become a single group: a zombie can drop like a tree, and a rock "
                        + "like a boss. Anything that drops something only swaps with something that also drops. Objects you placed give back "
                        + "their own item."));
        check(0, t("Randomizar a quantidade de cada item dropado (nunca zera um drop)", "Randomize the amount of each dropped item (never down to zero)"),
                s -> s.dropQuantities, (s, v) -> s.dropQuantities = v, null);
        slider(1, t("Quantidade mínima", "Minimum amount"), 0.0F, 5.0F, 0.1F, MULTIPLIER, s -> s.dropQuantityMin, (s, v) -> s.dropQuantityMin = v);
        slider(1, t("Quantidade máxima", "Maximum amount"), 0.0F, 5.0F, 0.1F, MULTIPLIER, s -> s.dropQuantityMax, (s, v) -> s.dropQuantityMax = v);
        check(0, t("Chance de dropar também um item aleatório ao matar um mob", "Chance to also drop a random item when killing a mob"),
                s -> s.randomItemDrop, (s, v) -> s.randomItemDrop = v, null);
        slider(1, t("Chance do item aleatório", "Random item chance"), 0.005F, 1.0F, 0.005F, PERCENT_DECIMAL, s -> s.randomItemDropChance,
                (s, v) -> s.randomItemDropChance = v);

        section(t("3 - Receitas", "3 - Recipes"));
        check(0, t("Embaralhar os ingredientes das receitas", "Shuffle recipe ingredients"), s -> s.recipes, (s, v) -> s.recipes = v, null);
        check(1, t("Só trocar entre receitas da mesma estação de trabalho (mais jogável)",
                "Only swap between recipes of the same crafting station (more playable)"), s -> s.recipesSameStation,
                (s, v) -> s.recipesSameStation = v, () -> edit.recipes);
        slider(1, t("Nível de embaralhamento (parte das receitas que trocam de ingredientes)", "Shuffle level (share of recipes that swap ingredients)"),
                0.01F, 1.0F, 0.01F, PERCENT, s -> s.recipesShuffleLevel, (s, v) -> s.recipesShuffleLevel = v);
        flow.next(4);
        check(0, t("Poção de Ressureição grátis", "Free Revival Potion"),
                t("Ativando esta opção, poção de ressureição é feita sem precisar de ingredientes na bancada de alquimia",
                        "With this option on, the revival potion is crafted at the alchemy table without needing any ingredients"),
                s -> s.recipesFreeRevivalPotion, (s, v) -> s.recipesFreeRevivalPotion = v, null);

        section(t("4 - Lojas", "4 - Shops"));
        check(0, t("Embaralhar as lojas dos NPCs de cidade", "Shuffle the town NPCs' shops"), s -> s.shops, (s, v) -> s.shops = v, null);
        Map<String, String> shopModes = new LinkedHashMap<>();
        shopModes.put("items", t("Trocar cada item vendido e comprado", "Swap every item sold and bought"));
        shopModes.put("npcs", t("Só trocar as lojas inteiras entre os aldeões", "Only swap whole shops between villagers"));
        dropdown(1, modeText, shopModes, s -> s.shopsMode, (s, v) -> s.shopsMode = v, () -> edit.shops);
        flow.next(4);
        check(0, t("Randomizar os preços de compra e venda", "Randomize buying and selling prices"), s -> s.shopPrices, (s, v) -> s.shopPrices = v, null);
        slider(1, t("Multiplicador mínimo dos preços", "Minimum price multiplier"), 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.shopPricesMin,
                (s, v) -> s.shopPricesMin = v);
        slider(1, t("Multiplicador máximo dos preços", "Maximum price multiplier"), 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.shopPricesMax,
                (s, v) -> s.shopPricesMax = v);

        section(t("5 - Danos", "5 - Damage"));
        check(0, t("5.1 - Armas", "5.1 - Weapons"), s -> s.weaponDamage, (s, v) -> s.weaponDamage = v, null);
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("multiplier", randomMultiplierText);
        modes.put("shuffle", t("Trocar entre armas de força parecida", "Swap between weapons of similar strength"));
        modes.put("chaos", t("Caos: trocar com qualquer arma do mesmo tipo", "Chaos: swap with any weapon of the same type"));
        dropdown(1, modeText, modes, s -> s.weaponDamageMode, (s, v) -> s.weaponDamageMode = v, () -> edit.weaponDamage);
        slider(1, t("Multiplicador mínimo (modo multiplicador)", "Minimum multiplier (multiplier mode)"), 0.1F, 5.0F, 0.1F, MULTIPLIER,
                s -> s.weaponDamageMin, (s, v) -> s.weaponDamageMin = v);
        slider(1, t("Multiplicador máximo (modo multiplicador)", "Maximum multiplier (multiplier mode)"), 0.1F, 5.0F, 0.1F, MULTIPLIER,
                s -> s.weaponDamageMax, (s, v) -> s.weaponDamageMax = v);

        flow.next(4);
        check(0, t("5.2 - Dano dos inimigos", "5.2 - Enemy damage"), s -> s.enemyDamage, (s, v) -> s.enemyDamage = v, null);
        Map<String, String> enemyModes = new LinkedHashMap<>();
        enemyModes.put("multiplier", randomMultiplierText);
        enemyModes.put("preswap", t("Usar o dano do inimigo antes da troca", "Use the pre-swap enemy's damage"));
        dropdown(1, modeText, enemyModes, s -> s.enemyDamageMode, (s, v) -> s.enemyDamageMode = v, () -> edit.enemyDamage);
        label(1, t("As barras valem nos dois modos: no \"antes da troca\" elas multiplicam o dano do inimigo original (x1,0 e x1,0 = dano igual ao dele).",
                "The sliders apply in both modes: in \"pre-swap\" mode they multiply the original enemy's damage (x1.0 and x1.0 = the same damage)."));
        slider(1, minMultiplierText, 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.enemyDamageMin, (s, v) -> s.enemyDamageMin = v);
        slider(1, maxMultiplierText, 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.enemyDamageMax, (s, v) -> s.enemyDamageMax = v);

        flow.next(4);
        check(0, t("5.3 - Dano dos chefes", "5.3 - Boss damage"), s -> s.bossDamage, (s, v) -> s.bossDamage = v, null);
        Map<String, String> bossModes = new LinkedHashMap<>();
        bossModes.put("multiplier", randomMultiplierText);
        bossModes.put("preswap", t("Usar o dano do chefe antes da troca", "Use the pre-swap boss's damage"));
        dropdown(1, modeText, bossModes, s -> s.bossDamageMode, (s, v) -> s.bossDamageMode = v, () -> edit.bossDamage);
        label(1, t("As barras valem nos dois modos: no \"antes da troca\" elas multiplicam o dano do chefe original (x1,0 e x1,0 = dano igual ao dele).",
                "The sliders apply in both modes: in \"pre-swap\" mode they multiply the original boss's damage (x1.0 and x1.0 = the same damage)."));
        slider(1, minMultiplierText, 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.bossDamageMin, (s, v) -> s.bossDamageMin = v);
        slider(1, maxMultiplierText, 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.bossDamageMax, (s, v) -> s.bossDamageMax = v);

        section(t("6 - Vida", "6 - Health"));
        label(0, t("Em todos os itens abaixo, as barras valem nos dois modos: no \"antes da troca\" elas multiplicam a vida do mob original "
                        + "(x1,0 e x1,0 = vida igual à dele).",
                "In every option below, the sliders apply in both modes: in \"pre-swap\" mode they multiply the original mob's health "
                        + "(x1.0 and x1.0 = the same health)."));
        statBlocks("6", "Vida", "Health", "a vida", "health");

        section(t("7 - Defesa", "7 - Defense"));
        label(0, t("A defesa é a armadura: cada golpe perde metade da armadura em dano. As barras valem nos dois modos: no \"antes da troca\" "
                        + "elas multiplicam a defesa do mob original (x1,0 e x1,0 = defesa igual à dele).",
                "Defense is armor: every hit loses half the armor in damage. The sliders apply in both modes: in \"pre-swap\" mode "
                        + "they multiply the original mob's defense (x1.0 and x1.0 = the same defense)."));
        statBlocks("7", "Defesa", "Armor", "a defesa", "defense");

        section(t("8 - Empurrão", "8 - Knockback"));
        label(0, t("O empurrão é o quanto o mob é jogado para trás ao levar um golpe (normal = x1,0; 0 = não se move). As barras valem nos dois modos: "
                        + "no \"antes da troca\" elas multiplicam o empurrão do mob original.",
                "Knockback is how far the mob is pushed back when hit (normal = x1.0; 0 = doesn't move). The sliders apply in both modes: "
                        + "in \"pre-swap\" mode they multiply the original mob's knockback."));
        statBlocks("8", "Empurrão", "Knockback", "o empurrão", "knockback");

        section(t("9 - Chance de crítico", "9 - Critical chance"));
        check(0, t("Randomizar o crítico de armas e equipamentos", "Randomize the critical chance of weapons and equipment"),
                s -> s.critChance, (s, v) -> s.critChance = v, null);
        slider(1, t("Crítico extra mínimo em cada arma", "Minimum extra critical chance on each weapon"), 0.0F, 1.0F, 0.01F, PERCENT,
                s -> s.weaponCritMin, (s, v) -> s.weaponCritMin = v);
        slider(1, t("Crítico extra máximo em cada arma", "Maximum extra critical chance on each weapon"), 0.0F, 1.0F, 0.01F, PERCENT,
                s -> s.weaponCritMax, (s, v) -> s.weaponCritMax = v);
        slider(1, t("Multiplicador mínimo do crítico dos equipamentos", "Minimum multiplier of equipment critical chance"), 0.0F, 5.0F, 0.1F,
                MULTIPLIER, s -> s.equipmentCritMultMin, (s, v) -> s.equipmentCritMultMin = v);
        slider(1, t("Multiplicador máximo do crítico dos equipamentos", "Maximum multiplier of equipment critical chance"), 0.0F, 5.0F, 0.1F,
                MULTIPLIER, s -> s.equipmentCritMultMax, (s, v) -> s.equipmentCritMultMax = v);
        slider(1, t("Crítico extra máximo por armadura/acessório", "Maximum extra critical chance per armor piece/trinket"), 0.0F, 0.5F, 0.01F,
                PERCENT, s -> s.equipmentCritBonusMax, (s, v) -> s.equipmentCritBonusMax = v);

        section(t("10 - Bônus de conjunto", "10 - Set bonuses"));
        check(0, t("Randomizar os bônus de conjunto das armaduras", "Randomize armor set bonuses"), s -> s.armorSetBonuses,
                (s, v) -> s.armorSetBonuses = v, null);
        Map<String, String> setModes = new LinkedHashMap<>();
        setModes.put("swap", t("Trocar os bônus entre os conjuntos", "Swap the bonuses between sets"));
        setModes.put("double", t("Bônus duplo: o próprio + o de outro conjunto", "Double bonus: its own + another set's"));
        dropdown(1, modeText, setModes, s -> s.armorSetBonusMode, (s, v) -> s.armorSetBonusMode = v, () -> edit.armorSetBonuses);

        section(t("11 - Efeitos de equipamentos", "11 - Equipment effects"));
        check(0, t("Trocar os efeitos entre acessórios e entre peças de armadura do mesmo slot",
                "Swap the effects between trinkets and between armor pieces of the same slot"), s -> s.equipmentEffects,
                (s, v) -> s.equipmentEffects = v, null);
        Map<String, String> effectModes = new LinkedHashMap<>();
        effectModes.put("all", t("Trocar com qualquer um", "Swap with any of them"));
        effectModes.put("class", t("Só entre itens da mesma classe", "Only between items of the same class"));
        effectModes.put("progression", t("Só entre itens da mesma progressão", "Only between items of the same progression"));
        dropdown(1, modeText, effectModes, s -> s.equipmentEffectsMode, (s, v) -> s.equipmentEffectsMode = v, () -> edit.equipmentEffects);
        label(1, t("Classes: corpo a corpo, distância, magia, invocação e geral (lidas dos efeitos do acessório e do capacete do conjunto). "
                        + "Progressão: itens ordenados por raridade e valor (armaduras pela defesa) só trocam com os vizinhos nessa ordem.",
                "Classes: melee, ranged, magic, summon and general (read from the effects of the trinket and of the set's helmet). "
                        + "Progression: items sorted by rarity and value (armor by defense) only swap with their neighbors in that order."));

        section(t("12 - Dano dos equipamentos e pergaminhos", "12 - Equipment damage and scrolls"));
        check(0, t("Randomizar o bônus de dano dos equipamentos", "Randomize the damage bonus of equipment"), s -> s.equipmentDamage,
                (s, v) -> s.equipmentDamage = v, null);
        slider(1, minMultiplierText, 0.0F, 5.0F, 0.1F, MULTIPLIER, s -> s.equipmentDamageMultMin, (s, v) -> s.equipmentDamageMultMin = v);
        slider(1, maxMultiplierText, 0.0F, 5.0F, 0.1F, MULTIPLIER, s -> s.equipmentDamageMultMax, (s, v) -> s.equipmentDamageMultMax = v);
        flow.next(4);
        check(0, t("Randomizar os pergaminhos: os valores de cada encantamento são multiplicados por um fator fixo",
                "Randomize scrolls: the values of each enchantment are multiplied by a fixed factor"), s -> s.enchantScrolls,
                (s, v) -> s.enchantScrolls = v, null);
        slider(1, t("Multiplicador mínimo dos encantamentos", "Minimum enchantment multiplier"), 0.0F, 5.0F, 0.1F, MULTIPLIER,
                s -> s.enchantScrollsMin, (s, v) -> s.enchantScrollsMin = v);
        slider(1, t("Multiplicador máximo dos encantamentos", "Maximum enchantment multiplier"), 0.0F, 5.0F, 0.1F, MULTIPLIER,
                s -> s.enchantScrollsMax, (s, v) -> s.enchantScrollsMax = v);

        section(t("13 - Visuais", "13 - Visuals"));
        check(0, t("Trocar as texturas (um mob vira outro mob inteiro, um capacete vira outro capacete...)",
                "Swap textures (a mob becomes another whole mob, a helmet becomes another helmet...)"), s -> s.visuals, (s, v) -> s.visuals = v, null);
        label(1, t("O que trocar:", "What to swap:"));
        listChecks(2, Lang.pairs(VISUAL_FOLDERS), s -> s.visualFolders, (s, v) -> s.visualFolders = v, () -> edit.visuals);
        flow.next(4);
        check(0, t("Troca Bugada Divertida: as partes dos mobs trocam separadas (ex.: slime com braços de zumbi). Funciona mesmo com os visuais desligados.",
                "Funny Bugged Swap: mob parts swap separately (e.g. a slime with zombie arms). Works even with visuals off."),
                s -> s.funnyBuggedSwap, (s, v) -> s.funnyBuggedSwap = v, null);
        flow.next(4);
        check(0, t("Rotação: a seed gera 3 opções de troca de visual, que se alternam a cada 6 minutos de jogo",
                "Rotation: the seed makes 3 visual swap options, which take turns every 6 minutes of play"), performanceNote,
                s -> s.visualRotation, (s, v) -> s.visualRotation = v, () -> edit.visuals || edit.funnyBuggedSwap);

        section(t("14 - Projéteis", "14 - Projectiles"));
        check(0, t("Trocar os projéteis de armas, inimigos e chefes", "Swap the projectiles of weapons, enemies and bosses"), s -> s.projectiles,
                (s, v) -> s.projectiles = v, null);
        flow.next(4);
        check(0, t("Randomizar a velocidade dos projéteis (fator fixo por tipo)", "Randomize projectile speed (fixed factor per type)"),
                s -> s.projectileSpeed, (s, v) -> s.projectileSpeed = v, null);
        slider(1, t("Velocidade mínima", "Minimum speed"), 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.projectileSpeedMin, (s, v) -> s.projectileSpeedMin = v);
        slider(1, t("Velocidade máxima", "Maximum speed"), 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.projectileSpeedMax, (s, v) -> s.projectileSpeedMax = v);

        section(t("15 - Traduções", "15 - Translations"));
        check(0, t("Embaralhar os textos do jogo", "Shuffle the game's texts"), s -> s.translations, (s, v) -> s.translations = v, null);
        Map<String, String> translationModes = new LinkedHashMap<>();
        translationModes.put("entries", t("Trocar textos inteiros (nomes continuam legíveis)", "Swap whole texts (names stay readable)"));
        translationModes.put("words", t("Trocar palavras de lugar (caótico)", "Swap words around (chaotic)"));
        translationModes.put("entriesall", t("Usar frases de todas as traduções", "Use phrases from every language"));
        translationModes.put("words2", t("Usar palavras de duas traduções", "Use words from two languages"));
        translationModes.put("words3", t("Usar palavras de três traduções", "Use words from three languages"));
        translationModes.put("wordsall", t("Usar palavras de todas as traduções", "Use words from every language"));
        dropdown(1, modeText, translationModes, s -> s.translationMode, (s, v) -> s.translationMode = v, () -> edit.translations);

        section(t("16 - Sons", "16 - Sounds"));
        check(0, t("Trocar os sons dentro de cada categoria", "Swap sounds within each category"), s -> s.sounds, (s, v) -> s.sounds = v, null);
        label(1, t("Categorias:", "Categories:"));
        listChecks(2, Lang.pairs(SOUND_CATEGORIES), s -> s.soundCategories, (s, v) -> s.soundCategories = v, () -> edit.sounds);
        flow.next(4);
        check(0, t("Rotação: a seed gera 3 opções de troca de som, que se alternam a cada 6 minutos de jogo (a música muda na hora da troca)",
                "Rotation: the seed makes 3 sound swap options, which take turns every 6 minutes of play (the music changes when they switch)"),
                performanceNote, s -> s.soundRotation, (s, v) -> s.soundRotation = v, () -> edit.sounds);

        section(t("17 - Recompensas", "17 - Rewards"));
        check(0, t("Recompensas aleatórias: as quests dão itens aleatórios de recompensa", "Random rewards: quests give random items as rewards"),
                s -> s.questRewards, (s, v) -> s.questRewards = v, null);
        check(1, t("17.1 - Recompensas do Mestre Ancião", "17.1 - Elder rewards"), s -> s.questRewardsElder, (s, v) -> s.questRewardsElder = v,
                () -> edit.questRewards);
        check(1, t("17.2 - Recompensas dos desafios do diário de aventura", "17.2 - Adventure journal challenge rewards"), s -> s.questRewardsJournal,
                (s, v) -> s.questRewardsJournal = v, () -> edit.questRewards);

        section(t("18 - Pesca", "18 - Fishing"));
        check(0, t("Randomizar o poder de pesca das iscas", "Randomize the fishing power of baits"), s -> s.fishingBaitPower,
                (s, v) -> s.fishingBaitPower = v, null);
        slider(1, minMultiplierText, 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.fishingBaitPowerMin, (s, v) -> s.fishingBaitPowerMin = v);
        slider(1, maxMultiplierText, 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.fishingBaitPowerMax, (s, v) -> s.fishingBaitPowerMax = v);
        check(0, t("Randomizar o poder de pesca das varas", "Randomize the fishing power of rods"), s -> s.fishingRodPower,
                (s, v) -> s.fishingRodPower = v, null);
        slider(1, minMultiplierText, 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.fishingRodPowerMin, (s, v) -> s.fishingRodPowerMin = v);
        slider(1, maxMultiplierText, 0.1F, 5.0F, 0.1F, MULTIPLIER, s -> s.fishingRodPowerMax, (s, v) -> s.fishingRodPowerMax = v);
        check(0, t("Randomizar a pescaria", "Randomize catches"), s -> s.fishingCatches, (s, v) -> s.fishingCatches = v, null);
        Map<String, String> fishingModes = new LinkedHashMap<>();
        fishingModes.put("fish", t("Peixes (cada peixe vira outro peixe)", "Fish (each fish becomes another fish)"));
        fishingModes.put("all", t("Todos os itens do jogo", "Every item in the game"));
        dropdown(1, modeText, fishingModes, s -> s.fishingCatchesMode, (s, v) -> s.fishingCatchesMode = v, () -> edit.fishingCatches);

        section(t("Avançado", "Advanced"));
        check(0, t("Gravar o arquivo de spoiler com todas as trocas", "Write the spoiler file with every swap"), s -> s.spoilerLog,
                (s, v) -> s.spoilerLog = v, null);
        text(0, t("Mobs que nunca devem ser trocados (IDs separados por vírgula):", "Mobs that must never be swapped (comma-separated IDs):"),
                s -> s.excludeMobs, (s, v) -> s.excludeMobs = v);
        text(0, t("Itens que nunca devem ser trocados (IDs separados por vírgula):", "Items that must never be swapped (comma-separated IDs):"),
                s -> s.excludeItems, (s, v) -> s.excludeItems = v);

        if (visible) visibleEnd = flow.next();
        content.setContentBox(new java.awt.Rectangle(content.getWidth(), visibleEnd + 10));
        content.setScrollY(0);
        refresh();
    }

    /** Groups of a per-mob stat: settings field prefix, Portuguese plural and singular name, English name. */
    private static final String[][] STAT_GROUPS = {{"enemy", "inimigos", "inimigo", "enemy"}, {"villager", "aldeões", "aldeão", "villager"},
            {"critter", "bichos", "bicho", "critter"}, {"animal", "animais", "animal", "animal"}, {"boss", "chefes", "chefe", "boss"}};

    /**
     * The 5 options (enemies, villagers, critters, animals, bosses) of a stat whose fields are named like enemyArmor.
     * {@code title} and {@code what} are Portuguese ("Defesa", "a defesa"), {@code english} is the stat's English name ("defense").
     */
    private void statBlocks(String number, String title, String key, String what, String english) {
        for (int i = 0; i < STAT_GROUPS.length; i++) {
            String f = STAT_GROUPS[i][0] + key;
            String name = STAT_GROUPS[i][3];
            healthBlock(number + "." + (i + 1) + " - " + t(title + " dos " + STAT_GROUPS[i][1],
                            Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + english),
                    t("Usar " + what + " do " + STAT_GROUPS[i][2] + " antes da troca", "Use the pre-swap " + name + "'s " + english),
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
        modes.put("multiplier", randomMultiplierText);
        modes.put("preswap", preswapLabel);
        dropdown(1, modeText, modes, modeGet, modeSet, () -> enabledGet.test(edit));
        slider(1, minMultiplierText, 0.1F, 5.0F, 0.1F, MULTIPLIER, minGet, minSet);
        slider(1, maxMultiplierText, 0.1F, 5.0F, 0.1F, MULTIPLIER, maxGet, maxSet);
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
        return check(indent, text, null, getter, setter, enabledWhen);
    }

    /** A checkbox with an optional gray note to its right (the checkbox text wraps in the space left). */
    private FormCheckBox check(int indent, String text, String note, Predicate<SRSSettings> getter, BiConsumer<SRSSettings, Boolean> setter,
                               BooleanSupplier enabledWhen) {
        if (!visible) return null;
        int y = flow.next();
        int boxWidth = note == null ? width(indent) : width(indent) - NOTE_WIDTH - 10;
        FormCheckBox box = content.addComponent(new FormCheckBox(text, x(indent), y, boxWidth, getter.test(edit)));
        int height = Math.max(20, box.getBoundingBox().height);
        if (note != null) {
            FormLabel label = content.addComponent(new FormLabel(note, new FontOptions(12).color(getInterfaceStyle().inactiveTextColor), -1,
                    x(indent) + boxWidth + 10, y + 3, NOTE_WIDTH));
            height = Math.max(height, label.getHeight() + 3);
        }
        flow.next(height + 4);
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
        listChecks(indent, 2, entries, getter, setter, enabledWhen);
    }

    private void listChecks(int indent, int columns, String[][] entries, Function<SRSSettings, String> getter, BiConsumer<SRSSettings, String> setter,
                            BooleanSupplier enabledWhen) {
        if (!visible) return;
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
        // Decimal comma in Portuguese, decimal point in English
        char decimal = Lang.portuguese() ? ',' : '.';
        if (format == PERCENT_DECIMAL) return String.format(java.util.Locale.ROOT, "%.1f", value * 100).replace('.', decimal) + "%";
        return "x" + String.format(java.util.Locale.ROOT, "%.1f", value).replace('.', decimal);
    }

    private static String t(String portuguese, String english) {
        return Lang.t(portuguese, english);
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
            setStatus(needsRestart() ? t("Configuração salva! Reinicie o jogo para aplicar.", "Settings saved! Restart the game to apply them.")
                    : t("Configuração salva. Nada mudou em relação ao que está em uso.", "Settings saved. Nothing changed from what is in use."), true);
        } else {
            setStatus(t("Não foi possível salvar o arquivo de configuração.", "Could not save the settings file."), false);
        }
        return saved;
    }

    private void resetDefaults() {
        pull();
        long seed = edit.seed;
        edit = new SRSSettings();
        edit.seed = seed;
        buildContent();
        setStatus(t("Valores padrão carregados (a seed foi mantida). Clique em Salvar para gravar.",
                "Default values loaded (the seed was kept). Click Save to keep them."), false);
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
        confirm(t("Sortear uma nova seed? Toda a randomização muda (depois de salvar e reiniciar o jogo).",
                "Roll a new seed? All of the randomization changes (after saving and restarting the game)."), t("Sim", "Yes"), t("Não", "No"), () -> {
            long seed = 0;
            while (seed == 0) seed = new Random().nextLong();
            seedInput.setText(String.valueOf(seed));
            setStatus(t("Nova seed sorteada. Clique em Salvar para gravar.", "New seed rolled. Click Save to keep it."), false);
            modsForm.makeCurrent(screen);
        }, () -> modsForm.makeCurrent(screen));
    }

    private void back() {
        if (!isDirty()) {
            close();
            return;
        }
        confirm(t("Você tem alterações não salvas no Super Randomizer Show.", "You have unsaved changes in Super Randomizer Show."),
                t("Salvar", "Save"), t("Descartar", "Discard"), () -> {
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
