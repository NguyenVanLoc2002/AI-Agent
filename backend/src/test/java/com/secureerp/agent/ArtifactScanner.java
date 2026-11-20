package com.secureerp.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * SEC-05 artefact scan (EP-09c, A-46 decision 3A; THREAT_MODEL TH-14): looks for restricted values in build and
 * test artefacts (surefire/failsafe reports and the redirected output of every test class, which carries the
 * application log). A finding names the rule, the file and the line, never the matched text, so the scan's own
 * report cannot leak it.
 *
 * <p>The rules are deliberately specific (registered sentinels, test VINs, the demo password shape, key and
 * credential formats, personal contact formats, Mock ERP restricted field names): a generic "number" rule would
 * flag every timestamp. Each line is also checked in a normalised form (digit groups joined, escaped "@"
 * decoded, NUL removed) so grouped or escaped values do not slip through. Values that are not registered (a
 * real cost or debt) are out of reach of any scan, as for the egress guard (A-40(a)).
 */
public final class ArtifactScanner {

    /** Logged by {@link LogCanaryExtension} for every test class: proves test output reaches the scanned files. */
    public static final String CANARY = "SEC05-LOG-CANARY-7f3c";
    /**
     * Logged by {@link AppLogCanary} through an application logger once Spring Boot has set up logging: an ECS line
     * carrying it proves the application log itself reaches the scanned files (EP-09c round-2 review M1).
     */
    public static final String APP_CANARY = "SEC05-APP-LOG-CANARY-7f3c";
    private static final String ECS_MARKER = "\"ecs\":{\"version\"";

    /** A rule hit: rule ID, file (relative to the scanned root) and 1-based line. Never the matched text. */
    public record Finding(String rule, String file, int line) {
    }

    /** Rule ID → pattern. */
    public static final Map<String, Pattern> RULES = Map.ofEntries(
            // SEC-01/02 sentinels: Mock ERP cost, credit, customer prices, honeytoken text, ERP customer keys,
            // staff notes of the tests, synthetic test VINs.
            Map.entry("SENTINEL", Pattern.compile(
                    "987654321|123456789|555444333|666777888|1110888666|ZZHoney|SYNTH-CUST|STAFFNOTE"
                            + "|5ZZAPAS11RA000001|RLZAPAS20RA000002")),
            // The demo password of the tests is "demo-" + a random UUID (DemoWebEnvironment).
            Map.entry("DEMO_PASSWORD", Pattern.compile("demo-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-")),
            Map.entry("PRIVATE_KEY", Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----")),
            Map.entry("CLOUD_KEY", Pattern.compile("(?<![A-Z0-9])AKIA[0-9A-Z]{16}(?![A-Z0-9])")),
            Map.entry("PROVIDER_KEY", Pattern.compile("(?<![A-Za-z0-9])sk-(?:ant-)?[A-Za-z0-9_-]{20,}")),
            Map.entry("BEARER_TOKEN", Pattern.compile("(?i)authorization[\"']?\\s*[:=]\\s*[\"']?bearer\\s+[A-Za-z0-9._-]{12,}")),
            Map.entry("EMAIL", Pattern.compile("(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*"
                    + "\\.(?:com|vn|net|org|io|dev)(?![A-Za-z0-9])")),
            // Not inside a longer token: container IDs and UUIDs carry digit runs between hex letters (first run).
            Map.entry("PHONE_VN", Pattern.compile("(?<![0-9A-Za-z.:_\\-])(?:\\+84|0)[35789][0-9]{8}(?![0-9A-Za-z])")),
            // Restricted Mock ERP field names (cost, credit, per-customer price), camel and snake case.
            Map.entry("RESTRICTED_FIELD", Pattern.compile(
                    "(?i)purchase_?cost|credit_?balance|credit_?limit|customer_?price_?[ab]\\b|\"margin\"")));

    /** Thousands grouping only (a separator followed by exactly three digits), so decimals stay apart. */
    private static final Pattern DIGIT_GROUP_SEPARATOR =
            Pattern.compile("(?<=\\d)[.,' \u00a0\u202f](?=\\d{3}(?!\\d))");
    private static final Pattern ESCAPED_AT = Pattern.compile("(?i)%40|\\\\u0040|&#64;|&#x40;");
    private static final Pattern ESCAPED_QUOTE = Pattern.compile("\\\\\"|&quot;|&#34;");

    private ArtifactScanner() {
    }

    /** Scans every regular file under the given roots (missing roots are skipped). */
    public static List<Finding> scan(Path base, List<Path> roots) {
        List<Finding> findings = new ArrayList<>();
        for (Path file : files(roots)) {
            scanFile(base, file, findings);
        }
        return findings;
    }

    /** Whether any file under {@code root} carries the {@link #CANARY} line. */
    public static boolean hasCanary(Path root) {
        return files(List.of(root)).stream().anyMatch(file -> read(file).contains(CANARY));
    }

    /** Whether any file under {@code root} carries {@link #APP_CANARY} on a structured (ECS) application log line. */
    public static boolean hasApplicationLogCanary(Path root) {
        return files(List.of(root)).stream().flatMap(file -> read(file).lines())
                .anyMatch(line -> line.contains(APP_CANARY) && line.contains(ECS_MARKER));
    }

    private static List<Path> files(List<Path> roots) {
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile).sorted().forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    private static void scanFile(Path base, Path file, List<Finding> findings) {
        String name = base.relativize(file).toString().replace('\\', '/');
        String[] lines = read(file).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String joined = DIGIT_GROUP_SEPARATOR.matcher(raw.replace("\u0000", "")).replaceAll("");
            String normalised = ESCAPED_QUOTE.matcher(ESCAPED_AT.matcher(joined).replaceAll("@")).replaceAll("\"");
            for (Map.Entry<String, Pattern> rule : RULES.entrySet()) {
                if (rule.getValue().matcher(raw).find() || rule.getValue().matcher(normalised).find()) {
                    findings.add(new Finding(rule.getKey(), name, i + 1));
                }
            }
        }
    }

    private static String read(Path file) {
        try {
            return decode(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * UTF-16 when the file starts with its byte order mark, else UTF-8, falling back to ISO-8859-1 so a binary
     * or mis-encoded artefact is still scanned byte by byte.
     */
    private static String decode(byte[] bytes) {
        if (bytes.length >= 2 && ((bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe)
                || (bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff))) {
            return new String(bytes, StandardCharsets.UTF_16);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }
}
