package srs.commands;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import necesse.engine.events.loot.ObjectLootTableDropsEvent;
import necesse.engine.journal.JournalChallenge;
import necesse.engine.journal.MultiJournalChallenge;
import necesse.engine.localization.Localization;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.BuffRegistry;
import necesse.engine.registries.ClassIDDataContainer;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.ProjectileRegistry;
import necesse.engine.storyObjectives.objectives.bossObjectives.DefeatEvilsProtectorStoryObjective;
import necesse.engine.util.GameRandom;
import necesse.entity.levelEvent.LevelEvent;
import necesse.entity.levelEvent.mobAbilityLevelEvent.MobAbilityLevelEvent;
import necesse.entity.mobs.EquipmentBuffManager;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.entity.mobs.buffs.EquipmentActiveBuff;
import necesse.entity.mobs.buffs.staticBuffs.Buff;
import necesse.entity.mobs.buffs.staticBuffs.armorBuffs.ArmorBuff;
import necesse.entity.mobs.friendly.human.humanShop.BuyingShopItem;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.entity.mobs.hostile.FurnaceGolemMob;
import necesse.entity.projectile.Projectile;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemStatTipList;
import necesse.inventory.item.armorItem.ArmorItem;
import necesse.inventory.item.armorItem.SetHelmetArmorItem;
import necesse.inventory.item.baitItem.BaitItem;
import necesse.inventory.item.matItem.FishItemInterface;
import necesse.inventory.item.placeableItem.fishingRodItem.FishingRodItem;
import necesse.inventory.lootTable.LootList;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.recipe.CanCraft;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelTile;
import necesse.level.maps.biomes.FishingLootTable;
import necesse.level.maps.biomes.FishingSpot;
import necesse.level.maps.biomes.MobChance;
import necesse.level.maps.biomes.MobSpawnTable;
import srs.core.MobPools;
import srs.core.MobPools.Pool;
import srs.core.SRS;
import srs.core.SpawnOrder;
import srs.random.DropRandomizer;
import srs.random.EnchantmentRandomizer;
import srs.random.EquipmentRandomizer;
import srs.random.FishingRandomizer;
import srs.random.MobDamageRandomizer;
import srs.random.MobHealthRandomizer;
import srs.random.MobStatRandomizer;
import srs.random.MobSpawnRandomizer;
import srs.random.ProjectileRandomizer;
import srs.random.QuestRewardRandomizer;
import srs.random.RecipeRandomizer;
import srs.random.ShopRandomizer;
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

        // 2 - drop swaps: whatever drops something must only take the drops of something that also drops (and the other way
        // around); only checked groups take part; in group mode nothing leaves its group; mob <-> object drops must roll
        if (SRS.settings().drops) {
            boolean allMode = "all".equalsIgnoreCase(SRS.settings().dropsMode);
            Set<String> enabledGroups = SRS.parseList(SRS.settings().dropGroups);
            int swappedPairs = 0, crossGroup = 0, mobObject = 0;
            List<String> lostDrops = new ArrayList<>();
            List<String> gainedDrops = new ArrayList<>();
            for (Map.Entry<String, String> e : new TreeMap<>(DropRandomizer.getMapping()).entrySet()) {
                String fromGroup = DropRandomizer.candidateGroup(e.getKey());
                String toGroup = DropRandomizer.candidateGroup(e.getValue());
                if (!enabledGroups.contains(fromGroup) || !enabledGroups.contains(toGroup)) {
                    failures.merge("troca de drop num grupo desmarcado: " + e.getKey() + "->" + e.getValue(), 1, Integer::sum);
                }
                if (e.getKey().equals(e.getValue())) continue;
                swappedPairs++;
                if (!fromGroup.equals(toGroup)) crossGroup++;
                if (!allMode && !fromGroup.equals(toGroup)) failures.merge("troca de drop fora do grupo: " + e.getKey() + "->" + e.getValue(), 1, Integer::sum);
                boolean crossKind = e.getKey().startsWith(DropRandomizer.MOB) != e.getValue().startsWith(DropRandomizer.MOB);
                try {
                    boolean fromDrops = DropRandomizer.dropsSomething(e.getKey(), level, tile.x, tile.y);
                    boolean toDrops = DropRandomizer.dropsSomething(e.getValue(), level, tile.x, tile.y);
                    if (fromDrops && !toDrops) lostDrops.add(e.getKey() + "->" + e.getValue());
                    if (!fromDrops && toDrops) gainedDrops.add(e.getKey() + "->" + e.getValue());
                    if (crossKind) {
                        mobObject++;
                        if (DropRandomizer.rollDrops(e.getValue(), level, 0, tile.x, tile.y) == null) {
                            failures.merge("drops de " + e.getValue() + " nao foram gerados para " + e.getKey(), 1, Integer::sum);
                        }
                    }
                } catch (Throwable t) {
                    fail(failures, "drops de " + e.getKey() + " -> " + e.getValue(), t);
                }
            }
            if (!lostDrops.isEmpty()) failures.merge("trocas que ficaram sem drop: " + lostDrops, 1, Integer::sum);
            if (!gainedDrops.isEmpty()) failures.merge("trocas sem drop que passaram a dropar (drop dobrado): " + gainedDrops, 1, Integer::sum);
            List<String> missing = new ArrayList<>();
            for (String key : new String[]{"object:woodwall", "object:stonewall", "object:yellowflowerpatch", "object:wildsunflower",
                    "object:sunflowerseed4", "object:beetseed4", "mob:cropplercarrot"}) {
                String id = DropRandomizer.idOf(key);
                if (SRS.isItemExcluded(id) || SRS.isMobExcluded(id)) continue;
                String group = DropRandomizer.candidateGroup(key);
                if (group == null || enabledGroups.contains(group) && !DropRandomizer.getMapping().containsKey(key)) missing.add(id);
            }
            if (!missing.isEmpty()) failures.merge("fora da troca de drops: " + missing, 1, Integer::sum);
            // Crops that haven't grown only give their seed back: swapping them would turn planting and breaking a seed into free loot
            List<String> unripe = new ArrayList<>();
            for (String id : new String[]{"beetseed", "beetseed1", "beetseed3", "sunflowerseed2"}) {
                if (DropRandomizer.candidateGroup(DropRandomizer.OBJECT + id) != null) unripe.add(id);
            }
            if (!unripe.isEmpty()) failures.merge("plantacoes que ainda nao cresceram na troca de drops: " + unripe, 1, Integer::sum);
            String beet = DropRandomizer.getMapping().get(DropRandomizer.OBJECT + "beetseed4");
            String sunflower = DropRandomizer.getMapping().get(DropRandomizer.OBJECT + "sunflowerseed4");
            log.accept("Plantacoes: beterraba madura dropa como " + (beet == null ? "ela mesma" : beet)
                    + ", girassol maduro dropa como " + (sunflower == null ? "ele mesmo" : sunflower));
            log.accept("Drops (modo " + SRS.settings().dropsMode + ", grupos " + String.join(",", enabledGroups) + "): " + swappedPairs
                    + " trocados, " + crossGroup + " entre grupos diferentes, " + mobObject + " entre mob e objeto, " + lostDrops.size()
                    + " ficaram sem drop, " + gainedDrops.size() + " passaram a dropar; paredes/canteiros/safrinhos na troca: "
                    + (missing.isEmpty() ? "sim" : "faltam " + missing));
            testPlayerPlaced(level, tile, failures, log);
            testGrownCrop(level, tile, failures, log);
        }

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

            // Debug projectiles (mousetest draws "null" and numbers) must never be picked
            for (String type : ProjectileRandomizer.getPool()) {
                Projectile p = ProjectileRegistry.getProjectile(ProjectileRegistry.getProjectileID(type));
                if (p != null && p.getClass().getSimpleName().contains("Test")) failures.merge("projetil de teste no sorteio: " + type, 1, Integer::sum);
            }
            // 14 - speed: a projectile added to the level flies at its type's multiplier
            if (SRS.settings().projectileSpeed) {
                Projectile p = null;
                try {
                    p = ProjectileRegistry.getProjectile(ProjectileRegistry.getProjectileID("stonearrow"), level);
                    p.applyData(x, y, x + 300, y, 100.0F, 400, new GameDamage(10.0F), owner);
                    p.resetUniqueID(GameRandom.globalRandom);
                    level.entityManager.projectiles.add(p);
                    Projectile added = level.entityManager.projectiles.get(p.getUniqueID(), false);
                    if (added != null) {
                        float expected = 100.0F * ProjectileRandomizer.getSpeedFactor(added.getStringID());
                        log.accept("Velocidade: stonearrow (virou " + added.getStringID() + ") com velocidade 100 voa a " + Math.round(added.speed)
                                + " (esperado " + Math.round(expected) + ")");
                        if (Math.abs(added.speed - expected) > 0.5F) failures.merge("velocidade do projetil: " + added.speed + ", esperado " + expected, 1, Integer::sum);
                        added.remove();
                    }
                } catch (Throwable e) {
                    fail(failures, "velocidade de projetil", e);
                } finally {
                    if (p != null) p.remove();
                }
            }
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
        if (SRS.settings().armorSetBonuses && "double".equalsIgnoreCase(SRS.settings().armorSetBonusMode)) testDoubleSetBonus(level, x, y, failures, log);
        if (SRS.settings().enchantScrolls) {
            log.accept("Pergaminhos: " + EnchantmentRandomizer.changedCount() + " encantamentos com valores randomizados");
            if (EnchantmentRandomizer.changedCount() == 0) failures.merge("nenhum encantamento foi randomizado", 1, Integer::sum);
        }

        // 4 - shops: whole shops moved to other NPCs, and prices
        testShops(level, x, y, failures, log);

        // Spawn order fix: the furnace golem's heat event must wait for the golem and then be added with it as owner
        Mob golem = MobRegistry.getMob(MobRegistry.getMobID("furnacegolem"), level);
        if (golem instanceof FurnaceGolemMob) {
            try {
                int before = SpawnOrder.deferredCount();
                level.entityManager.addMob(golem, x, y);
                LevelEvent event = level.entityManager.events.get(((FurnaceGolemMob) golem).eventUniqueID, false);
                boolean owned = event instanceof MobAbilityLevelEvent && ((MobAbilityLevelEvent) event).owner == golem;
                log.accept("Ordem de spawn: evento de calor do golem " + (SpawnOrder.deferredCount() > before ? "esperou o golem" : "NAO esperou")
                        + " e " + (owned ? "foi adicionado com o golem como dono" : "NAO esta no nivel com o dono certo"));
                if (SpawnOrder.deferredCount() == before || !owned) failures.merge("evento de calor do golem fora de ordem", 1, Integer::sum);
            } catch (Throwable t) {
                fail(failures, "golem de fornalha", t);
            } finally {
                golem.remove();
            }
        }

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

        // 3 - free revival potion: the indexed recipes must need nothing and count as craftable
        if (SRS.settings().recipesFreeRevivalPotion) {
            int free = 0;
            for (Recipe r : Recipes.getRecipes()) {
                if (!r.resultStringID.equals(RecipeRandomizer.REVIVAL_POTION)) continue;
                if (r.ingredients.length == 0 && new CanCraft(r, false).canCraft()) free++;
                else failures.merge("receita de " + r.resultStringID + "@" + r.tech.getStringID() + " ainda pede ingredientes", 1, Integer::sum);
            }
            if (free == 0) failures.merge("nenhuma receita gratis de " + RecipeRandomizer.REVIVAL_POTION, 1, Integer::sum);
            log.accept("Pocao de Ressureicao gratis: " + free + " receita(s) sem ingredientes");
        }

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
            boolean bait = item instanceof BaitItem;
            float a = bait ? SRS.settings().fishingBaitPowerMin : SRS.settings().fishingRodPowerMin;
            float b = bait ? SRS.settings().fishingBaitPowerMax : SRS.settings().fishingRodPowerMax;
            float min = Math.min(a, b), max = Math.max(a, b);
            if (power < 1 || power < Math.floor(original * min) - 1 || power > Math.ceil(original * max) + 1) {
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

    /**
     * Breaking a swapped object: when a player placed it, it must give itself back; when it grew naturally, the swap
     * applies. Uses an empty-looking tile next to the spawn and restores its player-placed flag afterwards.
     */
    private static void testPlayerPlaced(Level level, Point tile, Map<String, Integer> failures, Consumer<String> log) {
        String from = null;
        for (Map.Entry<String, String> e : new TreeMap<>(DropRandomizer.getMapping()).entrySet()) {
            String id = DropRandomizer.idOf(e.getKey());
            if (e.getKey().startsWith(DropRandomizer.OBJECT) && !e.getKey().equals(e.getValue()) && ItemRegistry.itemExists(id) && id.contains("wall")) {
                from = id;
                break;
            }
        }
        if (from == null) return;
        GameObject object = ObjectRegistry.getObject(from);
        boolean before = level.objectLayer.isPlayerPlaced(0, tile.x, tile.y);
        try {
            String[] results = new String[2];
            for (int placed = 0; placed < 2; placed++) {
                level.objectLayer.setIsPlayerPlaced(0, tile.x, tile.y, placed == 1);
                ArrayList<InventoryItem> drops = new ArrayList<>();
                drops.add(new InventoryItem(from, 1));
                ObjectLootTableDropsEvent event = new ObjectLootTableDropsEvent(object, level, 0, tile.x, tile.y,
                        new Point(tile.x * 32 + 16, tile.y * 32 + 16), drops, new ArrayList<>());
                DropRandomizer.onObjectDrops(event);
                results[placed] = event.objectDrops == null || event.objectDrops.isEmpty() ? "nada" : event.objectDrops.get(0).item.getStringID();
            }
            if (!results[1].equals(from)) failures.merge(from + " colocado pelo jogador deu " + results[1] + " (esperado o proprio item)", 1, Integer::sum);
            if (results[0].equals(from)) failures.merge(from + " natural nao trocou o drop", 1, Integer::sum);
            log.accept("Quebrar " + from + ": natural -> " + results[0] + ", colocado pelo jogador -> " + results[1]);
        } catch (Throwable t) {
            fail(failures, "teste de objeto colocado", t);
        } finally {
            level.objectLayer.setIsPlayerPlaced(0, tile.x, tile.y, before);
        }
    }

    /**
     * Breaking a grown beet a player planted: its drops (seeds + beets) aren't its own item, so the swap must apply
     * and give only what the other object can drop.
     */
    private static void testGrownCrop(Level level, Point tile, Map<String, Integer> failures, Consumer<String> log) {
        String from = DropRandomizer.OBJECT + "beetseed4";
        String to = DropRandomizer.getMapping().get(from);
        if (to == null || to.equals(from)) return;
        GameObject crop = ObjectRegistry.getObject(DropRandomizer.idOf(from));
        boolean before = level.objectLayer.isPlayerPlaced(0, tile.x, tile.y);
        try {
            level.objectLayer.setIsPlayerPlaced(0, tile.x, tile.y, true);
            ObjectLootTableDropsEvent event = new ObjectLootTableDropsEvent(crop, level, 0, tile.x, tile.y, new Point(tile.x * 32 + 16, tile.y * 32 + 16),
                    crop.getObjectDroppedItems(level, 0, tile.x, tile.y, "onDestroyed"), new ArrayList<>());
            DropRandomizer.onObjectDrops(event);
            List<String> got = new ArrayList<>();
            for (InventoryItem item : event.objectDrops) got.add(item.item.getStringID() + " x" + item.getAmount());
            if (to.startsWith(DropRandomizer.OBJECT)) {
                LootList possible = new LootList();
                ObjectRegistry.getObject(DropRandomizer.idOf(to)).getLootTable(level, 0, tile.x, tile.y).addPossibleLoot(possible);
                Set<Item> allowed = new HashSet<>();
                for (Item item : possible.getItems()) allowed.add(item);
                for (InventoryItem item : event.objectDrops) {
                    if (!allowed.contains(item.item)) failures.merge("beterraba madura plantada deu " + item.item.getStringID() + " (troca com " + to + ")", 1, Integer::sum);
                }
                if (event.objectDrops.isEmpty()) failures.merge("beterraba madura plantada nao dropou nada (troca com " + to + ")", 1, Integer::sum);
            }
            log.accept("Quebrar beterraba madura plantada pelo jogador (dropa como " + to + "): " + got);
        } catch (Throwable t) {
            fail(failures, "teste de plantacao madura", t);
        } finally {
            level.objectLayer.setIsPlayerPlaced(0, tile.x, tile.y, before);
        }
    }

    /** Wearing a full set adds the set's own bonus and the extra one; taking the helmet off removes both. */
    private static void testDoubleSetBonus(Level level, float x, float y, Map<String, Integer> failures, Consumer<String> log) {
        SetHelmetArmorItem helmet = null;
        for (Item item : ItemRegistry.getItems()) {
            if (!(item instanceof SetHelmetArmorItem) || EquipmentRandomizer.getExtraSetBonus(item.getStringID()) == null) continue;
            SetHelmetArmorItem candidate = (SetHelmetArmorItem) item;
            if (candidate.setChestStringID != null && candidate.setBootsStringID != null
                    && ItemRegistry.itemExists(candidate.setChestStringID) && ItemRegistry.itemExists(candidate.setBootsStringID)) {
                helmet = candidate;
                break;
            }
        }
        if (helmet == null) {
            failures.merge("bonus duplo ligado, mas nenhum capacete ganhou bonus extra", 1, Integer::sum);
            return;
        }
        String extra = EquipmentRandomizer.getExtraSetBonus(helmet.getStringID());
        Mob human = MobRegistry.getMob(MobRegistry.getMobID("human"), level);
        try {
            level.entityManager.addMob(human, x, y);
            final InventoryItem[] armor = {new InventoryItem(helmet), new InventoryItem(helmet.setChestStringID), new InventoryItem(helmet.setBootsStringID)};
            EquipmentBuffManager manager = new EquipmentBuffManager(human) {
                @Override
                public InventoryItem getArmorItem(int slot) {
                    return armor[slot];
                }

                @Override
                public InventoryItem getCosmeticItem(int slot) {
                    return null;
                }

                @Override
                public ArrayList<InventoryItem> getTrinketItems() {
                    return new ArrayList<>();
                }
            };
            manager.updateArmorBuffs();
            boolean own = human.buffManager.hasBuff(helmet.buffType);
            boolean extraOn = human.buffManager.hasBuff(extra);
            armor[0] = null;
            manager.updateArmorBuffs();
            boolean extraAfter = human.buffManager.hasBuff(extra);
            log.accept("Bonus duplo: " + helmet.getStringID() + " com o conjunto -> " + helmet.buffType + " " + (own ? "ativo" : "INATIVO")
                    + " + " + extra + " " + (extraOn ? "ativo" : "INATIVO") + "; sem o capacete o extra " + (extraAfter ? "CONTINUA" : "sai"));
            if (!own || !extraOn || extraAfter) failures.merge("bonus duplo de " + helmet.getStringID() + " nao funcionou", 1, Integer::sum);
        } catch (Throwable t) {
            fail(failures, "bonus duplo", t);
        } finally {
            human.remove();
        }
    }

    private static void testShops(Level level, float x, float y, Map<String, Integer> failures, Consumer<String> log) {
        List<String> lines = new ArrayList<>();
        // Every shop NPC must still be constructible with its swapped shop (a duplicate shop item throws in the constructor)
        int shopNPCs = 0;
        for (ClassIDDataContainer<Mob> container : MobRegistry.getMobs()) {
            try {
                if (container.newInstance() instanceof HumanShop) shopNPCs++;
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                fail(failures, "criar " + container.getIDData().getStringID(), cause);
            }
        }
        lines.add(shopNPCs + " NPCs de loja criados");
        String npc = "blacksmithhuman";
        String to = ShopRandomizer.getNpcShop(npc);
        if (to != null && !to.equals(npc)) {
            Mob mob = MobRegistry.getMob(MobRegistry.getMobID(npc), level);
            try {
                level.entityManager.addMob(mob, x, y);
                Set<String> got = new TreeSet<>();
                for (SellingShopItem item : ((HumanShop) mob).shop.sellingShop.getItems()) got.add(item.getStringID());
                Set<String> expected = new TreeSet<>();
                for (SellingShopItem item : ((HumanShop) MobRegistry.getMob(MobRegistry.getMobID(to))).shop.sellingShop.getItems()) expected.add(item.getStringID());
                lines.add(npc + " vende como " + to + " " + got);
                if (!got.equals(expected)) failures.merge(npc + " devia vender como " + to + " " + expected + ", vende " + got, 1, Integer::sum);
            } catch (Throwable t) {
                fail(failures, "loja de " + npc, t);
            } finally {
                mob.remove();
            }
        }
        if (SRS.settings().shopPrices) {
            Mob template = MobRegistry.getMob(MobRegistry.getMobID(npc));
            for (BuyingShopItem item : ((HumanShop) template).shop.buyingShop.getItems()) {
                int price = ShopRandomizer.modifyBuyingPrice(item, 100);
                float a = SRS.settings().shopPricesMin, b = SRS.settings().shopPricesMax;
                if (price < Math.floor(100 * Math.min(a, b)) - 1 || price > Math.ceil(100 * Math.max(a, b)) + 1) {
                    failures.merge("preco de " + item.getStringID() + " fora da faixa: " + price, 1, Integer::sum);
                }
                if (lines.size() < 3) lines.add(item.getStringID() + " compra por 100 -> " + price);
            }
        }
        if (!lines.isEmpty()) log.accept("Lojas: " + String.join("; ", lines));
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
