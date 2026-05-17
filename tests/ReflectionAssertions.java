import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Read-only reflection helpers for the OO structural contract tests
 * (Requirement 15).
 *
 * <p>Encapsulates the synthetic-method filter and the public-instance-method
 * enumeration so each Req-15 assertion reads cleanly. Without filtering,
 * {@code javac}-generated synthetic members (e.g. {@code access$N} bridges on
 * inner classes, covariant-return bridges) would inflate the observed public
 * API surface beyond what the source actually declares.
 *
 * <p>This helper is strictly read-only:
 * <ul>
 *   <li>It never calls {@link java.lang.reflect.AccessibleObject#setAccessible(boolean)}.</li>
 *   <li>It never invokes methods or constructs instances.</li>
 *   <li>It never mutates state on the inspected class or its members.</li>
 * </ul>
 *
 * <p>Lives in the default (unnamed) Java package because {@code RingBuffer}
 * and its nested {@code Writer} / {@code Reader} types are in the default
 * package and named-package callers cannot import default-package types.
 */
final class ReflectionAssertions {

    private ReflectionAssertions() {
        // utility class — not instantiable
    }

    /**
     * Returns the declared methods of {@code c} that are public, non-static,
     * and non-synthetic, in unspecified order.
     *
     * <p>Filtering rationale (per Req 15.6 / 15.7):
     * <ul>
     *   <li>{@code Modifier.isPublic(...)} restricts to the public API surface.</li>
     *   <li>{@code !Modifier.isStatic(...)} drops static helpers — the contract
     *       describes instance methods only ({@code Writer.write}, {@code Reader.read}).</li>
     *   <li>{@code !Method.isSynthetic()} drops compiler-generated bridges
     *       (e.g. {@code access$N} accessors for private outer fields used by
     *       the inner class, covariant-return bridges).</li>
     * </ul>
     *
     * @param c the class to inspect; must not be {@code null}
     * @return a fresh, modifiable list of matching {@link Method} objects
     */
    static List<Method> publicInstanceMethods(Class<?> c) {
        List<Method> result = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            int mods = m.getModifiers();
            if (Modifier.isPublic(mods) && !Modifier.isStatic(mods) && !m.isSynthetic()) {
                result.add(m);
            }
        }
        return result;
    }

    /**
     * Passthrough wrapper around {@link Class#getDeclaredConstructors()} that
     * returns the result as a {@link List}.
     *
     * <p>Exists for symmetry with {@link #publicInstanceMethods(Class)} and to
     * keep the {@code java.lang.reflect} imports localized in this helper so
     * the Req-15 test class can stay focused on assertions.
     *
     * @param c the class to inspect; must not be {@code null}
     * @return a fresh, modifiable list of the declared constructors of {@code c}
     */
    static List<Constructor<?>> declaredConstructors(Class<?> c) {
        return new ArrayList<>(Arrays.asList(c.getDeclaredConstructors()));
    }

    /**
     * Returns {@code true} when {@code c} is a non-static inner class — i.e.,
     * its modifiers do not include {@code static} and it has an enclosing
     * class.
     *
     * <p>Used by Req 15.2 / 15.3 to assert that the nested {@code Writer} and
     * {@code Reader} types are inner classes (each instance bound to an
     * enclosing {@code RingBuffer} instance) rather than static nested
     * classes.
     *
     * @param c the class to inspect; must not be {@code null}
     */
    static boolean isNonStaticInnerClass(Class<?> c) {
        return !Modifier.isStatic(c.getModifiers()) && c.getEnclosingClass() != null;
    }
}
