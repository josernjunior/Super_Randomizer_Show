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
    public static final String DEFAULT_VISUAL_FOLDERS = "mobs,projectiles";
    public static final String DEFAULT_SOUND_CATEGORIES = "music,animals,creatures,environment,weather,weapons,impacts,interface,bosses,other";

    // Seed: 0 means "generate one on the next start", so every clean install gets its own random seed
    public long seed = 0;

    // 1 - Mob spawns
    public boolean mobSpawns = true;
    public boolean mobSpawnHostiles = true;
    public boolean mobSpawnCritters = true;
    public boolean mobSpawnTownNPCs = true;
    public boolean mobSpawnAnimals = true;
    public String bossPool = DEFAULT_BOSS_POOL;
    public String townNPCPool = DEFAULT_TOWN_NPC_POOL;

    // 2 - Drops
    public boolean drops = true;
    public boolean dropQuantities = true;
    public float dropQuantityMin = 0.4F;
    public float dropQuantityMax = 2.5F;
    public boolean randomItemDrop = true;
    public float randomItemDropChance = 0.01F;

    // 3 - Recipes
    public boolean recipes = false;
    public boolean recipesSameStation = false;
    public float recipesShuffleLevel = 1.0F;

    // 4 - Shops
    public boolean shops = true;
    public String shopsMode = "items";
    public boolean shopPrices = false;
    public float shopPricesMin = 0.5F;
    public float shopPricesMax = 1.5F;

    // 5 - Damage (5.1 weapons, 5.2 enemies, 5.3 bosses)
    public boolean weaponDamage = true;
    public String weaponDamageMode = "multiplier";
    public float weaponDamageMin = 0.8F;
    public float weaponDamageMax = 1.8F;
    public boolean enemyDamage = true;
    public String enemyDamageMode = "preswap";
    public float enemyDamageMin = 0.6F;
    public float enemyDamageMax = 0.8F;
    public boolean bossDamage = true;
    public String bossDamageMode = "preswap";
    public float bossDamageMin = 0.6F;
    public float bossDamageMax = 0.8F;

    // 6 - Health
    public boolean enemyHealth = true;
    public String enemyHealthMode = "preswap";
    public float enemyHealthMin = 0.8F;
    public float enemyHealthMax = 1.2F;
    public boolean villagerHealth = true;
    public String villagerHealthMode = "preswap";
    public float villagerHealthMin = 0.8F;
    public float villagerHealthMax = 1.2F;
    public boolean critterHealth = true;
    public String critterHealthMode = "preswap";
    public float critterHealthMin = 0.8F;
    public float critterHealthMax = 1.2F;
    public boolean animalHealth = true;
    public String animalHealthMode = "preswap";
    public float animalHealthMin = 0.8F;
    public float animalHealthMax = 1.2F;
    public boolean bossHealth = true;
    public String bossHealthMode = "preswap";
    public float bossHealthMin = 0.8F;
    public float bossHealthMax = 1.2F;

    // 7 - Defense (armor)
    public boolean enemyArmor = true;
    public String enemyArmorMode = "preswap";
    public float enemyArmorMin = 0.8F;
    public float enemyArmorMax = 1.2F;
    public boolean villagerArmor = true;
    public String villagerArmorMode = "preswap";
    public float villagerArmorMin = 0.8F;
    public float villagerArmorMax = 1.2F;
    public boolean critterArmor = true;
    public String critterArmorMode = "preswap";
    public float critterArmorMin = 0.8F;
    public float critterArmorMax = 1.2F;
    public boolean animalArmor = true;
    public String animalArmorMode = "preswap";
    public float animalArmorMin = 0.8F;
    public float animalArmorMax = 1.2F;
    public boolean bossArmor = true;
    public String bossArmorMode = "preswap";
    public float bossArmorMin = 0.8F;
    public float bossArmorMax = 1.2F;

    // 8 - Knockback taken
    public boolean enemyKnockback = true;
    public String enemyKnockbackMode = "preswap";
    public float enemyKnockbackMin = 0.8F;
    public float enemyKnockbackMax = 1.2F;
    public boolean villagerKnockback = true;
    public String villagerKnockbackMode = "preswap";
    public float villagerKnockbackMin = 0.8F;
    public float villagerKnockbackMax = 1.2F;
    public boolean critterKnockback = true;
    public String critterKnockbackMode = "preswap";
    public float critterKnockbackMin = 0.8F;
    public float critterKnockbackMax = 1.2F;
    public boolean animalKnockback = true;
    public String animalKnockbackMode = "preswap";
    public float animalKnockbackMin = 0.8F;
    public float animalKnockbackMax = 1.2F;
    public boolean bossKnockback = true;
    public String bossKnockbackMode = "preswap";
    public float bossKnockbackMin = 0.8F;
    public float bossKnockbackMax = 1.2F;

    // 9 - Crit chance
    public boolean critChance = true;
    public float weaponCritMin = 0.0F;
    public float weaponCritMax = 0.15F;
    public float equipmentCritMultMin = 0.0F;
    public float equipmentCritMultMax = 2.5F;
    public float equipmentCritBonusMax = 0.04F;

    // 10 - Armor set bonuses
    public boolean armorSetBonuses = true;
    public String armorSetBonusMode = "swap";

    // 11 - Equipment effects
    public boolean equipmentEffects = true;
    public String equipmentEffectsMode = "all";

    // 12 - Equipment damage % and enchantment scrolls
    public boolean equipmentDamage = true;
    public float equipmentDamageMultMin = 0.0F;
    public float equipmentDamageMultMax = 2.5F;
    public boolean enchantScrolls = false;
    public float enchantScrollsMin = 0.5F;
    public float enchantScrollsMax = 2.0F;

    // 13 - Visuals
    public boolean visuals = true;
    public String visualFolders = DEFAULT_VISUAL_FOLDERS;
    public boolean funnyBuggedSwap = true;
    public boolean visualRotation = false;

    // 14 - Projectiles
    public boolean projectiles = true;
    public boolean projectileSpeed = false;
    public float projectileSpeedMin = 1.0F;
    public float projectileSpeedMax = 1.0F;

    // 15 - Translations
    public boolean translations = false;
    public String translationMode = "entries";

    // 16 - Sounds
    public boolean sounds = true;
    public String soundCategories = DEFAULT_SOUND_CATEGORIES;

    // 17 - Quest rewards
    public boolean questRewards = true;
    public boolean questRewardsElder = true;
    public boolean questRewardsJournal = true;

    // 18 - Fishing
    public boolean fishingBaitPower = true;
    public float fishingBaitPowerMin = 0.8F;
    public float fishingBaitPowerMax = 1.2F;
    public boolean fishingRodPower = true;
    public float fishingRodPowerMin = 0.8F;
    public float fishingRodPowerMax = 1.2F;
    public boolean fishingCatches = true;
    public String fishingCatchesMode = "fish";

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
        data.addBoolean("mobSpawnAnimals", mobSpawnAnimals, "1d - Animais (ovelhas, vacas, porcos, galinhas, avestruzes, pinguins...) colocados na criacao do mundo");
        data.addSafeString("bossPool", bossPool, "1.2 - Chefes que podem trocar entre si, tambem nos drops (IDs separados por virgula; vazio = nao trocar chefes)");
        data.addSafeString("townNPCPool", townNPCPool, "1.1 - NPCs que podem trocar de lugar entre si (IDs separados por virgula)");

        data.addBoolean("drops", drops, "2 - Troca a tabela de drops entre inimigos, chefes, bichos, rochas, arvores, plantas, flores e paredes");
        data.addBoolean("dropQuantities", dropQuantities, "2 - Multiplica a quantidade de cada item dropado por um fator fixo da seed (nunca zera um drop)");
        data.addFloat("dropQuantityMin", dropQuantityMin, "Multiplicador minimo de quantidade");
        data.addFloat("dropQuantityMax", dropQuantityMax, "Multiplicador maximo de quantidade");
        data.addBoolean("randomItemDrop", randomItemDrop, "2 - Ao matar um mob, chance de dropar tambem um item aleatorio de todo o jogo");
        data.addFloat("randomItemDropChance", randomItemDropChance, "Chance do item aleatorio (0.01 = 1%)");

        data.addBoolean("recipes", recipes, "3 - Embaralha os ingredientes das receitas");
        data.addBoolean("recipesSameStation", recipesSameStation, "true = so troca entre receitas da mesma estacao de trabalho (mais jogavel)");
        data.addFloat("recipesShuffleLevel", recipesShuffleLevel, "Nivel de embaralhamento: parte das receitas que trocam de ingredientes (0.01 = 1%, 1.0 = 100%)");

        data.addBoolean("shops", shops, "4 - Embaralha as lojas dos NPCs de cidade");
        data.addSafeString("shopsMode", shopsMode, "items = troca cada item vendido/comprado por outro | npcs = so troca as lojas inteiras entre os NPCs (aldeoes)");
        data.addBoolean("shopPrices", shopPrices, "4 - Randomiza os precos de compra e venda das lojas (fator fixo por item)");
        data.addFloat("shopPricesMin", shopPricesMin, "Multiplicador minimo dos precos");
        data.addFloat("shopPricesMax", shopPricesMax, "Multiplicador maximo dos precos");

        data.addBoolean("weaponDamage", weaponDamage, "5.1 - Randomiza o dano das armas");
        data.addSafeString("weaponDamageMode", weaponDamageMode, "multiplier = multiplica por um fator aleatorio | shuffle = troca o dano entre armas de forca parecida | chaos = troca com qualquer arma do mesmo tipo");
        data.addFloat("weaponDamageMin", weaponDamageMin, "Fator minimo (modo multiplier)");
        data.addFloat("weaponDamageMax", weaponDamageMax, "Fator maximo (modo multiplier)");
        data.addBoolean("enemyDamage", enemyDamage, "5.2 - Randomiza o dano dos inimigos");
        data.addSafeString("enemyDamageMode", enemyDamageMode, "multiplier = multiplicador aleatorio | preswap = o inimigo que trocou de lugar bate como o inimigo que estava ali antes");
        data.addFloat("enemyDamageMin", enemyDamageMin, "Multiplicador minimo (nos dois modos; no preswap multiplica o dano do inimigo original; 1.0 e 1.0 = sem variacao)");
        data.addFloat("enemyDamageMax", enemyDamageMax, "Multiplicador maximo (nos dois modos)");
        data.addBoolean("bossDamage", bossDamage, "5.3 - Randomiza o dano dos chefes");
        data.addSafeString("bossDamageMode", bossDamageMode, "multiplier = multiplicador aleatorio | preswap = o chefe que trocou de lugar bate como o chefe que estava ali antes");
        data.addFloat("bossDamageMin", bossDamageMin, "Multiplicador minimo (nos dois modos; no preswap multiplica o dano do chefe original; 1.0 e 1.0 = sem variacao)");
        data.addFloat("bossDamageMax", bossDamageMax, "Multiplicador maximo (nos dois modos)");

        addHealth(data, "enemy", "6.1 - Randomiza a vida dos inimigos", "inimigo", enemyHealth, enemyHealthMode, enemyHealthMin, enemyHealthMax);
        addHealth(data, "villager", "6.2 - Randomiza a vida dos aldeoes (NPCs de cidade)", "aldeao", villagerHealth, villagerHealthMode, villagerHealthMin, villagerHealthMax);
        addHealth(data, "critter", "6.3 - Randomiza a vida dos bichos", "bicho", critterHealth, critterHealthMode, critterHealthMin, critterHealthMax);
        addHealth(data, "animal", "6.4 - Randomiza a vida dos animais", "animal", animalHealth, animalHealthMode, animalHealthMin, animalHealthMax);
        addHealth(data, "boss", "6.5 - Randomiza a vida dos chefes", "chefe", bossHealth, bossHealthMode, bossHealthMin, bossHealthMax);

        addStats(data, "Armor", "7", "Randomiza a defesa (armadura)", "a defesa");
        addStats(data, "Knockback", "8", "Randomiza o empurrao recebido (1.0 = normal, 0 = nao se move)", "o empurrao");

        data.addBoolean("critChance", critChance, "9 - Randomiza a chance de critico de armas e equipamentos");
        data.addFloat("weaponCritMin", weaponCritMin, "Critico base minimo adicionado a cada arma (0.05 = 5%)");
        data.addFloat("weaponCritMax", weaponCritMax, "Critico base maximo adicionado a cada arma");
        data.addFloat("equipmentCritMultMin", equipmentCritMultMin, "Multiplicador minimo do critico que o equipamento ja tinha");
        data.addFloat("equipmentCritMultMax", equipmentCritMultMax, "Multiplicador maximo do critico que o equipamento ja tinha");
        data.addFloat("equipmentCritBonusMax", equipmentCritBonusMax, "Critico extra maximo sorteado para cada armadura/acessorio");

        data.addBoolean("armorSetBonuses", armorSetBonuses, "10 - Troca os bonus de conjunto entre as armaduras");
        data.addSafeString("armorSetBonusMode", armorSetBonusMode, "swap = cada conjunto ganha o bonus de outro | double = bonus duplo: cada conjunto mantem o seu e ganha tambem o de outro");
        data.addBoolean("equipmentEffects", equipmentEffects, "11 - Troca os efeitos entre acessorios e entre pecas de armadura");
        data.addSafeString("equipmentEffectsMode", equipmentEffectsMode, "all = troca com qualquer um | class = so entre itens da mesma classe (corpo a corpo, distancia, magia, invocacao, geral) | progression = so entre itens achados na mesma fase do jogo");
        data.addBoolean("equipmentDamage", equipmentDamage, "12 - Randomiza o % de aumento de dano dos equipamentos");
        data.addFloat("equipmentDamageMultMin", equipmentDamageMultMin, "Multiplicador minimo do bonus de dano");
        data.addFloat("equipmentDamageMultMax", equipmentDamageMultMax, "Multiplicador maximo do bonus de dano");
        data.addBoolean("enchantScrolls", enchantScrolls, "12 - Randomiza os pergaminhos: multiplica os valores de cada encantamento por um fator fixo da seed");
        data.addFloat("enchantScrollsMin", enchantScrollsMin, "Multiplicador minimo dos encantamentos");
        data.addFloat("enchantScrollsMax", enchantScrollsMax, "Multiplicador maximo dos encantamentos");

        data.addBoolean("visuals", visuals, "13 - Troca texturas entre itens, armaduras, armas, mobs, NPCs, cosmeticos (so entre imagens do mesmo tamanho)");
        data.addSafeString("visualFolders", visualFolders, "Pastas de textura que participam (separadas por virgula)");
        data.addBoolean("funnyBuggedSwap", funnyBuggedSwap, "13b - Troca Bugada Divertida: as partes dos mobs trocam separadas (ex.: slime com bracos de zumbi). Funciona mesmo com visuals desligado");
        data.addBoolean("visualRotation", visualRotation, "13c - A seed gera 3 opcoes de troca de visual, que se alternam a cada 6 minutos de jogo");

        data.addBoolean("projectiles", projectiles, "14 - Troca os projeteis de armas, inimigos e chefes");
        data.addBoolean("projectileSpeed", projectileSpeed, "14 - Randomiza a velocidade dos projeteis (fator fixo por tipo de projetil)");
        data.addFloat("projectileSpeedMin", projectileSpeedMin, "Multiplicador minimo da velocidade");
        data.addFloat("projectileSpeedMax", projectileSpeedMax, "Multiplicador maximo da velocidade");

        data.addBoolean("translations", translations, "15 - Embaralha as traducoes");
        data.addSafeString("translationMode", translationMode, "entries = troca textos inteiros dentro da mesma categoria | words = troca palavras de lugar"
                + " | entriesall = textos inteiros de todas as traducoes | words2, words3 = palavras de 2 ou 3 traducoes | wordsall = palavras de todas as traducoes");

        data.addBoolean("sounds", sounds, "16 - Troca sons dentro da mesma categoria");
        data.addSafeString("soundCategories", soundCategories, "Categorias: music,animals,creatures,environment,weather,weapons,impacts,interface,bosses,notes,other");

        data.addBoolean("questRewards", questRewards, "17 - Recompensas aleatorias: troca os itens que as quests dao de recompensa");
        data.addBoolean("questRewardsElder", questRewardsElder, "17.1 - Recompensas dos objetivos do Mestre Anciao");
        data.addBoolean("questRewardsJournal", questRewardsJournal, "17.2 - Recompensas dos desafios do diario de aventura");

        data.addBoolean("fishingBaitPower", fishingBaitPower, "18 - Pesca: randomiza o poder de pesca das iscas");
        data.addFloat("fishingBaitPowerMin", fishingBaitPowerMin, "Multiplicador minimo do poder das iscas");
        data.addFloat("fishingBaitPowerMax", fishingBaitPowerMax, "Multiplicador maximo do poder das iscas");
        data.addBoolean("fishingRodPower", fishingRodPower, "18 - Pesca: randomiza o poder de pesca das varas");
        data.addFloat("fishingRodPowerMin", fishingRodPowerMin, "Multiplicador minimo do poder das varas");
        data.addFloat("fishingRodPowerMax", fishingRodPowerMax, "Multiplicador maximo do poder das varas");
        data.addBoolean("fishingCatches", fishingCatches, "18 - Pesca: randomiza o que e pescado");
        data.addSafeString("fishingCatchesMode", fishingCatchesMode, "fish = cada peixe vira outro peixe | all = cada coisa pescada vira um item qualquer do jogo");

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
        mobSpawnAnimals = data.getBoolean("mobSpawnAnimals", mobSpawnAnimals, false);
        bossPool = data.getSafeString("bossPool", bossPool, false);
        townNPCPool = data.getSafeString("townNPCPool", townNPCPool, false);

        drops = data.getBoolean("drops", drops, false);
        dropQuantities = data.getBoolean("dropQuantities", dropQuantities, false);
        dropQuantityMin = data.getFloat("dropQuantityMin", dropQuantityMin, false);
        dropQuantityMax = data.getFloat("dropQuantityMax", dropQuantityMax, false);
        randomItemDrop = data.getBoolean("randomItemDrop", randomItemDrop, false);
        randomItemDropChance = data.getFloat("randomItemDropChance", randomItemDropChance, false);

        recipes = data.getBoolean("recipes", recipes, false);
        recipesSameStation = data.getBoolean("recipesSameStation", recipesSameStation, false);
        recipesShuffleLevel = data.getFloat("recipesShuffleLevel", recipesShuffleLevel, false);

        shops = data.getBoolean("shops", shops, false);
        shopsMode = data.getSafeString("shopsMode", shopsMode, false);
        shopPrices = data.getBoolean("shopPrices", shopPrices, false);
        shopPricesMin = data.getFloat("shopPricesMin", shopPricesMin, false);
        shopPricesMax = data.getFloat("shopPricesMax", shopPricesMax, false);

        weaponDamage = data.getBoolean("weaponDamage", weaponDamage, false);
        weaponDamageMode = data.getSafeString("weaponDamageMode", weaponDamageMode, false);
        weaponDamageMin = data.getFloat("weaponDamageMin", weaponDamageMin, false);
        weaponDamageMax = data.getFloat("weaponDamageMax", weaponDamageMax, false);
        enemyDamage = data.getBoolean("enemyDamage", enemyDamage, false);
        enemyDamageMode = data.getSafeString("enemyDamageMode", enemyDamageMode, false);
        enemyDamageMin = data.getFloat("enemyDamageMin", enemyDamageMin, false);
        enemyDamageMax = data.getFloat("enemyDamageMax", enemyDamageMax, false);
        bossDamage = data.getBoolean("bossDamage", bossDamage, false);
        bossDamageMode = data.getSafeString("bossDamageMode", bossDamageMode, false);
        bossDamageMin = data.getFloat("bossDamageMin", bossDamageMin, false);
        bossDamageMax = data.getFloat("bossDamageMax", bossDamageMax, false);

        enemyHealth = data.getBoolean("enemyHealth", enemyHealth, false);
        enemyHealthMode = data.getSafeString("enemyHealthMode", enemyHealthMode, false);
        enemyHealthMin = data.getFloat("enemyHealthMin", enemyHealthMin, false);
        enemyHealthMax = data.getFloat("enemyHealthMax", enemyHealthMax, false);
        villagerHealth = data.getBoolean("villagerHealth", villagerHealth, false);
        villagerHealthMode = data.getSafeString("villagerHealthMode", villagerHealthMode, false);
        villagerHealthMin = data.getFloat("villagerHealthMin", villagerHealthMin, false);
        villagerHealthMax = data.getFloat("villagerHealthMax", villagerHealthMax, false);
        critterHealth = data.getBoolean("critterHealth", critterHealth, false);
        critterHealthMode = data.getSafeString("critterHealthMode", critterHealthMode, false);
        critterHealthMin = data.getFloat("critterHealthMin", critterHealthMin, false);
        critterHealthMax = data.getFloat("critterHealthMax", critterHealthMax, false);
        animalHealth = data.getBoolean("animalHealth", animalHealth, false);
        animalHealthMode = data.getSafeString("animalHealthMode", animalHealthMode, false);
        animalHealthMin = data.getFloat("animalHealthMin", animalHealthMin, false);
        animalHealthMax = data.getFloat("animalHealthMax", animalHealthMax, false);
        bossHealth = data.getBoolean("bossHealth", bossHealth, false);
        bossHealthMode = data.getSafeString("bossHealthMode", bossHealthMode, false);
        bossHealthMin = data.getFloat("bossHealthMin", bossHealthMin, false);
        bossHealthMax = data.getFloat("bossHealthMax", bossHealthMax, false);

        loadStats(data, "Armor");
        loadStats(data, "Knockback");

        critChance = data.getBoolean("critChance", critChance, false);
        weaponCritMin = data.getFloat("weaponCritMin", weaponCritMin, false);
        weaponCritMax = data.getFloat("weaponCritMax", weaponCritMax, false);
        equipmentCritMultMin = data.getFloat("equipmentCritMultMin", equipmentCritMultMin, false);
        equipmentCritMultMax = data.getFloat("equipmentCritMultMax", equipmentCritMultMax, false);
        equipmentCritBonusMax = data.getFloat("equipmentCritBonusMax", equipmentCritBonusMax, false);

        armorSetBonuses = data.getBoolean("armorSetBonuses", armorSetBonuses, false);
        armorSetBonusMode = data.getSafeString("armorSetBonusMode", armorSetBonusMode, false);
        equipmentEffects = data.getBoolean("equipmentEffects", equipmentEffects, false);
        equipmentEffectsMode = data.getSafeString("equipmentEffectsMode", equipmentEffectsMode, false);
        equipmentDamage = data.getBoolean("equipmentDamage", equipmentDamage, false);
        equipmentDamageMultMin = data.getFloat("equipmentDamageMultMin", equipmentDamageMultMin, false);
        equipmentDamageMultMax = data.getFloat("equipmentDamageMultMax", equipmentDamageMultMax, false);
        enchantScrolls = data.getBoolean("enchantScrolls", enchantScrolls, false);
        enchantScrollsMin = data.getFloat("enchantScrollsMin", enchantScrollsMin, false);
        enchantScrollsMax = data.getFloat("enchantScrollsMax", enchantScrollsMax, false);

        visuals = data.getBoolean("visuals", visuals, false);
        visualFolders = data.getSafeString("visualFolders", visualFolders, false);
        funnyBuggedSwap = data.getBoolean("funnyBuggedSwap", funnyBuggedSwap, false);
        visualRotation = data.getBoolean("visualRotation", visualRotation, false);

        projectiles = data.getBoolean("projectiles", projectiles, false);
        projectileSpeed = data.getBoolean("projectileSpeed", projectileSpeed, false);
        projectileSpeedMin = data.getFloat("projectileSpeedMin", projectileSpeedMin, false);
        projectileSpeedMax = data.getFloat("projectileSpeedMax", projectileSpeedMax, false);

        translations = data.getBoolean("translations", translations, false);
        translationMode = data.getSafeString("translationMode", translationMode, false);

        sounds = data.getBoolean("sounds", sounds, false);
        soundCategories = data.getSafeString("soundCategories", soundCategories, false);

        questRewards = data.getBoolean("questRewards", questRewards, false);
        questRewardsElder = data.getBoolean("questRewardsElder", questRewardsElder, false);
        questRewardsJournal = data.getBoolean("questRewardsJournal", questRewardsJournal, false);

        fishingBaitPower = data.getBoolean("fishingBaitPower", fishingBaitPower, false);
        fishingBaitPowerMin = data.getFloat("fishingBaitPowerMin", fishingBaitPowerMin, false);
        fishingBaitPowerMax = data.getFloat("fishingBaitPowerMax", fishingBaitPowerMax, false);
        fishingRodPower = data.getBoolean("fishingRodPower", fishingRodPower, false);
        fishingRodPowerMin = data.getFloat("fishingRodPowerMin", fishingRodPowerMin, false);
        fishingRodPowerMax = data.getFloat("fishingRodPowerMax", fishingRodPowerMax, false);
        fishingCatches = data.getBoolean("fishingCatches", fishingCatches, false);
        fishingCatchesMode = data.getSafeString("fishingCatchesMode", fishingCatchesMode, false);

        excludeMobs = data.getSafeString("excludeMobs", excludeMobs, false);
        excludeItems = data.getSafeString("excludeItems", excludeItems, false);
        spoilerLog = data.getBoolean("spoilerLog", spoilerLog, false);
    }

    private static void addHealth(SaveData data, String prefix, String title, String who, boolean enabled, String mode, float min, float max) {
        data.addBoolean(prefix + "Health", enabled, title);
        data.addSafeString(prefix + "HealthMode", mode, "multiplier = multiplicador aleatorio | preswap = o " + who + " que trocou de lugar fica com a vida do " + who + " que estava ali antes");
        data.addFloat(prefix + "HealthMin", min, "Multiplicador minimo da vida (nos dois modos; 1.0 e 1.0 = sem variacao)");
        data.addFloat(prefix + "HealthMax", max, "Multiplicador maximo da vida (nos dois modos)");
    }

    /** Settings field prefix, plural and singular name of the groups defense and knockback are randomized for. */
    private static final String[][] STAT_GROUPS = {{"enemy", "inimigos", "inimigo"}, {"villager", "aldeoes", "aldeao"},
            {"critter", "bichos", "bicho"}, {"animal", "animais", "animal"}, {"boss", "chefes", "chefe"}};

    /** Writes the 4 settings (on, mode, min, max) of a stat for every group, e.g. enemyArmor, enemyArmorMode... */
    private void addStats(SaveData data, String key, String number, String title, String what) {
        for (int i = 0; i < STAT_GROUPS.length; i++) {
            String f = STAT_GROUPS[i][0] + key;
            String who = STAT_GROUPS[i][2];
            data.addBoolean(f, (Boolean) field(f), number + "." + (i + 1) + " - " + title + " dos " + STAT_GROUPS[i][1]);
            data.addSafeString(f + "Mode", (String) field(f + "Mode"), "multiplier = multiplicador aleatorio | preswap = o " + who
                    + " que trocou de lugar fica com " + what + " do " + who + " que estava ali antes");
            data.addFloat(f + "Min", (Float) field(f + "Min"), "Multiplicador minimo (nos dois modos; 1.0 e 1.0 = sem variacao)");
            data.addFloat(f + "Max", (Float) field(f + "Max"), "Multiplicador maximo (nos dois modos)");
        }
    }

    private void loadStats(LoadData data, String key) {
        for (String[] group : STAT_GROUPS) {
            String f = group[0] + key;
            setField(f, data.getBoolean(f, (Boolean) field(f), false));
            setField(f + "Mode", data.getSafeString(f + "Mode", (String) field(f + "Mode"), false));
            setField(f + "Min", data.getFloat(f + "Min", (Float) field(f + "Min"), false));
            setField(f + "Max", data.getFloat(f + "Max", (Float) field(f + "Max"), false));
        }
    }

    private Object field(String name) {
        try {
            return SRSSettings.class.getField(name).get(this);
        } catch (Exception e) {
            throw new IllegalStateException("Missing setting " + name, e);
        }
    }

    private void setField(String name, Object value) {
        try {
            SRSSettings.class.getField(name).set(this, value);
        } catch (Exception e) {
            throw new IllegalStateException("Missing setting " + name, e);
        }
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
