package com.termux.pkgconv.writer;

import com.termux.pkgconv.model.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class DpkgWriter {

    private static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";

    private final Path prefixPath;
    private final Path statusFile;
    private final Path infoDir;

    public DpkgWriter() {
        this(Paths.get(TERMUX_PREFIX));
    }

    public DpkgWriter(Path prefixPath) {
        this.prefixPath = prefixPath;
        this.statusFile = prefixPath.resolve("var/lib/dpkg/status");
        this.infoDir = prefixPath.resolve("var/lib/dpkg/info");
    }

    public void write(List<PackageModel> packages) throws IOException {
        Files.createDirectories(infoDir);
        writeStatusFile(packages);
        for (PackageModel pkg : packages) {
            writeFileList(pkg);
            writeMd5Sums(pkg);
            writeConffiles(pkg);
            writeScripts(pkg);
        }
    }

    private void writeStatusFile(List<PackageModel> packages) throws IOException {
        List<String> lines = new ArrayList<>();
        List<PackageModel> sorted = new ArrayList<>(packages);
        sorted.sort(Comparator.comparing(PackageModel::getName, Comparator.nullsLast(String::compareTo)));

        for (int i = 0; i < sorted.size(); i++) {
            PackageModel pkg = sorted.get(i);
            writeStanza(pkg, lines);
            if (i < sorted.size() - 1) {
                lines.add("");
            }
        }

        Files.write(statusFile, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeStanza(PackageModel pkg, List<String> lines) {
        appendField(lines, "Package", pkg.getName());
        appendField(lines, "Status", "install ok installed");

        String priority = pkg.getGroups().contains("base") ? "required" : "optional";
        appendField(lines, "Priority", priority);

        String section = "misc";
        appendField(lines, "Section", section);

        int installedSize = (int) Math.ceil(pkg.getInstalledSize() / 1024.0);
        appendField(lines, "Installed-Size", String.valueOf(installedSize));

        if (pkg.getMaintainer() != null) {
            appendField(lines, "Maintainer", pkg.getMaintainer());
        }

        String arch = pkg.getArch();
        if ("any".equals(arch)) {
            arch = "all";
        }
        appendField(lines, "Architecture", arch);

        appendField(lines, "Version", pkg.getVersion());

        String replaces = formatDependencies(pkg.getReplaces());
        if (!replaces.isEmpty()) {
            appendField(lines, "Replaces", replaces);
        }

        String depends = formatDependencies(pkg.getDepends());
        if (!depends.isEmpty()) {
            appendField(lines, "Depends", depends);
        }

        String conflicts = formatDependencies(pkg.getConflicts());
        if (!conflicts.isEmpty()) {
            appendField(lines, "Conflicts", conflicts);
        }

        String provides = formatDependencies(pkg.getProvides());
        if (!provides.isEmpty()) {
            appendField(lines, "Provides", provides);
        }

        if (!pkg.getConffiles().isEmpty()) {
            lines.add("Conffiles:");
            for (Conffile cf : pkg.getConffiles()) {
                String absPath = toAbsolutePath(cf.getPath());
                String md5 = cf.getMd5sum();
                if (md5 != null && !md5.isEmpty()) {
                    lines.add(" " + absPath + " " + md5);
                } else {
                    lines.add(" " + absPath);
                }
            }
        }

        if (pkg.getUrl() != null && !pkg.getUrl().isEmpty()) {
            appendField(lines, "Homepage", pkg.getUrl());
        }

        if (pkg.getGroups().contains("base")) {
            appendField(lines, "Essential", "yes");
        }

        String desc = pkg.getDescription();
        if (desc != null && !desc.isEmpty()) {
            int idx = desc.indexOf('\n');
            if (idx == -1) {
                appendField(lines, "Description", desc);
            } else {
                String firstLine = desc.substring(0, idx);
                appendField(lines, "Description", firstLine);
                String rest = desc.substring(idx + 1);
                for (String line : rest.split("\n")) {
                    lines.add(" " + line);
                }
            }
        }
    }

    private void appendField(List<String> lines, String field, String value) {
        lines.add(field + ": " + value);
    }

    private String formatDependencies(List<Dependency> deps) {
        if (deps == null || deps.isEmpty()) return "";

        Map<String, List<Dependency>> grouped = new LinkedHashMap<>();
        for (Dependency dep : deps) {
            grouped.computeIfAbsent(dep.getName(), k -> new ArrayList<>()).add(dep);
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<Dependency>> entry : grouped.entrySet()) {
            List<Dependency> group = entry.getValue();
            if (group.size() == 1) {
                parts.add(formatSingleDependency(group.get(0)));
            } else {
                List<String> orParts = new ArrayList<>();
                for (Dependency dep : group) {
                    orParts.add(formatSingleDependency(dep));
                }
                parts.add(String.join(" | ", orParts));
            }
        }

        return String.join(", ", parts);
    }

    private String formatSingleDependency(Dependency dep) {
        String name = dep.getName();
        switch (dep.getOperator()) {
            case ANY:
                return name;
            case GE:
                return name + " (>= " + dep.getVersion() + ")";
            case LE:
                return name + " (<= " + dep.getVersion() + ")";
            case GT:
                return name + " (>> " + dep.getVersion() + ")";
            case LT:
                return name + " (<< " + dep.getVersion() + ")";
            case EQ:
                return name + " (= " + dep.getVersion() + ")";
            default:
                return name;
        }
    }

    private String toAbsolutePath(String relativePath) {
        if (relativePath == null) return "";
        if (relativePath.startsWith("/")) return relativePath;
        return prefixPath + "/" + relativePath;
    }

    private void writeFileList(PackageModel pkg) throws IOException {
        List<FilePath> files = pkg.getFiles();
        if (files == null || files.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        for (FilePath fp : files) {
            lines.add(toAbsolutePath(fp.getPath()));
        }

        Path listFile = infoDir.resolve(pkg.getName() + ".list");
        Files.write(listFile, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeMd5Sums(PackageModel pkg) throws IOException {
        List<FilePath> files = pkg.getFiles();
        if (files == null || files.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        for (FilePath fp : files) {
            String md5 = fp.getMd5sum();
            if (md5 != null && !md5.isEmpty()) {
                lines.add(md5 + "  " + fp.getPath());
            }
        }

        if (lines.isEmpty()) return;

        Path md5File = infoDir.resolve(pkg.getName() + ".md5sums");
        Files.write(md5File, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeConffiles(PackageModel pkg) throws IOException {
        List<Conffile> conffiles = pkg.getConffiles();
        if (conffiles == null || conffiles.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        for (Conffile cf : conffiles) {
            lines.add(toAbsolutePath(cf.getPath()));
        }

        Path conffileFile = infoDir.resolve(pkg.getName() + ".conffiles");
        Files.write(conffileFile, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeScripts(PackageModel pkg) throws IOException {
        Scripts scripts = pkg.getScripts();
        if (scripts == null || !scripts.hasAny()) return;

        String name = pkg.getName();
        writeScript(name + ".preinst", scripts.getPreInst());
        writeScript(name + ".postinst", scripts.getPostInst());
        writeScript(name + ".prerm", scripts.getPreRm());
        writeScript(name + ".postrm", scripts.getPostRm());
    }

    private void writeScript(String filename, String content) throws IOException {
        if (content == null || content.isEmpty()) return;

        Path scriptFile = infoDir.resolve(filename);
        Files.write(scriptFile, Collections.singletonList(content), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
