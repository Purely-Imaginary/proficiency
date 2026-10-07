package dev.amman.proficiency.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.perk.Talents;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One player's twenty skills. Lives as a NeoForge data attachment on the player, so it rides along
 * with the playerdata file and needs no world save format of its own.
 */
public final class PlayerSkills {

    public record SkillState(int level, float xp) {
        public static final Codec<SkillState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("level").forGetter(SkillState::level),
                Codec.FLOAT.fieldOf("xp").forGetter(SkillState::xp)
        ).apply(i, SkillState::new));
    }

    public static final Codec<PlayerSkills> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(Codec.STRING, SkillState.CODEC)
                            .optionalFieldOf("skills", Map.of())
                            .forGetter(PlayerSkills::toMap),
                    Codec.unboundedMap(Codec.STRING, Codec.INT)
                            .optionalFieldOf("talents", Map.of())
                            .forGetter(skills -> Map.copyOf(skills.ranks)),
                    Codec.STRING.listOf()
                            .optionalFieldOf("paid", List.of())
                            .forGetter(skills -> List.copyOf(skills.paid)),
                    Codec.unboundedMap(Codec.STRING, Codec.LONG)
                            .optionalFieldOf("cooldowns", Map.of())
                            .forGetter(PlayerSkills::cooldownMap),
                    Codec.STRING.listOf()
                            .optionalFieldOf("visited", List.of())
                            .forGetter(skills -> List.copyOf(skills.visited)),
                    Codec.BOOL.optionalFieldOf("onboarded", false)
                            .forGetter(skills -> skills.onboarded),
                    Codec.LONG.optionalFieldOf("streak", 0L)
                            .forGetter(skills -> skills.streakTicks),
                    Codec.BOOL.optionalFieldOf("xpFeed", false)
                            .forGetter(skills -> skills.xpFeed),
                    Codec.LONG.optionalFieldOf("buildDay", 0L)
                            .forGetter(skills -> skills.buildDay),
                    Codec.unboundedMap(Codec.STRING, Codec.INT)
                            .optionalFieldOf("buildFirst", Map.of())
                            .forGetter(skills -> Map.copyOf(skills.buildFirst))
            ).apply(instance, PlayerSkills::fromParts));

    public static final StreamCodec<FriendlyByteBuf, PlayerSkills> STREAM_CODEC = StreamCodec.of(
            (buf, skills) -> {
                for (Skill skill : Skill.VALUES) {
                    buf.writeVarInt(skills.levels[skill.ordinal()]);
                    buf.writeFloat(skills.xp[skill.ordinal()]);
                }
                buf.writeVarInt(skills.ranks.size());
                skills.ranks.forEach((key, rank) -> {
                    buf.writeUtf(key);
                    buf.writeVarInt(rank);
                });
                buf.writeVarInt(skills.paid.size());
                for (String key : skills.paid) {
                    buf.writeUtf(key);
                }
                for (Skill skill : Skill.VALUES) {
                    buf.writeVarLong(skills.cooldownUntil[skill.ordinal()]);
                }
                buf.writeVarLong(skills.streakTicks);
            },
            buf -> {
                PlayerSkills skills = new PlayerSkills();
                for (Skill skill : Skill.VALUES) {
                    skills.levels[skill.ordinal()] = buf.readVarInt();
                    skills.xp[skill.ordinal()] = buf.readFloat();
                }
                int rankCount = buf.readVarInt();
                for (int i = 0; i < rankCount; i++) {
                    skills.ranks.put(buf.readUtf(), buf.readVarInt());
                }
                int paidCount = buf.readVarInt();
                for (int i = 0; i < paidCount; i++) {
                    skills.paid.add(buf.readUtf());
                }
                for (Skill skill : Skill.VALUES) {
                    skills.cooldownUntil[skill.ordinal()] = buf.readVarLong();
                }
                skills.streakTicks = buf.readVarLong();
                return skills;
            });

    private final int[] levels = new int[Skill.VALUES.length];
    private final float[] xp = new float[Skill.VALUES.length];

    /**
     * Talent key to rank, e.g. {@code woodcutting/precision -> 3}. Only non-zero ranks are kept.
     * Permanent: dying costs progress, never levels or talents.
     */
    private final Map<String, Integer> ranks = new HashMap<>();

    /** Talents whose materials have been handed over once. Survives a respec on purpose. */
    private final Set<String> paid = new HashSet<>();

    /** Whether this player has ever been told the system exists. */
    private boolean onboarded;

    /**
     * Opt-in debug feed: every XP gain listed down the left of the screen. Off by default, turned
     * on with {@code /skills xpfeed}. Saved, so it survives a relog, but not sent in the sync
     * packet: the client only needs the feed itself, and the sync format stays as it was.
     */
    private boolean xpFeed;

    /**
     * The real day (epoch day, server local date) that {@link #buildFirst} counts, and how many
     * first-time bonuses each build skill paid that day. Saved so a relog does not refill the
     * cap, and not sent in the sync packet: the client never needs it.
     */
    private long buildDay;
    private final Map<String, Integer> buildFirst = new HashMap<>();

    /** Biome and dimension ids already seen, which is what Wayfaring is paid for. */
    private final Set<String> visited = new HashSet<>();

    /** Game time an active ability comes off cooldown. Persisted, so relogging is not a reset. */
    private final long[] cooldownUntil = new long[Skill.VALUES.length];

    /** Game time a frenzy ends. Deliberately not persisted: twenty seconds does not survive a relog. */
    private final transient long[] frenzyUntil = new long[Skill.VALUES.length];

    /**
     * Survival streak: active ticks lived since the last death. Persisted and synced, so a relog
     * pauses it and the client can draw it. Only a death zeroes it.
     */
    private long streakTicks;

    /** Game time of the last XP grant, which is what "active" means. A relog has to earn it again. */
    private transient long lastActiveAt = Long.MIN_VALUE;

    /** Not serialised: set when something changed and the client has not been told yet. */
    private transient boolean dirty;

    public PlayerSkills() {
    }

    public int level(Skill skill) {
        return levels[skill.ordinal()];
    }

    public float xp(Skill skill) {
        return xp[skill.ordinal()];
    }

    public float progress(Skill skill) {
        return SkillMath.progress(level(skill), xp(skill));
    }

    /** The passive, with any unlocked perks folded in. Identical on both sides: perks are synced. */
    public double bonus(Skill skill) {
        return bonusAt(skill, level(skill));
    }

    /** The passive this player would have at {@code level}, with the talents they own now. */
    public double bonusAt(Skill skill, int level) {
        return SkillMath.bonus(skill, level) * perkModifier(skill, PerkEffect.BONUS);
    }

    public double procChance(Skill skill) {
        return procChanceAt(skill, level(skill));
    }

    /**
     * The proc chance at {@code level} with the talents owned now. The world-dependent synergies
     * (Tidecaller in water, Deep Delver below 0, Charger's sprint) multiply on top in
     * {@code ProcService} at roll time.
     */
    public double procChanceAt(Skill skill, int level) {
        return SkillMath.procChance(skill, level) * perkModifier(skill, PerkEffect.PROC_CHANCE);
    }

    /** How hard the signature proc lands: a multiplier, 1.0 with no talents. */
    public double procPower(Skill skill) {
        return perkModifier(skill, PerkEffect.PROC_POWER);
    }

    /**
     * One talent-driven multiplier taken apart: this tree's ranks ({@code 1 + sum}), the active
     * synergies, Discipline (the passive only, 1.0 otherwise), and the clamped product that the
     * game uses. The tree screen shows the parts; everything else reads {@link #perkModifier}.
     */
    public record Modifier(double ranks, double synergies, double discipline, double total) {
    }

    public Modifier modifier(Skill skill, PerkEffect effect) {
        double ranks = 1.0 + rankSum(skill, effect);
        double synergies = Synergies.multiplier(this, skill, effect);
        double discipline = effect == PerkEffect.BONUS
                ? 1.0 + Synergies.discipline(this, skill.category()) : 1.0;
        double raw = ranks * synergies * discipline;
        double total = effect == PerkEffect.ABILITY_COOLDOWN ? Math.max(0.25, raw) : Math.max(0.0, raw);
        return new Modifier(ranks, synergies, discipline, total);
    }

    /**
     * Everything the trees do to one number: this tree's ranks summed, then every active synergy
     * multiplied on top, then Discipline for the passive. {@link PerkEffect#DEATH_WARD} is not a
     * multiplier and is read through {@link #deathWard} instead.
     */
    public double perkModifier(Skill skill, PerkEffect effect) {
        return modifier(skill, effect).total();
    }

    /** Sum of one effect over this tree's ranks, before any synergy. */
    public double rankSum(Skill skill, PerkEffect effect) {
        double sum = 0.0;
        for (Talent talent : Talents.of(skill)) {
            int rank = rank(talent);
            if (rank > 0) {
                Double perRank = talent.perRank().get(effect);
                if (perRank != null) {
                    sum += perRank * rank;
                }
            }
        }
        return sum;
    }

    /** Share of this skill's progress bar that survives a death, 0 to 1. */
    public double deathWard(Skill skill) {
        return Math.max(0.0, Math.min(1.0, rankSum(skill, PerkEffect.DEATH_WARD)));
    }

    /** Returns true the first time a place is seen, which is the only time it is worth XP. */
    public boolean markVisited(String id) {
        if (visited.add(id)) {
            dirty = true;
            return true;
        }
        return false;
    }

    /**
     * Takes one of today's first-time slots for a build skill. False when the cap is used up, and
     * then nothing is counted, so the caller must not mark the kind visited.
     *
     * @param day the epoch day; a different day than the stored one starts every count at zero
     */
    public boolean takeBuildFirstTime(String skillId, long day, int cap) {
        if (day != buildDay) {
            buildDay = day;
            buildFirst.clear();
            dirty = true;
        }
        int used = buildFirst.getOrDefault(skillId, 0);
        if (used >= cap) {
            return false;
        }
        buildFirst.put(skillId, used + 1);
        dirty = true;
        return true;
    }

    public boolean xpFeed() {
        return xpFeed;
    }

    public void setXpFeed(boolean on) {
        if (xpFeed != on) {
            xpFeed = on;
            dirty = true;
        }
    }

    /** True exactly once per player, ever. */
    public boolean needsOnboarding() {
        if (onboarded) {
            return false;
        }
        onboarded = true;
        dirty = true;
        return true;
    }

    public boolean hasVisited(String id) {
        return visited.contains(id);
    }

    /**
     * Whether any dimension or biome was ever marked. The spawn rule reads this, not
     * {@link #placesSeen()}, which also counts first-time kinds and structures: a player who earned
     * one of those before the first check would otherwise be paid for the place they spawned in.
     */
    public boolean hasAnyPlace() {
        for (String key : visited) {
            if (key.startsWith("dim:") || key.startsWith("biome:")) {
                return true;
            }
        }
        return false;
    }

    /** A read-only view of every visited key, for the journal sync. */
    public Set<String> visitedKeys() {
        return java.util.Collections.unmodifiableSet(visited);
    }

    public int placesSeen() {
        return visited.size();
    }

    public long cooldownRemaining(Skill skill, long now) {
        return Math.max(0L, cooldownUntil[skill.ordinal()] - now);
    }

    public long frenzyRemaining(Skill skill, long now) {
        return Math.max(0L, frenzyUntil[skill.ordinal()] - now);
    }

    /** Surge: a proc pulls the cooldown in. Never below now, so it cannot bank negative time. */
    public void shortenCooldown(Skill skill, long ticks, long now) {
        int index = skill.ordinal();
        if (cooldownUntil[index] > now) {
            cooldownUntil[index] = Math.max(now, cooldownUntil[index] - ticks);
            dirty = true;
        }
    }

    public void beginFrenzy(Skill skill, long frenzyEnd, long cooldownEnd) {
        frenzyUntil[skill.ordinal()] = frenzyEnd;
        cooldownUntil[skill.ordinal()] = cooldownEnd;
        dirty = true;
    }

    private Map<String, Long> cooldownMap() {
        Map<String, Long> map = new HashMap<>();
        for (Skill skill : Skill.VALUES) {
            if (cooldownUntil[skill.ordinal()] > 0) {
                map.put(skill.id(), cooldownUntil[skill.ordinal()]);
            }
        }
        return map;
    }

    public long streakTicks() {
        return streakTicks;
    }

    /** Whole stacks earned, capped. A step or cap of zero means the streak is switched off. */
    public int streakStacks(long stepTicks, int maxStacks) {
        if (stepTicks <= 0 || maxStacks <= 0) {
            return 0;
        }
        return (int) Math.min(maxStacks, streakTicks / stepTicks);
    }

    /** Called by every XP grant. The streak only grows within a window of the last one. */
    public void noteActive(long now) {
        lastActiveAt = now;
    }

    /**
     * Adds {@code elapsed} ticks to the streak if the player earned XP in the last
     * {@code windowTicks}. Marks dirty only when the stack count moves, so a second-by-second tick
     * does not push a sync packet every second. Returns true when it moved.
     */
    public boolean tickStreak(long now, long elapsed, long windowTicks, long stepTicks, int maxStacks) {
        if (elapsed <= 0 || stepTicks <= 0 || maxStacks <= 0) {
            return false;
        }
        if (lastActiveAt == Long.MIN_VALUE || now - lastActiveAt > windowTicks || now < lastActiveAt) {
            return false;
        }
        long cap = stepTicks * maxStacks;
        if (streakTicks >= cap) {
            return false;
        }
        int before = streakStacks(stepTicks, maxStacks);
        streakTicks = Math.min(cap, streakTicks + elapsed);
        if (streakStacks(stepTicks, maxStacks) != before) {
            dirty = true;
            return true;
        }
        return false;
    }

    public void setStreakTicks(long ticks) {
        streakTicks = Math.max(0L, ticks);
        dirty = true;
    }

    /** Death: the whole streak goes. Returns the stacks that were lost. */
    public int loseStreak(long stepTicks, int maxStacks) {
        int lost = streakStacks(stepTicks, maxStacks);
        if (streakTicks != 0) {
            streakTicks = 0;
            dirty = true;
        }
        return lost;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    /**
     * Feeds XP into a skill and returns how many levels it gained. Everything above
     * {@link SkillMath#MAX_LEVEL} is dropped rather than banked, so a maxed skill stops counting.
     */
    public int addXp(Skill skill, float amount) {
        // NaN has to be rejected explicitly: every comparison against it is false, so `amount <= 0`
        // waves it through, and then `xp < need` inside the loop below is false too, which walks
        // the skill to 100 in one call and leaves no trace once the max-level cleanup wipes the xp.
        // Infinity gets the same treatment for the same reason.
        if (!Float.isFinite(amount) || amount <= 0 || !ProficiencyConfig.enabled(skill)) {
            return 0;
        }
        int index = skill.ordinal();
        if (levels[index] >= SkillMath.MAX_LEVEL) {
            return 0;
        }

        xp[index] += amount;
        dirty = true;

        int gained = 0;
        while (levels[index] < SkillMath.MAX_LEVEL) {
            float need = SkillMath.xpToNext(levels[index]);
            if (xp[index] < need) {
                break;
            }
            xp[index] -= need;
            levels[index]++;
            gained++;
        }
        if (levels[index] >= SkillMath.MAX_LEVEL) {
            xp[index] = 0;
        }
        return gained;
    }

    public void setLevel(Skill skill, int level) {
        levels[skill.ordinal()] = Math.max(0, Math.min(SkillMath.MAX_LEVEL, level));
        xp[skill.ordinal()] = 0;
        dirty = true;
    }

    public void reset() {
        java.util.Arrays.fill(levels, 0);
        java.util.Arrays.fill(xp, 0f);
        ranks.clear();
        paid.clear();
        visited.clear();
        buildFirst.clear();
        // Cooldowns too. A reset player is back at level 0, below the level an ability unlocks at,
        // so a cooldown left running is one they cannot even clear by using the ability again.
        java.util.Arrays.fill(cooldownUntil, 0L);
        java.util.Arrays.fill(frenzyUntil, 0L);
        streakTicks = 0;
        dirty = true;
    }

    public int rank(Talent talent) {
        return ranks.getOrDefault(talent.key(), 0);
    }

    public int rank(String key) {
        return ranks.getOrDefault(key, 0);
    }

    public boolean isFull(Talent talent) {
        return rank(talent) >= talent.maxRank();
    }

    public Map<String, Integer> talentRanks() {
        return java.util.Collections.unmodifiableMap(ranks);
    }

    /** Adds a rank without asking. Callers go through {@link TalentService}; tests do not. */
    public void addRank(Talent talent) {
        setRank(talent, rank(talent) + 1);
    }

    public void setRank(Talent talent, int rank) {
        int clamped = Math.max(0, Math.min(talent.maxRank(), rank));
        if (clamped == 0) {
            ranks.remove(talent.key());
        } else {
            ranks.put(talent.key(), clamped);
        }
        dirty = true;
    }

    /** Fills a whole tree. Only for tests and ops; skips every check. */
    public void fillTree(Skill skill) {
        for (Talent talent : Talents.of(skill)) {
            setRank(talent, talent.maxRank());
            paid.add(talent.key());
        }
    }

    public boolean hasPaid(Talent talent) {
        return paid.contains(talent.key());
    }

    public void markPaid(Talent talent) {
        if (paid.add(talent.key())) {
            dirty = true;
        }
    }

    /** Empties one tree. Materials already handed over stay handed over. */
    public int respec(Skill skill) {
        int refunded = pointsSpent(skill);
        for (Talent talent : Talents.of(skill)) {
            ranks.remove(talent.key());
        }
        dirty = true;
        return refunded;
    }

    /**
     * Everything about whether one more rank can go in except the materials, which need an
     * inventory. Pure, so the client uses it to draw the tree and the tests can reach it.
     */
    public TalentService.Outcome check(Talent talent) {
        if (isFull(talent)) {
            return TalentService.Outcome.MAXED;
        }
        for (String parent : talent.parents()) {
            Talent node = Talents.get(talent.skill(), parent);
            if (node != null && !isFull(node)) {
                return TalentService.Outcome.PARENT_NOT_FULL;
            }
        }
        if (level(talent.skill()) < talent.requiredLevel()) {
            return TalentService.Outcome.LEVEL_TOO_LOW;
        }
        if (pointsAvailable(talent.skill()) < talent.costPerRank()) {
            return TalentService.Outcome.NOT_ENOUGH_POINTS;
        }
        return TalentService.Outcome.OK;
    }

    /** Trees with their capstone taken, which is what Renaissance counts. */
    public int grandmasters() {
        int count = 0;
        for (Skill skill : Skill.VALUES) {
            for (Talent talent : Talents.of(skill)) {
                if (talent.kind() == Talent.Kind.CAPSTONE && isFull(talent)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Talent points produced by a skill: one for each of its own levels, a hundred at 100. */
    public int pointsEarned(Skill skill) {
        return level(skill) / Talents.LEVELS_PER_POINT;
    }

    public int pointsSpent(Skill skill) {
        int spent = 0;
        for (Talent talent : Talents.of(skill)) {
            spent += rank(talent) * talent.costPerRank();
        }
        return spent;
    }

    public int pointsSpentInCategory(SkillCategory category) {
        int spent = 0;
        for (Skill skill : Skill.VALUES) {
            if (skill.category() == category) {
                spent += pointsSpent(skill);
            }
        }
        return spent;
    }

    /**
     * Clamped at zero because a table change or an op's {@code set} can leave a player having spent
     * more than their current levels have earned. Forgiving on purpose. A death no longer can:
     * it takes progress, never levels.
     */
    public int pointsAvailable(Skill skill) {
        return Math.max(0, pointsEarned(skill) - pointsSpent(skill));
    }

    /**
     * The death cost: every skill's progress toward its next level is wiped. Levels, talents and
     * spent points are never touched. A ward keeps its share of the bar, so a 60% ward keeps 60%
     * of the progress and a full ward keeps all of it.
     *
     * <p>Replaced Valheim's 5% level tax on 2026-09-29, which the owner called dumb. The grace
     * floor went with it: it only existed because rounding a percentage of a level up was
     * regressive, and wiping a bar has nothing to round.
     *
     * @return the share of the bar lost (0 to 1) for every skill that lost anything.
     */
    public Map<Skill, Float> applyDeathPenalty() {
        Map<Skill, Float> lost = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.VALUES) {
            int index = skill.ordinal();
            if (xp[index] <= 0f) {
                continue;
            }
            double ward = deathWard(skill);
            if (ward >= 1.0) {
                continue;
            }
            float before = progress(skill);
            xp[index] = (float) (xp[index] * ward);
            float share = before - progress(skill);
            if (share > 0f) {
                lost.put(skill, share);
            }
        }
        if (!lost.isEmpty()) {
            dirty = true;
        }
        return lost;
    }

    public void copyFrom(PlayerSkills other) {
        System.arraycopy(other.levels, 0, this.levels, 0, levels.length);
        System.arraycopy(other.xp, 0, this.xp, 0, xp.length);
        this.ranks.clear();
        this.ranks.putAll(other.ranks);
        this.paid.clear();
        this.paid.addAll(other.paid);
        System.arraycopy(other.cooldownUntil, 0, this.cooldownUntil, 0, cooldownUntil.length);
        this.visited.clear();
        this.visited.addAll(other.visited);
        this.onboarded = other.onboarded;
        this.xpFeed = other.xpFeed;
        this.buildDay = other.buildDay;
        this.buildFirst.clear();
        this.buildFirst.putAll(other.buildFirst);
        this.streakTicks = other.streakTicks;
        dirty = true;
    }

    private Map<String, SkillState> toMap() {
        Map<String, SkillState> map = new HashMap<>();
        for (Skill skill : Skill.VALUES) {
            int index = skill.ordinal();
            if (levels[index] != 0 || xp[index] != 0f) {
                map.put(skill.id(), new SkillState(levels[index], xp[index]));
            }
        }
        return map;
    }

    private static PlayerSkills fromParts(Map<String, SkillState> map, Map<String, Integer> talents,
            List<String> paid,
            Map<String, Long> cooldowns, List<String> visited, boolean onboarded, long streak, boolean xpFeed,
            long buildDay, Map<String, Integer> buildFirst) {
        PlayerSkills skills = new PlayerSkills();
        skills.buildDay = buildDay;
        buildFirst.forEach((id, n) -> skills.buildFirst.put(id, Math.max(0, n)));
        skills.xpFeed = xpFeed;
        skills.streakTicks = Math.max(0L, streak);
        skills.visited.addAll(visited);
        skills.onboarded = onboarded;
        cooldowns.forEach((id, until) -> {
            Skill skill = Skill.byId(id);
            if (skill != null) {
                skills.cooldownUntil[skill.ordinal()] = until;
            }
        });
        // A talent that no longer exists in the table is dropped rather than crashing the load,
        // and a rank above a node's maximum (the table shrank) is clamped rather than kept.
        talents.forEach((key, rank) -> {
            Talent talent = Talents.byKey(key);
            if (talent != null && rank != null && rank > 0) {
                skills.ranks.put(key, Math.min(rank, talent.maxRank()));
            }
        });
        paid.stream().filter(key -> Talents.byKey(key) != null).forEach(skills.paid::add);
        map.forEach((id, state) -> {
            Skill skill = Skill.byId(id);
            // A skill that was removed from the enum is dropped rather than crashing the load.
            if (skill != null) {
                skills.levels[skill.ordinal()] =
                        Math.max(0, Math.min(SkillMath.MAX_LEVEL, state.level()));
                skills.xp[skill.ordinal()] = Math.max(0f, state.xp());
            }
        });
        return skills;
    }
}
