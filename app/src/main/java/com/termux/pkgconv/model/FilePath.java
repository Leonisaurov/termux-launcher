package com.termux.pkgconv.model;

public class FilePath {
    private String path;
    private String md5sum;
    private boolean directory;

    public FilePath() {}

    public FilePath(String path, String md5sum, boolean directory) {
        this.path = path;
        this.md5sum = md5sum;
        this.directory = directory;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMd5sum() {
        return md5sum;
    }

    public void setMd5sum(String md5sum) {
        this.md5sum = md5sum;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }
}
