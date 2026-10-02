package local.mediarescan;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

public class ScanScopeTest {
    private static void rejects(File root, File target) throws Exception {
        try { ScanScope.validate(root, target); }
        catch (IOException expected) { return; }
        throw new AssertionError("Unexpected allowed directory: " + target);
    }
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("media-rescan-scope-");
        try {
            File root = Files.createDirectory(temp.resolve("storage")).toFile().getCanonicalFile();
            File album = Files.createDirectories(root.toPath().resolve("DCIM/Album")).toFile();
            ScanScope.validate(root, root);
            ScanScope.validate(root, album);
            rejects(root, Files.createDirectory(temp.resolve("storage-other")).toFile());
            rejects(root, new File(root, "missing"));
            rejects(root, Files.createFile(root.toPath().resolve("file.jpg")).toFile());
            rejects(root, Files.createDirectories(root.toPath().resolve(".hidden/Album")).toFile());
            rejects(root, Files.createDirectories(root.toPath().resolve("Android/media/app")).toFile());
            Files.createFile(album.getParentFile().toPath().resolve(".nomedia"));
            rejects(root, album);
            Files.delete(album.getParentFile().toPath().resolve(".nomedia"));
            ScanScope.validate(root, album);
            Files.createFile(root.toPath().resolve(".nomedia"));
            rejects(root, album);
            rejects(root, root);
            System.out.println("ScanScope tests passed");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temp)) {
                for (Path p : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
            }
        }
    }
}
