package dev.amman.proficiency.platform.bus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Kept so the handler classes read as they did. Fabric has no annotation scan, so
 * {@code Subscribers} lists them by hand; {@link #value()} still decides whether a class is
 * registered on a dedicated server.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {

    String modid() default "";

    Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER};
}
