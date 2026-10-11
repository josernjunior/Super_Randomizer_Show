package srs.commands;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import necesse.engine.journal.JournalChallenge;
import necesse.engine.journal.MultiJournalChallenge;
import necesse.engine.localization.Localization;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.BuffRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.engine.registries.ProjectileRegistry;
import necesse.engine.storyObjectives.objectives.bossObjectives.DefeatEvilsProtectorStoryObjective;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.entity.mobs.buffs.EquipmentActiveBuff;
import necesse.entity.mobs.buffs.staticBuffs.Buff;
import necesse.entity.mobs.buffs.staticBuffs.armorBuffs.ArmorBuff;
import necesse.entity.projectile.Projectile;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemStatTipList;
import necesse.inventory.item.armorItem.ArmorItem;
import necesse.inventory.item.baitItem.BaitItem;
import necesse.inventory.item.matItem.FishItemInterface;
import necesse.inventory.item.placeableItem.fishingRodItem.FishingRodItem;
import necesse.inventory.lootTable.LootList;
import necesse.inventory.lootTable.LootTable;
import necesse.level.maps.Level;
import necesse.level.maps.LevelTile;
import necesse.level.maps.biomes.FishingLootTable;
import necesse.level.maps.biomes.FishingSpot;
import necesse.level.maps.biomes.MobChance;
import necesse.level.maps.biomes.MobSpawnTable;
import srs.core.MobPools;
import srs.core.MobPools.Pool;
import srs.core.SRS;
import srs.random.DropRandomizer;
import srs.random.FishingRandomizer;
import srs.random.MobDamageRandomizer;
import srs.random.MobHealthRandomizer;
import srs.random.MobStatRandomizer;
import srs.random.MobSpawnRandomizer;
import srs.random.ProjectileRandomizer;
import srs.random.QuestRewardRandomizer;
import srs.ui.MobChoices;

/**
 * /srs test - runs the randomized code paths on the server's spawn level and reports what failed.
 * Meant for diagnosing problems: it spawns and kills mobs, fires every projectile type, equips buffs on a test
 * NPC and builds armor tooltips, catching every exception.
 */
public final class SelfTest {

    private SelfTest() {
    }

    public static void run(Server server, Consumer<String> log) {
        if (server == null || server.world == null) {
            log.accept("Sem servidor/mundo carregado.");
            return;
        }
        Level level = server.world.getLevel(server.world.worldEntity.spawnLevelIdentifier);
        Point tile = server.world.worldEntity.spawnTile;
        if (level == null || tile == null) {
            log.accept("Nivel de spawn nao carregado.");
            return;
        }
        float x = tile.x * 32 + 16;
        float y = tile.y * 32 + 16;
        Map<String, Integer> failures = new TreeMap<>();

        // 1 - natural spawn remapping goes through MobSpawnTable
        int spawnChecked = 0;
        int spawnRemapped = 0;
        int spawnWrong = 0;
        List<String> natural = new ArrayList<>(MobPools.get(Pool.HOSTILE));
        natural.addAll(MobPools.get(Pool.CRITTER));
        natural.addAll(MobPools.get(Pool.ANIMAL));
        for (String id : natural) {
            try {
                MobChance chance = new MobSpawnTable().add(1, id).getRandomMob(level, (ServerClient) null, tile, GameRandom.globalRandom, "srstest");
                Mob mob = chance.getMob(level, null, tile);
                spawnChecked++;
                String expected = MobSpawnRandomizer.getMapping(id);
                if (expected == null) expected = id;
                if (mob != null && !mob.getStringID().equals(id)) spawnRemapped++;
                if (mob == null || !mob.getStringID().equals(expected)) spawnWrong++;
            } catch (Throwable e) {
                fail(failures, "spawn " + id, e);
            }
        }
        log.accept("Spawn natural: " + spawnChecked + " testados, " + spawnRemapped + " trocados, " + spawnWrong + " diferentes do esperado");

        // 2 - kill every randomized mob and count the drops
        int killed = 0;
        int drops = 0;
        // Animals placed when the world was generated (shows whether sheep, cows... were swapped)
        Map<String, Integer> animals = new TreeMap<>();
        level.entityManager.mobs.stream().filter(m -> MobPools.poolOf(m.getStringID()) == Pool.ANIMAL)
                .forEach(m -> animals.merge(m.getStringID(), 1, Integer::sum));
        log.accept("Animais carregados no mundo: " + (animals.isEmpty() ? "nenhum perto do spawn" : animals.toString())
                + " | grupo de animais: " + MobPools.get(Pool.ANIMAL));

        List<String> killList = new ArrayList<>(MobPools.get(Pool.HOSTILE));
        killList.addAll(MobPools.get(Pool.BOSS));
        killList.addAll(MobPools.get(Pool.CRITTER));
        killList.addAll(MobPools.get(Pool.ANIMAL));
        for (String id : killList) {
            try {
                Mob mob = MobRegistry.getMob(MobRegistry.getMobID(id), level);
                if (mob == null) continue;
                level.entityManager.addMob(mob, x, y);
                int before = level.entityManager.pickups.count();
                mob.remove(0.0F, 0.0F, null, true);
                drops += Math.max(0, level.entityManager.pickups.count() - before);
                killed++;
            } catch (Throwable e) {
                fail(failures, "kill " + id, e);
            }
        }
        level.entityManager.pickups.stream().forEach(p -> p.remove());
        log.accept("Mortes: " + killed + " mobs mortos, " + drops + " drops gerados");

        // 3 - fire every projectile type and tick it a few times
        Mob owner = MobRegistry.getMob(MobRegistry.getMobID("zombie"), level);
        int fired = 0;
        int swapped = 0;
        if (owner != null) {
            level.entityManager.addMob(owner, x, y);
            int count = ProjectileRandomizer.registrySize();
            for (int id = 0; id < count; id++) {
                String stringID = ProjectileRegistry.getProjectileStringID(id);
                // Types that aren't swapped behave exactly like vanilla, which can't create all of them generically
                if (stringID == null || ProjectileRandomizer.getMapping(stringID) == null) continue;
                Projectile added = null;
                try {
                    Projectile p = ProjectileRegistry.getProjectile(id, level);
                    p.applyData(x, y, x + 300, y, 60.0F, 400, new GameDamage(10.0F), owner);
                    p.resetUniqueID(GameRandom.globalRandom);
                    level.entityManager.projectiles.add(p);
                    added = level.entityManager.projectiles.get(p.getUniqueID(), false);
                    if (added != null && added != p) swapped++;
                    if (added != null) {
                        for (int t = 0; t < 10 && !added.removed(); t++) added.serverTick();
                    }
                    fired++;
                } catch (Throwable e) {
                    fail(failures, "projectile " + stringID + (added != null ? " (virou " + added.getStringID() + ")" : ""), e);
                } finally {
                    if (added != null) added.remove();
                }
            }
            log.accept("Projeteis: " + fired + " disparados, " + swapped + " trocados");

            // Every type in the pool must survive being created generically, which is what a swap does
            int targetsOk = 0;
            Projectile template = ProjectileRegistry.getProjectile(ProjectileRegistry.getProjectileID("stonearrow"), level);
            template.applyData(x, y, x + 300, y, 60.0F, 400, new GameDamage(10.0F), owner);
            for (String type : ProjectileRandomizer.getPool()) {
                Projectile p = null;
                Set<Integer> mobsBefore = new HashSet<>();
                level.entityManager.mobs.stream().forEach(m -> mobsBefore.add(m.getUniqueID()));
                List<Mob> spawned = new ArrayList<>();
                try {
                    template.resetUniqueID(GameRandom.globalRandom);
                    p = ProjectileRandomizer.createLike(type, template, level);
                    level.entityManager.projectiles.addHidden(p);
                    // Fly until the end, up to 10 seconds: movement is where hits and impact effects happen
                    for (int t = 0; t < 200 && !p.removed(); t++) {
                        p.tickMovement(50.0F);
                        if (!p.removed()) p.serverTick();
                    }
                    // Mobs the projectile created (spikes, summons...) must work too
                    level.entityManager.mobs.stream().filter(m -> !mobsBefore.contains(m.getUniqueID())).forEach(spawned::add);
                    for (Mob m : spawned) {
                        for (int t = 0; t < 5 && !m.removed(); t++) {
                            m.tickMovement(50.0F);
                            m.serverTick();
                        }
                    }
                    targetsOk++;
                } catch (Throwable e) {
                    fail(failures, "substituto " + type + (spawned.isEmpty() ? "" : " (mobs criados: " + spawned.size() + ")"), e);
                } finally {
                    if (p != null) p.remove();
                    for (Mob m : spawned) m.remove();
                }
            }
            log.accept("Substitutos de projetil: " + targetsOk + "/" + ProjectileRandomizer.getPool().size() + " funcionam criados de forma generica");
            owner.remove();
        }

        // 4 - equipment: buffs on a test NPC and armor tooltips
        Mob human = MobRegistry.getMob(MobRegistry.getMobID("human"), level);
        int buffs = 0;
        int tooltips = 0;
        if (human != null) {
            level.entityManager.addMob(human, x, y);
            for (Buff buff : BuffRegistry.getBuffs()) {
                if (!(buff instanceof ArmorBuff)) continue;
                try {
                    human.buffManager.addBuff(new ActiveBuff(buff, human, 1000, null), false);
                    human.buffManager.removeBuff(buff, false);
                    buffs++;
                } catch (Throwable e) {
                    fail(failures, "buff " + buff.getStringID(), e);
                }
            }
            int equipped = 0;
            int armorDiffs = 0;
            for (Item item : ItemRegistry.getItems()) {
                if (!(item instanceof ArmorItem)) continue;
                try {
                    ((ArmorItem) item).addModifierTooltips(new ItemStatTipList(), new InventoryItem(item), null, human);
                    tooltips++;
                } catch (Throwable e) {
                    fail(failures, "tooltip " + item.getStringID(), e);
                }
                try {
                    // Same calls the game makes when a piece is equipped
                    EquipmentActiveBuff equipment = new EquipmentActiveBuff(BuffRegistry.EQUIPMENT_BUFF, human);
                    equipment.addArmorBuff(human, new InventoryItem(item), null);
                    human.buffManager.addBuff(equipment, false);
                    int armor = equipment.getModifier(BuffModifiers.ARMOR_FLAT);
                    if (armor != ((ArmorItem) item).getTotalArmorValue(new InventoryItem(item), human)) armorDiffs++;
                    human.buffManager.removeBuff(BuffRegistry.EQUIPMENT_BUFF.getID(), false);
                    equipped++;
                } catch (Throwable e) {
                    fail(failures, "equipar " + item.getStringID(), e);
                }
            }
            human.remove();
            log.accept("Armaduras equipadas: " + equipped + " (" + armorDiffs + " com valor de armadura diferente do original)");
        }
        log.accept("Equipamentos: " + buffs + " buffs aplicados, " + tooltips + " descricoes de armadura geradas");

        // 5.2/5.3 - enemy and boss damage
        int pooled = MobPools.get(Pool.HOSTILE).size() + MobPools.get(Pool.BOSS).size();
        String example = null;
        for (String id : MobPools.get(Pool.HOSTILE)) {
            if (MobDamageRandomizer.getFactor(id) != 1.0F) {
                example = id;
                break;
            }
        }
        String hitResult = "sem tipo com fator para testar";
        if (example != null) {
            Mob attackerMob = MobRegistry.getMob(MobRegistry.getMobID(example), level);
            Mob targetMob = MobRegistry.getMob(MobRegistry.getMobID("human"), level);
            try {
                level.entityManager.addMob(attackerMob, x + 64, y);
                level.entityManager.addMob(targetMob, x, y);
                GameDamage scaled = MobDamageRandomizer.modify(new GameDamage(100.0F), attackerMob, targetMob);
                float expected = 100.0F * MobDamageRandomizer.getFactor(example);
                targetMob.isServerHit(new GameDamage(100.0F), x, y, 0.0F, attackerMob);
                hitResult = Math.abs(scaled.damage - expected) < 0.01F
                        ? "golpe de 100 virou " + Math.round(scaled.damage) + " (esperado " + Math.round(expected) + ")"
                        : "ERRO: golpe de 100 virou " + scaled.damage + ", esperado " + expected;
                if (!hitResult.startsWith("golpe")) failures.merge(hitResult, 1, Integer::sum);
            } catch (Throwable e) {
                fail(failures, "golpe de " + example, e);
            } finally {
                attackerMob.remove();
                targetMob.remove();
            }
        }
        log.accept("Dano de inimigos/chefes: " + MobDamageRandomizer.measuredCount() + " de " + pooled + " tipos com dano medido"
                + (example == null ? "" : "; " + example + " (dano base " + MobDamageRandomizer.getBaseDamage(example) + ") bate x"
                + String.format(java.util.Locale.ROOT, "%.2f", MobDamageRandomizer.getFactor(example))) + "; " + hitResult);

        // 6 - health: a fresh mob of each group must start with the randomized max health
        List<String> healthLines = new ArrayList<>();
        for (Pool pool : new Pool[]{Pool.HOSTILE, Pool.TOWN, Pool.CRITTER, Pool.ANIMAL, Pool.BOSS}) {
            String id = null;
            for (String candidate : MobPools.get(pool)) {
                if (MobHealthRandomizer.getFactor(candidate) != 1.0F) {
                    id = candidate;
                    break;
                }
            }
            if (id == null) continue;
            Mob mob = MobRegistry.getMob(MobRegistry.getMobID(id), level);
            if (mob == null) continue;
            int flat = mob.getMaxHealthFlat();
            int max = mob.getMaxHealth();
            int expected = Math.max(1, Math.round(flat * MobHealthRandomizer.getFactor(id)));
            healthLines.add(id + " " + flat + "->" + max);
            if (Math.abs(max - expected) > 1 || mob.getHealth() != max) {
                failures.merge("vida de " + id + ": " + flat + " -> " + max + " (esperado " + expected + ", vida atual " + mob.getHealth() + ")", 1, Integer::sum);
            }
        }
        log.accept("Vida: " + (healthLines.isEmpty() ? "nenhum grupo com fator" : String.join(", ", healthLines)));

        // 7/8 - defense and knockback: the getters must return the randomized values
        List<String> statLines = new ArrayList<>();
        for (Pool pool : new Pool[]{Pool.HOSTILE, Pool.TOWN, Pool.CRITTER, Pool.ANIMAL, Pool.BOSS}) {
            List<String> ids = MobPools.get(pool);
            if (ids.isEmpty()) continue;
            String id = ids.get(ids.size() / 2);
            Mob mob = MobRegistry.getMob(MobRegistry.getMobID(id), level);
            if (mob == null) continue;
            try {
                float armor = mob.getArmor();
                float expectedArmor = MobStatRandomizer.modifyArmor(mob, mob.getArmorFlat());
                float knockback = mob.getKnockbackModifier();
                statLines.add(id + " defesa " + mob.getArmorFlat() + "->" + Math.round(armor) + ", empurrao " + String.format(java.util.Locale.ROOT, "%.2f", knockback));
                if (Math.abs(armor - expectedArmor) > 0.01F) failures.merge("defesa de " + id + ": " + armor + " (esperado " + expectedArmor + ")", 1, Integer::sum);
            } catch (Throwable e) {
                fail(failures, "defesa/empurrao de " + id, e);
            }
        }
        log.accept("Defesa/empurrao: " + String.join("; ", statLines));

        // 17 - quest rewards: the tables the game reads must be the wrapped ones, and every roll must give pool items
        if (SRS.settings().questRewards) {
            int elder = 0, journal = 0, rolledItems = 0;
            String rewardExample = null;
            Set<Item> pool = new HashSet<>(DropRandomizer.randomItemPool());
            for (Map.Entry<String, LootTable> e : QuestRewardRandomizer.getWrapped().entrySet()) {
                if (e.getKey().startsWith(QuestRewardRandomizer.ELDER)) elder++;
                else journal++;
                try {
                    for (InventoryItem item : e.getValue().getNewList(new GameRandom(1), 1.0F)) {
                        rolledItems++;
                        if (!pool.contains(item.item) && !SRS.isItemExcluded(item.item.getStringID())) {
                            failures.merge("recompensa de " + e.getKey() + " fora do sorteio: " + item.item.getStringID(), 1, Integer::sum);
                        }
                        if (rewardExample == null) rewardExample = e.getKey() + " -> " + item.item.getStringID() + " x" + item.getAmount();
                    }
                    LootList shown = new LootList();
                    e.getValue().addPossibleLoot(shown);
                    int shownCount = 0;
                    for (Item item : shown.getItems()) {
                        shownCount++;
                        if (!pool.contains(item) && !SRS.isItemExcluded(item.getStringID())) {
                            failures.merge("janela de " + e.getKey() + " mostra item fora do sorteio: " + item.getStringID(), 1, Integer::sum);
                        }
                    }
                    if (shownCount == 0) failures.merge("recompensa de " + e.getKey() + " sem itens na janela", 1, Integer::sum);
                } catch (Throwable t) {
                    fail(failures, "recompensa de " + e.getKey(), t);
                }
            }
            if (SRS.settings().questRewardsElder && (elder == 0 || !QuestRewardRandomizer.isWrapped(DefeatEvilsProtectorStoryObjective.rewards))) {
                failures.merge("recompensas do Mestre Anciao nao foram trocadas", 1, Integer::sum);
            }
            LootTable gettingStarted = QuestRewardRandomizer.getWrapped().get(QuestRewardRandomizer.ELDER + ": gettingstarted");
            if (gettingStarted != null && !SRS.isItemExcluded("leatherdashers")
                    && gettingStarted.getNewList(new GameRandom(1), 1.0F).stream().noneMatch(i -> i.item.getStringID().equals("leatherdashers"))) {
                failures.merge("o primeiro objetivo do Mestre Anciao nao da mais as Leather Dashers (o seguinte pede para equipa-las)", 1, Integer::sum);
            }
            if (SRS.settings().questRewardsJournal) {
                int journalWithReward = 0, journalWrapped = 0;
                for (JournalChallenge challenge : JournalChallengeRegistry.getChallenges()) {
                    if (!(challenge instanceof MultiJournalChallenge) || ((MultiJournalChallenge) challenge).reward == null) continue;
                    journalWithReward++;
                    if (QuestRewardRandomizer.isWrapped(challenge.getReward())) journalWrapped++;
                }
                if (journalWrapped != journalWithReward || journalWithReward == 0) {
                    failures.merge("desafios do diario trocados: " + journalWrapped + " de " + journalWithReward, 1, Integer::sum);
                }
            }
            log.accept("Recompensas: " + elder + " do Mestre Anciao e " + journal + " do diario trocadas, " + rolledItems
                    + " itens sorteados" + (rewardExample == null ? "" : " (ex.: " + rewardExample + ")"));
        }

        // 18 - fishing: powers live in the items' fields; catches go through FishingLootTable.getRandomItem (regions near the spawn are loaded, never changed, to find water)
        List<String> powerLines = new ArrayList<>();
        int powersChanged = 0;
        for (Item item : ItemRegistry.getItems()) {
            if (!(item instanceof BaitItem) && !(item instanceof FishingRodItem)) continue;
            int original = FishingRandomizer.originalPower(item.getStringID());
            if (original < 0) continue;
            int power = FishingRandomizer.getPower(item);
            float variation = item instanceof BaitItem ? SRS.settings().fishingBaitPowerVariation : SRS.settings().fishingRodPowerVariation;
            if (power < 1 || power < Math.floor(original * (1 - variation)) - 1 || power > Math.ceil(original * (1 + variation)) + 1) {
                failures.merge("poder de pesca de " + item.getStringID() + " fora da faixa: " + power, 1, Integer::sum);
            }
            if (power != original) powersChanged++;
            powerLines.add(item.getStringID() + " " + original + "%->" + power + "%");
        }
        String catchLine = "pescaria desligada";
        if (SRS.settings().fishingCatches) {
            try {
                Set<Item> randomPool = new HashSet<>(DropRandomizer.randomItemPool());
                boolean allItems = "all".equalsIgnoreCase(SRS.settings().fishingCatchesMode);
                InventoryItem carp = new FishingLootTable().add(1, spot -> true, "carp").getRandomItem(null, new GameRandom(1));
                Item expected = FishingRandomizer.catchFor("carp");
                String expectedID = expected == null ? "carp" : expected.getStringID();
                if (carp == null || !carp.item.getStringID().equals(expectedID)) {
                    failures.merge("pescar carp deu " + (carp == null ? "nada" : carp.item.getStringID()) + " (esperado " + expectedID + ")", 1, Integer::sum);
                }
                catchLine = "carp vira " + expectedID;
                Point water = null;
                for (int r = 0; r <= 96 && water == null; r += 2) {
                    for (int dx = -r; dx <= r && water == null; dx += 2) {
                        for (int dy = -r; dy <= r && water == null; dy += 2) {
                            if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                            if (level.regionManager.getRegionByTile(tile.x + dx, tile.y + dy, true) == null) continue;
                            if (level.getTile(tile.x + dx, tile.y + dy).isLiquid) water = new Point(tile.x + dx, tile.y + dy);
                        }
                    }
                }
                if (water != null) {
                    FishingSpot spot = new FishingSpot(new LevelTile(level, water.x, water.y), (FishingRodItem) ItemRegistry.getItem("woodfishingrod"), null);
                    FishingLootTable table = spot.getBiome().getFishingLootTable(spot);
                    TreeMap<String, Integer> caught = new TreeMap<>();
                    for (int i = 0; i < 200; i++) {
                        InventoryItem c = table.getRandomItem(spot, GameRandom.globalRandom);
                        if (c == null) continue;
                        caught.merge(c.item.getStringID(), 1, Integer::sum);
                        boolean ok = allItems ? randomPool.contains(c.item)
                                : c.item instanceof FishItemInterface || FishingRandomizer.catchFor(c.item.getStringID()) == null;
                        if (!ok) failures.merge("pescado inesperado: " + c.item.getStringID(), 1, Integer::sum);
                    }
                    catchLine += "; 200 tentativas na agua em " + water.x + "," + water.y + ": " + caught;
                } else {
                    catchLine += "; nenhuma agua perto do spawn";
                }
            } catch (Throwable t) {
                fail(failures, "pescaria", t);
            }
        }
        log.accept("Pesca: " + powersChanged + " de " + powerLines.size() + " poderes mudados (" + String.join(", ", powerLines) + "); " + catchLine);

        String[][] bosses = MobChoices.bosses(SRS.settings().bossPool);
        String[][] npcs = MobChoices.townNPCs(SRS.settings().townNPCPool);
        log.accept("Tela de configuracao: " + bosses.length + " chefes para marcar: " + names(bosses));
        log.accept("Tela de configuracao: " + npcs.length + " NPCs para marcar: " + names(npcs));

        log.accept("Traducoes (exemplo): item.copperbar = \"" + Localization.translate("item", "copperbar")
                + "\", mob.zombie = \"" + Localization.translate("mob", "zombie") + "\"");

        if (failures.isEmpty()) {
            log.accept("Nenhuma falha encontrada.");
        } else {
            log.accept(failures.size() + " falhas:");
            for (Map.Entry<String, Integer> e : failures.entrySet()) log.accept("  " + e.getKey());
        }
    }

    private static String names(String[][] entries) {
        StringBuilder sb = new StringBuilder();
        for (String[] e : entries) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e[1]);
        }
        return sb.toString();
    }

    private static void fail(Map<String, Integer> failures, String what, Throwable e) {
        String key = what + ": " + e;
        failures.merge(key, 1, Integer::sum);
        SRS.warn("selftest " + key);
    }

}
