package dev.amman.proficiency.skill;

/**
 * Tells a machine's stand-in player from a real one without naming any loader's class. Create's
 * deployer, Mekanism's digital miner, Ars Nouveau's turrets and rituals and the like all act
 * through a fake player, and every one of those classes is called something ending in
 * {@code FakePlayer} or extends the loader's own {@code FakePlayer}. Walking the class chain for
 * that name works on NeoForge, Fabric and Forge alike and needs no dependency on any of them.
 *
 * <p>The answer is cached per class, so the check costs one map read after the first call.
 */
public final class FakeActors {

    private static final ClassValue<Boolean> FAKE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
                if (k.getSimpleName().endsWith("FakePlayer")) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }
    };

    private FakeActors() {
    }

    /** Whether this object's class is, or extends, a fake player. Null is not. */
    public static boolean isFake(Object actor) {
        return actor != null && FAKE.get(actor.getClass());
    }
}
