import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Match this legacy client's RSA/SHA1 format using an ephemeral local key. */
class SignClientPackages {
    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final List<String> PACKAGES = List.of(
        "bin32/bin32.pak", "Data/func_pet/func_pet.pak", "Plugin/RelicCalc/RelicCalc.pak");

    private static byte[] readHex(Path path) throws Exception {
        return HEX.parseHex(Files.readString(path).strip());
    }

    private static void verify(PublicKey key, byte[] bytes, byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(key);
        verifier.update(bytes);
        if (!verifier.verify(signature)) throw new SecurityException("Package signature mismatch");
    }

    public static void main(String[] args) throws Exception {
        Path client = Path.of(args[0]);
        Path output = Path.of(args[1]);
        PublicKey originalKey = KeyFactory.getInstance("RSA").generatePublic(
            new X509EncodedKeySpec(readHex(client.resolve("Pub.key"))));
        Set<String> actual = new HashSet<>();
        try (var paths = Files.walk(client)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".pak.sig"))
                .filter(p -> !p.toString().contains("TransmogMenu-backups"))
                .forEach(p -> actual.add(client.relativize(p).toString().replace('\\', '/')));
        }
        Set<String> expected = new HashSet<>();
        for (String path : PACKAGES) expected.add(path + ".sig");
        if (!actual.equals(expected)) throw new IllegalStateException("Unexpected signed packages: " + actual);
        for (String path : PACKAGES) {
            verify(originalKey, Files.readAllBytes(client.resolve(path)), readHex(client.resolve(path + ".sig")));
        }
        // Match the legacy 128-byte signature size; the private key is never saved.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair localKey = generator.generateKeyPair();
        Files.writeString(output.resolve("Pub.key"), HEX.formatHex(localKey.getPublic().getEncoded()), StandardCharsets.US_ASCII);
        for (String path : PACKAGES) {
            Path stagedPackage = output.resolve(path);
            byte[] bytes = Files.readAllBytes(Files.exists(stagedPackage) ? stagedPackage : client.resolve(path));
            Signature signer = Signature.getInstance("SHA1withRSA");
            signer.initSign(localKey.getPrivate());
            signer.update(bytes);
            byte[] signature = signer.sign();
            verify(localKey.getPublic(), bytes, signature);
            Path destination = output.resolve(path + ".sig");
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, HEX.formatHex(signature), StandardCharsets.US_ASCII);
        }
        System.out.println("Verified all three original signatures and all three local signatures.");
    }
}
