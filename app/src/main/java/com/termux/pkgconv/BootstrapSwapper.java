package com.termux.pkgconv;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BootstrapSwapper {

    private static final Logger LOGGER = Logger.getLogger(BootstrapSwapper.class.getName());

    private final Path prefixPath;

    public BootstrapSwapper() {
        this(Paths.get("/data/data/com.termux/files/usr"));
    }

    public BootstrapSwapper(Path prefixPath) {
        this.prefixPath = prefixPath;
    }

    /**
     * Intercambia los binarios y config de un gestor a otro.
     * @param from Origen (gestor actual)
     * @param to Destino (gestor al que se quiere cambiar)
     */
    public void swap(PackageManagerConverter.PackageManager from,
                     PackageManagerConverter.PackageManager to) throws IOException {

        if (from == to) return;

        // 1. PRIMERO extraer los nuevos binarios
        extractPackageManagerFiles(to);

        // 2. LUEGO eliminar los viejos
        removePackageManagerFiles(from);
    }

    /**
     * Intercambia los binarios y config de un gestor a otro usando Context
     * para extraer desde los assets de Android.
     * @param from Origen (gestor actual)
     * @param to Destino (gestor al que se quiere cambiar)
     * @param context Context de Android para acceder a assets
     */
    public void swap(PackageManagerConverter.PackageManager from,
                     PackageManagerConverter.PackageManager to,
                     Context context) throws IOException {

        if (from == to) return;

        // 1. PRIMERO extraer los nuevos binarios
        extractPackageManagerFiles(to, context);

        // 2. LUEGO eliminar los viejos
        removePackageManagerFiles(from);
    }

    /**
     * Elimina los archivos específicos del gestor origen.
     */
    private void removePackageManagerFiles(PackageManagerConverter.PackageManager pm) throws IOException {
        if (pm == PackageManagerConverter.PackageManager.APT) {
            removeAPTFiles();
        } else {
            removePACMANFiles();
        }
    }

    private void removeAPTFiles() throws IOException {
        List<String> aptBins = Arrays.asList(
            "bin/apt", "bin/apt-get", "bin/apt-cache", "bin/apt-config",
            "bin/apt-key", "bin/apt-mark", "bin/apt-sortpkgs",
            "bin/dpkg", "bin/dpkg-deb", "bin/dpkg-query", "bin/dpkg-split",
            "bin/dpkg-statoverride", "bin/dpkg-trigger",
            "bin/dpkg-architecture", "bin/dpkg-buildpackage",
            "bin/dpkg-checkbuilddeps", "bin/dpkg-genchanges",
            "bin/dpkg-gencontrol", "bin/dpkg-name",
            "bin/dpkg-scanpackages", "bin/dpkg-scansources",
            "bin/dpkg-shlibdeps", "bin/dpkg-source", "bin/dpkg-divertsion",
            "bin/pkg"
        );

        for (String bin : aptBins) {
            deleteQuietly(prefixPath.resolve(bin));
        }

        deleteDirectory(prefixPath.resolve("lib/apt"));

        deleteDirectory(prefixPath.resolve("etc/apt"));

        deleteDirectory(prefixPath.resolve("var/log/apt"));
    }

    private void removePACMANFiles() throws IOException {
        List<String> pacmanBins = Arrays.asList(
            "bin/pacman", "bin/pacman-key", "bin/makepkg", "bin/pkg-config"
        );

        for (String bin : pacmanBins) {
            deleteQuietly(prefixPath.resolve(bin));
        }

        deleteDirectory(prefixPath.resolve("etc/pacman.d"));

        deleteQuietly(prefixPath.resolve("etc/pacman.conf"));

        deleteQuietly(prefixPath.resolve("var/log/pacman.log"));
    }

    /**
     * Extrae los archivos específicos del gestor destino.
     * Busca el bootstrap ZIP en assets/ o en una ruta conocida.
     */
    private void extractPackageManagerFiles(PackageManagerConverter.PackageManager pm) throws IOException {
        if (pm == PackageManagerConverter.PackageManager.PACMAN) {
            log("PACMAN binaries need to be extracted from bootstrap zip");
            log("Required files: bin/pacman*, etc/pacman.d/, etc/pacman.conf");
            markPendingExtraction(PackageManagerConverter.PackageManager.PACMAN);
        } else {
            log("APT binaries need to be extracted from bootstrap zip");
            log("Required files: bin/apt*, bin/dpkg*, bin/pkg, lib/apt/, etc/apt/");
            markPendingExtraction(PackageManagerConverter.PackageManager.APT);
        }
    }

    /**
     * Extrae los archivos específicos del gestor destino desde los assets de Android.
     * @param pm Gestor de paquetes destino
     * @param context Context de Android para acceder a assets
     */
    private void extractPackageManagerFiles(PackageManagerConverter.PackageManager pm,
                                            Context context) throws IOException {
        AssetManager assetManager = context.getAssets();

        String bootstrapZipName = "bootstrap-" + getArchName() + ".zip";

        try (InputStream zipStream = assetManager.open(bootstrapZipName)) {
            extractSpecificFiles(zipStream, pm);
            log("Extracted " + pm.name().toLowerCase() + " binaries from " + bootstrapZipName);
        } catch (IOException e) {
            log("Bootstrap zip not found in assets: " + bootstrapZipName);
            log("Will mark as pending for later extraction");
            markPendingExtraction(pm);
        }
    }

    /**
     * Extrae solo los archivos específicos del gestor desde el ZIP.
     */
    private void extractSpecificFiles(InputStream zipStream,
                                       PackageManagerConverter.PackageManager pm) throws IOException {
        Set<String> targetPaths = getTargetPaths(pm);

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.contains("..") || name.startsWith("/")) {
                    log("Skipping unsafe entry: " + name);
                    zis.closeEntry();
                    continue;
                }

                if (isRelevantEntry(name, targetPaths)) {
                    Path outputPath = prefixPath.resolve(name);

                    if (entry.isDirectory()) {
                        Files.createDirectories(outputPath);
                    } else {
                        Files.createDirectories(outputPath.getParent());
                        Files.copy(zis, outputPath, StandardCopyOption.REPLACE_EXISTING);

                        if (name.startsWith("bin/") || name.startsWith("libexec/")) {
                            outputPath.toFile().setExecutable(true, false);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private Set<String> getTargetPaths(PackageManagerConverter.PackageManager pm) {
        Set<String> paths = new HashSet<>();

        if (pm == PackageManagerConverter.PackageManager.PACMAN) {
            paths.add("bin/pacman");
            paths.add("bin/pacman-key");
            paths.add("bin/makepkg");
            paths.add("etc/pacman.conf");
            paths.add("etc/pacman.d/");
            paths.add("var/lib/pacman/");
        } else {
            paths.add("bin/apt");
            paths.add("bin/apt-get");
            paths.add("bin/apt-cache");
            paths.add("bin/apt-config");
            paths.add("bin/apt-key");
            paths.add("bin/apt-mark");
            paths.add("bin/dpkg");
            paths.add("bin/dpkg-deb");
            paths.add("bin/dpkg-query");
            paths.add("bin/dpkg-split");
            paths.add("bin/pkg");
            paths.add("lib/apt/");
            paths.add("etc/apt/");
            paths.add("var/lib/dpkg/");
        }

        return paths;
    }

    private boolean isRelevantEntry(String entryName, Set<String> targetPaths) {
        for (String target : targetPaths) {
            if (entryName.equals(target) || entryName.startsWith(target)) {
                return true;
            }
        }
        return false;
    }

    private String getArchName() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null || abis.length == 0) return "aarch64";

        String abi = abis[0];
        switch (abi) {
            case "arm64-v8a": return "aarch64";
            case "armeabi-v7a": return "arm";
            case "x86_64": return "x86_64";
            case "x86": return "i686";
            default: return "aarch64";
        }
    }

    /**
     * Marca que se necesita extracción pendiente.
     * Crea un archivo marker para que TermuxInstaller complete la extracción.
     */
    private void markPendingExtraction(PackageManagerConverter.PackageManager pm) throws IOException {
        Path markerDir = prefixPath.resolve("var/run/pm-swap");
        Files.createDirectories(markerDir);
        Files.write(markerDir.resolve("pending-" + pm.name().toLowerCase() + ".swap"),
            ("Pending extraction for: " + pm.name()).getBytes());
    }

    /**
     * Verifica si hay extracciones pendientes y las completa.
     * Este método es llamado desde TermuxInstaller que tiene acceso a assets.
     */
    public boolean hasPendingExtraction() {
        Path markerDir = prefixPath.resolve("var/run/pm-swap");
        if (!Files.exists(markerDir)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(markerDir, "*.swap")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Obtiene el gestor pendiente de extracción.
     */
    public PackageManagerConverter.PackageManager getPendingExtraction() {
        Path markerDir = prefixPath.resolve("var/run/pm-swap");
        if (!Files.exists(markerDir)) return null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(markerDir, "*.swap")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.startsWith("pending-") && name.endsWith(".swap")) {
                    String pm = name.substring(8, name.length() - 5);
                    return PackageManagerConverter.PackageManager.valueOf(pm.toUpperCase());
                }
            }
        } catch (IOException e) {
        }
        return null;
    }

    // ============================================================
    // UTILITIES
    // ============================================================

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
             .sorted(Comparator.reverseOrder())
             .map(java.nio.file.Path::toFile)
             .forEach(File::delete);
    }

    private void deleteQuietly(Path path) {
        try {
            if (Files.isDirectory(path)) {
                deleteDirectory(path);
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            LOGGER.fine("Could not delete " + path + ": " + e.getMessage());
        }
    }

    private void log(String message) {
        LOGGER.info("[BootstrapSwapper] " + message);
    }
}
