package srs;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

/**
 * Everything the player can configure. Persisted by Necesse to cfg/mods/<modid>.cfg under SETTINGS { ... }.
 * Almost every option is applied while the game boots, so changing it requires a restart.
 */
public class SRSSettings extends ModSettings {

    public static final String DEFAULT_BOSS_POOL = "evilsprotector,queenspider,voidwizard,chieftain,swampguardian,ancientvulture,"
            + "piratecaptain,reaper,cryoqueen,thecursedcrone,pestwarden,sageandgrit,fallenwizard,motherslime,nightswarm,"
            + "spiderempress,sunlightchampion,moonlightdancer,crystaldragon,mutanthydra";
    public static final String DEFAULT_TOWN_NPC_POOL = "human,farmerhuman,blacksmithhuman,guardhuman,magehuman,gunsmithhuman,"
            + "alchemisthuman,hunterhuman,elderhuman,anglerhuman,pawnbrokerhuman,animalkeeperhuman,stylisthuman,"
            + "piratehuman,explorerhuman,minerhuman,traderhuman";
    public static final String DEFAULT_VISUAL_FOLDERS = "items,mobs,player/armor,player/weapons,player/holditems,player/hair,projectiles";
    public static final String DEFAULT_SOUND_CATEGORIES = "music,animals,creatures,environment,weather,weapons,impacts,interface,bosses,other";

    // Seed: 0 means "generate one on the next start"
    public long seed = 0;

    // 1 - Mob spawns
    public boolean mobSpawns = true;
    public boolean mobSpawnHostiles = true;
    public boolean mobSpawnCritters = true;
    public boolean mobSpawnTownNPCs = true;
    public boolean mobSpawnBosses = true;
    public String bossPool = DEFAULT_BOSS_POOL;
    public String townNPCPool = DEFAULT_TOWN_NPC_POOL;

    // 2 - Drops
    public boolean drops = true;
    public boolean dropQuantities = true;
    public float dropQuantityMin = 0.5F;
    public float dropQuantityMax = 2.5F;

    // 3 - Recipes
    public boolean recipes = true;
    public boolean recipesSameStation = true;

    // 4 - Shops
    public boolean shops = true;

    // 5 - Weapon damage
    public boolean weaponDamage = true;
    public String weaponDamageMode = "multiplier";
    public float weaponDamageMin = 0.5F;
    public float weaponDamageMax = 2.0F;

    // 6 - Crit chance
    public boolean critChance = true;
    public float weaponCritMin = 0.0F;
    public float weaponCritMax = 0.15F;
    public float equipmentCritMultMin = 0.0F;
    public float equipmentCritMultMax = 2.5F;
    public float equipmentCritBonusMax = 0.05F;

    // 7 - Armor set bonuses
    public boolean armorSetBonuses = true;

    // 8 - Equipment effects
    public boolean equipmentEffects = true;

    // 9 - Equipment damage %
    public boolean equipmentDamage = true;
    public float equipmentDamageMultMin = 0.0F;
    public float equipmentDamageMultMax = 2.5F;

    // 10 - Visuals
    public boolean visuals = true;
    public String visualFolders = DEFAULT_VISUAL_FOLDERS;

    // 11 - Projectiles
    public boolean projectiles = true;

    // 12 - Translations
    public boolean translations = true;
    public String translationMode = "entries";

    // 13 - Sounds
    public boolean sounds = true;
    public String soundCategories = DEFAULT_SOUND_CATEGORIES;

    // General
    public String excludeMobs = "";
    public String excludeItems = "";
    public boolean spoilerLog = true;

    @Override
    public void addSaveData(SaveData data) {
        data.addLong("seed", seed, "Seed do randomizer. Mesma seed = mesmo resultado. 0 = gerar uma nova no proximo inicio. Mudancas exigem reiniciar o jogo.");

        data.addBoolean("mobSpawns", mobSpawns, "1 - Troca onde inimigos, bichos, NPCs de cidade e chefes aparecem");
        data.addBoolean("mobSpawnHostiles", mobSpawnHostiles, "1a - Inimigos que nascem naturalmente");
        data.addBoolean("mobSpawnCritters", mobSpawnCritters, "1b - Bichos (criaturas passivas)");
        data.addBoolean("mobSpawnTownNPCs", mobSpawnTownNPCs, "1c - NPCs de vilas e estruturas");
        data.addBoolean("mobSpawnBosses", mobSpawnBosses, "1d - Chefes invocados por itens e incursoes");
        data.addSafeString("bossPool", bossPool, "Chefes que podem ser trocados entre si (IDs separados por virgula)");
        data.addSafeString("townNPCPool", townNPCPool, "NPCs de cidade que podem ser trocados entre si (IDs separados por virgula)");

        data.addBoolean("drops", drops, "2 - Troca a tabela de drops entre inimigos, chefes, bichos, rochas, arvores e plantas");
        data.addBoolean("dropQuantities", dropQuantities, "2 - Multiplica a quantidade de cada item dropado por um fator fixo da seed");
        data.addFloat("dropQuantityMin", dropQuantityMin, "Multiplicador minimo de quantidade");
        data.addFloat("dropQuantityMax", dropQuantityMax, "Multiplicador maximo de quantidade");

        data.addBoolean("recipes", recipes, "3 - Embaralha os ingredientes das receitas");
        data.addBoolean("recipesSameStation", recipesSameStation, "true = so troca entre receitas da mesma estacao de trabalho (mais jogavel)");

        data.addBoolean("shops", shops, "4 - Embaralha os itens vendidos e comprados pelos NPCs de cidade");

        data.addBoolean("weaponDamage", weaponDamage, "5 - Randomiza o dano das armas");
        data.addSafeString("weaponDamageMode", weaponDamageMode, "multiplier = multiplica por um fator aleatorio | shuffle = troca o dano entre armas de forca parecida | chaos = troca com qualquer arma do mesmo tipo");
        data.addFloat("weaponDamageMin", weaponDamageMin, "Fator minimo (modo multiplier)");
        data.addFloat("weaponDamageMax", weaponDamageMax, "Fator maximo (modo multiplier)");

        data.addBoolean("critChance", critChance, "6 - Randomiza a chance de critico de armas e equipamentos");
        data.addFloat("weaponCritMin", weaponCritMin, "Critico base minimo adicionado a cada arma (0.05 = 5%)");
        data.addFloat("weaponCritMax", weaponCritMax, "Critico base maximo adicionado a cada arma");
        data.addFloat("equipmentCritMultMin", equipmentCritMultMin, "Multiplicador minimo do critico que o equipamento ja tinha");
        data.addFloat("equipmentCritMultMax", equipmentCritMultMax, "Multiplicador maximo do critico que o equipamento ja tinha");
        data.addFloat("equipmentCritBonusMax", equipmentCritBonusMax, "Critico extra maximo sorteado para cada armadura/acessorio");

        data.addBoolean("armorSetBonuses", armorSetBonuses, "7 - Troca os bonus de conjunto entre as armaduras");
        data.addBoolean("equipmentEffects", equipmentEffects, "8 - Troca os efeitos entre acessorios e entre pecas de armadura");
        data.addBoolean("equipmentDamage", equipmentDamage, "9 - Randomiza o % de aumento de dano dos equipamentos");
        data.addFloat("equipmentDamageMultMin", equipmentDamageMultMin, "Multiplicador minimo do bonus de dano");
        data.addFloat("equipmentDamageMultMax", equipmentDamageMultMax, "Multiplicador maximo do bonus de dano");

        data.addBoolean("visuals", visuals, "10 - Troca texturas entre itens, armaduras, armas, mobs, NPCs, cosmeticos (so entre imagens do mesmo tamanho)");
        data.addSafeString("visualFolders", visualFolders, "Pastas de textura que participam (separadas por virgula)");

        data.addBoolean("projectiles", projectiles, "11 - Troca os projeteis de armas, inimigos e chefes");

        data.addBoolean("translations", translations, "12 - Embaralha as traducoes");
        data.addSafeString("translationMode", translationMode, "entries = troca textos inteiros dentro da mesma categoria | words = troca palavras de lugar");

        data.addBoolean("sounds", sounds, "13 - Troca sons dentro da mesma categoria");
        data.addSafeString("soundCategories", soundCategories, "Categorias: music,animals,creatures,environment,weather,weapons,impacts,interface,bosses,notes,other");

        data.addSafeString("excludeMobs", excludeMobs, "IDs de mobs que nunca devem ser randomizados (separados por virgula)");
        data.addSafeString("excludeItems", excludeItems, "IDs de itens que nunca devem ser randomizados (separados por virgula)");
        data.addBoolean("spoilerLog", spoilerLog, "Grava todo o resultado da randomizacao em cfg/mods/<modid>-spoiler.txt");
    }

    @Override
    public void applyLoadData(LoadData data) {
        seed = data.getLong("seed", seed, false);

        mobSpawns = data.getBoolean("mobSpawns", mobSpawns, false);
        mobSpawnHostiles = data.getBoolean("mobSpawnHostiles", mobSpawnHostiles, false);
        mobSpawnCritters = data.getBoolean("mobSpawnCritters", mobSpawnCritters, false);
        mobSpawnTownNPCs = data.getBoolean("mobSpawnTownNPCs", mobSpawnTownNPCs, false);
        mobSpawnBosses = data.getBoolean("mobSpawnBosses", mobSpawnBosses, false);
        bossPool = data.getSafeString("bossPool", bossPool, false);
        townNPCPool = data.getSafeString("townNPCPool", townNPCPool, false);

        drops = data.getBoolean("drops", drops, false);
        dropQuantities = data.getBoolean("dropQuantities", dropQuantities, false);
        dropQuantityMin = data.getFloat("dropQuantityMin", dropQuantityMin, false);
        dropQuantityMax = data.getFloat("dropQuantityMax", dropQuantityMax, false);

        recipes = data.getBoolean("recipes", recipes, false);
        recipesSameStation = data.getBoolean("recipesSameStation", recipesSameStation, false);

        shops = data.getBoolean("shops", shops, false);

        weaponDamage = data.getBoolean("weaponDamage", weaponDamage, false);
        weaponDamageMode = data.getSafeString("weaponDamageMode", weaponDamageMode, false);
        weaponDamageMin = data.getFloat("weaponDamageMin", weaponDamageMin, false);
        weaponDamageMax = data.getFloat("weaponDamageMax", weaponDamageMax, false);

        critChance = data.getBoolean("critChance", critChance, false);
        weaponCritMin = data.getFloat("weaponCritMin", weaponCritMin, false);
        weaponCritMax = data.getFloat("weaponCritMax", weaponCritMax, false);
        equipmentCritMultMin = data.getFloat("equipmentCritMultMin", equipmentCritMultMin, false);
        equipmentCritMultMax = data.getFloat("equipmentCritMultMax", equipmentCritMultMax, false);
        equipmentCritBonusMax = data.getFloat("equipmentCritBonusMax", equipmentCritBonusMax, false);

        armorSetBonuses = data.getBoolean("armorSetBonuses", armorSetBonuses, false);
        equipmentEffects = data.getBoolean("equipmentEffects", equipmentEffects, false);
        equipmentDamage = data.getBoolean("equipmentDamage", equipmentDamage, false);
        equipmentDamageMultMin = data.getFloat("equipmentDamageMultMin", equipmentDamageMultMin, false);
        equipmentDamageMultMax = data.getFloat("equipmentDamageMultMax", equipmentDamageMultMax, false);

        visuals = data.getBoolean("visuals", visuals, false);
        visualFolders = data.getSafeString("visualFolders", visualFolders, false);

        projectiles = data.getBoolean("projectiles", projectiles, false);

        translations = data.getBoolean("translations", translations, false);
        translationMode = data.getSafeString("translationMode", translationMode, false);

        sounds = data.getBoolean("sounds", sounds, false);
        soundCategories = data.getSafeString("soundCategories", soundCategories, false);

        excludeMobs = data.getSafeString("excludeMobs", excludeMobs, false);
        excludeItems = data.getSafeString("excludeItems", excludeItems, false);
        spoilerLog = data.getBoolean("spoilerLog", spoilerLog, false);
    }

    /** Serializes these settings in the same layout Necesse uses for the mod cfg file. */
    public String toScript() {
        SaveData root = new SaveData("");
        SaveData settings = new SaveData("SETTINGS");
        addSaveData(settings);
        root.addSaveData(settings);
        return root.getScript();
    }

    /** Applies a script produced by {@link #toScript()}. Returns false if it could not be parsed. */
    public boolean applyScript(String script) {
        try {
            LoadData root = new LoadData(script);
            LoadData settings = root.getFirstLoadDataByName("SETTINGS");
            if (settings == null || !settings.isArray()) return false;
            applyLoadData(settings);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Everything that changes the randomization (spoilerLog is just output, so it's left out). */
    public String fingerprint() {
        boolean log = spoilerLog;
        spoilerLog = true;
        String script = toScript();
        spoilerLog = log;
        return script;
    }

}
