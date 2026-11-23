package io.github.soldierdonkey2.winterheart.api;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Rand {
    private Rand() {}

    public static <T> T pick(List<T> list) {
        return list.isEmpty() ? null : list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    @SafeVarargs
    public static <T> T pick(T... items) {
        return pick(List.of(items));
    }

    /** True with the given percent chance (0-100). */
    public static boolean chance(double percent) {
        return ThreadLocalRandom.current().nextDouble() * 100 < percent;
    }
}
