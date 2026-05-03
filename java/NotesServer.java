import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Collectors;

public class NotesServer {

    static final Path NOTES_DIR = Paths.get(System.getProperty("user.home"), ".notes", "notes");

    static final Path DATASETS_DIR = Paths.get(System.getProperty("user.home"), ".notes", "datasets");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(NOTES_DIR);
        Files.createDirectories(DATASETS_DIR);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", NotesServer::home);
        server.createContext("/api/notes", NotesServer::notesApi);
        server.createContext("/api/search", NotesServer::searchApi);
        server.createContext("/api/datasets", NotesServer::datasetsApi);

        server.start();

        System.out.println("NoteVault website running:");
        System.out.println("http://localhost:8080");
    }

    static void home(HttpExchange exchange) throws IOException {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>NoteVault</title>
                    <style>
                        body { font-family: Arial; margin: 40px; background: #f6f6f6; }
                        textarea { width: 100%; height: 200px; }
                        input { width: 100%; padding: 8px; margin: 5px 0; }
                        button { padding: 10px; margin: 5px 0; cursor: pointer; }
                        .note { background: white; padding: 15px; margin: 10px 0; border-radius: 8px; }
                        pre { white-space: pre-wrap; }
                    </style>
                </head>
                <body>
                    <h1>NoteVault</h1>

                    <h2>Create Note</h2>
                    <input id="title" placeholder="Title">
                    <input id="tags" placeholder="Tags comma separated">
                    <textarea id="content" placeholder="Write note content"></textarea>
                    <button onclick="createNote()">Create Note</button>

                    <h2>Search</h2>
                    <input id="search" placeholder="Search notes">
                    <button onclick="searchNotes()">Search</button>

                    <h2>Notes</h2>
                    <button onclick="loadNotes()">Refresh Notes</button>
                    <div id="notes"></div>

                    <script>
                        async function loadNotes() {
                            const res = await fetch('/api/notes');
                            const notes = await res.json();

                            document.getElementById('notes').innerHTML = notes.map(n => `
                                <div class="note">
                                    <h3>${n.id}</h3>
                                    <pre>${n.content}</pre>
                                    <button onclick="deleteNote('${n.id}')">Delete</button>
                                </div>
                            `).join('');
                        }

                        async function createNote() {
                            const title = document.getElementById('title').value;
                            const tags = document.getElementById('tags').value;
                            const content = document.getElementById('content').value;

                            await fetch('/api/notes', {
                                method: 'POST',
                                body: JSON.stringify({ title, tags, content })
                            });

                            document.getElementById('title').value = '';
                            document.getElementById('tags').value = '';
                            document.getElementById('content').value = '';

                            loadNotes();
                        }

                        async function deleteNote(id) {
                            await fetch('/api/notes/' + id, { method: 'DELETE' });
                            loadNotes();
                        }

                        async function searchNotes() {
                            const q = document.getElementById('search').value;
                            const res = await fetch('/api/search?q=' + encodeURIComponent(q));
                            const notes = await res.json();

                            document.getElementById('notes').innerHTML = notes.map(n => `
                                <div class="note">
                                    <h3>${n.id}</h3>
                                    <pre>${n.content}</pre>
                                </div>
                            `).join('');
                        }

                        loadNotes();
                    </script>
                </body>
                </html>
                """;

        send(exchange, 200, html, "text/html");
    }

    static void notesApi(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (method.equals("GET") && path.equals("/api/notes")) {
            String json = listNotesJson();
            send(exchange, 200, json, "application/json");
            return;
        }

        if (method.equals("POST") && path.equals("/api/notes")) {
            String body = readBody(exchange);

            String title = extractJsonValue(body, "title");
            String tags = extractJsonValue(body, "tags");
            String content = extractJsonValue(body, "content");

            String id = makeId(title);
            String now = java.time.Instant.now().toString();

            String note = """
                    ---
                    title: %s
                    created: %s
                    modified: %s
                    tags: [%s]
                    ---

                    # %s

                    %s
                    """.formatted(title, now, now, tags, title, content);

            Files.writeString(
                    NOTES_DIR.resolve(id + ".note"),
                    note,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            send(exchange, 201, "{\"id\":\"" + id + "\"}", "application/json");
            return;
        }

        if (path.startsWith("/api/notes/")) {
            String id = path.substring("/api/notes/".length());
            Path file = NOTES_DIR.resolve(id + ".note");

            if (method.equals("GET")) {
                if (!Files.exists(file)) {
                    send(exchange, 404, "Note not found", "text/plain");
                    return;
                }

                String content = Files.readString(file);
                send(exchange, 200, "{\"id\":\"" + id + "\",\"content\":\"" + escapeJson(content) + "\"}",
                        "application/json");
                return;
            }

            if (method.equals("DELETE")) {
                Files.deleteIfExists(file);
                send(exchange, 200, "{\"message\":\"deleted\"}", "application/json");
                return;
            }

            if (method.equals("PUT")) {
                String body = readBody(exchange);
                String content = extractJsonValue(body, "content");

                if (!Files.exists(file)) {
                    send(exchange, 404, "Note not found", "text/plain");
                    return;
                }

                Files.writeString(file, content, StandardOpenOption.TRUNCATE_EXISTING);
                send(exchange, 200, "{\"message\":\"updated\"}", "application/json");
                return;
            }
        }

        send(exchange, 404, "Not found", "text/plain");
    }

    static void searchApi(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();

        if (query == null || !query.startsWith("q=")) {
            send(exchange, 400, "Missing q", "text/plain");
            return;
        }

        String q = java.net.URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8).toLowerCase();

        String json = Files.list(NOTES_DIR)
                .filter(p -> p.toString().endsWith(".note"))
                .filter(p -> {
                    try {
                        return Files.readString(p).toLowerCase().contains(q);
                    } catch (IOException e) {
                        return false;
                    }
                })
                .map(p -> {
                    try {
                        String id = p.getFileName().toString().replace(".note", "");
                        String content = Files.readString(p);
                        return "{\"id\":\"" + id + "\",\"content\":\"" + escapeJson(content) + "\"}";
                    } catch (IOException e) {
                        return "";
                    }
                })
                .collect(Collectors.joining(",", "[", "]"));

        send(exchange, 200, json, "application/json");
    }

    static void datasetsApi(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (method.equals("GET") && path.equals("/api/datasets")) {
            String json = Files.list(DATASETS_DIR)
                    .filter(p -> p.toString().endsWith(".dataset.yml"))
                    .map(p -> "\"" + p.getFileName().toString().replace(".dataset.yml", "") + "\"")
                    .collect(Collectors.joining(",", "[", "]"));

            send(exchange, 200, json, "application/json");
            return;
        }

        if (path.startsWith("/api/datasets/") && path.endsWith("/preview")) {
            String id = path.replace("/api/datasets/", "").replace("/preview", "");

            Path csv = DATASETS_DIR.resolve(id + ".csv");
            Path json = DATASETS_DIR.resolve(id + ".json");

            Path file = Files.exists(csv) ? csv : json;

            if (!Files.exists(file)) {
                send(exchange, 404, "Dataset not found", "text/plain");
                return;
            }

            String preview = Files.lines(file).limit(10).collect(Collectors.joining("\n"));
            send(exchange, 200, preview, "text/plain");
            return;
        }

        send(exchange, 404, "Dataset endpoint not found", "text/plain");
    }

    static String listNotesJson() throws IOException {
        return Files.list(NOTES_DIR)
                .filter(p -> p.toString().endsWith(".note"))
                .map(p -> {
                    try {
                        String id = p.getFileName().toString().replace(".note", "");
                        String content = Files.readString(p);
                        return "{\"id\":\"" + id + "\",\"content\":\"" + escapeJson(content) + "\"}";
                    } catch (IOException e) {
                        return "";
                    }
                })
                .collect(Collectors.joining(",", "[", "]"));
    }

    static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static void send(HttpExchange exchange, int status, String response, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);

        if (start == -1) {
            return "";
        }

        start = json.indexOf("\"", start + search.length()) + 1;
        int end = json.indexOf("\"", start);

        if (start == 0 || end == -1) {
            return "";
        }

        return json.substring(start, end);
    }

    static String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
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
