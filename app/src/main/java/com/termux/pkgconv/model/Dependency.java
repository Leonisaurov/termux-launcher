package com.termux.pkgconv.model;

public class Dependency {
    public enum Operator { GE, LE, GT, LT, EQ, ANY }

    private String name;
    private Operator operator;
    private String version;
    private int orGroupId;

    public Dependency() {
        this.operator = Operator.ANY;
    }

    public Dependency(String name, Operator operator, String version) {
        this.name = name;
        this.operator = operator;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getOrGroupId() {
        return orGroupId;
    }

    public void setOrGroupId(int orGroupId) {
        this.orGroupId = orGroupId;
    }

    @Override
    public String toString() {
        if (operator == Operator.ANY) {
            return name;
        }
        String sym;
        switch (operator) {
            case GE: sym = ">="; break;
            case LE: sym = "<="; break;
            case GT: sym = ">"; break;
            case LT: sym = "<"; break;
            case EQ: sym = "="; break;
            default: sym = "";
        }
        return name + sym + version;
    }
}
