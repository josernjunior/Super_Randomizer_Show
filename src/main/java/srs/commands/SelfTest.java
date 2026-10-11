package srs.commands;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import necesse.engine.commands.CommandLog;
import necesse.engine.localization.Localization;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.BuffRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.engine.registries.ProjectileRegistry;
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
import necesse.level.maps.Level;
import necesse.level.maps.biomes.MobChance;
import necesse.level.maps.biomes.MobSpawnTable;
import srs.core.MobPools;
import srs.core.MobPools.Pool;
import srs.core.SRS;
import srs.random.MobSpawnRandomizer;
import srs.random.ProjectileRandomizer;

/**
 * /srs test - runs the randomized code paths on the server's spawn level and reports what failed.
 * Meant for diagnosing problems: it spawns and kills mobs, fires every projectile type, equips buffs on a test
 * NPC and builds armor tooltips, catching every exception.
 */
public final class SelfTest {

    private SelfTest() {
    }

    public static void run(Server server, CommandLog log) {
        if (server == null || server.world == null) {
            log.add("Sem servidor/mundo carregado.");
            return;
        }
        Level level = server.world.getLevel(server.world.worldEntity.spawnLevelIdentifier);
        Point tile = server.world.worldEntity.spawnTile;
        if (level == null || tile == null) {
            log.add("Nivel de spawn nao carregado.");
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
        log.add("Spawn natural: " + spawnChecked + " testados, " + spawnRemapped + " trocados, " + spawnWrong + " diferentes do esperado");

        // 2 - kill every randomized mob and count the drops
        int killed = 0;
        int drops = 0;
        List<String> killList = new ArrayList<>(MobPools.get(Pool.HOSTILE));
        killList.addAll(MobPools.get(Pool.BOSS));
        killList.addAll(MobPools.get(Pool.CRITTER));
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
        log.add("Mortes: " + killed + " mobs mortos, " + drops + " drops gerados");

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
            log.add("Projeteis: " + fired + " disparados, " + swapped + " trocados");

            // Every type in the pool must survive being created generically, which is what a swap does
            int targetsOk = 0;
            Projectile template = ProjectileRegistry.getProjectile(ProjectileRegistry.getProjectileID("stonearrow"), level);
            template.applyData(x, y, x + 300, y, 60.0F, 400, new GameDamage(10.0F), owner);
            for (String type : ProjectileRandomizer.getPool()) {
                Projectile p = null;
                try {
                    template.resetUniqueID(GameRandom.globalRandom);
                    p = ProjectileRandomizer.createLike(type, template, level);
                    level.entityManager.projectiles.addHidden(p);
                    for (int t = 0; t < 10 && !p.removed(); t++) p.serverTick();
                    targetsOk++;
                } catch (Throwable e) {
                    fail(failures, "substituto " + type, e);
                } finally {
                    if (p != null) p.remove();
                }
            }
            log.add("Substitutos de projetil: " + targetsOk + "/" + ProjectileRandomizer.getPool().size() + " funcionam criados de forma generica");
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
            log.add("Armaduras equipadas: " + equipped + " (" + armorDiffs + " com valor de armadura diferente do original)");
        }
        log.add("Equipamentos: " + buffs + " buffs aplicados, " + tooltips + " descricoes de armadura geradas");

        log.add("Traducoes (exemplo): item.copperbar = \"" + Localization.translate("item", "copperbar")
                + "\", mob.zombie = \"" + Localization.translate("mob", "zombie") + "\"");

        if (failures.isEmpty()) {
            log.add("Nenhuma falha encontrada.");
        } else {
            log.add(failures.size() + " falhas:");
            for (Map.Entry<String, Integer> e : failures.entrySet()) log.add("  " + e.getKey());
        }
    }

    private static void fail(Map<String, Integer> failures, String what, Throwable e) {
        String key = what + ": " + e;
        failures.merge(key, 1, Integer::sum);
        SRS.warn("selftest " + key);
    }

}
