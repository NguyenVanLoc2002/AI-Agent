package com.secureerp.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Positive and negative controls of the SEC-05 artefact scan. Planted values are built at run time and never
 * used as test arguments: a parameterised display name would put them into the very reports the scan reads.
 */
class ArtifactScannerTest {

    @TempDir
    Path base;

    private Path write(String name, String content) throws IOException {
        Path file = base.resolve("reports").resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private List<ArtifactScanner.Finding> scan() {
        return ArtifactScanner.scan(base, List.of(base.resolve("reports"), base.resolve("missing")));
    }

    @Test
    void everyRuleFindsItsPlantedValueAndNamesOnlyTheRule() throws IOException {
        String digits = "5554" + "44333";
        Map<String, String> planted = Map.of(
                "SENTINEL", "{\"total\":" + digits + "}",
                "DEMO_PASSWORD", "login with demo-" + "1b2c3d4e-aaaa-bbbb-cccc-0fedcba98765",
                "PRIVATE_KEY", "-----BEGIN " + "RSA PRIVATE KEY-----",
                "CLOUD_KEY", "key=AKIA" + "ABCDEFGHIJKLMNOP",
                "PROVIDER_KEY", "x-api-key: sk-" + "ant-abcdefghijklmnopqrstuv",
                "BEARER_TOKEN", "Authorization: Bearer " + "abcdefghijklmnop",
                "EMAIL", "contact someone" + "@example.com now",
                "PHONE_VN", "call 09" + "12345678 today",
                "RESTRICTED_FIELD", "{\"purchase" + "Cost\":1}");
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : planted.entrySet()) {
            text.append("clean line\n").append(entry.getValue()).append('\n');
        }
        write("TEST-planted.xml", text.toString());

        List<ArtifactScanner.Finding> findings = scan();

        assertThat(findings).extracting(ArtifactScanner.Finding::rule)
                .containsExactlyInAnyOrderElementsOf(planted.keySet());
        assertThat(findings).allSatisfy(finding -> {
            assertThat(finding.file()).isEqualTo("reports/TEST-planted.xml");
            assertThat(finding.line() % 2).as("only the planted lines").isZero();
        });
        assertThat(findings.toString()).doesNotContain(digits).doesNotContain("example.com");
    }

    /** Negative controls: the shapes that fill real reports must not be findings. */
    @Test
    void ordinaryReportContentIsClean() throws IOException {
        write("TEST-clean.xml", String.join("\n",
                "<testcase name=\"aConfirmedQuote\" time=\"0.283\"/>",
                "{\"@timestamp\":\"2026-11-20T07:07:43.944989200Z\",\"traceId\":\"a095d7ce-c7d1-4772-b5e9-d7ec7bd1bebb\"}",
                "pid 30436 thread http-nio-127.0.0.1-auto-1-exec-4 elapsed 0912 ms",
                "quote BG-ABCDEFGH version 2 total 1234567 VND sku SKU-BRK-PAD-A",
                "usr_TestResetOperatorAAAAA cv_NoSuchConversationAAAA",
                "container 1185a3bfc4984ba4a1dc5f1de977f0" + "9731234560ac0205ad49e58f730800 starting",
                "traceId=02154bb1-aea9-4182-b170-8e0334" + "912345 bytes=1678",
                "org.springframework.dao.DataIntegrityViolationException at Foo.java:123",
                "margin: 0 auto; password field is required"));
        write("nested/deeper/binary.dump", "\u0000\u0001plainÿ");

        assertThat(scan()).isEmpty();
    }

    /** EP-09c reviews (security L-1): grouped digits, escaped "@", snake-case fields and UTF-16 are caught. */
    @Test
    void groupedEscapedAndUtf16ValuesAreFound() throws IOException {
        Map<String, String> planted = Map.of(
                "SENTINEL", "total 555." + "444.333 VND",
                "EMAIL", "mail someone%40" + "example.com",
                "RESTRICTED_FIELD", "{\"credit_" + "limit\":0}");
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : planted.entrySet()) {
            text.append("clean line\n").append(entry.getValue()).append('\n');
        }
        write("TEST-encoded.xml", text.toString());
        Path utf16 = base.resolve("reports").resolve("utf16-output.txt");
        Files.write(utf16, ("credit" + "Limit=0\n").getBytes(StandardCharsets.UTF_16));

        assertThat(scan()).extracting(ArtifactScanner.Finding::rule)
                .containsExactlyInAnyOrder("SENTINEL", "EMAIL", "RESTRICTED_FIELD", "RESTRICTED_FIELD");
    }

    /** Round-2 review L1/L2: escaped quotes around "margin", narrow no-break space grouping; decimals stay apart. */
    @Test
    void escapedFieldsAndProperGroupingAreFoundButDecimalsAreNot() throws IOException {
        write("TEST-escaped.xml", String.join("\n",
                "{\\\"mar" + "gin\\\":5}",
                "&quot;mar" + "gin&quot;: 5",
                "total 555\u202f444\u202f" + "333 VND",
                "ratio 0.91234" + "5678 elapsed 12 3456789 ms"));

        assertThat(scan()).extracting(ArtifactScanner.Finding::line).containsExactly(1, 2, 3);
    }

    /** Round-2 review M1: plain test output is not the application log; only an ECS line with the app canary is. */
    @Test
    void theApplicationLogCanaryNeedsAStructuredLine() throws IOException {
        write("plain-output.txt", "INFO " + ArtifactScanner.APP_CANARY + "\n" + ArtifactScanner.CANARY + "\n");
        assertThat(ArtifactScanner.hasApplicationLogCanary(base.resolve("reports"))).isFalse();

        write("app-output.txt", "{\"message\":\"" + ArtifactScanner.APP_CANARY + "\",\"ecs\":{\"version\":\"8.11\"}}\n");
        assertThat(ArtifactScanner.hasApplicationLogCanary(base.resolve("reports"))).isTrue();
        assertThat(scan()).isEmpty();
    }

    /** The canary is found where it was logged and nowhere else. */
    @Test
    void theCanaryIsFoundOnlyWhereItWasLogged() throws IOException {
        write("TEST-with-canary-output.txt", "INFO " + ArtifactScanner.CANARY + "\n");
        Path other = base.resolve("other");
        Files.createDirectories(other);
        Files.writeString(other.resolve("TEST-without.txt"), "INFO nothing\n");

        assertThat(ArtifactScanner.hasCanary(base.resolve("reports"))).isTrue();
        assertThat(ArtifactScanner.hasCanary(other)).isFalse();
        assertThat(ArtifactScanner.hasCanary(base.resolve("missing"))).isFalse();
        assertThat(scan()).as("the canary is not a finding").isEmpty();
    }

    @Test
    void aFileThatIsNotUtf8IsStillScanned() throws IOException {
        Path file = base.resolve("reports").resolve("latin1.log");
        Files.createDirectories(file.getParent());
        byte[] head = {(byte) 0xc3, (byte) 0x28, '\n'}; // invalid UTF-8, not a byte order mark
        byte[] tail = ("ZZ" + "Honey\n").getBytes(StandardCharsets.US_ASCII);
        byte[] all = new byte[head.length + tail.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        Files.write(file, all);

        assertThat(scan()).extracting(ArtifactScanner.Finding::rule).containsExactly("SENTINEL");
    }
}
