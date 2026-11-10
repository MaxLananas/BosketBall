package fr.bosketball;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

/**
 * A Bukkit world that records what the plugin asks it to put on screen.
 *
 * <p>The ball is drawn by an {@code ItemDisplay}: if that display is never spawned, is spawned with
 * an empty item, is scaled to zero, keeps a zero-size culling box or is deleted while the ball is
 * still in flight, the server keeps simulating a perfectly good ball and the player sees nothing.
 * None of that is visible to a test built on a null world - the plugin's own headless tests pass a
 * {@code Location(null, ...)} and every render call returns at the first {@code world == null} - so
 * a disappearing ball can only be caught with a world that answers.
 *
 * <p>This is that world: a {@link Proxy} over {@link World} that returns air for every block, no
 * players and no ray hits, and hands back a recording proxy for every entity the plugin spawns. It
 * is not a server and it does not draw anything; it records the packets a server would have sent,
 * which is exactly the layer an invisible ball lives in.
 */
public final class FakeBukkitWorld {

    /** One entity the plugin spawned, with the state it was given. */
    public static final class Spawned implements InvocationHandler {
        public final Class<?> type;
        public final Entity proxy;
        public boolean valid = true;
        public boolean removed;
        public Location location;
        public Object item;
        public org.bukkit.util.Transformation transformation;
        public float width = -1f;
        public float height = -1f;
        public float viewRange = -1f;
        public int teleports;
        public int removals;

        private final UUID uniqueId = UUID.randomUUID();
        private final World world;

        private Spawned(Class<?> type) {
            this.type = type;
            this.world = null;
            this.proxy = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(),
                new Class<?>[] {type}, this);
        }

        private Spawned(Class<?> type, World world) {
            this.type = type;
            this.world = world;
            this.proxy = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(),
                new Class<?>[] {type}, this);
        }

        public float scaleX() {
            return transformation == null ? -1f : transformation.getScale().x;
        }

        public float scaleY() {
            return transformation == null ? -1f : transformation.getScale().y;
        }

        @Override
        public Object invoke(Object self, Method method, Object[] args) {
            String name = method.getName();
            switch (name) {
                case "isValid", "isDead" -> {
                    return name.equals("isDead") ? !valid : valid;
                }
                case "remove", "discard" -> {
                    valid = false;
                    removed = true;
                    removals++;
                    return null;
                }
                case "teleport" -> {
                    if (args != null && args.length >= 1 && args[0] instanceof Location target) {
                        location = target.clone();
                        teleports++;
                    }
                    return true;
                }
                case "getLocation" -> {
                    return location != null ? location.clone() : new Location(world, 0, 0, 0);
                }
                case "getWorld" -> {
                    return world;
                }
                case "getUniqueId" -> {
                    return uniqueId;
                }
                case "setItemStack" -> {
                    item = args[0];
                    return null;
                }
                case "getItemStack" -> {
                    return item;
                }
                case "setTransformation" -> {
                    transformation = (org.bukkit.util.Transformation) args[0];
                    return null;
                }
                case "getTransformation" -> {
                    return transformation;
                }
                case "setDisplayWidth" -> {
                    width = (float) args[0];
                    return null;
                }
                case "getDisplayWidth" -> {
                    return width;
                }
                case "setDisplayHeight" -> {
                    height = (float) args[0];
                    return null;
                }
                case "getDisplayHeight" -> {
                    return height;
                }
                case "setViewRange" -> {
                    viewRange = (float) args[0];
                    return null;
                }
                case "getViewRange" -> {
                    return viewRange;
                }
                case "equals" -> {
                    return self == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(self);
                }
                case "toString" -> {
                    return "Spawned(" + type.getSimpleName() + "@" + Integer.toHexString(uniqueId.hashCode()) + ")";
                }
                default -> {
                    // Every other setter, flag and tag is a no-op; every other getter answers null.
                    return defaultValue(method.getReturnType());
                }
            }
        }

    }

    private final List<Spawned> spawned = new ArrayList<>();
    /** Not final: the handler below closes over it, and it is assigned once, right after. */
    private World world;
    public int particles;
    public int rayTraces;

    public FakeBukkitWorld() {
        world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
            (self, method, args) -> {
                switch (method.getName()) {
                    case "spawn" -> {
                        Class<?> entityType = (Class<?>) args[1];
                        Spawned entity = new Spawned(entityType, world);
                        entity.location = ((Location) args[0]).clone();
                        if (args.length >= 3 && args[2] instanceof java.util.function.Consumer<?> consumer) {
                            @SuppressWarnings("unchecked")
                            java.util.function.Consumer<Object> typed = (java.util.function.Consumer<Object>) consumer;
                            typed.accept(entity.proxy);
                        }
                        spawned.add(entity);
                        return entity.proxy;
                    }
                    case "spawnParticle" -> {
                        particles++;
                        return null;
                    }
                    case "rayTraceBlocks" -> {
                        rayTraces++;
                        return null;
                    }
                    case "getPlayers" -> {
                        return List.of();
                    }
                    case "getBlockAt" -> {
                        return air();
                    }
                    case "getName" -> {
                        return "harness";
                    }
                    case "getUID" -> {
                        return uuid;
                    }
                    case "getMinHeight" -> {
                        return -64;
                    }
                    case "getMaxHeight" -> {
                        return 320;
                    }
                    case "getNearbyEntities", "getEntities", "getLivingEntities" -> {
                        return List.of();
                    }
                    case "isChunkLoaded" -> {
                        return true;
                    }
                    case "toString" -> {
                        return "FakeBukkitWorld";
                    }
                    case "hashCode" -> {
                        return System.identityHashCode(self);
                    }
                    case "equals" -> {
                        return self == args[0];
                    }
                    default -> {
                        return defaultValue(method.getReturnType());
                    }
                }
            });
    }

    private final UUID uuid = UUID.randomUUID();

    public World world() {
        return world;
    }

    /** Everything the plugin spawned, in order. */
    public List<Spawned> spawned() {
        return spawned;
    }

    /** The entities still alive of one type, in spawn order. */
    public List<Spawned> aliveOf(Class<?> type) {
        List<Spawned> out = new ArrayList<>();
        for (Spawned entity : spawned) {
            if (entity.type.equals(type) && entity.valid) {
                out.add(entity);
            }
        }
        return out;
    }

    private static Block air() {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
            (self, method, args) -> switch (method.getName()) {
                case "isPassable", "isEmpty", "isLiquid" -> true;
                case "getType" -> Material.AIR;
                default -> defaultValue(method.getReturnType());
            });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
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
        return 0d;
    }
}
