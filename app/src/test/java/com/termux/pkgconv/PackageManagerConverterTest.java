package com.termux.pkgconv;

import com.termux.pkgconv.model.*;
import com.termux.pkgconv.parser.*;
import com.termux.pkgconv.writer.*;

import org.junit.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.Assert.*;

public class PackageManagerConverterTest {

    private Path tempPrefix;

    @Before
    public void setUp() throws IOException {
        tempPrefix = Files.createTempDirectory("termux-roundtrip-");
        createMockDpkgDatabase();
    }

    @After
    public void tearDown() throws IOException {
        Files.walk(tempPrefix)
             .sorted(Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
    }

    private void createMockDpkgDatabase() throws IOException {
        Path dpkgDir = tempPrefix.resolve("var/lib/dpkg");
        Files.createDirectories(dpkgDir.resolve("info"));

        String status =
            "Package: bash\nStatus: install ok installed\nPriority: required\n" +
            "Section: shells\nInstalled-Size: 1256\n" +
            "Maintainer: Fredrik Fornwall <fredrik@fornwall.net>\n" +
            "Architecture: aarch64\nVersion: 5.2.26-2\n" +
            "Depends: ncurses, readline (>= 8.0)\n" +
            "Description: GNU Bourne Again SHell\nEssential: yes\n\n" +
            "Package: termux-tools\nStatus: install ok installed\nPriority: important\n" +
            "Section: misc\nInstalled-Size: 140\n" +
            "Maintainer: Fredrik Fornwall <fredrik@fornwall.net>\n" +
            "Architecture: all\nVersion: 1.44.0\n" +
            "Description: Termux helper tools\nEssential: yes\n";
        Files.write(dpkgDir.resolve("status"), status.getBytes());

        Files.write(dpkgDir.resolve("info/bash.list"),
            "/data/data/com.termux/files/usr/bin/bash\n".getBytes());
        Files.write(dpkgDir.resolve("info/termux-tools.list"),
            "/data/data/com.termux/files/usr/bin/pkg\n".getBytes());
    }

    @Test
    public void testDpkgToAlpmRoundTrip() throws IOException {
        DpkgParser dpkgParser = new DpkgParser(tempPrefix);
        List<PackageModel> originalPkgs = dpkgParser.parse();
        assertFalse("Should have parsed packages", originalPkgs.isEmpty());

        AlpmWriter alpmWriter = new AlpmWriter(tempPrefix);
        alpmWriter.write(originalPkgs);

        AlpmParser alpmParser = new AlpmParser(tempPrefix);
        List<PackageModel> alpmPkgs = alpmParser.parse();

        assertEquals("Same number of packages after dpkg\u2192ALPM",
            originalPkgs.size(), alpmPkgs.size());

        DpkgWriter dpkgWriter = new DpkgWriter(tempPrefix);
        dpkgWriter.write(alpmPkgs);

        List<PackageModel> finalPkgs = dpkgParser.parse();

        assertEquals("Same number of packages after full round-trip",
            originalPkgs.size(), finalPkgs.size());

        for (PackageModel original : originalPkgs) {
            PackageModel final_ = finalPkgs.stream()
                .filter(p -> p.getName().equals(original.getName()))
                .findFirst().orElse(null);

            assertNotNull("Package " + original.getName() + " preserved", final_);
            assertEquals("Version preserved for " + original.getName(),
                original.getVersion(), final_.getVersion());
            assertEquals("Arch preserved for " + original.getName(),
                original.getArch(), final_.getArch());
        }
    }

    @Test
    public void testDetectPackageManager_apt() throws IOException {
        PackageManagerConverter converter = new PackageManagerConverter(tempPrefix);
        assertEquals(PackageManagerConverter.PackageManager.APT,
            converter.detectCurrentPackageManager());
    }

    @Test
    public void testDetectPackageManager_none() throws IOException {
        Path emptyPrefix = Files.createTempDirectory("termux-empty-");
        try {
            PackageManagerConverter converter = new PackageManagerConverter(emptyPrefix);
            assertEquals(PackageManagerConverter.PackageManager.NONE,
                converter.detectCurrentPackageManager());
        } finally {
            Files.walk(emptyPrefix)
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }
}
