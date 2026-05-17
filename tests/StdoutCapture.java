import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Static helper that redirects {@link System#out} to an in-memory buffer for the
 * duration of a {@link Runnable}, then restores the previous stream and returns
 * the captured text.
 *
 * <p>Used by the {@code printBuffer()} tests (Requirement 13) which need to
 * observe output written via {@code System.out.println(...)} without depending
 * on a particular host line-separator. The returned string has its line endings
 * normalized so tests do not have to branch on LF vs. CRLF.
 *
 * <p>Lives in the default Java package: production sources at {@code src/} are
 * in the default package and named-package tests cannot import default-package
 * types.
 */
final class StdoutCapture {

    private StdoutCapture() {
        // no instances
    }

    /**
     * Runs {@code action} with {@link System#out} redirected to a fresh
     * {@link ByteArrayOutputStream}, then restores the previous stream and
     * returns the captured text with line endings normalized to {@code "\n"}.
     *
     * <p>The original {@code System.out} is restored in a {@code finally} block
     * so a throwing action does not leave the JVM with a dangling redirected
     * stream. Any exception thrown by {@code action} propagates to the caller
     * after the stream is restored.
     *
     * @param action the block to execute while stdout is redirected; must not be {@code null}
     * @return everything {@code action} wrote to {@code System.out}, decoded as UTF-8,
     *         with CRLF and lone CR sequences normalized to LF
     */
    static String capture(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream tee = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            System.setOut(tee);
            try {
                action.run();
            } finally {
                // Flush before swapping back so any buffered bytes land in `buf`.
                tee.flush();
            }
        } finally {
            System.setOut(original);
        }
        return normalizeLineEndings(buf.toString(StandardCharsets.UTF_8));
    }

    /**
     * Collapses any {@code "\r\n"} or lone {@code "\r"} sequences to {@code "\n"}
     * so callers can write platform-independent assertions.
     */
    private static String normalizeLineEndings(String s) {
        // Replace CRLF first, then any remaining lone CR, to avoid double-translation.
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }
}
