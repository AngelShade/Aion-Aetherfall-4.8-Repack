package com.aionemu.tools.navmesh;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

final class Artifacts {
    private Artifacts() {}
    static String hash(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest d = digest(); byte[] bytes = new byte[65536]; int n;
            while ((n = in.read(bytes)) >= 0) d.update(bytes, 0, n);
            return HexFormat.of().formatHex(d.digest());
        }
    }
    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    static String hashText(String text) {
        return HexFormat.of().formatHex(digest().digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    static Properties read(Path path) throws IOException {
        Properties p = new Properties();
        try (var in = Files.newInputStream(path)) { p.load(in); }
        return p;
    }
    interface Writer { void write(OutputStream out) throws IOException; }
    static void atomic(Path file, Writer writer) throws IOException {
        Path temp = Files.createTempFile(file.getParent(), ".navmesh-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) { writer.write(out); }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    static void store(Path file, Properties p) throws IOException { atomic(file, out -> p.store(out, "Aion navmesh offline evidence")); }
    static String toolHash() throws Exception {
        Path code = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(code)) return hash(code);
        StringBuilder b = new StringBuilder("recast4j=1.5.8\n");
        try (var files = Files.walk(code)) {
            for (Path f : files.filter(Files::isRegularFile).sorted().toList())
                b.append(code.relativize(f)).append('=').append(hash(f)).append('\n');
        }
        return hashText(b.toString());
    }
}
