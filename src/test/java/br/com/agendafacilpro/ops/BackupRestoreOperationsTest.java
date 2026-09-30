package br.com.agendafacilpro.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupRestoreOperationsTest {

    private static final Path BACKUP_SCRIPT = Path.of("ops/backup/backup-postgres.sh");
    private static final Path RETENTION_SCRIPT = Path.of("ops/backup/backup-retention.sh");
    private static final Path RESTORE_SCRIPT = Path.of("ops/backup/restore-postgres.sh");

    @Test
    void backupUsesValidatedCustomArchiveEncryptionChecksumAndCleanup() throws Exception {
        String script = read(BACKUP_SCRIPT);

        assertThat(script)
                .contains("set -euo pipefail")
                .contains("umask 0077")
                .contains("pg_dump --version")
                .contains("pg_restore --version")
                .contains("--format=custom")
                .contains("--no-owner")
                .contains("--no-acl")
                .contains("pg_restore --list \"$plain_archive\"")
                .contains("--recipient \"$recipient\"")
                .contains("--encrypt \"$plain_archive\"")
                .contains("sha256sum \"$archive_encrypted_name\"")
                .contains("sha256sum --check \"$checksum_name\"")
                .contains("trap cleanup EXIT")
                .contains("mv -T -- \"$partial_bundle\" \"$final_bundle\"")
                .doesNotContain("set -x")
                .doesNotContain("admin123")
                .doesNotContain("password=super-secret");

        assertThat(script.indexOf("pg_restore --list \"$plain_archive\""))
                .isLessThan(script.indexOf("--encrypt \"$plain_archive\""));
        assertThat(script.indexOf("sha256sum --check \"$checksum_name\""))
                .isLessThan(script.indexOf("mv -T -- \"$partial_bundle\" \"$final_bundle\""));
    }

    @Test
    void retentionKeepsNewestCountsAndDoesNotDuplicateOrOverwritePeriodSnapshots(@TempDir Path temp)
            throws Exception {
        Path repository = temp.resolve("repository");
        Files.createDirectories(repository.resolve("daily"));
        Files.createDirectories(repository.resolve("weekly"));
        Files.createDirectories(repository.resolve("monthly"));

        LocalDate firstDate = LocalDate.of(2024, 1, 15);
        for (int index = 0; index < 20; index++) {
            LocalDate date = firstDate.plusMonths(index);
            Path daily = createBundle(repository, date, "060000", "archive-" + index);
            runSuccessfully("bash", RETENTION_SCRIPT.toString(), repository.toString(), daily.toString(),
                    isoWeek(date), date.format(DateTimeFormatter.ofPattern("uuuu-MM")));
        }

        assertThat(directoryNames(repository.resolve("daily")))
                .hasSize(14)
                .containsExactlyElementsOf(expectedDailyNames(firstDate, 6, 20));
        assertThat(directoryNames(repository.resolve("weekly")))
                .hasSize(8)
                .containsExactlyElementsOf(expectedWeekNames(firstDate, 12, 20));
        assertThat(directoryNames(repository.resolve("monthly")))
                .hasSize(6)
                .containsExactlyElementsOf(expectedMonthNames(firstDate, 14, 20));

        LocalDate latest = firstDate.plusMonths(19);
        Path weeklySnapshot = repository.resolve("weekly").resolve(isoWeek(latest));
        Path monthlySnapshot = repository.resolve("monthly")
                .resolve(latest.format(DateTimeFormatter.ofPattern("uuuu-MM")));
        byte[] weeklyBefore = encryptedBytes(weeklySnapshot);
        byte[] monthlyBefore = encryptedBytes(monthlySnapshot);

        Path duplicatePeriod = createBundle(repository, latest.plusDays(1), "060001", "newer-same-period");
        runSuccessfully("bash", RETENTION_SCRIPT.toString(), repository.toString(), duplicatePeriod.toString(),
                isoWeek(latest), latest.format(DateTimeFormatter.ofPattern("uuuu-MM")));

        assertThat(directoryNames(repository.resolve("daily"))).hasSize(14);
        assertThat(directoryNames(repository.resolve("weekly"))).hasSize(8);
        assertThat(directoryNames(repository.resolve("monthly"))).hasSize(6);
        assertThat(encryptedBytes(weeklySnapshot)).isEqualTo(weeklyBefore);
        assertThat(encryptedBytes(monthlySnapshot)).isEqualTo(monthlyBefore);
    }

    @Test
    void restoreChecksIntegrityAndFreshTargetBeforeStreamingTransactionally() throws Exception {
        String script = read(RESTORE_SCRIPT);

        assertThat(script)
                .contains("set -euo pipefail")
                .contains("RESTORE_CONFIRM_DATABASE")
                .contains("[[ \"$RESTORE_CONFIRM_DATABASE\" == \"$PGDATABASE\" ]]")
                .contains("sha256sum --check")
                .contains("user_table_count")
                .contains("o banco alvo não está vazio")
                .contains("gpg --batch --no-tty --quiet --decrypt")
                .contains("pg_restore --list")
                .contains("--no-owner")
                .contains("--no-acl")
                .contains("--exit-on-error")
                .contains("--single-transaction")
                .contains("flyway_schema_history")
                .doesNotContain("--clean")
                .doesNotContain("dropdb")
                .doesNotContain("set -x");

        assertThat(script.indexOf("sha256sum --check"))
                .isLessThan(script.indexOf("gpg --batch --no-tty --quiet --decrypt"));
        assertThat(script.indexOf("user_table_count"))
                .isLessThan(script.indexOf("gpg --batch --no-tty --quiet --decrypt"));
    }

    @Test
    void environmentExampleContainsNoCredentialOrRecipient() throws Exception {
        String environment = read(Path.of("ops/backup/agendafacil-backup.env.example"));

        assertThat(environment)
                .contains("PGHOST=\n")
                .contains("PGPORT=5432")
                .contains("PGDATABASE=\n")
                .contains("PGUSER=\n")
                .contains("PGPASSWORD=\n")
                .contains("PGSSLMODE=\n")
                .contains("BACKUP_GPG_RECIPIENT=\n")
                .contains("BACKUP_OUTPUT_DIR=/var/lib/agendafacil-backup/repository")
                .doesNotContain("admin123")
                .doesNotContain("jdbc:postgresql://");
    }

    @Test
    void systemdRunsBackupAsDedicatedRestrictedNetworkCapableUser() throws Exception {
        String service = read(Path.of("ops/systemd/agendafacil-backup.service"));

        assertThat(service)
                .contains("Type=oneshot")
                .contains("User=agendafacil-backup")
                .contains("Group=agendafacil-backup")
                .contains("EnvironmentFile=/etc/agendafacil/backup.env")
                .contains("RuntimeDirectory=agendafacil-backup")
                .contains("RuntimeDirectoryMode=0700")
                .contains("StateDirectory=agendafacil-backup")
                .contains("StateDirectoryMode=0700")
                .contains("UMask=0077")
                .contains("NoNewPrivileges=true")
                .contains("PrivateTmp=true")
                .contains("ProtectHome=true")
                .contains("ProtectSystem=strict")
                .contains("ReadWritePaths=/run/agendafacil-backup /var/lib/agendafacil-backup")
                .doesNotContain("User=root")
                .doesNotContain("PrivateNetwork=true");
    }

    @Test
    void timerRunsDailyAtExplicitUtcTimeAndCatchesMissedRuns() throws Exception {
        String timer = read(Path.of("ops/systemd/agendafacil-backup.timer"));

        assertThat(timer)
                .contains("OnCalendar=*-*-* 06:00:00 UTC")
                .contains("Persistent=true")
                .contains("Unit=agendafacil-backup.service")
                .contains("WantedBy=timers.target");
    }

    @Test
    void versionedShellScriptsHaveValidBashSyntax() throws Exception {
        runSuccessfully("bash", "-n", BACKUP_SCRIPT.toString());
        runSuccessfully("bash", "-n", RETENTION_SCRIPT.toString());
        runSuccessfully("bash", "-n", RESTORE_SCRIPT.toString());
    }

    @Test
    void runbookSeparatesArtifactsBackupsOffsiteRequirementAndUnprovenTargets() throws Exception {
        String runbook = read(Path.of("docs/BACKUP_RESTORE.md"));

        assertThat(runbook)
                .contains("formato custom")
                .contains("14 backups diários, 8 semanais e 6 mensais")
                .contains("RPO de até 24 horas")
                .contains("RTO de até 4 horas")
                .contains("São objetivos, não garantias")
                .contains("banco novo e vazio")
                .contains("armazenamento off-site")
                .contains("Depois de cada transferência, execute `sha256sum --check`")
                .contains("não configura storage off-site")
                .contains("o repositório GitHub")
                .contains("artifact JAR do GitHub Actions")
                .contains("migrations Flyway")
                .contains("snapshot isolado do VPS")
                .contains("banco local de desenvolvimento");
    }

    private Path createBundle(Path repository, LocalDate date, String time, String contents) throws Exception {
        String name = "agendafacil-" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "T" + time + "Z";
        Path bundle = Files.createDirectory(repository.resolve("daily").resolve(name));
        Path encrypted = bundle.resolve(name + ".dump.gpg");
        Files.writeString(encrypted, contents, StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(contents.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(bundle.resolve(name + ".dump.gpg.sha256"),
                digest + "  " + encrypted.getFileName() + System.lineSeparator(), StandardCharsets.UTF_8);
        return bundle;
    }

    private List<String> expectedDailyNames(LocalDate firstDate, int fromInclusive, int toExclusive) {
        return java.util.stream.IntStream.range(fromInclusive, toExclusive)
                .mapToObj(index -> "agendafacil-" + firstDate.plusMonths(index)
                        .format(DateTimeFormatter.BASIC_ISO_DATE) + "T060000Z")
                .toList();
    }

    private List<String> expectedWeekNames(LocalDate firstDate, int fromInclusive, int toExclusive) {
        return java.util.stream.IntStream.range(fromInclusive, toExclusive)
                .mapToObj(index -> isoWeek(firstDate.plusMonths(index)))
                .toList();
    }

    private List<String> expectedMonthNames(LocalDate firstDate, int fromInclusive, int toExclusive) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM");
        return java.util.stream.IntStream.range(fromInclusive, toExclusive)
                .mapToObj(index -> firstDate.plusMonths(index).format(formatter))
                .toList();
    }

    private String isoWeek(LocalDate date) {
        WeekFields iso = WeekFields.ISO;
        return String.format(Locale.ROOT, "%04d-W%02d",
                date.get(iso.weekBasedYear()), date.get(iso.weekOfWeekBasedYear()));
    }

    private List<String> directoryNames(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    private byte[] encryptedBytes(Path bundle) throws IOException {
        try (var paths = Files.list(bundle)) {
            Path encrypted = paths.filter(path -> path.getFileName().toString().endsWith(".dump.gpg"))
                    .findFirst()
                    .orElseThrow();
            return Files.readAllBytes(encrypted);
        }
    }

    private String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private void runSuccessfully(String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor())
                .withFailMessage("Command failed (%s):%n%s", String.join(" ", command), output)
                .isZero();
    }
}
