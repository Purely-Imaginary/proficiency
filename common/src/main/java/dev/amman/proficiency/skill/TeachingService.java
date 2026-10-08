package dev.amman.proficiency.skill;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pays teachers. A student who spends rested XP that a teacher filled earns the teacher Social XP
 * ({@link RestedMath#credit}). The credit waits in a {@link TeachingLedger} that is saved to the
 * world, so a teacher who is offline is paid at the next login, and a student's death cancels what
 * is still waiting on them. The rules of who may teach whom are in {@link RestedService}.
 */
public final class TeachingService {

    /** The XP log and feed line for the payout, and the telemetry kind {@code teaching}. */
    public static final String SOURCE = "proficiency.xplog.source.teaching";

    /** A teacher is paid at most this often while online (5 s), so a lesson is one log line. */
    private static final long PAY_INTERVAL_TICKS = 100L;
    /** The toast shows at most this often per teacher (30 s); credit in between adds up. */
    private static final long TOAST_INTERVAL_TICKS = 600L;
    /** The ledger is written at most this often (real ms) while it changes, and always on stop. */
    private static final long SAVE_INTERVAL_MS = 30_000L;

    private static final class Toast {
        double xp;
        long last = Long.MIN_VALUE;
    }

    private static final Map<UUID, Long> LAST_PAY = new ConcurrentHashMap<>();
    private static final Map<UUID, Toast> TOASTS = new ConcurrentHashMap<>();

    private static volatile TeachingLedger ledger = new TeachingLedger();
    private static volatile Path ledgerFile;
    private static long lastSaveMs;

    private TeachingService() {
    }

    /** The ledger for the running world, loaded from its folder the first time it is needed. */
    public static synchronized TeachingLedger ledger(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("proficiency")
                .resolve("teaching_credit.txt");
        if (!file.equals(ledgerFile)) {
            // A single-player JVM can open another world: do not carry one world's credit into it.
            ledgerFile = file;
            ledger = TeachingLedger.load(file);
            lastSaveMs = System.currentTimeMillis();
        }
        return ledger;
    }

    /** What a teacher's Teacher's Pride rank is: from the online player, 0 when they are away. */
    public static int cutRank(MinecraftServer server, UUID teacher) {
        ServerPlayer online = server.getPlayerList().getPlayer(teacher);
        if (online == null) {
            return 0;
        }
        return dev.amman.proficiency.perk.TalentService.rank(ProficiencyAttachments.of(online),
                Skill.SOCIAL, "teacher_cut");
    }

    /** Records what a spend owes its teachers. The student's UUID is kept so their death can cancel it. */
    public static void credit(ServerPlayer student, List<RestedMath.Credit> credits) {
        if (credits.isEmpty()) {
            return;
        }
        TeachingLedger book = ledger(student.server);
        for (RestedMath.Credit credit : credits) {
            book.add(credit.teacher(), student.getUUID(), credit.socialXp());
        }
    }

    /** Once a second from the server tick. Pays an online teacher what is waiting, every 5 seconds. */
    public static void tick(ServerPlayer teacher) {
        if (!teacher.isAlive() || teacher.isSpectator()) {
            return;
        }
        TeachingLedger book = ledger(teacher.server);
        if (!(book.owedTo(teacher.getUUID()) > 0)) {
            return;
        }
        long now = teacher.level().getGameTime();
        Long last = LAST_PAY.get(teacher.getUUID());
        if (last != null && now >= last && now - last < PAY_INTERVAL_TICKS) {
            return;
        }
        LAST_PAY.put(teacher.getUUID(), now);
        pay(teacher, false);
    }

    /** At login: pays what piled up while the teacher was away, and says so in chat. */
    public static void onLogin(ServerPlayer teacher) {
        pay(teacher, true);
    }

    private static void pay(ServerPlayer teacher, boolean login) {
        TeachingLedger book = ledger(teacher.server);
        double base = book.take(teacher.getUUID());
        if (!(base > 0)) {
            return;
        }
        float paid = ProficiencyConfig.enabled(Skill.SOCIAL)
                ? SkillService.grant(teacher, Skill.SOCIAL, base, SOURCE) : 0f;
        if (!(paid > 0)) {
            return;
        }
        if (login) {
            teacher.sendSystemMessage(Component.translatable("proficiency.teaching.away", fmt(paid))
                    .withStyle(ChatFormatting.AQUA));
            return;
        }
        // A small toast, rate-limited: credit in between is added up, never dropped.
        Toast toast = TOASTS.computeIfAbsent(teacher.getUUID(), id -> new Toast());
        toast.xp += paid;
        long now = teacher.level().getGameTime();
        if (toast.last == Long.MIN_VALUE || now < toast.last || now - toast.last >= TOAST_INTERVAL_TICKS) {
            teacher.displayClientMessage(Component.translatable("proficiency.teaching.paid", fmt(toast.xp))
                    .withStyle(ChatFormatting.AQUA), true);
            toast.xp = 0;
            toast.last = now;
        }
    }

    private static String fmt(double xp) {
        return String.format(Locale.ROOT, xp < 10 ? "%.1f" : "%.0f", xp);
    }

    /** A student's death: the credit still waiting on them is lost with their rested XP. Returns base XP lost. */
    public static double onStudentDeath(MinecraftServer server, UUID student) {
        return ledger(server).wipeStudent(student);
    }

    /** Writes the ledger when it changed and the interval has passed. Once a second from the tick. */
    public static void saveIfDue(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < SAVE_INTERVAL_MS) {
            return;
        }
        TeachingLedger book = ledger(server);
        lastSaveMs = now;
        if (book.takeDirty()) {
            write(book);
        }
    }

    /** Writes the ledger now. On server stop. */
    public static synchronized void saveNow() {
        if (ledgerFile != null && ledger.takeDirty()) {
            write(ledger);
        }
    }

    private static void write(TeachingLedger book) {
        try {
            book.save(ledgerFile);
        } catch (IOException e) {
            book.markDirty();
            Proficiency.LOG.warn("Could not save the teaching credit file {}: {}", ledgerFile, e.toString());
        }
    }

    public static void forget(UUID player) {
        LAST_PAY.remove(player);
        TOASTS.remove(player);
    }

    /** For tests: forgets the loaded world so the next call reads its own folder. */
    public static synchronized void resetForTests() {
        ledgerFile = null;
        ledger = new TeachingLedger();
        LAST_PAY.clear();
        TOASTS.clear();
    }
}
