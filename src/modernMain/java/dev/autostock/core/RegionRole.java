package dev.autostock.core;

public enum RegionRole {
    MATERIAL("材料区"), EMPTY_BOX("空盒区"), OUTPUT("备货区");
    private final String label;
    RegionRole(String label) { this.label = label; }
    public String label() { return label; }
}
