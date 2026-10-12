package srs.commands;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.commands.parameterHandlers.PresetStringParameterHandler;
import necesse.engine.commands.parameterHandlers.StringParameterHandler;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import srs.SRSSettings;
import srs.core.SRS;
import srs.core.ServerTasks;
import srs.core.Spoiler;

/**
 * /srs - view and manage the randomizer configuration from the chat.
 * Changes are written to the config file and take effect after restarting the game (or the server).
 */
public class SRSCommand extends ModularChatCommand {

    private static final String RESTART = "Salvo. Reinicie o jogo/servidor para aplicar.";

    public SRSCommand() {
        super("srs", "Gerencia o Super Randomizer Show (status, seed, set, get, options, toggle, spoiler, reset, test)", PermissionLevel.ADMIN, false,
                new CmdParameter("action", new PresetStringParameterHandler("status", "seed", "set", "get", "options", "toggle", "spoiler", "reset", "test"), true),
                new CmdParameter("option", new StringParameterHandler(), true),
                new CmdParameter("value", new StringParameterHandler(), true));
    }

    @Override
    public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog log) {
        String action = args[0] == null ? "status" : ((String) args[0]).toLowerCase();
        String option = (String) args[1];
        String value = (String) args[2];
        SRSSettings s = SRS.settings();
        switch (action) {
            case "seed": {
                if (option == null) {
                    long saved = pending().seed;
                    log.add("Seed atual: " + s.seed + (saved == s.seed ? "" : "  (salva para o proximo inicio: " + saved + ")"));
                    return;
                }
                long seed;
                if (option.equalsIgnoreCase("random") || option.equalsIgnoreCase("aleatoria")) {
                    seed = randomSeed();
                } else {
                    try {
                        seed = Long.parseLong(option);
                    } catch (NumberFormatException e) {
                        seed = option.hashCode();
                    }
                }
                SRSSettings copy = pending();
                copy.seed = seed;
                save(copy, log);
                log.add("Nova seed: " + seed);
                return;
            }
            case "set": {
                if (option == null || value == null) {
                    log.add("Uso: /srs set <opcao> <valor>   (veja /srs options)");
                    return;
                }
                SRSSettings copy = pending();
                String error = setField(copy, option, value);
                if (error != null) {
                    log.add(error);
                    return;
                }
                save(copy, log);
                log.add(option + " = " + getField(copy, option));
                return;
            }
            case "toggle": {
                if (option == null) {
                    log.add("Uso: /srs toggle <opcao>");
                    return;
                }
                Field f = findField(option);
                if (f == null || f.getType() != boolean.class) {
                    log.add("Opcao booleana desconhecida: " + option);
                    return;
                }
                SRSSettings copy = pending();
                try {
                    f.setBoolean(copy, !f.getBoolean(copy));
                } catch (IllegalAccessException e) {
                    log.add("Erro: " + e);
                    return;
                }
                save(copy, log);
                log.add(f.getName() + " = " + getField(copy, f.getName()));
                return;
            }
            case "get": {
                if (option == null) {
                    log.add("Uso: /srs get <opcao>");
                    return;
                }
                Object v = getField(s, option);
                if (v == null) {
                    log.add("Opcao desconhecida: " + option);
                    return;
                }
                Object saved = getField(pending(), option);
                log.add(option + " = " + v + (String.valueOf(v).equals(String.valueOf(saved)) ? "" : "  (salvo para o proximo inicio: " + saved + ")"));
                return;
            }
            case "options": {
                StringBuilder sb = new StringBuilder();
                for (Field f : fields()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(f.getName());
                }
                log.add("Opcoes: " + sb);
                return;
            }
            case "test": {
                // Runs on the server tick thread: from a dedicated server's console this command arrives on another thread
                log.add("Autoteste iniciado, o resultado aparece em instantes...");
                ServerTasks.submit(() -> SelfTest.run(server, line -> {
                    if (serverClient != null) {
                        serverClient.sendChatMessage(line);
                    } else {
                        System.out.println(line);
                    }
                }));
                return;
            }
            case "spoiler": {
                log.add("Spoiler gravado em: " + Spoiler.write().getAbsolutePath());
                return;
            }
            case "reset": {
                SRSSettings defaults = new SRSSettings();
                defaults.seed = pending().seed;
                save(defaults, log);
                log.add("Opcoes voltaram ao padrao (a seed foi mantida).");
                return;
            }
            default: {
                log.add("Super Randomizer Show - seed " + s.seed);
                log.add(" 1 Mobs: " + onOff(s.mobSpawns) + " | 2 Drops: " + onOff(s.drops) + " (qtd " + onOff(s.dropQuantities) + ")"
                        + " | 3 Receitas: " + onOff(s.recipes) + " (" + Math.round(s.recipesShuffleLevel * 100) + "%)"
                        + " | 4 Lojas: " + onOff(s.shops) + " (" + s.shopsMode + "), precos " + onOff(s.shopPrices));
                log.add(" 5 Danos: armas " + onOff(s.weaponDamage) + ", inimigos " + onOff(s.enemyDamage) + ", chefes " + onOff(s.bossDamage)
                        + " | 6 Vida: inimigos " + onOff(s.enemyHealth) + ", aldeoes " + onOff(s.villagerHealth) + ", bichos " + onOff(s.critterHealth)
                        + ", animais " + onOff(s.animalHealth) + ", chefes " + onOff(s.bossHealth));
                log.add(" 7 Defesa: inimigos " + onOff(s.enemyArmor) + ", aldeoes " + onOff(s.villagerArmor) + ", bichos " + onOff(s.critterArmor)
                        + ", animais " + onOff(s.animalArmor) + ", chefes " + onOff(s.bossArmor)
                        + " | 8 Empurrao: inimigos " + onOff(s.enemyKnockback) + ", aldeoes " + onOff(s.villagerKnockback) + ", bichos " + onOff(s.critterKnockback)
                        + ", animais " + onOff(s.animalKnockback) + ", chefes " + onOff(s.bossKnockback));
                log.add(" 9 Critico: " + onOff(s.critChance) + " | 10 Bonus conjunto: " + onOff(s.armorSetBonuses) + " (" + s.armorSetBonusMode + ")"
                        + " | 11 Efeitos: " + onOff(s.equipmentEffects) + " (" + s.equipmentEffectsMode + ")"
                        + " | 12 % dano equip.: " + onOff(s.equipmentDamage) + ", pergaminhos " + onOff(s.enchantScrolls));
                log.add(" 13 Visuais: " + onOff(s.visuals) + " (Troca Bugada Divertida: " + onOff(s.funnyBuggedSwap) + ", rotacao: " + onOff(s.visualRotation) + ")"
                        + " | 14 Projeteis: " + onOff(s.projectiles) + " (velocidade " + onOff(s.projectileSpeed) + ")"
                        + " | 15 Traducoes: " + onOff(s.translations) + " (" + s.translationMode + ") | 16 Sons: " + onOff(s.sounds)
                        + " (rotacao: " + onOff(s.soundRotation) + ")");
                log.add(" 17 Recompensas: " + onOff(s.questRewards) + " (Mestre Anciao " + onOff(s.questRewardsElder)
                        + ", diario de aventura " + onOff(s.questRewardsJournal) + ")"
                        + " | 18 Pesca: iscas " + onOff(s.fishingBaitPower) + ", varas " + onOff(s.fishingRodPower)
                        + ", pescaria " + onOff(s.fishingCatches) + " (" + s.fishingCatchesMode + ")");
                if (!pending().fingerprint().equals(s.fingerprint())) {
                    log.add("Ha mudancas salvas no arquivo que so valem depois de reiniciar (seed salva: " + pending().seed + ").");
                }
                log.add("Config: " + SRS.configFile().getAbsolutePath());
                log.add("Spoiler: " + Spoiler.getFile().getAbsolutePath());
                log.add("Comandos: /srs seed <n|random>, /srs toggle <opcao>, /srs set <opcao> <valor>, /srs get <opcao>, /srs options, /srs spoiler, /srs reset, /srs test");
            }
        }
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "off";
    }

    private static long randomSeed() {
        long seed = 0;
        while (seed == 0) seed = new Random().nextLong();
        return seed;
    }

    /** Makes the edited settings the stored ones (so Necesse's own saves keep them) and writes the cfg file. */
    private static void save(SRSSettings settings, CommandLog log) {
        SRS.stored().applyScript(settings.toScript());
        log.add(SRS.saveConfig() ? RESTART : "Nao foi possivel salvar o arquivo de configuracao.");
    }

    private static SRSSettings copy(SRSSettings s) {
        SRSSettings copy = new SRSSettings();
        copy.applyScript(s.toScript());
        return copy;
    }

    /** What is saved in the config file right now (changes made since the game started included). */
    private static SRSSettings pending() {
        SRSSettings out = copy(SRS.stored());
        File file = SRS.configFile();
        if (file.exists()) {
            try {
                LoadData settings = new LoadData(file).getFirstLoadDataByName("SETTINGS");
                if (settings != null && settings.isArray()) out.applyLoadData(settings);
            } catch (Exception e) {
                SRS.warn("Could not read config file: " + e);
            }
        }
        return out;
    }

    private static List<Field> fields() {
        List<Field> out = new ArrayList<>();
        for (Field f : SRSSettings.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) out.add(f);
        }
        return out;
    }

    private static Field findField(String name) {
        for (Field f : fields()) {
            if (f.getName().equalsIgnoreCase(name)) return f;
        }
        return null;
    }

    private static Object getField(SRSSettings s, String name) {
        Field f = findField(name);
        if (f == null) return null;
        try {
            return f.get(s);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static String setField(SRSSettings s, String name, String value) {
        Field f = findField(name);
        if (f == null) return "Opcao desconhecida: " + name + " (veja /srs options)";
        try {
            Class<?> type = f.getType();
            if (type == boolean.class) {
                String v = value.toLowerCase();
                f.setBoolean(s, v.equals("true") || v.equals("on") || v.equals("1") || v.equals("sim"));
            } else if (type == float.class) {
                f.setFloat(s, Float.parseFloat(value.replace(',', '.')));
            } else if (type == long.class) {
                f.setLong(s, Long.parseLong(value));
            } else if (type == int.class) {
                f.setInt(s, Integer.parseInt(value));
            } else {
                f.set(s, value);
            }
            return null;
        } catch (Exception e) {
            return "Valor invalido para " + f.getName() + ": " + value;
        }
    }

}
