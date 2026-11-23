package io.github.soldierdonkey2.winterheart_guides;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Named Java predicates that guides.json can refer to: a `custom` sweep's "test" and any trigger's "where" are
 * the NAME of a function registered here (JSON can't hold code). Register from your mod's setup:
 * {@code GuideFunctions.register("my_test", ctx -> ...)}.
 */
public final class GuideFunctions {
    private static final Map<String, Predicate<Sweeps.Ctx>> FUNCTIONS = new HashMap<>();

    private GuideFunctions() {}

    public static void register(String name, Predicate<Sweeps.Ctx> fn) { FUNCTIONS.put(name, fn); }

    public static Predicate<Sweeps.Ctx> get(String name) { return FUNCTIONS.get(name); }

    public static boolean has(String name) { return FUNCTIONS.containsKey(name); }
}
