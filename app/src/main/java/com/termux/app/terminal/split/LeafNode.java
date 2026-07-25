package com.termux.app.terminal.split;

/**
 * Nodo hoja del árbol de splits.
 * Representa una terminal individual, referenciada por su índice
 * en la lista de sesiones de TermuxShellManager.
 */
public class LeafNode extends SplitNode {
    public final int sessionIndex;
    
    public LeafNode(int sessionIndex) {
        this.sessionIndex = sessionIndex;
    }
    
    @Override
    public String toString() {
        return "LeafNode{sessionIndex=" + sessionIndex + "}";
    }
}
