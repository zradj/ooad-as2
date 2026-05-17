import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OO structural contract tests for {@link RingBuffer} (Requirement 15).
 *
 * <p>Validates Property 9 (Structural OO Contract) using read-only reflection
 * via {@link ReflectionAssertions}: {@code RingBuffer} declares exactly two
 * nested classes ({@code Writer} and {@code Reader}), both are public
 * non-static inner classes, neither has a public constructor, {@code Writer}
 * exposes only {@code write(T)} as a public instance method, {@code Reader}
 * exposes only {@code read()} as a public instance method, and
 * {@code RingBuffer} exposes the documented factory methods
 * {@code getWriterInstance()} and {@code createReader()}.
 *
 * <p><b>Validates: Requirements 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7,
 * 15.8, 15.9</b>
 *
 * <h2>Note on Requirement 15.9 (production-source guard)</h2>
 * <p>A unit test cannot truly detect "the production source was edited",
 * because by the time the test runs the change is already part of the working
 * copy. The structural assertions in 15.1&ndash;15.8 already catch any change
 * to the public API surface; this guard adds a byte-exact tripwire for
 * non-API edits (whitespace tweaks, comment changes, helper-method
 * refactors).
 *
 * <p>The guard reads {@code src/RingBuffer.java} and {@code src/Main.java}
 * from disk via {@link Files#readAllBytes(Path)} (raw bytes, so line-ending
 * normalization is not an issue), computes a SHA-256 digest using
 * {@link MessageDigest#getInstance(String) MessageDigest.getInstance("SHA-256")},
 * hex-encodes the result, and compares it to a hard-coded baseline captured
 * at authoring time.
 *
 * <p><b>If the production sources legitimately need to change</b>, the
 * baseline digest constants {@link #BASELINE_RINGBUFFER_SHA256} and
 * {@link #BASELINE_MAIN_SHA256} <b>must</b> be updated as part of that same
 * pull request, and the change should be recorded in
 * {@code tests/UNTESTABLE_ISSUES.md} per Req 15.9 rather than silently
 * accepted as a fix. Updating the digest is an explicit, reviewable signal
 * that production code was modified.
 *
 * <p>Lives in the default (unnamed) Java package because {@code RingBuffer}
 * and its nested types are in the default package and named-package callers
 * cannot import default-package types.
 */
class OoStructuralContractTest {

    // ------------------------------------------------------------------
    // Baseline digests for Requirement 15.9.
    //
    // These are the SHA-256 digests of src/RingBuffer.java and src/Main.java
    // as they exist in the repository at the time these tests were authored.
    // Lowercase hex matches the format produced by the test's own hex
    // encoding below, so an exact String equality assertion suffices.
    //
    // To regenerate (PowerShell):
    //   Get-FileHash -Algorithm SHA256 src/RingBuffer.java, src/Main.java
    //
    // To regenerate (POSIX shells):
    //   sha256sum src/RingBuffer.java src/Main.java
    // ------------------------------------------------------------------

    /** SHA-256 of {@code src/RingBuffer.java} as committed at authoring time. */
    private static final String BASELINE_RINGBUFFER_SHA256 =
            "8d6664217562eb2cac7b0260f4ef699d964fd0a817f5cf586340395471336975";

    /** SHA-256 of {@code src/Main.java} as committed at authoring time. */
    private static final String BASELINE_MAIN_SHA256 =
            "62569d1d1223128f6f3791921d4d862f7cfe84748b3a7bd030b389da13c93b6d";

    // ------------------------------------------------------------------
    // Requirement 15.1 — RingBuffer declares exactly two nested classes
    // named "Writer" and "Reader".
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RingBuffer declares exactly two nested classes named Writer and Reader")
    void ringBuffer_declaresExactlyTwoNestedClassesNamedWriterAndReader() {
        Class<?>[] declared = RingBuffer.class.getDeclaredClasses();

        assertEquals(2, declared.length,
                "RingBuffer must declare exactly two nested classes; "
                        + "found: " + Arrays.toString(declared));

        Class<?> writerClass = null;
        Class<?> readerClass = null;
        for (Class<?> c : declared) {
            if ("Writer".equals(c.getSimpleName())) {
                writerClass = c;
            } else if ("Reader".equals(c.getSimpleName())) {
                readerClass = c;
            }
        }

        assertNotNull(writerClass,
                "RingBuffer must declare a nested class with simple name 'Writer'");
        assertNotNull(readerClass,
                "RingBuffer must declare a nested class with simple name 'Reader'");
    }

    // ------------------------------------------------------------------
    // Requirement 15.2 — Writer is a public non-static inner class.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Writer nested class is public and a non-static inner class")
    void writerNestedClass_isPublic_andNonStaticInner() {
        Class<?> writerClass = findNestedClass("Writer");

        int modifiers = writerClass.getModifiers();

        assertTrue(Modifier.isPublic(modifiers),
                "Writer nested class must be declared public");
        assertTrue(ReflectionAssertions.isNonStaticInnerClass(writerClass),
                "Writer must be a non-static inner class (instance bound to an enclosing RingBuffer)");
    }

    // ------------------------------------------------------------------
    // Requirement 15.3 — Reader is a public non-static inner class.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reader nested class is public and a non-static inner class")
    void readerNestedClass_isPublic_andNonStaticInner() {
        Class<?> readerClass = findNestedClass("Reader");

        int modifiers = readerClass.getModifiers();

        assertTrue(Modifier.isPublic(modifiers),
                "Reader nested class must be declared public");
        assertTrue(ReflectionAssertions.isNonStaticInnerClass(readerClass),
                "Reader must be a non-static inner class (instance bound to an enclosing RingBuffer)");
    }

    // ------------------------------------------------------------------
    // Requirement 15.4 — Writer has no public constructor.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Writer declares no public constructor")
    void writerHasNoPublicConstructor() {
        Class<?> writerClass = findNestedClass("Writer");

        List<Constructor<?>> ctors = ReflectionAssertions.declaredConstructors(writerClass);
        assertFalse(ctors.isEmpty(),
                "Writer is expected to declare at least one (non-public) constructor");

        for (Constructor<?> ctor : ctors) {
            assertFalse(Modifier.isPublic(ctor.getModifiers()),
                    "Writer must not declare a public constructor; offending constructor: " + ctor);
        }
    }

    // ------------------------------------------------------------------
    // Requirement 15.5 — Reader has no public constructor.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reader declares no public constructor")
    void readerHasNoPublicConstructor() {
        Class<?> readerClass = findNestedClass("Reader");

        List<Constructor<?>> ctors = ReflectionAssertions.declaredConstructors(readerClass);
        assertFalse(ctors.isEmpty(),
                "Reader is expected to declare at least one (non-public) constructor");

        for (Constructor<?> ctor : ctors) {
            assertFalse(Modifier.isPublic(ctor.getModifiers()),
                    "Reader must not declare a public constructor; offending constructor: " + ctor);
        }
    }

    // ------------------------------------------------------------------
    // Requirement 15.6 — Writer's only public instance method is write(T)
    // with exactly one parameter (synthetic methods filtered out).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Writer's only public instance method is write with one parameter")
    void writerOnlyPublicInstanceMethodIsWriteWithOneParameter() {
        Class<?> writerClass = findNestedClass("Writer");

        List<Method> publicInstanceMethods = ReflectionAssertions.publicInstanceMethods(writerClass);

        assertEquals(1, publicInstanceMethods.size(),
                "Writer must declare exactly one public, non-static, non-synthetic instance method; "
                        + "found: " + publicInstanceMethods);

        Method only = publicInstanceMethods.get(0);
        assertEquals("write", only.getName(),
                "Writer's sole public instance method must be named 'write'");
        assertEquals(1, only.getParameterCount(),
                "Writer.write must take exactly one parameter");
    }

    // ------------------------------------------------------------------
    // Requirement 15.7 — Reader's only public instance method is read()
    // with zero parameters (synthetic methods filtered out).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reader's only public instance method is read with zero parameters")
    void readerOnlyPublicInstanceMethodIsReadWithZeroParameters() {
        Class<?> readerClass = findNestedClass("Reader");

        List<Method> publicInstanceMethods = ReflectionAssertions.publicInstanceMethods(readerClass);

        assertEquals(1, publicInstanceMethods.size(),
                "Reader must declare exactly one public, non-static, non-synthetic instance method; "
                        + "found: " + publicInstanceMethods);

        Method only = publicInstanceMethods.get(0);
        assertEquals("read", only.getName(),
                "Reader's sole public instance method must be named 'read'");
        assertEquals(0, only.getParameterCount(),
                "Reader.read must take zero parameters");
    }

    // ------------------------------------------------------------------
    // Requirement 15.8 — RingBuffer exposes getWriterInstance() and
    // createReader() factory methods. getMethod throws
    // NoSuchMethodException if the method isn't present, which propagates
    // as a test failure naturally.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RingBuffer exposes getWriterInstance() and createReader() factory methods")
    void ringBuffer_exposesGetWriterInstanceAndCreateReaderFactoryMethods() throws NoSuchMethodException {
        Method getWriterInstance = RingBuffer.class.getMethod("getWriterInstance");
        Method createReader = RingBuffer.class.getMethod("createReader");

        assertNotNull(getWriterInstance,
                "RingBuffer.getWriterInstance() must be a public no-arg method");
        assertNotNull(createReader,
                "RingBuffer.createReader() must be a public no-arg method");
    }

    // ------------------------------------------------------------------
    // Requirement 15.9 — production sources unmodified since baseline.
    //
    // Reads raw bytes via Files.readAllBytes (so the digest is byte-exact
    // and not affected by line-ending normalization), computes SHA-256,
    // hex-encodes lowercase, and compares to the hard-coded baselines
    // captured at authoring time.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Production source files are byte-exact identical to the baseline digests")
    void productionSourceFiles_unmodifiedSinceBaseline() throws Exception {
        String ringBufferDigest = sha256Hex(Path.of("src", "RingBuffer.java"));
        String mainDigest = sha256Hex(Path.of("src", "Main.java"));

        assertEquals(BASELINE_RINGBUFFER_SHA256, ringBufferDigest,
                "src/RingBuffer.java has been modified relative to the baseline. "
                        + "If this change is intentional, update BASELINE_RINGBUFFER_SHA256 in this test "
                        + "and record the modification in tests/UNTESTABLE_ISSUES.md per Req 15.9.");
        assertEquals(BASELINE_MAIN_SHA256, mainDigest,
                "src/Main.java has been modified relative to the baseline. "
                        + "If this change is intentional, update BASELINE_MAIN_SHA256 in this test "
                        + "and record the modification in tests/UNTESTABLE_ISSUES.md per Req 15.9.");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Looks up one of {@code RingBuffer}'s declared nested classes by simple
     * name. Iterates {@code getDeclaredClasses()} (rather than calling
     * {@code Class.forName("RingBuffer$Writer")}) so the test relies only on
     * the structural surface specified in Req 15.1, not on the JVM's binary
     * naming scheme.
     */
    private static Class<?> findNestedClass(String simpleName) {
        for (Class<?> c : RingBuffer.class.getDeclaredClasses()) {
            if (simpleName.equals(c.getSimpleName())) {
                return c;
            }
        }
        throw new AssertionError(
                "Expected RingBuffer to declare a nested class with simple name '" + simpleName + "'");
    }

    /**
     * Computes the SHA-256 of {@code path}'s raw bytes and returns the digest
     * as a lowercase hex string. Reads via {@link Files#readAllBytes(Path)}
     * so line-ending normalization (LF vs CRLF) is irrelevant — the digest
     * is over the exact byte sequence on disk.
     */
    private static String sha256Hex(Path path) throws java.io.IOException, NoSuchAlgorithmException {
        byte[] bytes = Files.readAllBytes(path);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);

        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }
}
