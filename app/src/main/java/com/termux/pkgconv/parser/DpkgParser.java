package com.termux.pkgconv.parser;

import com.termux.pkgconv.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class DpkgParser {

    private static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    private static final String DPKG_INFO_DIR = TERMUX_PREFIX + "/var/lib/dpkg/info";
    private static final String DPKG_STATUS_FILE = TERMUX_PREFIX + "/var/lib/dpkg/status";
    private static final Logger LOGGER = Logger.getLogger(DpkgParser.class.getName());

    private final Path prefixPath;
    private final Path statusFile;
    private final Path infoDir;

    public DpkgParser() {
        this(Paths.get(TERMUX_PREFIX));
    }

    public DpkgParser(Path prefixPath) {
        this.prefixPath = prefixPath;
        this.statusFile = prefixPath.resolve("var/lib/dpkg/status");
        this.infoDir = prefixPath.resolve("var/lib/dpkg/info");
    }

    public List<PackageModel> parse() throws IOException {
        String statusContent = readFile(statusFile);
        List<Map<String, String>> stanzas = parseStanzas(statusContent);

        List<PackageModel> packages = new ArrayList<>();
        for (Map<String, String> stanza : stanzas) {
            if (isInstalled(stanza)) {
                PackageModel pkg = parseStanza(stanza);
                if (pkg != null) {
                    loadFileList(pkg);
                    loadMd5Sums(pkg);
                    loadConffiles(pkg);
                    loadScripts(pkg);
                    packages.add(pkg);
                }
            }
        }

        return packages;
    }

    private List<Map<String, String>> parseStanzas(String content) {
        List<Map<String, String>> stanzas = new ArrayList<>();
        String[] blocks = content.split("\n\n");
        for (String block : blocks) {
            block = block.trim();
            if (!block.isEmpty()) {
                Map<String, String> fields = parseStanzaBlock(block);
                if (!fields.isEmpty()) {
                    stanzas.add(fields);
                }
            }
        }
        return stanzas;
    }

    static Map<String, String> parseStanzaBlock(String block) {
        Map<String, String> fields = new LinkedHashMap<>();
        String[] lines = block.split("\n");
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                String continuation = line.substring(1);
                if (continuation.equals(".")) {
                    continuation = "";
                }
                if (currentKey == null) {
                    continue;
                }
                if (currentValue.length() > 0) {
                    currentValue.append('\n');
                }
                currentValue.append(continuation);
            } else {
                if (currentKey != null) {
                    fields.put(currentKey, currentValue.toString());
                }
                int colonIndex = line.indexOf(':');
                if (colonIndex != -1) {
                    currentKey = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    currentValue = new StringBuilder(value);
                } else {
                    currentKey = null;
                    currentValue = new StringBuilder();
                }
            }
        }
        if (currentKey != null) {
            fields.put(currentKey, currentValue.toString());
        }

        return fields;
    }

    private PackageModel parseStanza(Map<String, String> fields) {
        PackageModel pkg = new PackageModel();

        pkg.setName(fields.get("Package"));
        pkg.setVersion(fields.get("Version"));

        String arch = fields.get("Architecture");
        if ("all".equals(arch)) {
            arch = "any";
        }
        pkg.setArch(arch);

        String desc = fields.get("Description");
        if (desc != null) {
            int newlineIdx = desc.indexOf('\n');
            if (newlineIdx != -1) {
                pkg.setDescription(desc.substring(0, newlineIdx));
            } else {
                pkg.setDescription(desc);
            }
        }

        String sizeStr = fields.get("Installed-Size");
        if (sizeStr != null) {
            try {
                pkg.setInstalledSize(Long.parseLong(sizeStr.trim()) * 1024L);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid Installed-Size for " + pkg.getName() + ": " + sizeStr);
            }
        }

        pkg.setMaintainer(fields.get("Maintainer"));
        pkg.setUrl(fields.get("Homepage"));
        pkg.setInstallDate(0);
        pkg.setReason(InstallReason.UNKNOWN);

        if (fields.containsKey("Depends")) {
            pkg.setDepends(parseDependencies(fields.get("Depends")));
        }
        if (fields.containsKey("Replaces")) {
            pkg.setReplaces(parseDependencies(fields.get("Replaces")));
        }
        if (fields.containsKey("Conflicts")) {
            pkg.setConflicts(parseDependencies(fields.get("Conflicts")));
        }
        if (fields.containsKey("Provides")) {
            pkg.setProvides(parseDependencies(fields.get("Provides")));
        }

        if ("yes".equalsIgnoreCase(fields.get("Essential"))) {
            List<String> groups = new ArrayList<>();
            groups.add("base");
            pkg.setGroups(groups);
        }

        String conffilesStr = fields.get("Conffiles");
        if (conffilesStr != null && !conffilesStr.isEmpty()) {
            List<Conffile> conffiles = new ArrayList<>();
            String[] conffileLines = conffilesStr.split("\n");
            for (String cfLine : conffileLines) {
                cfLine = cfLine.trim();
                if (cfLine.isEmpty()) continue;
                int spaceIdx = cfLine.indexOf(' ');
                if (spaceIdx != -1) {
                    String cfPath = cfLine.substring(0, spaceIdx).trim();
                    String cfMd5 = cfLine.substring(spaceIdx + 1).trim();
                    conffiles.add(new Conffile(relativizePath(cfPath), cfMd5));
                }
            }
            pkg.setConffiles(conffiles);
        }

        return pkg;
    }

    private boolean isInstalled(Map<String, String> fields) {
        String status = fields.get("Status");
        return status != null && status.contains("install ok installed");
    }

    private List<Dependency> parseDependencies(String depString) {
        List<Dependency> result = new ArrayList<>();
        if (depString == null || depString.trim().isEmpty()) {
            return result;
        }
        String[] parts = depString.split(",");
        for (int i = 0; i < parts.length; i++) {
            List<Dependency> group = parseDependencyGroup(parts[i].trim());
            for (Dependency dep : group) {
                dep.setOrGroupId(i);
            }
            result.addAll(group);
        }
        return result;
    }

    private List<Dependency> parseDependencyGroup(String group) {
        List<Dependency> result = new ArrayList<>();
        String[] alternatives = group.split("\\|");
        for (String alt : alternatives) {
            result.add(parseSingleDependency(alt.trim()));
        }
        return result;
    }

    static Dependency parseSingleDependency(String dep) {
        dep = dep.trim();
        Dependency d = new Dependency();

        int parenIdx = dep.indexOf('(');
        if (parenIdx != -1) {
            String name = dep.substring(0, parenIdx).trim();
            int archQualifier = name.indexOf(':');
            if (archQualifier != -1) {
                name = name.substring(0, archQualifier);
            }
            d.setName(name);

            String versionPart = dep.substring(parenIdx + 1);
            int closeParen = versionPart.indexOf(')');
            if (closeParen != -1) {
                versionPart = versionPart.substring(0, closeParen).trim();
            }
            versionPart = versionPart.trim();

            if (versionPart.startsWith(">=")) {
                d.setOperator(Dependency.Operator.GE);
                d.setVersion(versionPart.substring(2).trim());
            } else if (versionPart.startsWith("<=")) {
                d.setOperator(Dependency.Operator.LE);
                d.setVersion(versionPart.substring(2).trim());
            } else if (versionPart.startsWith("<<")) {
                d.setOperator(Dependency.Operator.LT);
                d.setVersion(versionPart.substring(2).trim());
            } else if (versionPart.startsWith(">>")) {
                d.setOperator(Dependency.Operator.GT);
                d.setVersion(versionPart.substring(2).trim());
            } else if (versionPart.startsWith("=")) {
                d.setOperator(Dependency.Operator.EQ);
                d.setVersion(versionPart.substring(1).trim());
            } else {
                d.setOperator(Dependency.Operator.ANY);
                d.setVersion(versionPart);
            }
        } else {
            String name = dep.trim();
            int colonIdx = name.indexOf(':');
            if (colonIdx != -1) {
                name = name.substring(0, colonIdx);
            }
            d.setName(name);
            d.setOperator(Dependency.Operator.ANY);
        }

        return d;
    }

    private void loadFileList(PackageModel pkg) throws IOException {
        Path listFile = infoDir.resolve(pkg.getName() + ".list");
        if (!Files.exists(listFile)) {
            LOGGER.warning("File list not found for " + pkg.getName());
            return;
        }
        List<String> lines = Files.readAllLines(listFile);
        List<FilePath> files = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String relPath = relativizePath(line);
            files.add(new FilePath(relPath, null, false));
        }
        pkg.setFiles(files);
    }

    private void loadMd5Sums(PackageModel pkg) throws IOException {
        Path md5File = infoDir.resolve(pkg.getName() + ".md5sums");
        if (!Files.exists(md5File)) {
            return;
        }
        List<String> lines = Files.readAllLines(md5File);
        Map<String, String> md5Map = new HashMap<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int spaceIdx = line.indexOf(' ');
            if (spaceIdx != -1) {
                String hash = line.substring(0, spaceIdx).trim();
                String relPath = line.substring(spaceIdx + 1).trim();
                md5Map.put(relPath, hash);
            }
        }
        for (FilePath file : pkg.getFiles()) {
            String md5 = md5Map.get(file.getPath());
            if (md5 != null) {
                file.setMd5sum(md5);
            }
        }
    }

    private void loadConffiles(PackageModel pkg) throws IOException {
        Path conffilesFile = infoDir.resolve(pkg.getName() + ".conffiles");
        if (!Files.exists(conffilesFile)) {
            return;
        }
        List<String> lines = Files.readAllLines(conffilesFile);
        List<Conffile> existingConffiles = pkg.getConffiles();
        Set<String> existingPaths = new HashSet<>();
        for (Conffile cf : existingConffiles) {
            existingPaths.add(cf.getPath());
        }
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String relPath = relativizePath(line);
            if (!existingPaths.contains(relPath)) {
                existingConffiles.add(new Conffile(relPath, null));
            }
        }
    }

    private void loadScripts(PackageModel pkg) throws IOException {
        Scripts scripts = new Scripts();

        Path preinstFile = infoDir.resolve(pkg.getName() + ".preinst");
        if (Files.exists(preinstFile)) {
            scripts.setPreInst(readFile(preinstFile));
        }

        Path postinstFile = infoDir.resolve(pkg.getName() + ".postinst");
        if (Files.exists(postinstFile)) {
            scripts.setPostInst(readFile(postinstFile));
        }

        Path prermFile = infoDir.resolve(pkg.getName() + ".prerm");
        if (Files.exists(prermFile)) {
            scripts.setPreRm(readFile(prermFile));
        }

        Path postrmFile = infoDir.resolve(pkg.getName() + ".postrm");
        if (Files.exists(postrmFile)) {
            scripts.setPostRm(readFile(postrmFile));
        }

        pkg.setScripts(scripts);
    }

    private String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String relativizePath(String absolutePath) {
        String prefix = TERMUX_PREFIX + "/";
        if (absolutePath.startsWith(prefix)) {
            return absolutePath.substring(prefix.length());
        }
        String customPrefix = prefixPath.toString() + "/";
        if (absolutePath.startsWith(customPrefix)) {
            return absolutePath.substring(customPrefix.length());
        }
        return absolutePath;
    }
}
