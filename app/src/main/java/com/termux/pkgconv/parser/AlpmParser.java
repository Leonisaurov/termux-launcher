package com.termux.pkgconv.parser;

import com.termux.pkgconv.model.*;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AlpmParser {

    private static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    private static final String PACMAN_LOCAL_DIR = TERMUX_PREFIX + "/var/lib/pacman/local";
    private static final Logger LOGGER = Logger.getLogger(AlpmParser.class.getName());

    private final Path prefixPath;
    private final Path localDir;

    public AlpmParser() {
        this(Paths.get(TERMUX_PREFIX));
    }

    public AlpmParser(Path prefixPath) {
        this.prefixPath = prefixPath;
        this.localDir = prefixPath.resolve("var/lib/pacman/local");
    }

    public List<PackageModel> parse() throws IOException {
        List<PackageModel> packages = new ArrayList<>();

        if (!Files.isDirectory(localDir)) {
            return packages;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(localDir)) {
            for (Path pkgDir : stream) {
                if (!Files.isDirectory(pkgDir)) continue;

                PackageModel pkg = parsePackageDir(pkgDir);
                if (pkg != null) {
                    packages.add(pkg);
                }
            }
        }

        return packages;
    }

    private PackageModel parsePackageDir(Path pkgDir) {
        String dirName = pkgDir.getFileName().toString();
        NameVersionRel nvr = parseDirName(dirName);

        PackageModel pkg = new PackageModel();

        try {
            Path descFile = pkgDir.resolve("desc");
            if (Files.exists(descFile)) {
                parseDescFile(descFile, pkg);
            }

            Path filesFile = pkgDir.resolve("files");
            if (Files.exists(filesFile)) {
                parseFilesFile(filesFile, pkg);
            }

            Path dependsFile = pkgDir.resolve("depends");
            if (Files.exists(dependsFile)) {
                parseDependsFile(dependsFile, pkg);
            }

            Path installFile = pkgDir.resolve("install");
            if (Files.exists(installFile)) {
                parseInstallFile(installFile, pkg);
            }
        } catch (IOException e) {
            LOGGER.warning("Error reading package dir " + dirName + ": " + e.getMessage());
            return null;
        }

        if (pkg.getName() == null) {
            pkg.setName(nvr.name);
        }
        if (pkg.getVersion() == null) {
            String ver = nvr.version;
            if (nvr.pkgrel != null) {
                ver = nvr.version + "-" + nvr.pkgrel;
            }
            pkg.setVersion(ver);
        }

        return pkg;
    }

    static NameVersionRel parseDirName(String dirName) {
        String[] parts = dirName.split("-");
        int last = parts.length - 1;
        String pkgrel = null;
        String version;
        String name;

        if (last >= 0 && parts[last].matches("\\d+")) {
            pkgrel = parts[last];
            last--;
        }

        if (last >= 0 && parts[last].matches(".*\\d.*")) {
            version = parts[last];
            last--;
        } else {
            version = parts[last];
            last--;
        }

        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i <= last; i++) {
            if (i > 0) nameBuilder.append("-");
            nameBuilder.append(parts[i]);
        }
        name = nameBuilder.toString();

        NameVersionRel result = new NameVersionRel();
        result.name = name;
        result.version = version;
        result.pkgrel = pkgrel;
        return result;
    }

    private String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

    private void parseDescFile(Path descFile, PackageModel pkg) throws IOException {
        String content = readFile(descFile);
        String[] lines = content.split("\n");
        String currentKey = null;
        List<String> currentValues = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("%") && line.endsWith("%")) {
                if (currentKey != null) {
                    processSection(currentKey, currentValues, pkg);
                }
                currentKey = line.substring(1, line.length() - 1);
                currentValues = new ArrayList<>();
            } else if (currentKey != null) {
                if (!line.trim().isEmpty()) {
                    currentValues.add(line);
                }
            }
        }

        if (currentKey != null) {
            processSection(currentKey, currentValues, pkg);
        }
    }

    private void processSection(String key, List<String> values, PackageModel pkg) {
        switch (key) {
            case "NAME":
                if (!values.isEmpty()) pkg.setName(values.get(0));
                break;
            case "VERSION":
                if (!values.isEmpty()) pkg.setVersion(values.get(0));
                break;
            case "DESC":
                if (!values.isEmpty()) pkg.setDescription(values.get(0));
                break;
            case "GROUPS":
                pkg.setGroups(new ArrayList<>(values));
                break;
            case "URL":
                if (!values.isEmpty()) pkg.setUrl(values.get(0));
                break;
            case "LICENSE":
                if (!values.isEmpty()) pkg.setLicense(values.get(0));
                break;
            case "ARCH":
                if (!values.isEmpty()) pkg.setArch(values.get(0));
                break;
            case "BUILDDATE":
                if (!values.isEmpty()) {
                    try {
                        pkg.setBuildDate(Long.parseLong(values.get(0)));
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Invalid BUILDDATE for " + pkg.getName() + ": " + values.get(0));
                    }
                }
                break;
            case "INSTALLDATE":
                if (!values.isEmpty()) {
                    try {
                        pkg.setInstallDate(Long.parseLong(values.get(0)));
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Invalid INSTALLDATE for " + pkg.getName() + ": " + values.get(0));
                    }
                }
                break;
            case "PACKAGER":
                if (!values.isEmpty()) pkg.setMaintainer(values.get(0));
                break;
            case "SIZE":
                if (!values.isEmpty()) {
                    try {
                        pkg.setInstalledSize(Long.parseLong(values.get(0)));
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Invalid SIZE for " + pkg.getName() + ": " + values.get(0));
                    }
                }
                break;
            case "REASON":
                if (!values.isEmpty()) {
                    String reason = values.get(0).trim();
                    if ("0".equals(reason)) {
                        pkg.setReason(InstallReason.AUTO);
                    } else if ("1".equals(reason)) {
                        pkg.setReason(InstallReason.EXPLICIT);
                    }
                }
                break;
            case "DEPENDS":
                pkg.setDepends(parseDependencies(values));
                break;
            case "CONFLICTS":
                pkg.setConflicts(parseDependenciesWithAny(values));
                break;
            case "PROVIDES":
                pkg.setProvides(parseDependenciesWithAny(values));
                break;
            case "REPLACES":
                pkg.setReplaces(parseDependenciesWithAny(values));
                break;
            case "BACKUP":
                List<Conffile> conffiles = new ArrayList<>();
                for (String val : values) {
                    String[] parts = val.split(" ", 2);
                    if (parts.length == 2) {
                        conffiles.add(new Conffile(parts[0], parts[1]));
                    }
                }
                pkg.setConffiles(conffiles);
                break;
        }
    }

    private void parseFilesFile(Path filesFile, PackageModel pkg) throws IOException {
        String content = readFile(filesFile);
        String currentSection = null;
        List<FilePath> filePaths = new ArrayList<>();
        List<Conffile> conffiles = new ArrayList<>(pkg.getConffiles());

        for (String line : content.split("\n")) {
            if (line.equals("%FILES%")) {
                currentSection = "FILES";
            } else if (line.equals("%BACKUP%")) {
                currentSection = "BACKUP";
            } else if (currentSection != null && !line.trim().isEmpty()) {
                if ("FILES".equals(currentSection)) {
                    boolean isDir = line.endsWith("/");
                    String path = isDir ? line.substring(0, line.length() - 1) : line;
                    filePaths.add(new FilePath(path, null, isDir));
                } else if ("BACKUP".equals(currentSection)) {
                    String[] parts = line.split(" ", 2);
                    if (parts.length == 2) {
                        conffiles.add(new Conffile(parts[0], parts[1]));
                    }
                }
            }
        }

        pkg.setFiles(filePaths);
        pkg.setConffiles(conffiles);
    }

    private void parseDependsFile(Path dependsFile, PackageModel pkg) throws IOException {
        List<String> lines = Files.readAllLines(dependsFile);
        List<Dependency> deps = pkg.getDepends();
        deps.addAll(parseDependencies(lines));
        pkg.setDepends(deps);
    }

    private void parseInstallFile(Path installFile, PackageModel pkg) throws IOException {
        String content = readFile(installFile);
        Scripts scripts = new Scripts();

        Pattern funcPattern = Pattern.compile(
                "(post_install|pre_remove|post_remove|pre_upgrade|post_upgrade)\\s*\\(\\)\\s*\\{"
        );
        Matcher matcher = funcPattern.matcher(content);

        int lastEnd = 0;
        while (matcher.find()) {
            String funcName = matcher.group(1);
            int start = matcher.start();
            int braceStart = content.indexOf('{', start);
            if (braceStart == -1) continue;

            int braceCount = 1;
            int braceEnd = braceStart + 1;
            while (braceEnd < content.length() && braceCount > 0) {
                char c = content.charAt(braceEnd);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                braceEnd++;
            }

            String funcBody = content.substring(braceStart + 1, braceEnd - 1).trim();

            switch (funcName) {
                case "post_install":
                    scripts.setPostInst(funcBody);
                    break;
                case "pre_remove":
                    scripts.setPreRm(funcBody);
                    break;
                case "post_remove":
                    scripts.setPostRm(funcBody);
                    break;
                case "pre_upgrade":
                    String existingPost = scripts.getPostInst();
                    if (existingPost != null && !existingPost.isEmpty()) {
                        scripts.setPostInst(existingPost + "\n\n# pre_upgrade\n" + funcBody);
                    } else {
                        scripts.setPostInst("# pre_upgrade\n" + funcBody);
                    }
                    break;
                case "post_upgrade":
                    String existingPost2 = scripts.getPostInst();
                    if (existingPost2 != null && !existingPost2.isEmpty()) {
                        scripts.setPostInst(existingPost2 + "\n\n# post_upgrade\n" + funcBody);
                    } else {
                        scripts.setPostInst("# post_upgrade\n" + funcBody);
                    }
                    break;
            }
        }

        pkg.setScripts(scripts);
    }

    private List<Dependency> parseDependencies(List<String> lines) {
        List<Dependency> result = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            result.add(parseSingleDependency(line));
        }
        return result;
    }

    private List<Dependency> parseDependenciesWithAny(List<String> lines) {
        List<Dependency> result = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Dependency dep = parseSingleDependency(line);
            dep.setOperator(Dependency.Operator.ANY);
            result.add(dep);
        }
        return result;
    }

    static Dependency parseSingleDependency(String dep) {
        dep = dep.trim();
        Dependency d = new Dependency();

        Pattern pattern = Pattern.compile("(>=|<=|>|<|=)");
        Matcher matcher = pattern.matcher(dep);

        if (matcher.find()) {
            String name = dep.substring(0, matcher.start()).trim();
            String op = matcher.group(1);
            String ver = dep.substring(matcher.end()).trim();
            d.setName(name);

            switch (op) {
                case ">=":
                    d.setOperator(Dependency.Operator.GE);
                    break;
                case "<=":
                    d.setOperator(Dependency.Operator.LE);
                    break;
                case ">":
                    d.setOperator(Dependency.Operator.GT);
                    break;
                case "<":
                    d.setOperator(Dependency.Operator.LT);
                    break;
                case "=":
                    d.setOperator(Dependency.Operator.EQ);
                    break;
            }
            d.setVersion(ver);
        } else {
            d.setName(dep);
            d.setOperator(Dependency.Operator.ANY);
        }

        return d;
    }

    static class NameVersionRel {
        String name;
        String version;
        String pkgrel;
    }
}
