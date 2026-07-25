package com.termux.app.terminal.split;

/**
 * Nodo rama del árbol de splits.
 * Representa una división de la pantalla en dos paneles,
 * con una orientación (horizontal/vertical) y una proporción.
 */
public class BranchNode extends SplitNode {
    
    public enum Orientation {
        HORIZONTAL,
        VERTICAL
    }
    
    public Orientation orientation;
    public float ratio; // 0.0 to 1.0, proportion for the first child
    public SplitNode first;
    public SplitNode second;
    
    public BranchNode(Orientation orientation, float ratio, SplitNode first, SplitNode second) {
        this.orientation = orientation;
        this.ratio = ratio;
        this.first = first;
        this.second = second;
    }
    
    @Override
    public String toString() {
        return "BranchNode{orientation=" + orientation + ", ratio=" + ratio
            + ", first=" + first + ", second=" + second + "}";
    }
}
