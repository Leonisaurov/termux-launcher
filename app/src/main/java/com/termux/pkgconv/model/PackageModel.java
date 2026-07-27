package com.termux.pkgconv.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PackageModel {
    private String name;
    private String version;
    private String arch;
    private String description;
    private String url;
    private String license;
    private String maintainer;
    private long installedSize;
    private long installDate;
    private long buildDate;
    private InstallReason reason;
    private List<String> groups;
    private List<Dependency> depends;
    private List<Dependency> replaces;
    private List<Dependency> conflicts;
    private List<Dependency> provides;
    private List<FilePath> files;
    private List<Conffile> conffiles;
    private Scripts scripts;

    public PackageModel() {
        this.reason = InstallReason.UNKNOWN;
        this.groups = new ArrayList<>();
        this.depends = new ArrayList<>();
        this.replaces = new ArrayList<>();
        this.conflicts = new ArrayList<>();
        this.provides = new ArrayList<>();
        this.files = new ArrayList<>();
        this.conffiles = new ArrayList<>();
        this.scripts = new Scripts();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getMaintainer() {
        return maintainer;
    }

    public void setMaintainer(String maintainer) {
        this.maintainer = maintainer;
    }

    public long getInstalledSize() {
        return installedSize;
    }

    public void setInstalledSize(long installedSize) {
        this.installedSize = installedSize;
    }

    public long getInstallDate() {
        return installDate;
    }

    public void setInstallDate(long installDate) {
        this.installDate = installDate;
    }

    public long getBuildDate() {
        return buildDate;
    }

    public void setBuildDate(long buildDate) {
        this.buildDate = buildDate;
    }

    public InstallReason getReason() {
        return reason;
    }

    public void setReason(InstallReason reason) {
        this.reason = reason;
    }

    public List<String> getGroups() {
        return groups != null ? groups : new ArrayList<>();
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public List<Dependency> getDepends() {
        return depends != null ? depends : new ArrayList<>();
    }

    public void setDepends(List<Dependency> depends) {
        this.depends = depends;
    }

    public List<Dependency> getReplaces() {
        return replaces != null ? replaces : new ArrayList<>();
    }

    public void setReplaces(List<Dependency> replaces) {
        this.replaces = replaces;
    }

    public List<Dependency> getConflicts() {
        return conflicts != null ? conflicts : new ArrayList<>();
    }

    public void setConflicts(List<Dependency> conflicts) {
        this.conflicts = conflicts;
    }

    public List<Dependency> getProvides() {
        return provides != null ? provides : new ArrayList<>();
    }

    public void setProvides(List<Dependency> provides) {
        this.provides = provides;
    }

    public List<FilePath> getFiles() {
        return files != null ? files : new ArrayList<>();
    }

    public void setFiles(List<FilePath> files) {
        this.files = files;
    }

    public List<Conffile> getConffiles() {
        return conffiles != null ? conffiles : new ArrayList<>();
    }

    public void setConffiles(List<Conffile> conffiles) {
        this.conffiles = conffiles;
    }

    public Scripts getScripts() {
        return scripts;
    }

    public void setScripts(Scripts scripts) {
        this.scripts = scripts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackageModel that = (PackageModel) o;
        return Objects.equals(name, that.name) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }

    @Override
    public String toString() {
        return "PackageModel{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", arch='" + arch + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
