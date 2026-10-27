package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.RidingEvents;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.player.PlayerInteractEvent;
import dev.amman.proficiency.platform.event.tick.PlayerTickEvent;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Riding in a real world with mock players: XP on a horse, a camel, a boat, a minecart and under
 * elytra, every anti-farm guard (no steering, a leash, creative, a portal step, a loop), the
 * passive on the mount, Tailwind, Full Gallop and the tree's mechanics. The arithmetic is in
 * RidingMathTest and RidingTrackerTest.
 *
 * <p>Each test is its own batch, and every player a test makes leaves the server at the end.
 */
public final class RidingGameTests {

    private static final String T = "proficiency:empty";

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public RidingGameTests() {
    }

    private static final class Cast {
        final GameTestHelper helper;
        final List<ServerPlayer> players = new ArrayList<>();

        Cast(GameTestHelper helper) {
            this.helper = helper;
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (ServerPlayer other : List.copyOf(helper.getLevel().players())) {
                if (other.position().distanceTo(origin) < 96) {
                    other.setPos(other.getX(), other.getY() + 400, other.getZ());
                }
            }
            clear();
        }

        void clear() {
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            AABB box = new AABB(origin, origin).inflate(160);
            for (Mob mob : helper.getLevel().getEntitiesOfClass(Mob.class, box)) {
                mob.discard();
            }
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
                item.discard();
            }
            for (Boat boat : helper.getLevel().getEntitiesOfClass(Boat.class, box)) {
                boat.discard();
            }
            for (Minecart cart : helper.getLevel().getEntitiesOfClass(Minecart.class, box)) {
                cart.discard();
            }
        }

        ServerPlayer player(double x, double z) {
            ServerPlayer player = MockPlayers.make(helper);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
            player.setYRot(0f);
            clearSpawnProtection(player);
            RidingEvents.forget(player.getUUID());
            players.add(player);
            return player;
        }

        void done() {
            for (ServerPlayer player : players) {
                player.stopRiding();
                ProcService.forget(player.getUUID());
                RidingEvents.forget(player.getUUID());
                player.server.getPlayerList().remove(player);
            }
            clear();
            helper.succeed();
        }
    }

    private static void clearSpawnProtection(ServerPlayer player) {
        try {
            java.lang.reflect.Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not clear spawn protection", e);
        }
    }

    private static float logged(ServerPlayer player, String source) {
        var log = SkillService.xpLog(player);
        float sum = 0f;
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == Skill.RIDING.ordinal() && source.equals(entry.source())) {
                    sum += entry.amount();
                }
            }
        }
        return sum;
    }

    private static PlayerSkills skills(ServerPlayer player) {
        return ProficiencyAttachments.of(player);
    }

    private static void rank(ServerPlayer player, String talent, int rank) {
        skills(player).setRank(Talents.get(Skill.RIDING, talent), rank);
    }

    private static void tick(ServerPlayer player) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    }

    private static void saddle(AbstractHorse horse) {
        horse.equipSaddle(new ItemStack(Items.SADDLE), null);
    }

    /** A tamed, saddled horse the player sits on. */
    private static Horse horse(GameTestHelper helper, ServerPlayer player, double x, double z) {
        Horse horse = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos((int) x, 2, (int) z));
        horse.setTamed(true);
        horse.setOwnerUUID(player.getUUID());
        saddle(horse);
        player.setPos(horse.position());
        player.startRiding(horse, true);
        return horse;
    }

    /** Moves the vehicle and the player together, as one tick of riding would. */
    private static void move(ServerPlayer player, Entity vehicle, double x, double y, double z) {
        vehicle.xo = vehicle.getX();
        vehicle.zo = vehicle.getZ();
        vehicle.setPos(x, y, z);
        player.setPos(x, y, z);
    }

    /** Rides {@code ticks} game ticks at {@code speed} blocks a tick along +x, posting the player tick each time. */
    private static void rideFor(GameTestHelper helper, ServerPlayer player, Entity vehicle, int ticks, double speed,
            boolean steer, Runnable then) {
        AtomicInteger done = new AtomicInteger();
        double x0 = vehicle.getX();
        double y = vehicle.getY();
        double z = vehicle.getZ();
        helper.onEachTick(() -> {
            int i = done.get();
            if (i >= ticks) {
                return;
            }
            done.incrementAndGet();
            player.zza = steer ? 1.0f : 0.0f;
            move(player, vehicle, x0 + speed * (i + 1), y, z);
            tick(player);
        });
        helper.runAfterDelay(ticks + 2L, then);
    }

    /**
     * One vehicle and its rider moving along +x, for the tests that change what they do half way. They
     * run in one {@link #drive}: a callback that registers more timers while the game test is
     * ticking its own timer map can crash the test server, so nothing here schedules from a callback.
     */
    private static final class Rider {
        final ServerPlayer player;
        Entity vehicle;
        double x;
        double y;
        double z;
        boolean steer = true;
        double speed = 0.4;

        Rider(ServerPlayer player, Entity vehicle) {
            this.player = player;
            this.vehicle = vehicle;
            this.x = vehicle.getX();
            this.y = vehicle.getY();
            this.z = vehicle.getZ();
        }

        void tick() {
            player.zza = steer ? 1.0f : 0.0f;
            x += speed;
            move(player, vehicle, x, y, z);
            RidingGameTests.tick(player);
        }

        void switchTo(Entity next) {
            vehicle = next;
            x = next.getX();
            y = next.getY();
            z = next.getZ();
        }
    }

    /** Calls {@code step} with 1, 2, ... {@code total} on successive game ticks, from one registered timer. */
    private static void drive(GameTestHelper helper, int total, java.util.function.IntConsumer step) {
        AtomicInteger n = new AtomicInteger();
        helper.onEachTick(() -> {
            int i = n.incrementAndGet();
            if (i <= total) {
                step.accept(i);
            }
        });
    }

    // ---- XP: mounts ---------------------------------------------------------------------------

    /** A steered horse pays by the metre, and the first ride on a kind of mount pays the first-time bonus. */
    @GameTest(template = T, batch = "riding_horse", timeoutTicks = 200)
    public static void aSteeredHorsePaysByTheMetre(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse horse = horse(helper, player, 1.5, 1.5);
        rideFor(helper, player, horse, 80, 0.4, true, () -> {
            float paid = logged(player, RidingEvents.SOURCE_MOUNT);
            helper.assertTrue(paid > 0.3f && paid < 6f, "32 steered metres on a horse paid " + paid);
            helper.assertTrue(skills(player).hasVisited("first:riding:entity.minecraft.horse"),
                    "the first horse ride did not count as a first of its kind");
            cast.done();
        });
    }

    /** A camel (any other mount, a modded one would be the same) pays at its own rate. */
    @GameTest(template = T, batch = "riding_camel", timeoutTicks = 200)
    public static void aCamelPaysToo(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Camel camel = helper.spawnWithNoFreeWill(EntityType.CAMEL, new BlockPos(1, 2, 1));
        saddle(camel);
        player.setPos(camel.position());
        player.startRiding(camel, true);
        helper.assertTrue(player.getControlledVehicle() == camel, "setup: the camel is not steered by the player");
        rideFor(helper, player, camel, 80, 0.4, true, () -> {
            helper.assertTrue(logged(player, RidingEvents.SOURCE_MOUNT) > 0.3f, "a steered camel paid nothing");
            helper.assertTrue(skills(player).hasVisited("first:riding:entity.minecraft.camel"),
                    "no first-ride bonus for the camel");
            cast.done();
        });
    }

    /** No key held, no XP: a mount that drifts or is dragged is not a ride. */
    @GameTest(template = T, batch = "riding_afk", timeoutTicks = 200)
    public static void anUnsteeredHorsePaysNothing(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse horse = horse(helper, player, 1.5, 1.5);
        rideFor(helper, player, horse, 80, 0.4, false, () -> {
            helper.assertTrue(logged(player, RidingEvents.SOURCE_MOUNT) == 0f,
                    "an unsteered ride paid " + logged(player, RidingEvents.SOURCE_MOUNT));
            cast.done();
        });
    }

    /** A horse on a lead pays nothing, however you steer. */
    @GameTest(template = T, batch = "riding_leash", timeoutTicks = 200)
    public static void aLeashedMountPaysNothing(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse horse = horse(helper, player, 1.5, 1.5);
        Cow post = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 2, 3));
        horse.setLeashedTo(post, true);
        helper.assertTrue(horse.isLeashed(), "setup: the horse is not leashed");
        Rider rider = new Rider(player, horse);
        drive(helper, 82, i -> {
            if (i <= 80) {
                // The holder walks along and the lead is tied on again each tick, so the horse is
                // leashed whenever the ride is looked at (a vanilla lead can snap or be dropped).
                post.setPos(rider.x + 1.0, rider.y, rider.z + 1.0);
                horse.setLeashedTo(post, false);
                rider.tick();
            } else {
                helper.assertTrue(logged(player, RidingEvents.SOURCE_MOUNT) == 0f, "a leash-dragged ride paid");
                cast.done();
            }
        });
    }

    /** Survival only: creative riding pays nothing. */
    @GameTest(template = T, batch = "riding_creative", timeoutTicks = 200)
    public static void creativeRidingPaysNothing(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse horse = horse(helper, player, 1.5, 1.5);
        player.setGameMode(GameType.CREATIVE);
        rideFor(helper, player, horse, 80, 0.4, true, () -> {
            helper.assertTrue(logged(player, RidingEvents.SOURCE_MOUNT) == 0f, "a creative ride paid");
            cast.done();
        });
    }

    /** A 12 block step in one tick is a portal or a teleport: skipped, and riding on from there pays again. */
    @GameTest(template = T, batch = "riding_portal", timeoutTicks = 300)
    public static void aPortalStepIsSkipped(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse horse = horse(helper, player, 1.5, 1.5);
        Rider rider = new Rider(player, horse);
        float[] before = new float[1];
        drive(helper, 130, i -> {
            if (i <= 60 || (i > 61 && i <= 121)) {
                rider.tick();
            } else if (i == 61) {
                before[0] = logged(player, RidingEvents.SOURCE_MOUNT);
                helper.assertTrue(before[0] > 0f, "setup: the first stretch paid nothing");
                rider.x += 12;
                rider.tick();
                helper.assertTrue(logged(player, RidingEvents.SOURCE_MOUNT) == before[0],
                        "a 12 block step paid " + (logged(player, RidingEvents.SOURCE_MOUNT) - before[0]));
            } else if (i == 125) {
                helper.assertTrue(logged(player, RidingEvents.SOURCE_MOUNT) > before[0], "riding on from there paid nothing");
                cast.done();
            }
        });
    }

    /** Going round the same loop pays the first lap and then nothing. */
    @GameTest(template = T, batch = "riding_loop", timeoutTicks = 500)
    public static void aLoopPaysOnlyItsFirstLap(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse horse = horse(helper, player, 1.5, 1.5);
        double cx = horse.getX();
        double cz = horse.getZ() + 6;
        double y = horse.getY();
        AtomicInteger i = new AtomicInteger();
        float[] atLapEnd = new float[1];
        helper.onEachTick(() -> {
            int n = i.incrementAndGet();
            if (n > 420) {
                return;
            }
            double angle = n * 0.4 / 6.0 - Math.PI / 2;
            player.zza = 1.0f;
            move(player, horse, cx + 6 * Math.cos(angle), y, cz + 6 * Math.sin(angle));
            tick(player);
            if (n == 140) {
                atLapEnd[0] = logged(player, RidingEvents.SOURCE_MOUNT);
            }
        });
        helper.runAfterDelay(430, () -> {
            helper.assertTrue(atLapEnd[0] > 0f, "the first lap paid nothing");
            helper.assertTrue(logged(player, RidingEvents.SOURCE_MOUNT) == atLapEnd[0],
                    "the same loop paid again: " + atLapEnd[0] + " then " + logged(player, RidingEvents.SOURCE_MOUNT));
            cast.done();
        });
    }

    // ---- XP: boats and minecarts ---------------------------------------------------------------

    /** A boat has to be steered; a minecart is not asked to be. */
    @GameTest(template = T, batch = "riding_boat", timeoutTicks = 400)
    public static void aBoatNeedsSteeringAndAMinecartDoesNot(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(1, 2, 1));
        player.setPos(boat.position());
        player.startRiding(boat, true);
        Rider rider = new Rider(player, boat);
        rider.steer = false;
        Minecart cart = helper.spawn(EntityType.MINECART, new BlockPos(1, 2, 24));
        drive(helper, 290, i -> {
            if (i <= 80) {
                rider.tick();
            } else if (i == 81) {
                helper.assertTrue(logged(player, RidingEvents.SOURCE_BOAT) == 0f, "a drifting boat paid");
                rider.steer = true;
                rider.tick();
            } else if (i <= 160) {
                rider.tick();
            } else if (i == 161) {
                helper.assertTrue(logged(player, RidingEvents.SOURCE_BOAT) > 0f, "a steered boat paid nothing");
                player.stopRiding();
                // The cart waits 24 blocks off, on ground the boat never covered.
                player.setPos(cart.position());
                player.startRiding(cart, true);
                rider.switchTo(cart);
                rider.steer = false;
            } else if (i <= 285) {
                rider.tick();
            } else if (i == 288) {
                helper.assertTrue(logged(player, RidingEvents.SOURCE_MINECART) > 0f, "a minecart ride paid nothing");
                cast.done();
            }
        });
    }

    // ---- XP and wear: elytra -------------------------------------------------------------------

    private static void glide(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        player.setPos(player.getX(), player.getY() + 12, player.getZ());
        player.startFallFlying();
    }

    /** A glide pays by the metre. */
    @GameTest(template = T, batch = "riding_elytra", timeoutTicks = 300)
    public static void aGlidePaysByTheMetre(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        glide(player);
        helper.assertTrue(player.isFallFlying(), "setup: not gliding");
        AtomicInteger i = new AtomicInteger();
        double x0 = player.getX();
        helper.onEachTick(() -> {
            int n = i.incrementAndGet();
            if (n <= 140) {
                player.setPos(x0 + n * 1.0, player.getY(), player.getZ());
                tick(player);
            }
        });
        helper.runAfterDelay(150, () -> {
            float paid = logged(player, RidingEvents.SOURCE_ELYTRA);
            helper.assertTrue(paid > 0.4f && paid < 8f, "140 metres of glide paid " + paid);
            helper.assertTrue(skills(player).hasVisited("first:riding:item.minecraft.elytra"),
                    "no first-flight bonus");
            cast.done();
        });
    }

    /** At level 100 about 45% of the wear points a glide takes are given back; level 0 gives none back. */
    @GameTest(template = T, batch = "riding_wear", timeoutTicks = 300)
    public static void anElytraWearsSlowerWithYourLevel(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        glide(player);
        AtomicInteger i = new AtomicInteger();
        int[] worn = new int[2];
        helper.onEachTick(() -> {
            int n = i.incrementAndGet();
            if (n == 1) {
                tick(player); // seeds the damage the watch compares with
            }
            if (n >= 2 && n <= 101) {
                ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
                chest.setDamageValue(chest.getDamageValue() + 1);
                tick(player);
            }
            if (n == 101) {
                worn[0] = player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
                skills(player).setLevel(Skill.RIDING, 100);
                player.getItemBySlot(EquipmentSlot.CHEST).setDamageValue(0);
                tick(player);
            }
            if (n >= 102 && n <= 201) {
                ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
                chest.setDamageValue(chest.getDamageValue() + 1);
                tick(player);
            }
            if (n == 201) {
                worn[1] = player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
            }
        });
        helper.runAfterDelay(210, () -> {
            helper.assertTrue(worn[0] == 100, "level 0 spared wear: " + worn[0] + " of 100");
            helper.assertTrue(worn[1] < 85 && worn[1] > 25, "level 100 wore " + worn[1] + " of 100 points");
            cast.done();
        });
    }

    // ---- The passive ---------------------------------------------------------------------------

    private static double modifier(AttributeInstance attribute, String path) {
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("proficiency", path);
        var found = attribute == null ? null : attribute.getModifier(id);
        return found == null ? 0.0 : found.amount();
    }

    /** The mount runs faster and jumps higher while you steer it, and the modifiers are gone when you get off. */
    @GameTest(template = T, batch = "riding_passive")
    public static void theMountIsFasterWhileRiddenAndPlainAfter(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        skills(player).setLevel(Skill.RIDING, 100);
        Horse horse = horse(helper, player, 1.5, 1.5);
        double speedBase = horse.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double jumpBase = horse.getAttributeValue(Attributes.JUMP_STRENGTH);
        player.zza = 1.0f;
        tick(player);
        helper.assertTrue(Math.abs(modifier(horse.getAttribute(Attributes.MOVEMENT_SPEED), "riding_speed") - 0.30) < 0.02,
                "speed modifier " + modifier(horse.getAttribute(Attributes.MOVEMENT_SPEED), "riding_speed"));
        helper.assertTrue(Math.abs(modifier(horse.getAttribute(Attributes.JUMP_STRENGTH), "riding_jump") - 0.15) < 0.02,
                "jump modifier " + modifier(horse.getAttribute(Attributes.JUMP_STRENGTH), "riding_jump"));
        helper.assertTrue(horse.getAttributeValue(Attributes.MOVEMENT_SPEED) > speedBase * 1.25
                && horse.getAttributeValue(Attributes.JUMP_STRENGTH) > jumpBase * 1.1, "the mount is not faster");
        player.stopRiding();
        tick(player);
        helper.assertTrue(modifier(horse.getAttribute(Attributes.MOVEMENT_SPEED), "riding_speed") == 0.0
                        && modifier(horse.getAttribute(Attributes.JUMP_STRENGTH), "riding_jump") == 0.0,
                "the modifiers stayed after the dismount");
        helper.assertTrue(Math.abs(horse.getAttributeValue(Attributes.MOVEMENT_SPEED) - speedBase) < 1e-9,
                "the mount's speed did not return to plain");
        cast.done();
    }

    /** A passenger who does not steer, and a rider of an unsaddled mount, give it nothing. */
    @GameTest(template = T, batch = "riding_passive_other")
    public static void aMountNobodyStersGetsNothing(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        skills(player).setLevel(Skill.RIDING, 100);
        Horse horse = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(1, 2, 1));
        horse.setTamed(true);
        player.setPos(horse.position());
        player.startRiding(horse, true);
        helper.assertTrue(player.getControlledVehicle() == null, "setup: an unsaddled horse is steered");
        tick(player);
        helper.assertTrue(modifier(horse.getAttribute(Attributes.MOVEMENT_SPEED), "riding_speed") == 0.0,
                "an unsaddled horse got the passive");
        cast.done();
    }

    // ---- Tailwind and Full Gallop --------------------------------------------------------------

    /** Tailwind: Speed and Jump Boost on a mount, a push in flight, regeneration in a boat. */
    @GameTest(template = T, batch = "riding_tailwind")
    public static void tailwindFitsTheRide(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse horse = horse(helper, player, 1.5, 1.5);
        RidingEvents.tailwind(player, horse, false);
        helper.assertTrue(horse.hasEffect(MobEffects.MOVEMENT_SPEED) && horse.hasEffect(MobEffects.JUMP),
                "the mount did not get Speed and Jump Boost");
        helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED), "the rider got the mount's Speed");
        player.stopRiding();

        glide(player);
        player.setYRot(0f);
        player.setXRot(0f);
        Vec3 before = player.getDeltaMovement();
        RidingEvents.tailwind(player, null, true);
        helper.assertTrue(player.getDeltaMovement().subtract(before).length() > 0.3, "elytra got no push");

        Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(3, 2, 3));
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        RidingEvents.tailwind(player, boat, false);
        helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "a boat ride did not refresh the rider");
        cast.done();
    }

    /** Full Gallop: your mount and your tamed horses nearby run and jump; a stranger's horse does not. */
    @GameTest(template = T, batch = "riding_gallop")
    public static void fullGallopSpeedsYourHorses(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        PlayerSkills skills = skills(player);
        skills.setLevel(Skill.RIDING, 100);
        skills.fillTree(Skill.RIDING);
        Horse mine = horse(helper, player, 1.5, 1.5);
        Horse spare = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(4, 2, 4));
        spare.setTamed(true);
        spare.setOwnerUUID(player.getUUID());
        Horse stranger = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(5, 2, 4));
        stranger.setTamed(true);
        helper.assertTrue(ActiveService.activate(player, Skill.RIDING) == null, "Full Gallop would not start");
        helper.assertTrue(mine.hasEffect(MobEffects.MOVEMENT_SPEED) && mine.hasEffect(MobEffects.JUMP),
                "the mount you steer is not galloping");
        helper.assertTrue(spare.hasEffect(MobEffects.MOVEMENT_SPEED), "your other horse is not galloping");
        helper.assertTrue(!stranger.hasEffect(MobEffects.MOVEMENT_SPEED), "a stranger's horse galloped");
        helper.assertTrue(ActiveService.isFrenzied(player, Skill.RIDING), "Tailwind is not on every roll");
        cast.done();
    }

    // ---- The tree ------------------------------------------------------------------------------

    private static Zombie zombie(GameTestHelper helper, double x, double z) {
        Zombie mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos((int) x, 2, (int) z));
        var armour = mob.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.setBaseValue(0);
        }
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        mob.setHealth(400f);
        return mob;
    }

    private static float punch(ServerPlayer player, Zombie target, float amount) {
        target.invulnerableTime = 0;
        float before = target.getHealth();
        target.hurt(target.damageSources().playerAttack(player), amount);
        return before - target.getHealth();
    }

    /** Couched Lance: hits from the saddle hit harder, and harder again at a gallop; on foot nothing changes. */
    @GameTest(template = T, batch = "riding_lance")
    public static void aCouchedLanceHitsHarderFromTheSaddle(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Zombie plain = zombie(helper, 6, 6);
        float base = punch(player, plain, 10f);
        helper.assertTrue(Math.abs(base - 10f) < 0.2f, "setup: a plain punch did " + base);

        rank(player, "couched_lance", 3);
        float onFoot = punch(player, zombie(helper, 7, 6), 10f);
        helper.assertTrue(Math.abs(onFoot - base) < 0.2f, "the lance worked on foot: " + onFoot);

        Horse horse = horse(helper, player, 1.5, 1.5);
        tick(player); // seeds the speed watch
        tick(player);
        float slow = punch(player, zombie(helper, 8, 6), 10f);
        helper.assertTrue(Math.abs(slow - base * 1.18f) < 0.3f, "a mounted hit did " + slow + ", expected " + base * 1.18f);

        move(player, horse, horse.getX() + 0.5, horse.getY(), horse.getZ());
        tick(player);
        float fast = punch(player, zombie(helper, 9, 6), 10f);
        helper.assertTrue(Math.abs(fast - base * 1.36f) < 0.3f, "a galloping hit did " + fast + ", expected " + base * 1.36f);
        cast.done();
    }

    /** The lance is for a rider who steers a living mount: a passenger in a boat gets nothing. */
    @GameTest(template = T, batch = "riding_lance_passenger")
    public static void aPassengerGetsNoLance(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        float base = punch(player, zombie(helper, 6, 6), 10f);
        rank(player, "couched_lance", 3);
        Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(1, 2, 1));
        player.setPos(boat.position());
        player.startRiding(boat, true);
        tick(player);
        float inBoat = punch(player, zombie(helper, 7, 6), 10f);
        helper.assertTrue(Math.abs(inBoat - base) < 0.2f, "the lance worked from a boat: " + inBoat);
        cast.done();
    }

    /** The Whistle refuses mid-glide: there is no ground to put the horse on. */
    @GameTest(template = T, batch = "riding_whistle_glide")
    public static void theWhistleRefusesInTheAir(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse mine = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(1, 2, 1));
        mine.setTamed(true);
        mine.setOwnerUUID(player.getUUID());
        mine.setPos(player.getX() + 20, player.getY(), player.getZ());
        rank(player, "whistle", 1);
        glide(player);
        horn(player);
        helper.assertTrue(mine.distanceTo(player) > 15, "the horse was called into the air: " + mine.distanceTo(player));
        cast.done();
    }

    /** Barding: a mount you steer takes less damage; an unridden one and one hurt by its own rider take the full amount. */
    @GameTest(template = T, batch = "riding_barding")
    public static void bardingSparesTheMount(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse horse = horse(helper, player, 1.5, 1.5);
        horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        horse.setHealth(100f);
        horse.invulnerableTime = 0;
        horse.hurt(horse.damageSources().generic(), 10f);
        float plain = 100f - horse.getHealth();
        helper.assertTrue(Math.abs(plain - 10f) < 0.1f, "setup: plain damage " + plain);

        rank(player, "barding", 3);
        horse.setHealth(100f);
        horse.invulnerableTime = 0;
        horse.hurt(horse.damageSources().generic(), 10f);
        float armoured = 100f - horse.getHealth();
        helper.assertTrue(Math.abs(armoured - 7.6f) < 0.1f, "a barded mount took " + armoured);

        horse.setHealth(100f);
        horse.invulnerableTime = 0;
        horse.hurt(horse.damageSources().playerAttack(player), 10f);
        helper.assertTrue(Math.abs((100f - horse.getHealth()) - 10f) < 0.1f, "barding saved the horse from its own rider");

        player.stopRiding();
        horse.setHealth(100f);
        horse.invulnerableTime = 0;
        horse.hurt(horse.damageSources().generic(), 10f);
        helper.assertTrue(Math.abs((100f - horse.getHealth()) - 10f) < 0.1f, "barding worked on a mount nobody rides");
        cast.done();
    }

    /** Saddle Archer: an arrow loosed from a mount hits harder; one loosed on foot does not. */
    @GameTest(template = T, batch = "riding_archer")
    public static void anArrowFromTheSaddleHitsHarder(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        rank(player, "saddle_archer", 3);
        Arrow onFoot = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), null);
        helper.getLevel().addFreshEntity(onFoot);
        Zombie first = zombie(helper, 6, 6);
        first.invulnerableTime = 0;
        float before = first.getHealth();
        first.hurt(first.damageSources().arrow(onFoot, player), 10f);
        float plain = before - first.getHealth();

        horse(helper, player, 1.5, 1.5);
        Arrow mounted = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), null);
        helper.getLevel().addFreshEntity(mounted);
        Zombie second = zombie(helper, 7, 6);
        second.invulnerableTime = 0;
        before = second.getHealth();
        second.hurt(second.damageSources().arrow(mounted, player), 10f);
        float boosted = before - second.getHealth();
        helper.assertTrue(Math.abs(boosted - plain * 1.24f) < 0.3f,
                "a mounted arrow did " + boosted + ", expected " + plain * 1.24f + " (plain " + plain + ")");
        cast.done();
    }

    private static void horn(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOAT_HORN));
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
    }

    /** The Whistle: a goat horn calls your free tamed horse from afar, once per cooldown; not a stranger's, not a ridden one. */
    @GameTest(template = T, batch = "riding_whistle")
    public static void aGoatHornCallsYourHorse(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse mine = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(1, 2, 1));
        mine.setTamed(true);
        mine.setOwnerUUID(player.getUUID());
        mine.setPos(player.getX() + 20, player.getY(), player.getZ());
        Horse stranger = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(1, 2, 2));
        stranger.setTamed(true);
        stranger.setPos(player.getX(), player.getY(), player.getZ() + 14);

        horn(player);
        helper.assertTrue(mine.distanceTo(player) > 15, "the horn called a horse with no talent");

        rank(player, "whistle", 1);
        horn(player);
        helper.assertTrue(mine.distanceTo(player) < 3.5, "the horn did not bring your horse: " + mine.distanceTo(player));
        helper.assertTrue(stranger.distanceTo(player) > 10, "a stranger's horse came");

        mine.setPos(player.getX() + 20, player.getY(), player.getZ());
        horn(player);
        helper.assertTrue(mine.distanceTo(player) > 15, "the horn worked twice inside its cooldown");

        // A leashed horse stays where it is.
        mine.setPos(player.getX() + 15, player.getY(), player.getZ() + 5);
        RidingEvents.forget(player.getUUID());
        mine.setLeashedTo(helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 3)), true);
        horn(player);
        helper.assertTrue(mine.distanceTo(player) > 10, "a leashed horse was called");
        cast.done();
    }

    /** Rocket Saver: some rockets used in flight come back; none at rank 0. */
    @GameTest(template = T, batch = "riding_rocket", timeoutTicks = 300)
    public static void someRocketsAreNotUsedUp(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        glide(player);
        rank(player, "rocket_saver", 3);
        AtomicInteger i = new AtomicInteger();
        int[] saved = new int[1];
        int[] attempts = new int[1];
        helper.onEachTick(() -> {
            int n = i.incrementAndGet();
            if (n > 120) {
                return;
            }
            if (n % 2 == 1) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FIREWORK_ROCKET, 4));
                NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
                // What vanilla does with the rocket on the same use.
                player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
                attempts[0]++;
            } else {
                tick(player);
                if (player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 4) {
                    saved[0]++;
                }
            }
        });
        helper.runAfterDelay(130, () -> {
            helper.assertTrue(attempts[0] >= 55, "setup: only " + attempts[0] + " uses");
            helper.assertTrue(saved[0] >= 10 && saved[0] <= 45,
                    saved[0] + " of " + attempts[0] + " rockets came back (45% expected)");
            cast.done();
        });
    }

    /** Soft Landing: after you leave a mount the fall hurts nothing for a moment; without the talent it does. */
    @GameTest(template = T, batch = "riding_soft")
    public static void leavingAMountSoftensTheLanding(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        Horse horse = horse(helper, player, 1.5, 1.5);
        tick(player);
        player.stopRiding();
        tick(player);
        float full = player.getHealth();
        player.causeFallDamage(12f, 1.0f, player.damageSources().fall());
        helper.assertTrue(player.getHealth() < full, "setup: a 12 block fall did no damage without the talent");

        player.setHealth(20f);
        RidingEvents.forget(player.getUUID());
        rank(player, "soft_landing", 1);
        player.startRiding(horse, true);
        tick(player);
        player.stopRiding();
        tick(player);
        player.causeFallDamage(12f, 1.0f, player.damageSources().fall());
        helper.assertTrue(player.getHealth() == 20f, "the landing hurt after a dismount: " + player.getHealth());
        cast.done();
    }

    /** Stampede: a mount at a gallop tramples what it runs into, once a second per mob; a walking one does not. */
    @GameTest(template = T, batch = "riding_trample")
    public static void aGallopingMountTramplesHostiles(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        skills(player).setLevel(Skill.RIDING, 100);
        rank(player, "stampede", 1);
        Horse horse = horse(helper, player, 1.5, 1.5);
        Zombie zombie = zombie(helper, 2, 2);
        zombie.setPos(horse.getX() + 1.0, horse.getY(), horse.getZ());
        player.tickCount = 10;

        tick(player); // seeds the speed watch
        move(player, horse, horse.getX() + 0.1, horse.getY(), horse.getZ());
        zombie.setPos(horse.getX() + 1.0, horse.getY(), horse.getZ());
        tick(player);
        helper.assertTrue(zombie.getHealth() == 400f, "a walking mount trampled");

        move(player, horse, horse.getX() + 0.5, horse.getY(), horse.getZ());
        zombie.setPos(horse.getX() + 1.0, horse.getY(), horse.getZ());
        float before = zombie.getHealth();
        zombie.invulnerableTime = 0;
        tick(player);
        float hit = before - zombie.getHealth();
        helper.assertTrue(Math.abs(hit - 8f) < 0.6f, "a trample did " + hit + ", expected 8");
        zombie.invulnerableTime = 0;
        move(player, horse, horse.getX() + 0.5, horse.getY(), horse.getZ());
        zombie.setPos(horse.getX() + 1.0, horse.getY(), horse.getZ());
        tick(player);
        helper.assertTrue(Math.abs((before - zombie.getHealth()) - hit) < 0.01f, "a mob was trampled twice in one second");
        helper.assertTrue(logged(player, RidingEvents.SOURCE_MOUNT) == 0f, "a trample paid Riding XP");
        cast.done();
    }

    /** High Seat: reach grows while mounted and is gone on the ground. */
    @GameTest(template = T, batch = "riding_seat")
    public static void aHighSeatReachesFurther(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(1.5, 1.5);
        rank(player, "high_seat", 3);
        Horse horse = horse(helper, player, 1.5, 1.5);
        tick(player);
        helper.assertTrue(Math.abs(modifier(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), "riding_high_seat") - 1.5) < 1e-9,
                "no block reach from the saddle");
        helper.assertTrue(Math.abs(modifier(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), "riding_high_seat") - 1.5) < 1e-9,
                "no attack reach from the saddle");
        player.stopRiding();
        tick(player);
        helper.assertTrue(modifier(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), "riding_high_seat") == 0.0
                && modifier(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), "riding_high_seat") == 0.0,
                "the reach stayed on the ground");
        helper.assertTrue(horse.isAlive(), "setup");
        cast.done();
    }
}
