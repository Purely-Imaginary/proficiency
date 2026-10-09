package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shared GameTests on NeoForge. They name their template as a full id,
 * {@code proficiency:empty}, which is what Fabric needs; NeoForge's own registration would put a
 * namespace in front of it. So this generator builds each test the way vanilla's registry does,
 * with the template exactly as written. Test names stay {@code <class>.<method>}, lower case.
 */
@GameTestHolder(Proficiency.MOD_ID)
public final class NeoForgeGameTests {

    /** Every shared test class. Fabric lists the same ones as fabric-gametest entrypoints. */
    static final List<Class<?>> CLASSES = List.of(
            ProficiencyGameTests.class,
            CourageGameTests.class,
            GuardianGameTests.class,
            ChargerGameTests.class,
            TacticianGameTests.class,
            KillBonusGameTests.class,
            XpSourcesGameTests.class,
            AoeGameTests.class,
            NeoForgeToolGameTests.class,
            ExploitGameTests.class,
            MasteryGameTests.class,
            RestedGameTests.class);

    private NeoForgeGameTests() {
    }

    @GameTestGenerator
    public static List<TestFunction> sharedTests() {
        List<TestFunction> tests = new ArrayList<>();
        for (Class<?> holder : CLASSES) {
            for (Method method : holder.getDeclaredMethods()) {
                GameTest test = method.getAnnotation(GameTest.class);
                if (test != null) {
                    tests.add(function(holder, method, test));
                }
            }
        }
        return tests;
    }

    private static TestFunction function(Class<?> holder, Method method, GameTest test) {
        String name = holder.getSimpleName().toLowerCase(Locale.ROOT) + "."
                + method.getName().toLowerCase(Locale.ROOT);
        return new TestFunction(test.batch(), name, test.template(),
                StructureUtils.getRotationForRotationSteps(test.rotationSteps()), test.timeoutTicks(),
                test.setupTicks(), test.required(), test.manualOnly(), test.attempts(),
                test.requiredSuccesses(), test.skyAccess(), helper -> invoke(method, helper));
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
