package br.com.agendafacilpro.provisioning;

import java.io.Console;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class SecureCredentialPublisher implements CredentialPublisher {
    private final Supplier<PrintWriter> consoleWriter;

    public SecureCredentialPublisher() {
        this(() -> {
            Console console = System.console();
            return console == null ? null : console.writer();
        });
    }

    SecureCredentialPublisher(Supplier<PrintWriter> consoleWriter) {
        this.consoleWriter = consoleWriter;
    }

    @Override
    public CredentialChannel prepare(String outputFile) {
        if (outputFile != null && !outputFile.isBlank()) {
            return prepareFile(Path.of(outputFile.trim()));
        }
        PrintWriter writer = consoleWriter.get();
        if (writer == null) {
            throw new ProvisioningException(
                    "Nenhum console interativo disponível. Informe um arquivo absoluto e inexistente em app.provisioning.password-output-file.");
        }
        return singleUse(credential -> {
            writer.print("Senha inicial do OWNER: ");
            writer.println(credential);
            writer.flush();
        });
    }

    private CredentialChannel prepareFile(Path requestedPath) {
        Path path = requestedPath.normalize();
        if (!path.isAbsolute()) {
            throw new ProvisioningException("O arquivo de saída da senha deve usar caminho absoluto.");
        }
        Path parent = path.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new ProvisioningException("O diretório do arquivo de saída da senha não existe.");
        }
        if (Files.exists(path)) {
            throw new ProvisioningException("O arquivo de saída da senha já existe; escolha um caminho novo.");
        }
        try {
            FileStore store = Files.getFileStore(parent);
            if (!store.supportsFileAttributeView(PosixFileAttributeView.class)) {
                throw new ProvisioningException("O filesystem não permite garantir permissão 0600; use um console interativo seguro.");
            }
        } catch (IOException ex) {
            throw new ProvisioningException("Não foi possível validar o destino seguro da senha.", ex);
        }

        return singleUse(credential -> writeSecretFile(path, credential));
    }

    private void writeSecretFile(Path path, char[] credential) {
        boolean created = false;
        try {
            try (SeekableByteChannel channel = Files.newByteChannel(
                    path,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                 Writer writer = Channels.newWriter(channel, StandardCharsets.UTF_8)) {
                created = true;
                writer.write(credential);
                writer.write(System.lineSeparator());
            }
        } catch (IOException ex) {
            if (created) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A mensagem funcional permanece sem detalhes do filesystem.
                }
            }
            throw new ProvisioningException(
                    "Tenant criado, mas não foi possível disponibilizar a senha no arquivo seguro. Intervenção operacional é necessária.", ex);
        }
    }

    private CredentialChannel singleUse(CredentialWriter writer) {
        AtomicBoolean published = new AtomicBoolean();
        return credential -> {
            if (!published.compareAndSet(false, true)) {
                throw new ProvisioningException("A credencial inicial já foi disponibilizada.");
            }
            writer.write(credential);
        };
    }

    @FunctionalInterface
    private interface CredentialWriter {
        void write(char[] credential);
    }
}
