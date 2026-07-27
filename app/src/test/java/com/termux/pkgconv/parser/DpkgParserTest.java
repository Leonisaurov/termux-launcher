package com.termux.pkgconv.parser;

import com.termux.pkgconv.model.*;

import org.junit.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.Assert.*;

public class DpkgParserTest {

    private Path tempPrefix;
    private DpkgParser parser;

    @Before
    public void setUp() throws IOException {
        tempPrefix = Files.createTempDirectory("termux-test-");
        parser = new DpkgParser(tempPrefix);
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

        String statusContent =
            "Package: bash\n" +
            "Status: install ok installed\n" +
            "Priority: required\n" +
            "Section: shells\n" +
            "Installed-Size: 1256\n" +
            "Maintainer: Fredrik Fornwall <fredrik@fornwall.net>\n" +
            "Architecture: aarch64\n" +
            "Version: 5.2.26-2\n" +
            "Depends: ncurses, readline (>= 8.0)\n" +
            "Conflicts: bash-completion\n" +
            "Description: GNU Bourne Again SHell\n" +
            " Bash is a full-featured interactive shell.\n" +
            "Essential: yes\n" +
            "\n" +
            "Package: termux-tools\n" +
            "Status: install ok installed\n" +
            "Priority: important\n" +
            "Section: misc\n" +
            "Installed-Size: 140\n" +
            "Maintainer: Fredrik Fornwall <fredrik@fornwall.net>\n" +
            "Architecture: all\n" +
            "Version: 1.44.0\n" +
            "Description: Termux helper tools\n" +
            "Essential: yes\n";

        Files.write(dpkgDir.resolve("status"), statusContent.getBytes());

        String bashList =
            "/data/data/com.termux/files/usr/bin/bash\n" +
            "/data/data/com.termux/files/usr/bin/sh\n" +
            "/data/data/com.termux/files/usr/etc/bash.bashrc\n";
        Files.write(dpkgDir.resolve("info/bash.list"), bashList.getBytes());

        String bashMd5 =
            "d41d8cd98f00b204e9800998ecf8427e  usr/bin/bash\n" +
            "a3c9c1e0f9b8c7d6e5f4a3b2c1d0e9f8  usr/bin/sh\n";
        Files.write(dpkgDir.resolve("info/bash.md5sums"), bashMd5.getBytes());

        String toolsList =
            "/data/data/com.termux/files/usr/bin/termux-setup-storage\n" +
            "/data/data/com.termux/files/usr/bin/pkg\n";
        Files.write(dpkgDir.resolve("info/termux-tools.list"), toolsList.getBytes());
    }

    @Test
    public void testParse_returnsTwoPackages() throws IOException {
        List<PackageModel> packages = parser.parse();
        assertEquals(2, packages.size());
    }

    @Test
    public void testParse_bashPackage() throws IOException {
        List<PackageModel> packages = parser.parse();
        PackageModel bash = packages.stream()
            .filter(p -> "bash".equals(p.getName()))
            .findFirst().orElse(null);

        assertNotNull("bash package not found", bash);
        assertEquals("bash", bash.getName());
        assertEquals("5.2.26-2", bash.getVersion());
        assertEquals("aarch64", bash.getArch());
        assertEquals("GNU Bourne Again SHell", bash.getDescription());
        assertTrue("bash should be in base group",
            bash.getGroups().contains("base"));
    }

    @Test
    public void testParse_bashFiles() throws IOException {
        List<PackageModel> packages = parser.parse();
        PackageModel bash = packages.stream()
            .filter(p -> "bash".equals(p.getName()))
            .findFirst().orElse(null);

        assertNotNull(bash);
        assertTrue("bash.list should have bin/bash",
            bash.getFiles().stream().anyMatch(f -> f.getPath().equals("bin/bash")));
        assertTrue("bash.list should have bin/sh",
            bash.getFiles().stream().anyMatch(f -> f.getPath().equals("bin/sh")));
    }

    @Test
    public void testParse_bashDependencies() throws IOException {
        List<PackageModel> packages = parser.parse();
        PackageModel bash = packages.stream()
            .filter(p -> "bash".equals(p.getName()))
            .findFirst().orElse(null);

        assertNotNull(bash);
        assertTrue("bash should depend on ncurses",
            bash.getDepends().stream().anyMatch(d -> "ncurses".equals(d.getName())));
        assertTrue("bash should depend on readline>=8.0",
            bash.getDepends().stream().anyMatch(
                d -> "readline".equals(d.getName()) &&
                     d.getOperator() == Dependency.Operator.GE &&
                     "8.0".equals(d.getVersion())));
    }

    @Test
    public void testParse_termuxToolsArchAll() throws IOException {
        List<PackageModel> packages = parser.parse();
        PackageModel tools = packages.stream()
            .filter(p -> "termux-tools".equals(p.getName()))
            .findFirst().orElse(null);

        assertNotNull(tools);
        assertEquals("any", tools.getArch());
        assertTrue("termux-tools should be in base group",
            tools.getGroups().contains("base"));
    }
}
