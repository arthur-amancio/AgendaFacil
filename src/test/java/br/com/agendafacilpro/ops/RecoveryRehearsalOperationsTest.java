package br.com.agendafacilpro.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RecoveryRehearsalOperationsTest {

    private static final Path SCRIPT = Path.of("ops/recovery/run-recovery-rehearsal.sh");

    @Test
    void rehearsalScriptHasValidBashSyntax() throws Exception {
        Process process = new ProcessBuilder("bash", "-n", SCRIPT.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor())
                .withFailMessage("bash -n failed:%n%s", output)
                .isZero();
    }

    @Test
    void rehearsalUsesRealP067ScriptsPostgres16AndTheBuiltJar() throws Exception {
        String script = read(SCRIPT);

        assertThat(script)
                .contains("backup_script=\"$project_root/ops/backup/backup-postgres.sh\"")
                .contains("restore_script=\"$project_root/ops/backup/restore-postgres.sh\"")
                .contains("\"$backup_script\"")
                .contains("\"$restore_script\" \"$bundle\"")
                .contains("postgres:16-alpine")
                .contains("java -jar \"$application_jar\"")
                .contains("SPRING_PROFILES_ACTIVE=prod")
                .contains("/actuator/health/readiness")
                .contains("/login")
                .contains("/agenda/recovery-rehearsal-canary")
                .doesNotContain("spring.profiles.active=dev")
                .doesNotContain("mvn spring-boot:run");
    }

    @Test
    void rehearsalSeparatesEphemeralKeysAndExercisesFailClosedPaths() throws Exception {
        String script = read(SCRIPT);

        assertThat(script)
                .contains("recovery_gnupg=\"$work_root/recovery-gnupg\"")
                .contains("backup_gnupg=\"$work_root/backup-gnupg\"")
                .contains("--quick-generate-key")
                .contains("--export \"$gpg_fingerprint\"")
                .contains("list-secret-keys")
                .contains("backup-host-secret-key")
                .contains("tampered-checksum")
                .contains("mismatched-target")
                .contains("nonempty-target")
                .contains("incomplete-bundle")
                .contains("missing-private-key")
                .contains("rm -rf -- \"$work_root\"")
                .doesNotContain("set -x");
    }

    @Test
    void simulatedOffsiteBoundaryDiscardsOriginalBeforeRestoreAndComparesData() throws Exception {
        String script = read(SCRIPT);

        assertThat(script)
                .contains("simulated-offsite-boundary")
                .contains("sha256sum --check --strict")
                .contains("rm -rf -- \"$backup_repository\"")
                .contains("original local repository discarded")
                .contains("restored_canary")
                .contains("restored_establishments")
                .contains("restored_appointments")
                .contains("restored Flyway history differs from source")
                .contains("Backup duration milliseconds:")
                .contains("Recovery duration milliseconds:")
                .contains("Production RPO/RTO proof: NOT ESTABLISHED")
                .doesNotContain("AWS")
                .doesNotContain("S3")
                .doesNotContain("Backblaze")
                .doesNotContain("rclone");

        assertThat(script.indexOf("rm -rf -- \"$backup_repository\""))
                .isLessThan(script.indexOf("run_restore \"$recovered_bundle\" \"$recovery_database\""));
    }

    @Test
    void fixtureIsClearlySyntheticAndContainsDeterministicCanary() throws Exception {
        String fixture = read(Path.of("ops/recovery/rehearsal-fixture.sql"));

        assertThat(fixture)
                .contains("RECOVERY_REHEARSAL_CANARY_V1")
                .contains("recovery-rehearsal-canary")
                .contains("recovery-rehearsal-token-000001")
                .contains("192.0.2.10")
                .doesNotContain("admin@demo.local")
                .doesNotContain("CREATE TABLE")
                .doesNotContain("ALTER TABLE");
    }

    @Test
    void workflowRunsRehearsalAfterArtifactPreparationWithoutUploadingBackups() throws Exception {
        String workflow = read(Path.of(".github/workflows/ci.yml"));

        assertThat(workflow)
                .contains("run: mvn clean verify")
                .contains("Prepare distribution artifact")
                .contains("Prepare PostgreSQL 16 client")
                .contains("Run disposable recovery rehearsal")
                .contains("run: ops/recovery/run-recovery-rehearsal.sh dist/agendafacil-pro.jar")
                .contains("path: dist/")
                .doesNotContain("path: **/*.dump")
                .doesNotContain("path: **/*.dump.gpg")
                .doesNotContain("path: ops/recovery");

        assertThat(workflow.indexOf("Prepare distribution artifact"))
                .isLessThan(workflow.indexOf("Run disposable recovery rehearsal"));
        assertThat(workflow.indexOf("Run disposable recovery rehearsal"))
                .isLessThan(workflow.indexOf("Upload distribution artifact"));
    }

    @Test
    void documentationKeepsRealOffsiteAndProductionProofAsPilotBlockers() throws Exception {
        String documentation = read(Path.of("docs/RECOVERY_REHEARSAL.md")).replaceAll("\\s+", " ");

        assertThat(documentation)
                .contains("source")
                .contains("recovery PostgreSQL")
                .contains("simulated off-site boundary")
                .contains("não comprova off-site real")
                .contains("readiness `UP`")
                .contains("checksum adulterado")
                .contains("RTO de produção")
                .contains("RPO de 24 horas")
                .contains("Bloqueadores antes do piloto real")
                .contains("destino off-site independente")
                .doesNotContain("production ready")
                .doesNotContain("pilot ready");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path);
    }
}
