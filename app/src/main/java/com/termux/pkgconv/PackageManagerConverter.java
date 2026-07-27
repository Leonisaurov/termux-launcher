package com.termux.pkgconv;

import android.content.Context;

import com.termux.pkgconv.model.*;
import com.termux.pkgconv.parser.*;
import com.termux.pkgconv.writer.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Stream;

public class PackageManagerConverter {

    private static final Logger LOGGER = Logger.getLogger(PackageManagerConverter.class.getName());

    public enum PackageManager {
        APT, PACMAN, NONE
    }

    public enum ConvertStep {
        INIT(0),
        BACKUP_READY(1),
        PARSED(2),
        SWAPPED(3),
        WRITTEN(4),
        CLEANUP_DONE(5),
        COMPLETE(6);

        public final int step;
        ConvertStep(int step) { this.step = step; }
    }

    private final Path prefixPath;
    private final Path lockFile;
    private final Path backupDir;
    private final Path logFile;

    private Context context;
    private ProgressCallback progressCallback;

    public interface ProgressCallback {
        void onProgress(String message, int percent);
        void onError(String message, Exception e);
        void onComplete(boolean success);
    }

    public PackageManagerConverter() {
        this(Paths.get("/data/data/com.termux/files/usr"));
    }

    public PackageManagerConverter(Path prefixPath) {
        this.prefixPath = prefixPath;
        this.lockFile = prefixPath.resolve("var/run/pm-convert.lock");
        this.backupDir = prefixPath.resolve("var/backups/pm-convert");
        this.logFile = prefixPath.resolve("var/log/pm-convert.log");
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    public PackageManager detectCurrentPackageManager() throws IOException {
        Path dpkgStatus = prefixPath.resolve("var/lib/dpkg/status");
        Path pacmanLocal = prefixPath.resolve("var/lib/pacman/local");

        if (Files.exists(dpkgStatus) && Files.size(dpkgStatus) > 0) {
            return PackageManager.APT;
        } else if (Files.exists(pacmanLocal) && hasPacmanPackages(pacmanLocal)) {
            return PackageManager.PACMAN;
        }
        return PackageManager.NONE;
    }

    private boolean hasPacmanPackages(Path pacmanLocal) throws IOException {
        if (!Files.isDirectory(pacmanLocal)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pacmanLocal)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) return true;
            }
        }
        return false;
    }

    public void convert(PackageManager targetPM) throws IOException, ConversionException {
        PackageManager sourcePM = detectCurrentPackageManager();

        if (sourcePM == PackageManager.NONE) {
            throw new IllegalStateException("No package manager detected at " + prefixPath);
        }
        if (sourcePM == targetPM) {
            throw new IllegalStateException("Already using " + targetPM);
        }

        convert(sourcePM, targetPM);
    }

    public void convert(PackageManager sourcePM, PackageManager targetPM)
            throws IOException, ConversionException {

        List<PackageModel> packages = null;

        try {
            reportProgress("Initializing conversion...", 0);

            if (hasIncompleteConversion()) {
                handleIncompleteConversion();
            }

            reportProgress("Creating backup...", 5);
            createBackup(sourcePM);
            writeLock(ConvertStep.BACKUP_READY, sourcePM, targetPM);

            reportProgress("Reading installed packages...", 15);
            packages = parsePackages(sourcePM);
            if (packages == null || packages.size() < 5) {
                throw new ConversionException("Too few packages (" + (packages == null ? 0 : packages.size()) +
                    ") to convert. At least 5 packages expected (bootstrap minimum).");
            }
            reportProgress("Found " + packages.size() + " packages", 25);
            writeLock(ConvertStep.PARSED, sourcePM, targetPM);

            reportProgress("Swapping package manager binaries...", 30);
            swapBinaries(sourcePM, targetPM);
            reportProgress("Binaries swapped", 45);
            writeLock(ConvertStep.SWAPPED, sourcePM, targetPM);

            reportProgress("Writing new package database...", 50);
            writePackages(targetPM, packages);
            reportProgress("Database written", 75);
            writeLock(ConvertStep.WRITTEN, sourcePM, targetPM);

            reportProgress("Cleaning up old database...", 80);
            cleanupOldDatabase(sourcePM);
            writeLock(ConvertStep.CLEANUP_DONE, sourcePM, targetPM);

            writeLock(ConvertStep.COMPLETE, sourcePM, targetPM);
            removeLock();
            reportProgress("Conversion complete!", 100);

            log("Conversion completed: " + sourcePM + " \u2192 " + targetPM +
                " (" + packages.size() + " packages)");

        } catch (Exception e) {
            LOGGER.severe("Conversion failed: " + e.getMessage());
            log("Conversion FAILED: " + e.getMessage());
            reportError("Conversion failed: " + e.getMessage(), e);

            try {
                rollback(sourcePM, targetPM, packages);
            } catch (Exception rb) {
                LOGGER.severe("Rollback also failed: " + rb.getMessage());
                reportError("Rollback failed: " + rb.getMessage(), rb);
            }

            if (e instanceof IOException) throw (IOException) e;
            throw new ConversionException("Conversion failed", e);
        }
    }

    // ============================================================
    // STEPS
    // ============================================================

    private List<PackageModel> parsePackages(PackageManager sourcePM) throws IOException {
        if (sourcePM == PackageManager.APT) {
            DpkgParser parser = new DpkgParser(prefixPath);
            return parser.parse();
        } else {
            AlpmParser parser = new AlpmParser(prefixPath);
            return parser.parse();
        }
    }

    private void swapBinaries(PackageManager sourcePM, PackageManager targetPM) throws IOException {
        BootstrapSwapper swapper = new BootstrapSwapper(prefixPath);
        if (context != null) {
            swapper.swap(sourcePM, targetPM, context);
        } else {
            swapper.swap(sourcePM, targetPM);
        }
    }

    private void writePackages(PackageManager targetPM, List<PackageModel> packages) throws IOException {
        if (targetPM == PackageManager.APT) {
            DpkgWriter writer = new DpkgWriter(prefixPath);
            writer.write(packages);
        } else {
            AlpmWriter writer = new AlpmWriter(prefixPath);
            writer.write(packages);
        }
    }

    private void cleanupOldDatabase(PackageManager sourcePM) throws IOException {
        if (sourcePM == PackageManager.APT) {
            Path dpkgDir = prefixPath.resolve("var/lib/dpkg");
            deleteDirectory(dpkgDir);
            Path aptDir = prefixPath.resolve("etc/apt");
            deleteDirectory(aptDir);
        } else {
            Path pacmanLocal = prefixPath.resolve("var/lib/pacman/local");
            deleteDirectory(pacmanLocal);
        }
    }

    // ============================================================
    // BACKUP & ROLLBACK
    // ============================================================

    private void createBackup(PackageManager sourcePM) throws IOException {
        Files.createDirectories(backupDir);

        String timestamp = String.valueOf(System.currentTimeMillis());
        Path backupFile = backupDir.resolve("backup-" + timestamp + ".tar");

        List<Path> pathsToBackup = new ArrayList<>();

        if (sourcePM == PackageManager.APT) {
            addIfExists(pathsToBackup, prefixPath.resolve("var/lib/dpkg"));
            addIfExists(pathsToBackup, prefixPath.resolve("etc/apt"));
        } else {
            addIfExists(pathsToBackup, prefixPath.resolve("var/lib/pacman/local"));
            addIfExists(pathsToBackup, prefixPath.resolve("etc/pacman.d"));
            addIfExists(pathsToBackup, prefixPath.resolve("etc/pacman.conf"));
        }

        addIfExists(pathsToBackup, prefixPath.resolve("bin"));
        addIfExists(pathsToBackup, prefixPath.resolve("lib"));

        Path backupTarget = backupDir.resolve("pre-convert-" + sourcePM.name().toLowerCase());
        if (Files.exists(backupTarget)) {
            deleteDirectory(backupTarget);
        }
        Files.createDirectories(backupTarget);

        for (Path path : pathsToBackup) {
            if (Files.exists(path)) {
                copyDirectory(path, backupTarget.resolve(path.getFileName()));
            }
        }
    }

    private void rollback(PackageManager sourcePM, PackageManager targetPM,
                          List<PackageModel> packages) throws IOException {
        reportProgress("Rolling back...", 85);
        log("Starting rollback...");

        Path backupPath = backupDir.resolve("pre-convert-" + sourcePM.name().toLowerCase());

        if (!Files.exists(backupPath)) {
            log("No backup found at " + backupPath + ", rollback limited");
            reportError("No backup available for rollback", null);
            return;
        }

        if (targetPM == PackageManager.APT) {
            deleteQuietly(prefixPath.resolve("var/lib/dpkg"));
            deleteQuietly(prefixPath.resolve("etc/apt"));
        } else {
            deleteQuietly(prefixPath.resolve("var/lib/pacman/local"));
        }

        Path backupSourceDir = backupPath.resolve(
            sourcePM == PackageManager.APT ? "dpkg" : "local");
        if (Files.exists(backupSourceDir)) {
            copyDirectory(backupPath,
                sourcePM == PackageManager.APT ?
                    prefixPath.resolve("var/lib") :
                    prefixPath.resolve("var/lib/pacman"));
        }

        log("Rollback completed");
        removeLock();
        reportProgress("Rollback completed", 100);
    }

    // ============================================================
    // LOCK MANAGEMENT
    // ============================================================

    public boolean hasIncompleteConversion() {
        if (!Files.exists(lockFile)) return false;
        try {
            String content = new String(Files.readAllBytes(lockFile));
            return content.contains("\"step\":") && !content.contains("\"step\":6");
        } catch (IOException e) {
            return false;
        }
    }

    public boolean handleIncompleteConversionIfNeeded() {
        if (!hasIncompleteConversion()) return false;
        try {
            handleIncompleteConversion();
            return true;
        } catch (Exception e) {
            LOGGER.severe("Failed to handle incomplete conversion: " + e.getMessage());
            removeLock();
            return false;
        }
    }

    private void handleIncompleteConversion() throws IOException {
        log("Detected incomplete conversion, attempting rollback...");
        reportProgress("Detected interrupted conversion. Restoring...", 0);

        String lockContent = new String(Files.readAllBytes(lockFile));
        int step = extractStep(lockContent);
        String sourceStr = extractValue(lockContent, "source_pm");
        String targetStr = extractValue(lockContent, "target_pm");

        PackageManager sourcePM = PackageManager.valueOf(sourceStr);
        PackageManager targetPM = PackageManager.valueOf(targetStr);

        rollback(sourcePM, targetPM, null);

        log("Incomplete conversion recovered");
        reportProgress("Previous interrupted conversion has been restored", 100);
    }

    private void writeLock(ConvertStep step, PackageManager sourcePM, PackageManager targetPM)
            throws IOException {
        Files.createDirectories(lockFile.getParent());

        String json = String.format(
            "{\"step\":%d,\"step_name\":\"%s\",\"source_pm\":\"%s\",\"target_pm\":\"%s\",\"timestamp\":%d}",
            step.step, step.name(), sourcePM.name(), targetPM.name(), System.currentTimeMillis()
        );

        Files.write(lockFile, json.getBytes());
    }

    private void removeLock() {
        try {
            Files.deleteIfExists(lockFile);
        } catch (IOException e) {
            LOGGER.warning("Failed to remove lock file: " + e.getMessage());
        }
    }

    private int extractStep(String json) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"step\":(\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private String extractValue(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\":\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    // ============================================================
    // UTILITIES
    // ============================================================

    private void reportProgress(String message, int percent) {
        if (progressCallback != null) {
            progressCallback.onProgress(message, percent);
        }
    }

    private void reportError(String message, Exception e) {
        if (progressCallback != null) {
            progressCallback.onError(message, e);
        }
    }

    private void log(String message) {
        try {
            Files.createDirectories(logFile.getParent());
            String line = "[" + new java.util.Date() + "] " + message + "\n";
            Files.write(logFile, line.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warning("Failed to write log: " + e.getMessage());
        }
    }

    private void addIfExists(List<Path> list, Path path) {
        if (Files.exists(path)) list.add(path);
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            deleteDirectory(path);
        } catch (IOException e) {
            LOGGER.warning("Failed to delete " + path + ": " + e.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
