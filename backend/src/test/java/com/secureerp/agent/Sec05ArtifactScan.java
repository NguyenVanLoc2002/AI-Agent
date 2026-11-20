package com.secureerp.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SEC-05 artefact scan of this build (EP-09c, A-46 decisions 3A/5A). Run by its own failsafe execution in the
 * {@code verify} phase, after the unit and integration tests and their verification, so every surefire and
 * failsafe report and every redirected test output file is complete. Its own report goes to
 * {@code target/sec05-reports}, which it does not scan. Any finding fails the build; the failure message lists
 * rule, file and line only.
 *
 * <p>Order (EP-09c round-2 review M2): the existing report directories are scanned first, so a run whose
 * integration tests never ran (a unit test failed) still lists its findings. Only then are the positive controls
 * required: the {@link ArtifactScanner#CANARY} of the test output in every existing directory, and the
 * {@link ArtifactScanner#APP_CANARY} of the structured application log in the failsafe reports.
 *
 * <p>Scope: the artefacts of this build, with {@code mvn clean verify} as the gating command (without
 * {@code clean} old reports are scanned too). When a test fails, the build stops before this step; scan its
 * reports with {@code mvn failsafe:integration-test@sec05-artifact-scan failsafe:verify@sec05-artifact-scan}
 * before sharing {@code target/}. NOT RUN: seven days of logs, APM, the console of a running application.
 */
class Sec05ArtifactScan {

    @Test
    void buildArtefactsCarryNoRestrictedValues() {
        Path base = Path.of(System.getProperty("basedir", ".")).toAbsolutePath();
        Path target = base.resolve("target");
        Path surefire = target.resolve("surefire-reports");
        Path failsafe = target.resolve("failsafe-reports");
        List<Path> existing = List.of(surefire, failsafe).stream().filter(Files::isDirectory).toList();

        assertThat(existing).as("report directories to scan").isNotEmpty();
        assertThat(ArtifactScanner.scan(base, existing)).as("SEC-05 findings (rule, file, line)").isEmpty();

        for (Path root : existing) {
            assertThat(ArtifactScanner.hasCanary(root)).as("test output reached %s", base.relativize(root)).isTrue();
        }
        if (Files.isDirectory(failsafe)) {
            assertThat(ArtifactScanner.hasApplicationLogCanary(failsafe))
                    .as("the structured application log reached %s", base.relativize(failsafe)).isTrue();
        }
        System.out.println("SEC05-ARTIFACT-SCAN roots=" + existing.size() + " canary=present appLog="
                + Files.isDirectory(failsafe) + " findings=0");
    }
}
