package com.termux.pkgconv.writer;

import com.termux.pkgconv.model.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class AlpmWriter {

    private static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    private static final String PACMAN_LOCAL_DIR = TERMUX_PREFIX + "/var/lib/pacman/local";

    private final Path prefixPath;
    private final Path localDir;

    public AlpmWriter() {
        this(Paths.get(TERMUX_PREFIX));
    }

    public AlpmWriter(Path prefixPath) {
        this.prefixPath = prefixPath;
        this.localDir = prefixPath.resolve("var/lib/pacman/local");
    }

    public void write(List<PackageModel> packages) throws IOException {
        if (Files.exists(localDir)) {
            deleteDirectory(localDir);
        }
        Files.createDirectories(localDir);

        for (PackageModel pkg : packages) {
            writePackage(pkg);
        }
    }

    private void writePackage(PackageModel pkg) throws IOException {
        String dirName = getDirName(pkg);
        Path pkgDir = localDir.resolve(dirName);
        Files.createDirectories(pkgDir);

        writeDescFile(pkgDir.resolve("desc"), pkg);
        writeFilesFile(pkgDir.resolve("files"), pkg);
        writeDependsFile(pkgDir.resolve("depends"), pkg);
        writeInstallFile(pkgDir.resolve("install"), pkg);
    }

    private String getDirName(PackageModel pkg) {
        return pkg.getName() + "-" + pkg.getVersion();
    }

    private void writeDescFile(Path path, PackageModel pkg) throws IOException {
        List<String> lines = new ArrayList<>();

        writeSection(lines, "NAME", pkg.getName());

        writeSection(lines, "VERSION", pkg.getVersion());

        String desc = pkg.getDescription();
        if (desc != null) {
            int newlineIdx = desc.indexOf('\n');
            if (newlineIdx >= 0) {
                desc = desc.substring(0, newlineIdx);
            }
            writeSection(lines, "DESC", desc);
        }

        if (!pkg.getGroups().isEmpty()) {
            writeSection(lines, "GROUPS", pkg.getGroups());
        }

        if (pkg.getUrl() != null && !pkg.getUrl().isEmpty()) {
            writeSection(lines, "URL", pkg.getUrl());
        }

        if (pkg.getLicense() != null && !pkg.getLicense().isEmpty()) {
            writeSection(lines, "LICENSE", pkg.getLicense());
        }

        String arch = pkg.getArch();
        if (arch != null) {
            if ("all".equals(arch)) {
                arch = "any";
            }
            writeSection(lines, "ARCH", arch);
        }

        if (pkg.getBuildDate() > 0) {
            writeSection(lines, "BUILDDATE", String.valueOf(pkg.getBuildDate()));
        }

        if (pkg.getInstallDate() > 0) {
            writeSection(lines, "INSTALLDATE", String.valueOf(pkg.getInstallDate()));
        }

        if (pkg.getMaintainer() != null && !pkg.getMaintainer().isEmpty()) {
            writeSection(lines, "PACKAGER", pkg.getMaintainer());
        }

        writeSection(lines, "SIZE", String.valueOf(pkg.getInstalledSize()));

        String reason;
        switch (pkg.getReason()) {
            case AUTO:
                reason = "0";
                break;
            case EXPLICIT:
            case UNKNOWN:
            default:
                reason = "1";
                break;
        }
        writeSection(lines, "REASON", reason);

        if (!pkg.getDepends().isEmpty()) {
            List<String> depLines = new ArrayList<>();
            for (Dependency dep : pkg.getDepends()) {
                depLines.add(dep.toString());
            }
            Collections.sort(depLines);
            writeSection(lines, "DEPENDS", depLines);
        }

        if (!pkg.getConflicts().isEmpty()) {
            List<String> conflictLines = new ArrayList<>();
            for (Dependency dep : pkg.getConflicts()) {
                conflictLines.add(dep.toString());
            }
            Collections.sort(conflictLines);
            writeSection(lines, "CONFLICTS", conflictLines);
        }

        if (!pkg.getProvides().isEmpty()) {
            List<String> provideLines = new ArrayList<>();
            for (Dependency dep : pkg.getProvides()) {
                provideLines.add(dep.toString());
            }
            Collections.sort(provideLines);
            writeSection(lines, "PROVIDES", provideLines);
        }

        if (!pkg.getReplaces().isEmpty()) {
            List<String> replaceLines = new ArrayList<>();
            for (Dependency dep : pkg.getReplaces()) {
                replaceLines.add(dep.toString());
            }
            Collections.sort(replaceLines);
            writeSection(lines, "REPLACES", replaceLines);
        }

        if (!pkg.getConffiles().isEmpty()) {
            List<String> backupLines = new ArrayList<>();
            for (Conffile cf : pkg.getConffiles()) {
                backupLines.add(cf.getPath() + " " + cf.getMd5sum());
            }
            Collections.sort(backupLines);
            writeSection(lines, "BACKUP", backupLines);
        }

        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private void writeFilesFile(Path path, PackageModel pkg) throws IOException {
        List<String> lines = new ArrayList<>();

        List<String> fileLines = new ArrayList<>();
        for (FilePath fp : pkg.getFiles()) {
            String entry = fp.getPath();
            if (fp.isDirectory()) {
                entry += "/";
            }
            fileLines.add(entry);
        }
        Collections.sort(fileLines);
        writeSection(lines, "FILES", fileLines);

        if (!pkg.getConffiles().isEmpty()) {
            List<String> backupLines = new ArrayList<>();
            for (Conffile cf : pkg.getConffiles()) {
                backupLines.add(cf.getPath() + " " + cf.getMd5sum());
            }
            Collections.sort(backupLines);
            writeSection(lines, "BACKUP", backupLines);
        }

        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private void writeDependsFile(Path path, PackageModel pkg) throws IOException {
        List<Dependency> depends = pkg.getDepends();
        if (depends.isEmpty()) {
            return;
        }

        List<String> depLines = new ArrayList<>();
        for (Dependency dep : depends) {
            depLines.add(dep.toString());
        }
        Collections.sort(depLines);

        Files.write(path, depLines, StandardCharsets.UTF_8);
    }

    private void writeInstallFile(Path path, PackageModel pkg) throws IOException {
        Scripts scripts = pkg.getScripts();
        if (scripts == null || !scripts.hasAny()) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        if (scripts.getPreInst() != null && !scripts.getPreInst().isEmpty()) {
            sb.append("pre_install() {\n");
            sb.append(scripts.getPreInst());
            sb.append("\n}\n\n");
        }

        if (scripts.getPostInst() != null && !scripts.getPostInst().isEmpty()) {
            sb.append("post_install() {\n");
            sb.append(scripts.getPostInst());
            sb.append("\n}\n\n");
        }

        if (scripts.getPreRm() != null && !scripts.getPreRm().isEmpty()) {
            sb.append("pre_remove() {\n");
            sb.append(scripts.getPreRm());
            sb.append("\n}\n\n");
        }

        if (scripts.getPostRm() != null && !scripts.getPostRm().isEmpty()) {
            sb.append("post_remove() {\n");
            sb.append(scripts.getPostRm());
            sb.append("\n}\n\n");
        }

        String content = sb.toString().replaceAll("\n\n+$", "\n");

        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private String toAlpmOperator(Dependency.Operator op) {
        switch (op) {
            case GE: return ">=";
            case LE: return "<=";
            case GT: return ">";
            case LT: return "<";
            case EQ: return "=";
            case ANY:
            default: return "";
        }
    }

    private void writeSection(List<String> lines, String key, String value) {
        lines.add("%" + key + "%");
        lines.add(value);
        lines.add("");
    }

    private void writeSection(List<String> lines, String key, List<String> values) {
        lines.add("%" + key + "%");
        lines.addAll(values);
        lines.add("");
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
