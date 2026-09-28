package br.com.agendafacilpro.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitialCredentialAndPublisherTest {
    @TempDir Path tempDirectory;

    @Test
    void generatedCredentialUsesAtLeastTwentyFourBytesOfEntropy() {
        InitialCredentialGenerator generator = new InitialCredentialGenerator();

        char[] first = generator.generate();
        char[] second = generator.generate();

        assertThat(first).hasSize(32);
        assertThat(second).hasSize(32);
        assertThat(first).isNotEqualTo(second);
        assertThat(new String(first)).matches("[A-Za-z0-9_-]{32}");
    }

    @Test
    void interactiveChannelPublishesCredentialOnlyOnce() {
        StringWriter output = new StringWriter();
        SecureCredentialPublisher publisher = new SecureCredentialPublisher(() -> new PrintWriter(output));
        CredentialPublisher.CredentialChannel channel = publisher.prepare(null);
        char[] credential = "unique-secret-value".toCharArray();

        channel.publish(credential);

        assertThat(output.toString()).containsOnlyOnce(new String(credential));
        assertThatThrownBy(() -> channel.publish(credential))
                .isInstanceOf(ProvisioningException.class).hasMessageContaining("já foi disponibilizada");
        assertThat(output.toString()).containsOnlyOnce(new String(credential));
    }

    @Test
    void fileChannelCreatesNewSecretFileWithOwnerOnlyPermissions() throws Exception {
        Path output = tempDirectory.resolve("owner-password.txt").toAbsolutePath();
        SecureCredentialPublisher publisher = new SecureCredentialPublisher(() -> null);
        char[] credential = "file-only-secret".toCharArray();

        publisher.prepare(output.toString()).publish(credential);

        assertThat(Files.readString(output)).containsOnlyOnce(new String(credential));
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(output))).isEqualTo("rw-------");
        assertThatThrownBy(() -> publisher.prepare(output.toString()))
                .isInstanceOf(ProvisioningException.class).hasMessageContaining("já existe");
    }

    @Test
    void refusesNonInteractiveExecutionWithoutExplicitSecureFile() {
        SecureCredentialPublisher publisher = new SecureCredentialPublisher(() -> null);

        assertThatThrownBy(() -> publisher.prepare(null))
                .isInstanceOf(ProvisioningException.class)
                .hasMessageContaining("Nenhum console interativo");
    }
}
