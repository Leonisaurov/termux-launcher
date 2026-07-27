package com.termux.pkgconv.parser;

import com.termux.pkgconv.model.*;

import org.junit.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.Assert.*;

public class AlpmParserTest {

    private Path tempPrefix;
    private AlpmParser parser;

    @Before
    public void setUp() throws IOException {
        tempPrefix = Files.createTempDirectory("termux-test-");
        parser = new AlpmParser(tempPrefix);
        createMockAlpmDatabase();
    }

    @After
    public void tearDown() throws IOException {
        Files.walk(tempPrefix)
             .sorted(Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
    }

    private void createMockAlpmDatabase() throws IOException {
        Path bashDir = tempPrefix.resolve("var/lib/pacman/local/bash-5.2.26-2");
        Files.createDirectories(bashDir);

        String descContent =
            "%NAME%\nbash\n\n" +
            "%VERSION%\n5.2.26-2\n\n" +
            "%DESC%\nGNU Bourne Again SHell\n\n" +
            "%GROUPS%\nbase\n\n" +
            "%URL%\nhttps://www.gnu.org/software/bash/\n\n" +
            "%ARCH%\naarch64\n\n" +
            "%SIZE%\n1286144\n\n" +
            "%REASON%\n1\n\n" +
            "%DEPENDS%\nglibc\nreadline>=8.0\nncurses\n\n";
        Files.write(bashDir.resolve("desc"), descContent.getBytes());

        String filesContent =
            "%FILES%\nusr/bin/bash\nusr/bin/sh\nusr/etc/bash.bashrc\n\n" +
            "%BACKUP%\nusr/etc/bash.bashrc d41d8cd98f00b204e9800998ecf8427e\n\n";
        Files.write(bashDir.resolve("files"), filesContent.getBytes());

        Path toolsDir = tempPrefix.resolve("var/lib/pacman/local/termux-tools-1.44.0-1");
        Files.createDirectories(toolsDir);

        String toolsDesc =
            "%NAME%\ntermux-tools\n\n" +
            "%VERSION%\n1.44.0-1\n\n" +
            "%DESC%\nTermux helper tools\n\n" +
            "%GROUPS%\nbase\n\n" +
            "%ARCH%\nany\n\n" +
            "%SIZE%\n143360\n\n" +
            "%REASON%\n1\n\n";
        Files.write(toolsDir.resolve("desc"), toolsDesc.getBytes());

        String toolsFiles =
            "%FILES%\nusr/bin/termux-setup-storage\nusr/bin/pkg\n\n";
        Files.write(toolsDir.resolve("files"), toolsFiles.getBytes());
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

        assertNotNull(bash);
        assertEquals("bash", bash.getName());
        assertEquals("5.2.26-2", bash.getVersion());
        assertEquals("aarch64", bash.getArch());
        assertEquals("GNU Bourne Again SHell", bash.getDescription());
        assertEquals(1286144L, bash.getInstalledSize());
        assertTrue(bash.getGroups().contains("base"));
    }

    @Test
    public void testParse_bashDependencies() throws IOException {
        List<PackageModel> packages = parser.parse();
        PackageModel bash = packages.stream()
            .filter(p -> "bash".equals(p.getName()))
            .findFirst().orElse(null);

        assertNotNull(bash);
        assertTrue(bash.getDepends().stream().anyMatch(d -> "glibc".equals(d.getName())));
        assertTrue(bash.getDepends().stream().anyMatch(
            d -> "readline".equals(d.getName()) &&
                 d.getOperator() == Dependency.Operator.GE));
    }

    @Test
    public void testParse_bashFiles() throws IOException {
        List<PackageModel> packages = parser.parse();
        PackageModel bash = packages.stream()
            .filter(p -> "bash".equals(p.getName()))
            .findFirst().orElse(null);

        assertNotNull(bash);
        assertTrue(bash.getFiles().stream().anyMatch(f -> f.getPath().equals("usr/bin/bash")));
    }

    @Test
    public void testParse_bashConffiles() throws IOException {
        List<PackageModel> packages = parser.parse();
        PackageModel bash = packages.stream()
            .filter(p -> "bash".equals(p.getName()))
            .findFirst().orElse(null);

        assertNotNull(bash);
        assertTrue(bash.getConffiles().stream()
            .anyMatch(c -> c.getPath().equals("usr/etc/bash.bashrc")));
    }

    @Test
    public void testParseDirName_reverseSplit() {
        AlpmParser.NameVersionRel result = AlpmParser.parseDirName("bash-5.2.26-2");
        assertEquals("bash", result.name);
        assertEquals("5.2.26", result.version);
        assertEquals("2", result.pkgrel);

        result = AlpmParser.parseDirName("termux-tools-1.44.0-1");
        assertEquals("termux-tools", result.name);
        assertEquals("1.44.0", result.version);
        assertEquals("1", result.pkgrel);

        result = AlpmParser.parseDirName("glibc-2.38");
        assertEquals("glibc", result.name);
        assertEquals("2.38", result.version);
        assertNull(result.pkgrel);
    }
}
