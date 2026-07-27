package com.termux.pkgconv.model;

public class Conffile {
    private String path;
    private String md5sum;

    public Conffile() {}

    public Conffile(String path, String md5sum) {
        this.path = path;
        this.md5sum = md5sum;
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
}
