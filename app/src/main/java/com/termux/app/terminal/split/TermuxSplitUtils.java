package com.termux.app.terminal.split;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades para el sistema de split-screen.
 * Incluye helpers para serialización y validación del árbol.
 */
public class TermuxSplitUtils {
    
    /**
     * Cuenta cuántos LeafNode (terminales) hay en el árbol.
     */
    public static int countLeaves(SplitNode node) {
        if (node instanceof LeafNode) return 1;
        if (node instanceof BranchNode) {
            BranchNode b = (BranchNode) node;
            return countLeaves(b.first) + countLeaves(b.second);
        }
        return 0;
    }
    
    /**
     * Obtiene todos los sessionIndex del árbol en orden (in-order traversal).
     */
    public static List<Integer> getSessionIndices(SplitNode node) {
        List<Integer> indices = new ArrayList<>();
        collectIndices(node, indices);
        return indices;
    }
    
    private static void collectIndices(SplitNode node, List<Integer> indices) {
        if (node instanceof LeafNode) {
            indices.add(((LeafNode) node).sessionIndex);
        } else if (node instanceof BranchNode) {
            BranchNode b = (BranchNode) node;
            collectIndices(b.first, indices);
            collectIndices(b.second, indices);
        }
    }
    
    /**
     * Valida que todos los sessionIndex estén dentro del rango [0, sessionCount).
     */
    public static boolean validateIndices(SplitNode node, int sessionCount) {
        List<Integer> indices = getSessionIndices(node);
        for (int idx : indices) {
            if (idx < 0 || idx >= sessionCount) return false;
        }
        return true;
    }
    
    /**
     * Busca el BranchNode cuyo divider está en la posición (x, y) dada,
     * dentro del rectángulo dado para el nodo.
     * @return el BranchNode si se encontró un divider cerca de (x, y), o null si no.
     */
    @Nullable
    public static BranchNode findDividerAt(SplitNode node, int dividerSize, 
                                            int nodeLeft, int nodeTop, int nodeRight, int nodeBottom,
                                            float x, float y) {
        if (node instanceof LeafNode) return null;
        if (node instanceof BranchNode) {
            BranchNode b = (BranchNode) node;
            int halfDivider = dividerSize / 2;
            
            if (b.orientation == BranchNode.Orientation.HORIZONTAL) {
                int splitX = nodeLeft + (int) ((nodeRight - nodeLeft) * b.ratio);
                // Check if touch is on the divider
                Rect firstRect = new Rect(nodeLeft, nodeTop, splitX - halfDivider, nodeBottom);
                Rect secondRect = new Rect(splitX + halfDivider, nodeTop, nodeRight, nodeBottom);
                
                // Check divider zone
                if (x >= splitX - halfDivider && x <= splitX + halfDivider
                    && y >= nodeTop && y <= nodeBottom) {
                    return b;
                }
                
                // Recurse into children
                BranchNode result = findDividerAt(b.first, dividerSize, 
                    firstRect.left, firstRect.top, firstRect.right, firstRect.bottom, x, y);
                if (result != null) return result;
                return findDividerAt(b.second, dividerSize,
                    secondRect.left, secondRect.top, secondRect.right, secondRect.bottom, x, y);
                
            } else { // VERTICAL
                int splitY = nodeTop + (int) ((nodeBottom - nodeTop) * b.ratio);
                Rect firstRect = new Rect(nodeLeft, nodeTop, nodeRight, splitY - halfDivider);
                Rect secondRect = new Rect(nodeLeft, splitY + halfDivider, nodeRight, nodeBottom);
                
                if (x >= nodeLeft && x <= nodeRight
                    && y >= splitY - halfDivider && y <= splitY + halfDivider) {
                    return b;
                }
                
                BranchNode result = findDividerAt(b.first, dividerSize, 
                    firstRect.left, firstRect.top, firstRect.right, firstRect.bottom, x, y);
                if (result != null) return result;
                return findDividerAt(b.second, dividerSize,
                    secondRect.left, secondRect.top, secondRect.right, secondRect.bottom, x, y);
            }
        }
        return null;
    }
    
    // Inner Rect class to avoid importing android.graphics.Rect
    private static class Rect {
        int left, top, right, bottom;
        Rect(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
    }
}
