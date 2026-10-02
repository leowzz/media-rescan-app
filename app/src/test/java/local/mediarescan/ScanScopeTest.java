package local.mediarescan;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

public class ScanScopeTest {
    @org.junit.Test public void validatesBoundariesAndOverlaps() throws Exception { main(new String[0]); }
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
            File parent = album.getParentFile();
            File sibling = Files.createDirectory(parent.toPath().resolve("Album2")).toFile();
            if (!ScanScope.minimalRoots(java.util.Arrays.asList(album, album, sibling))
                    .equals(java.util.Arrays.asList(album, sibling))) throw new AssertionError("Duplicate/sibling handling");
            if (!ScanScope.minimalRoots(java.util.Arrays.asList(album, parent, sibling))
                    .equals(java.util.Arrays.asList(parent))) throw new AssertionError("Parent added after child");
            if (!ScanScope.minimalRoots(java.util.Arrays.asList(parent, album))
                    .equals(java.util.Arrays.asList(parent))) throw new AssertionError("Child added after parent");
            if (!ScanScope.minimalRoots(java.util.Arrays.asList(album, root, sibling))
                    .equals(java.util.Arrays.asList(root))) throw new AssertionError("Whole storage overlap");
            if (!ScanScope.minimalRoots(java.util.Collections.emptyList()).isEmpty()) throw new AssertionError("Empty list");
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
