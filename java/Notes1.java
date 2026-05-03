import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;

public class Notes1 {

    static final Scanner scanner = new Scanner(System.in);

    static final Path NOTES_DIR = Paths.get(System.getProperty("user.home"), ".notes", "notes");

    static final Path DATASETS_DIR = Paths.get(System.getProperty("user.home"), ".notes", "datasets");

    public static void main(String[] args) throws IOException {
        Files.createDirectories(NOTES_DIR);
        Files.createDirectories(DATASETS_DIR);

        while (true) {
            System.out.println("\n=== NoteVault ===");
            System.out.println("1. Create note");
            System.out.println("2. Read note");
            System.out.println("3. Update note");
            System.out.println("4. Delete note");
            System.out.println("5. List notes");
            System.out.println("6. Search notes");
            System.out.println("7. Add dataset");
            System.out.println("8. List datasets");
            System.out.println("9. Preview dataset");
            System.out.println("10. Delete dataset");
            System.out.println("0. Exit");
            System.out.print("Choose: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1" -> createNote();
                case "2" -> readNote();
                case "3" -> updateNote();
                case "4" -> deleteNote();
                case "5" -> listNotes();
                case "6" -> searchNotes();
                case "7" -> addDataset();
                case "8" -> listDatasets();
                case "9" -> previewDataset();
                case "10" -> deleteDataset();
                case "0" -> {
                    System.out.println("Goodbye!");
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    static void createNote() throws IOException {
        System.out.print("Title: ");
        String title = scanner.nextLine();

        System.out.print("Tags comma separated: ");
        String tags = scanner.nextLine();

        System.out.print("Author: ");
        String author = scanner.nextLine();

        System.out.print("Status: ");
        String status = scanner.nextLine();

        System.out.print("Priority: ");
        String priority = scanner.nextLine();

        System.out.println("Enter note content. Type END on a new line to finish:");

        StringBuilder body = new StringBuilder();

        while (true) {
            String line = scanner.nextLine();

            if (line.equalsIgnoreCase("END")) {
                break;
            }

            body.append(line).append("\n");
        }

        String id = makeId(title);
        String now = Instant.now().toString();

        String note = """
                ---
                title: %s
                created: %s
                modified: %s
                tags: [%s]
                author: %s
                status: %s
                priority: %s
                ---

                # %s

                %s
                """.formatted(
                title,
                now,
                now,
                tags,
                author,
                status,
                priority,
                title,
                body.toString());

        Files.writeString(
                NOTES_DIR.resolve(id + ".note"),
                note,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("Created note: " + id);
    }

    static void readNote() throws IOException {
        System.out.print("Enter note id: ");
        String id = scanner.nextLine();

        Path file = NOTES_DIR.resolve(id + ".note");

        if (!Files.exists(file)) {
            System.out.println("Note not found.");
            return;
        }

        System.out.println("\n" + Files.readString(file));
    }

    static void updateNote() throws IOException {
        System.out.print("Enter note id: ");
        String id = scanner.nextLine();

        Path file = NOTES_DIR.resolve(id + ".note");

        if (!Files.exists(file)) {
            System.out.println("Note not found.");
            return;
        }

        String existing = Files.readString(file);

        System.out.println("Enter new note content. Type END on a new line to finish:");

        StringBuilder body = new StringBuilder();

        while (true) {
            String line = scanner.nextLine();

            if (line.equalsIgnoreCase("END")) {
                break;
            }

            body.append(line).append("\n");
        }

        String updated = existing.replaceFirst(
                "modified: .*",
                "modified: " + Instant.now());

        String[] parts = updated.split("---", 3);

        if (parts.length == 3) {
            updated = "---" + parts[1] + "---\n\n" + body;
        } else {
            updated = body.toString();
        }

        Files.writeString(
                file,
                updated,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("Updated note.");
    }

    static void deleteNote() throws IOException {
        System.out.print("Enter note id: ");
        String id = scanner.nextLine();

        Path file = NOTES_DIR.resolve(id + ".note");

        if (Files.deleteIfExists(file)) {
            System.out.println("Deleted note.");
        } else {
            System.out.println("Note not found.");
        }
    }

    static void listNotes() throws IOException {
        System.out.println("\n--- Notes ---");

        try (var stream = Files.list(NOTES_DIR)) {
            stream.filter(path -> path.toString().endsWith(".note"))
                    .forEach(path -> System.out.println(
                            path.getFileName().toString().replace(".note", "")));
        }
    }

    static void searchNotes() throws IOException {
        System.out.print("Search text: ");
        String query = scanner.nextLine().toLowerCase();

        System.out.println("\n--- Search Results ---");

        try (var stream = Files.list(NOTES_DIR)) {
            stream.filter(path -> path.toString().endsWith(".note"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path).toLowerCase();

                            if (content.contains(query)) {
                                System.out.println(
                                        path.getFileName().toString().replace(".note", ""));
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    static void addDataset() throws IOException {
        System.out.print("Enter CSV/JSON file path: ");
        String inputPath = scanner.nextLine();

        Path source = Paths.get(inputPath);

        if (!Files.exists(source)) {
            System.out.println("File not found.");
            return;
        }

        String fileName = source.getFileName().toString();

        if (!fileName.endsWith(".csv") && !fileName.endsWith(".json")) {
            System.out.println("Only .csv and .json files allowed.");
            return;
        }

        System.out.print("Dataset title: ");
        String title = scanner.nextLine();

        System.out.print("Author: ");
        String author = scanner.nextLine();

        System.out.print("Tags comma separated: ");
        String tags = scanner.nextLine();

        Path destination = DATASETS_DIR.resolve(fileName);

        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

        String id = fileName.replace(".csv", "").replace(".json", "");
        String format = fileName.endsWith(".csv") ? "csv" : "json";
        String now = Instant.now().toString();

        long rowCount = countRows(destination, format);
        String schema = inferSchema(destination, format);

        String metadata = """
                id: %s
                title: %s
                author: %s
                created: %s
                modified: %s
                tags: [%s]
                format: %s
                path: %s
                rowCount: %d
                schema:
                %s
                """.formatted(
                id,
                title,
                author,
                now,
                now,
                tags,
                format,
                fileName,
                rowCount,
                schema);

        Files.writeString(
                DATASETS_DIR.resolve(id + ".dataset.yml"),
                metadata,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("Dataset added: " + id);
    }

    static void listDatasets() throws IOException {
        System.out.println("\n--- Datasets ---");

        try (var stream = Files.list(DATASETS_DIR)) {
            stream.filter(path -> path.toString().endsWith(".dataset.yml"))
                    .forEach(path -> System.out.println(
                            path.getFileName().toString().replace(".dataset.yml", "")));
        }
    }

    static void previewDataset() throws IOException {
        System.out.print("Enter dataset file name, example sales.csv: ");
        String fileName = scanner.nextLine();

        Path file = DATASETS_DIR.resolve(fileName);

        if (!Files.exists(file)) {
            System.out.println("Dataset not found.");
            return;
        }

        System.out.println("\n--- Preview ---");

        try (var lines = Files.lines(file)) {
            lines.limit(10).forEach(System.out::println);
        }
    }

    static void deleteDataset() throws IOException {
        System.out.print("Enter dataset id: ");
        String id = scanner.nextLine();

        boolean deletedCsv = Files.deleteIfExists(DATASETS_DIR.resolve(id + ".csv"));
        boolean deletedJson = Files.deleteIfExists(DATASETS_DIR.resolve(id + ".json"));
        boolean deletedMeta = Files.deleteIfExists(DATASETS_DIR.resolve(id + ".dataset.yml"));

        if (deletedCsv || deletedJson || deletedMeta) {
            System.out.println("Dataset deleted.");
        } else {
            System.out.println("Dataset not found.");
        }
    }

    static long countRows(Path file, String format) throws IOException {
        if (format.equals("csv")) {
            try (var lines = Files.lines(file)) {
                long count = lines.count();
                return Math.max(0, count - 1);
            }
        }

        String content = Files.readString(file).trim();

        if (content.startsWith("[")) {
            return content.split("\\{").length - 1;
        }

        return 1;
    }

    static String inferSchema(Path file, String format) throws IOException {
        if (format.equals("csv")) {
            List<String> lines = Files.readAllLines(file);

            if (lines.isEmpty()) {
                return "  []";
            }

            String[] headers = lines.get(0).split(",");
            String[] sample = lines.size() > 1 ? lines.get(1).split(",") : new String[0];

            StringBuilder schema = new StringBuilder();

            for (int i = 0; i < headers.length; i++) {
                String name = headers[i].trim();
                String value = i < sample.length ? sample[i].trim() : "";
                String type = inferType(value);

                schema.append("  - name: ").append(name).append("\n");
                schema.append("    type: ").append(type).append("\n");
            }

            return schema.toString();
        }

        return """
                  - name: json_document
                    type: object
                """;
    }

    static String inferType(String value) {
        if (value.matches("-?\\d+")) {
            return "integer";
        }

        if (value.matches("-?\\d+\\.\\d+")) {
            return "decimal";
        }

        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return "boolean";
        }

        return "string";
    }

    static String makeId(String title) {
        String id = title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        if (id.isBlank()) {
            id = "note-" + System.currentTimeMillis();
        }

        return id;
    }
}