package fr.bosketball;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import org.bukkit.Keyed;
import org.bukkit.Registry;

/**
 * A registry that exists only so the offline suite can touch {@code Material} and {@code ItemStack}.
 *
 * <p>Paper's {@code Material.asItemType()} - reached by every {@code new ItemStack(...)} - reads
 * {@code Registry.ITEM}, and {@code RegistryAccessHolder} fills itself from
 * {@code ServiceLoader}. With no server that loader came up empty and the first item stack in a
 * test killed {@code org.bukkit.Registry} for the rest of the JVM: {@code ItemStack} could not be
 * constructed anywhere, so nothing about the ball's display could be tested at all.
 *
 * <p>The item registry hands back a placeholder, which is all {@code ItemStack}'s constructor
 * checks. The block registry deliberately throws: production code treats "the block registry will
 * not answer" as "no opinion" and falls back to its name heuristic, and the suite has always been
 * written against that behaviour - an offline block registry that answered "yes" to everything
 * would quietly change what hundreds of material-resolution assertions mean.
 */
public final class TestRegistryAccess implements RegistryAccess {

    public TestRegistryAccess() {
        // ServiceLoader instantiates this by reflection; the registries are built per call.
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        return registryFor(key == null ? "" : key.key().value());
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        String name = type == null ? "" : type.getSimpleName().toLowerCase(java.util.Locale.ROOT);
        return registryFor(name.equals("blocktype") ? "block" : "item");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Keyed> Registry<T> registryFor(String key) {
        if (key.equals("block") || key.equals("block_entity_type") || key.equals("fluid")) {
            return throwingRegistry(key);
        }
        return (Registry<T>) Proxy.newProxyInstance(Registry.class.getClassLoader(),
            new Class<?>[] {Registry.class}, (self, method, args) -> {
                switch (method.getName()) {
                    case "get":
                        // Non-null: ItemStack's constructor rejects a material the item registry
                        // does not know, and the placeholder is only ever used for that check.
                        return placeholder();
                    case "getOrThrow":
                        return placeholder();
                    case "getKey":
                    case "getTag":
                        return null;
                    case "stream":
                        return java.util.stream.Stream.empty();
                    case "iterator":
                        return Collections.emptyIterator();
                    case "equals":
                        return self == args[0];
                    case "hashCode":
                        return System.identityHashCode(self);
                    case "toString":
                        return "TestRegistry(" + key + ")";
                    default:
                        return defaultOf(method.getReturnType());
                }
            });
    }

    /** A registry that never answers: the same "no registry" the offline suite was written on. */
    @SuppressWarnings("unchecked")
    private static <T extends Keyed> Registry<T> throwingRegistry(String key) {
        return (Registry<T>) Proxy.newProxyInstance(Registry.class.getClassLoader(),
            new Class<?>[] {Registry.class}, (self, method, args) -> {
                switch (method.getName()) {
                    case "equals":
                        return self == args[0];
                    case "hashCode":
                        return System.identityHashCode(self);
                    case "toString":
                        return "NoRegistry(" + key + ")";
                    case "iterator":
                        return Collections.emptyIterator();
                    case "stream":
                        return java.util.stream.Stream.empty();
                    default:
                        throw new IllegalStateException("no " + key + " registry offline");
                }
            });
    }

    private static Object placeholder() {
        InvocationHandler handler = (self, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return self == args[0];
                case "hashCode":
                    return System.identityHashCode(self);
                case "toString":
                    return "offline-registry-entry";
                default:
                    return defaultOf(method.getReturnType());
            }
        };
        return Proxy.newProxyInstance(Registry.class.getClassLoader(),
            new Class<?>[] {Keyed.class, org.bukkit.inventory.ItemType.class}, handler);
    }

    private static Object defaultOf(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        return (short) 0;
    }
}
