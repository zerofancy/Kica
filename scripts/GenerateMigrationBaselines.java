import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.util.Comparator;

public final class GenerateMigrationBaselines {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected SQL baseline directory and output directory");
        }
        Path sourceDirectory = Path.of(arguments[0]);
        Path outputDirectory = Path.of(arguments[1]);
        Files.createDirectories(outputDirectory);
        try (var outputs = Files.list(outputDirectory)) {
            for (Path output : outputs.filter(path -> path.getFileName().toString().endsWith(".db")).toList()) {
                Files.delete(output);
            }
        }
        try (var sources = Files.list(sourceDirectory)) {
            var baselines = sources.filter(path -> path.getFileName().toString().endsWith(".sql"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
            if (baselines.isEmpty()) {
                throw new IllegalStateException("No SQL migration baselines found");
            }
            for (Path baseline : baselines) {
                String filename = baseline.getFileName().toString();
                if (!filename.matches("[1-9][0-9]*\\.sql")) {
                    throw new IllegalArgumentException("Invalid baseline version: " + filename);
                }
                int version = Integer.parseInt(filename.substring(0, filename.length() - 4));
                Path temporary = Files.createTempFile(outputDirectory, ".baseline-", ".db");
                try {
                    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + temporary.toAbsolutePath());
                         var statement = connection.createStatement()) {
                        statement.executeUpdate(Files.readString(baseline, StandardCharsets.UTF_8));
                        statement.execute("PRAGMA user_version = " + version);
                    }
                    Files.move(temporary, outputDirectory.resolve(version + ".db"), StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }
    }
}
