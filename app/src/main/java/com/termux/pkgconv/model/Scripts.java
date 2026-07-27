package com.termux.pkgconv.model;

public class Scripts {
    private String preInst;
    private String postInst;
    private String preRm;
    private String postRm;

    public Scripts() {}

    public boolean hasAny() {
        return (preInst != null && !preInst.isEmpty()) ||
               (postInst != null && !postInst.isEmpty()) ||
               (preRm != null && !preRm.isEmpty()) ||
               (postRm != null && !postRm.isEmpty());
    }

    public String getPreInst() {
        return preInst;
    }

    public void setPreInst(String preInst) {
        this.preInst = preInst;
    }

    public String getPostInst() {
        return postInst;
    }

    public void setPostInst(String postInst) {
        this.postInst = postInst;
    }

    public String getPreRm() {
        return preRm;
    }

    public void setPreRm(String preRm) {
        this.preRm = preRm;
    }

    public String getPostRm() {
        return postRm;
    }

    public void setPostRm(String postRm) {
        this.postRm = postRm;
    }
}
