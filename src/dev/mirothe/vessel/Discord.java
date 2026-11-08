package dev.mirothe.vessel;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// rich presence over the pipe the Discord app opens (a named pipe on Windows, a unix socket on Linux and Mac),
// so it needs no library and does nothing when Discord isn't running
final class Discord {
    static final String CLIENT_ID = "1563622288698638446";

    private static final long STARTED = System.currentTimeMillis() / 1000;
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "discord presence");
        t.setDaemon(true);
        return t;
    });
    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(Discord.class);
    private static java.io.Closeable pipe;
    private static java.io.DataInputStream in;
    private static java.io.OutputStream out;
    private static String last;
    private static volatile String wantDetails, wantState, wantLarge, wantLargeText, wantSmall;
    private static final AtomicBoolean queued = new AtomicBoolean();
    private static long lastSent;

    private Discord() {
    }

    static boolean enabled() {
        return PREFS.getBoolean("discord", true);
    }

    static void enabled(boolean on) {
        PREFS.putBoolean("discord", on);
        if (on && wantDetails != null) show(wantDetails, wantState, wantLarge, wantLargeText, wantSmall);
        if (!on) WORKER.execute(() -> {
            try {
                // an activity call with no activity in it clears the status
                if (pipe != null) send(1, Map.of("cmd", "SET_ACTIVITY", "args", Map.of("pid", ProcessHandle.current().pid()), "nonce", UUID.randomUUID().toString()));
            } catch (IOException ignored) {
            }
            close();
        });
    }

    // Discord drops anything past about 5 updates in 20 seconds, so wait for things to settle and only send the newest
    // large is an asset key from the developer portal or an https link, small is optional and sits in its corner
    static void show(String details, String state, String large, String largeText, String small) {
        wantDetails = details;
        wantState = state;
        wantLarge = large;
        wantLargeText = largeText;
        wantSmall = small;
        if (CLIENT_ID.isEmpty() || !enabled() || queued.getAndSet(true)) return;
        long wait = Math.max(1500, 4000 - (System.currentTimeMillis() - lastSent));
        WORKER.schedule(Discord::push, wait, TimeUnit.MILLISECONDS);
    }

    private static void push() {
        queued.set(false);
        String details = wantDetails, state = wantState, large = wantLarge, largeText = wantLargeText, small = wantSmall;
        String key = details + "\n" + state + "\n" + large + "\n" + small;
        if (!enabled() || key.equals(last) && pipe != null) return;
        try {
            if (pipe == null) connect();
            Map<String, Object> activity = new LinkedHashMap<>();
            activity.put("details", details);
            if (state != null) activity.put("state", state);
            activity.put("timestamps", Map.of("start", STARTED));
            Map<String, Object> assets = new LinkedHashMap<>();
            assets.put("large_image", large);
            assets.put("large_text", largeText);
            if (small != null) {
                assets.put("small_image", small);
                assets.put("small_text", Forge.TOOL + " " + Forge.VERSION);
            }
            activity.put("assets", assets);
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("pid", ProcessHandle.current().pid());
            args.put("activity", activity);
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("cmd", "SET_ACTIVITY");
            msg.put("args", args);
            msg.put("nonce", UUID.randomUUID().toString());
            send(1, msg);
            last = key;
            lastSent = System.currentTimeMillis();
        } catch (IOException e) {
            close();
        }
    }

    // Discord only shows pictures from a link, so the icon goes up to catbox and the link comes back
    static String upload(java.nio.file.Path png) throws IOException, InterruptedException {
        String boundary = "vessel" + UUID.randomUUID().toString().replace("-", "");
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        String nl = "\r\n";
        body.writeBytes(("--" + boundary + nl + "Content-Disposition: form-data; name=\"reqtype\"" + nl + nl + "fileupload" + nl
                + "--" + boundary + nl + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"icon.png\"" + nl
                + "Content-Type: image/png" + nl + nl).getBytes(StandardCharsets.UTF_8));
        body.writeBytes(java.nio.file.Files.readAllBytes(png));
        body.writeBytes((nl + "--" + boundary + "--" + nl).getBytes(StandardCharsets.UTF_8));
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://catbox.moe/user/api.php"))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", Forge.TOOL.replace(' ', '-') + "/" + Forge.VERSION)
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        String link = java.net.http.HttpClient.newHttpClient().send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).body().strip();
        if (!link.startsWith("https://")) throw new IOException("catbox said: " + link);
        if (!works(link)) throw new IOException("catbox kept an empty file at " + link);
        return link;
    }

    // catbox sometimes answers with a link to an empty file, Discord then shows a question mark
    static boolean works(String link) {
        try {
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(link))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("User-Agent", Forge.TOOL.replace(' ', '-') + "/" + Forge.VERSION)
                    .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody()).build();
            java.net.http.HttpResponse<Void> r = java.net.http.HttpClient.newHttpClient().send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
            return r.statusCode() == 200 && r.headers().firstValueAsLong("Content-Length").orElse(1) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void connect() throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        for (String place : windows ? java.util.List.of("\\\\.\\pipe\\") : socketFolders()) {
            for (int i = 0; i < 10; i++) {
                try {
                    if (windows) openPipe(place + "discord-ipc-" + i);
                    else openSocket(java.nio.file.Path.of(place, "discord-ipc-" + i));
                    send(0, Map.of("v", 1, "client_id", CLIENT_ID));
                    return;
                } catch (IOException e) {
                    close();
                }
            }
        }
        throw new IOException("discord isn't running");
    }

    private static void openPipe(String name) throws IOException {
        RandomAccessFile file = new RandomAccessFile(name, "rw");
        pipe = file;
        in = new java.io.DataInputStream(new java.io.InputStream() {
            @Override
            public int read() throws IOException {
                return file.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return file.read(b, off, len);
            }
        });
        out = new java.io.OutputStream() {
            @Override
            public void write(int b) throws IOException {
                file.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                file.write(b, off, len);
            }
        };
    }

    private static void openSocket(java.nio.file.Path path) throws IOException {
        if (!java.nio.file.Files.exists(path)) throw new IOException("no socket at " + path);
        java.nio.channels.SocketChannel channel = java.nio.channels.SocketChannel.open(java.net.StandardProtocolFamily.UNIX);
        pipe = channel;
        channel.connect(java.net.UnixDomainSocketAddress.of(path));
        in = new java.io.DataInputStream(java.nio.channels.Channels.newInputStream(channel));
        out = java.nio.channels.Channels.newOutputStream(channel);
    }

    // Discord puts its socket in the runtime or temp folder, and the Flatpak and Snap builds one folder deeper
    private static java.util.List<String> socketFolders() {
        java.util.List<String> found = new java.util.ArrayList<>();
        for (String key : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String dir = System.getenv(key);
            if (dir != null && !dir.isBlank()) found.add(dir);
        }
        found.add("/tmp");
        java.util.List<String> all = new java.util.ArrayList<>();
        for (String dir : found) {
            all.add(dir);
            all.add(dir + "/app/com.discordapp.Discord");
            all.add(dir + "/snap.discord");
        }
        return all;
    }

    // every message gets a reply, reading it keeps the pipe from filling up
    private static void send(int op, Object json) throws IOException {
        byte[] body = Json.write(json).getBytes(StandardCharsets.UTF_8);
        ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.length);
        out.write(head.array());
        out.write(body);
        out.flush();
        byte[] reply = new byte[8];
        in.readFully(reply);
        int length = ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
        in.readFully(new byte[length]);
        if (ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == 2) throw new IOException("discord closed the pipe");
    }

    private static void close() {
        try {
            if (pipe != null) pipe.close();
        } catch (IOException ignored) {
        }
        pipe = null;
        in = null;
        out = null;
        last = null;
    }
}
