package dev.amman.proficiency.net.codec;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Forge 1.20.1 has no {@code net.minecraft.network.codec.StreamCodec} (it arrived in 1.20.5). This
 * is the small part of its shape the mod uses, so every payload keeps master's encode and decode
 * code word for word and only the import changes.
 */
public interface StreamCodec<B, T> {

    T decode(B buf);

    void encode(B buf, T value);

    static <B, T> StreamCodec<B, T> of(BiConsumer<B, T> encoder, Function<B, T> decoder) {
        return new StreamCodec<>() {
            @Override
            public T decode(B buf) {
                return decoder.apply(buf);
            }

            @Override
            public void encode(B buf, T value) {
                encoder.accept(buf, value);
            }
        };
    }

    static <B, T, A> StreamCodec<B, T> composite(StreamCodec<? super B, A> a, Function<T, A> getA,
            Function<A, T> make) {
        return of((buf, value) -> a.encode(buf, getA.apply(value)), buf -> make.apply(a.decode(buf)));
    }

    static <B, T, A, C> StreamCodec<B, T> composite(StreamCodec<? super B, A> a, Function<T, A> getA,
            StreamCodec<? super B, C> c, Function<T, C> getC, BiFunction<A, C, T> make) {
        return of((buf, value) -> {
            a.encode(buf, getA.apply(value));
            c.encode(buf, getC.apply(value));
        }, buf -> {
            A first = a.decode(buf);
            C second = c.decode(buf);
            return make.apply(first, second);
        });
    }
}
