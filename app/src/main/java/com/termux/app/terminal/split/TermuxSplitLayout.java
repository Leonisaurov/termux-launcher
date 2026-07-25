package com.termux.app.terminal.split;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.List;

public class TermuxSplitLayout extends ViewGroup {

    public interface SplitLayoutCallback {
        TerminalView createNewTerminalView();
        void onPaneFocused(TerminalView view, int paneIndex);
        void onPaneCountChanged(int newCount);
    }

    private static final int DEFAULT_DIVIDER_SIZE_DP = 4;
    private static final int FOCUS_BORDER_SIZE_DP = 2;
    private static final int FOCUS_BORDER_COLOR = 0xFF4CAF50;
    private static final int DIVIDER_COLOR = 0xFF37474F;
    private static final int BACKGROUND_COLOR = 0xFF000000;

    private static final int DIVIDER_TOUCH_SLOP_DP = 20;

    private final int mDividerSizePx;
    private final int mFocusBorderSizePx;
    private final int mDividerTouchSlopPx;

    private final Paint mDividerPaint;
    private final Paint mFocusBorderPaint;
    private final Paint mBackgroundPaint;

    private SplitNode mRootNode;
    private int mFocusedPaneIndex;
    private SplitLayoutCallback mCallback;

    private boolean mIsDraggingDivider;
    private BranchNode mDragBranch;
    private float mDragStartX;
    private float mDragStartY;
    private float mDragStartRatio;

    private final List<Rect> mPaneRects = new ArrayList<>();
    private final List<Rect> mDividerRects = new ArrayList<>();

    public TermuxSplitLayout(Context context) {
        this(context, null);
    }

    public TermuxSplitLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TermuxSplitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(BACKGROUND_COLOR);

        float density = Resources.getSystem().getDisplayMetrics().density;
        mDividerSizePx = (int) (DEFAULT_DIVIDER_SIZE_DP * density);
        mFocusBorderSizePx = (int) (FOCUS_BORDER_SIZE_DP * density);
        mDividerTouchSlopPx = (int) (DIVIDER_TOUCH_SLOP_DP * density);

        mDividerPaint = new Paint();
        mDividerPaint.setColor(DIVIDER_COLOR);
        mDividerPaint.setStyle(Paint.Style.FILL);

        mFocusBorderPaint = new Paint();
        mFocusBorderPaint.setColor(FOCUS_BORDER_COLOR);
        mFocusBorderPaint.setStyle(Paint.Style.STROKE);
        mFocusBorderPaint.setStrokeWidth(mFocusBorderSizePx);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(BACKGROUND_COLOR);
        mBackgroundPaint.setStyle(Paint.Style.FILL);
    }

    public void setSplitLayoutCallback(SplitLayoutCallback callback) {
        mCallback = callback;
    }

    public void initSinglePane(TerminalView terminalView) {
        removeAllViews();
        mRootNode = new LeafNode(0);
        mFocusedPaneIndex = 0;
        terminalView.setId(View.generateViewId());
        addView(terminalView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        requestLayout();
    }

    public void splitFocusedPane(BranchNode.Orientation orientation) {
        LeafNode focusedLeaf = findLeafAt(mRootNode, mFocusedPaneIndex, new int[]{0});
        if (focusedLeaf == null) return;
        if (mCallback == null) return;

        TerminalView newTerminalView = mCallback.createNewTerminalView();
        if (newTerminalView == null) return;

        int newIndex = TermuxSplitUtils.countLeaves(mRootNode);
        LeafNode newLeaf = new LeafNode(newIndex);

        SplitNode parent = findParentOf(mRootNode, focusedLeaf);
        BranchNode branch = new BranchNode(orientation, 0.5f, focusedLeaf, newLeaf);

        if (parent == null) {
            mRootNode = branch;
        } else if (parent instanceof BranchNode) {
            BranchNode bp = (BranchNode) parent;
            if (bp.first == focusedLeaf) {
                bp.first = branch;
            } else {
                bp.second = branch;
            }
        }

        newTerminalView.setId(View.generateViewId());
        addView(newTerminalView, mFocusedPaneIndex + 1,
            new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (mCallback != null) {
            mCallback.onPaneCountChanged(getPaneCount());
        }
        requestLayout();
        invalidate();
    }

    public boolean closeFocusedPane() {
        if (getPaneCount() <= 1) return false;

        LeafNode targetLeaf = findLeafAt(mRootNode, mFocusedPaneIndex, new int[]{0});
        if (targetLeaf == null) return false;

        TerminalView targetView = getTerminalViewByLeafOrder(targetLeaf);
        if (targetView != null) {
            removeView(targetView);
        }

        List<SplitNode> path = new ArrayList<>();
        findPathToLeaf(mRootNode, targetLeaf, path);
        if (path.size() < 2) {
            mRootNode = new LeafNode(0);
            mFocusedPaneIndex = 0;
            if (mCallback != null) {
                mCallback.onPaneCountChanged(getPaneCount());
            }
            requestLayout();
            invalidate();
            return true;
        }

        BranchNode parent = (BranchNode) path.get(path.size() - 2);
        SplitNode sibling = (parent.first == targetLeaf) ? parent.second : parent.first;

        int siblingIndex = getLeafInOrderIndex(sibling);
        if (siblingIndex < 0) siblingIndex = 0;

        if (path.size() >= 3) {
            BranchNode grandParent = (BranchNode) path.get(path.size() - 3);
            if (grandParent.first == parent) {
                grandParent.first = sibling;
            } else {
                grandParent.second = sibling;
            }
        } else {
            mRootNode = sibling;
        }

        mFocusedPaneIndex = Math.min(siblingIndex, getPaneCount() - 1);
        if (mCallback != null) {
            mCallback.onPaneCountChanged(getPaneCount());
        }
        requestLayout();
        invalidate();
        return true;
    }

    public void focusNext() {
        int count = getPaneCount();
        if (count <= 1) return;
        mFocusedPaneIndex = (mFocusedPaneIndex + 1) % count;
        invalidate();
        notifyPaneFocused();
    }

    public void focusPrevious() {
        int count = getPaneCount();
        if (count <= 1) return;
        mFocusedPaneIndex = (mFocusedPaneIndex - 1 + count) % count;
        invalidate();
        notifyPaneFocused();
    }

    public void resizeFocusedPane(int deltaX, int deltaY) {
        if (mRootNode == null || getPaneCount() <= 1) return;
        if (deltaX == 0 && deltaY == 0) return;

        adjustLeafAncestorRatios(mRootNode, mFocusedPaneIndex, new int[]{0}, deltaX, deltaY);
        requestLayout();
        invalidate();
    }

    @Nullable
    public TerminalView getFocusedTerminalView() {
        return getTerminalViewByOrder(mFocusedPaneIndex);
    }

    @Nullable
    public TerminalSession getFocusedSession() {
        TerminalView view = getFocusedTerminalView();
        return view != null ? view.getCurrentSession() : null;
    }

    public int getFocusedPaneIndex() {
        return mFocusedPaneIndex;
    }

    public int getPaneCount() {
        return TermuxSplitUtils.countLeaves(mRootNode);
    }

    @Nullable
    public SplitNode getRootNode() {
        return mRootNode;
    }

    public void setFocusedPaneIndex(int index) {
        int count = getPaneCount();
        if (index >= 0 && index < count) {
            mFocusedPaneIndex = index;
            invalidate();
            notifyPaneFocused();
        }
    }

    public void restoreFromNode(SplitNode root, List<TerminalView> views) {
        removeAllViews();
        mRootNode = root;

        int leafCount = TermuxSplitUtils.countLeaves(root);
        for (int i = 0; i < leafCount && i < views.size(); i++) {
            TerminalView tv = views.get(i);
            tv.setId(View.generateViewId());
            addView(tv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        mFocusedPaneIndex = 0;
        if (mCallback != null) {
            mCallback.onPaneCountChanged(getPaneCount());
        }
        requestLayout();
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mRootNode == null || getChildCount() == 0) return;

        mPaneRects.clear();
        mDividerRects.clear();

        Rect bounds = new Rect(
            getPaddingLeft(),
            getPaddingTop(),
            right - left - getPaddingRight(),
            bottom - top - getPaddingBottom()
        );

        layoutNode(mRootNode, bounds);

        int terminalChildIndex = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof TerminalView) {
                if (terminalChildIndex < mPaneRects.size()) {
                    Rect r = mPaneRects.get(terminalChildIndex);
                    child.layout(r.left, r.top, r.right, r.bottom);
                    child.setVisibility(VISIBLE);
                } else {
                    child.setVisibility(GONE);
                }
                terminalChildIndex++;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), mBackgroundPaint);

        for (Rect dividerRect : mDividerRects) {
            canvas.drawRect(dividerRect, mDividerPaint);
        }

        if (mFocusedPaneIndex >= 0 && mFocusedPaneIndex < mPaneRects.size()) {
            Rect focusedRect = mPaneRects.get(mFocusedPaneIndex);
            float halfStroke = mFocusBorderSizePx / 2f;
            canvas.drawRect(
                focusedRect.left + halfStroke,
                focusedRect.top + halfStroke,
                focusedRect.right - halfStroke,
                focusedRect.bottom - halfStroke,
                mFocusBorderPaint
            );
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                mDragBranch = findDividerAt(x, y);
                if (mDragBranch != null) {
                    mIsDraggingDivider = true;
                    mDragStartX = x;
                    mDragStartY = y;
                    mDragStartRatio = mDragBranch.ratio;
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mIsDraggingDivider && mDragBranch != null) {
                    float dx = x - mDragStartX;
                    float dy = y - mDragStartY;

                    Rect branchBounds = findBranchBounds(mRootNode, mDragBranch);
                    if (branchBounds != null) {
                        if (mDragBranch.orientation == BranchNode.Orientation.HORIZONTAL) {
                            float totalWidth = branchBounds.width() - mDividerSizePx;
                            if (totalWidth > 0) {
                                float newRatio = (branchBounds.width() - mDividerSizePx) * mDragStartRatio + dx;
                                mDragBranch.ratio = Math.max(0.15f, Math.min(0.85f, newRatio / totalWidth));
                            }
                        } else {
                            float totalHeight = branchBounds.height() - mDividerSizePx;
                            if (totalHeight > 0) {
                                float newRatio = (branchBounds.height() - mDividerSizePx) * mDragStartRatio + dy;
                                mDragBranch.ratio = Math.max(0.15f, Math.min(0.85f, newRatio / totalHeight));
                            }
                        }
                        requestLayout();
                        invalidate();
                    }
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mIsDraggingDivider = false;
                mDragBranch = null;
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void layoutNode(SplitNode node, Rect bounds) {
        if (node instanceof LeafNode) {
            mPaneRects.add(new Rect(bounds));
        } else if (node instanceof BranchNode) {
            BranchNode b = (BranchNode) node;
            int dividerSpace = mDividerSizePx;
            Rect firstBounds = new Rect();
            Rect secondBounds = new Rect();
            Rect dividerRect = new Rect();

            if (b.orientation == BranchNode.Orientation.HORIZONTAL) {
                int totalWidth = bounds.width() - dividerSpace;
                int splitX = bounds.left + (int) (totalWidth * b.ratio);

                firstBounds.set(bounds.left, bounds.top, splitX, bounds.bottom);
                secondBounds.set(splitX + dividerSpace, bounds.top, bounds.right, bounds.bottom);
                dividerRect.set(splitX, bounds.top, splitX + dividerSpace, bounds.bottom);
            } else {
                int totalHeight = bounds.height() - dividerSpace;
                int splitY = bounds.top + (int) (totalHeight * b.ratio);

                firstBounds.set(bounds.left, bounds.top, bounds.right, splitY);
                secondBounds.set(bounds.left, splitY + dividerSpace, bounds.right, bounds.bottom);
                dividerRect.set(bounds.left, splitY, bounds.right, splitY + dividerSpace);
            }

            mDividerRects.add(dividerRect);
            layoutNode(b.first, firstBounds);
            layoutNode(b.second, secondBounds);
        }
    }

    @Nullable
    private LeafNode findLeafAt(SplitNode node, int targetIndex, int[] current) {
        if (node instanceof LeafNode) {
            if (current[0] == targetIndex) return (LeafNode) node;
            current[0]++;
            return null;
        } else if (node instanceof BranchNode) {
            BranchNode b = (BranchNode) node;
            LeafNode result = findLeafAt(b.first, targetIndex, current);
            if (result != null) return result;
            return findLeafAt(b.second, targetIndex, current);
        }
        return null;
    }

    @Nullable
    private SplitNode findParentOf(SplitNode root, SplitNode target) {
        if (root instanceof BranchNode) {
            BranchNode b = (BranchNode) root;
            if (b.first == target || b.second == target) return root;
            SplitNode result = findParentOf(b.first, target);
            if (result != null) return result;
            return findParentOf(b.second, target);
        }
        return null;
    }

    private boolean findPathToLeaf(SplitNode node, LeafNode target, List<SplitNode> path) {
        if (node == null) return false;
        path.add(node);
        if (node == target) return true;
        if (node instanceof BranchNode) {
            BranchNode b = (BranchNode) node;
            if (findPathToLeaf(b.first, target, path)) return true;
            if (findPathToLeaf(b.second, target, path)) return true;
        }
        path.remove(path.size() - 1);
        return false;
    }

    private int getLeafInOrderIndex(SplitNode node) {
        int[] index = new int[]{0};
        LeafNode target = null;
        if (node instanceof LeafNode) {
            target = (LeafNode) node;
        } else {
            return 0;
        }
        return findLeafIndexInOrder(mRootNode, target, index);
    }

    private int findLeafIndexInOrder(SplitNode node, LeafNode target, int[] index) {
        if (node instanceof LeafNode) {
            int cur = index[0];
            index[0]++;
            if (node == target) return cur;
            return -1;
        }
        if (node instanceof BranchNode) {
            BranchNode b = (BranchNode) node;
            int result = findLeafIndexInOrder(b.first, target, index);
            if (result >= 0) return result;
            return findLeafIndexInOrder(b.second, target, index);
        }
        return -1;
    }

    @Nullable
    private TerminalView getTerminalViewByOrder(int order) {
        int terminalIndex = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof TerminalView) {
                if (terminalIndex == order) return (TerminalView) child;
                terminalIndex++;
            }
        }
        return null;
    }

    @Nullable
    private TerminalView getTerminalViewByLeafOrder(LeafNode leaf) {
        int[] index = new int[]{0};
        int order = findLeafIndexInOrder(mRootNode, leaf, index);
        if (order < 0) return null;
        return getTerminalViewByOrder(order);
    }

    private void adjustLeafAncestorRatios(SplitNode node, int targetIndex, int[] current,
                                          int deltaX, int deltaY) {
        if (node instanceof BranchNode) {
            BranchNode b = (BranchNode) node;
            int beforeFirst = current[0];
            adjustLeafAncestorRatios(b.first, targetIndex, current, deltaX, deltaY);
            int afterFirst = current[0];
            int beforeSecond = current[0];
            adjustLeafAncestorRatios(b.second, targetIndex, current, deltaX, deltaY);
            int afterSecond = current[0];

            boolean foundInFirst = (targetIndex >= beforeFirst && targetIndex < afterFirst);
            boolean foundInSecond = (targetIndex >= beforeSecond && targetIndex < afterSecond);

            if (foundInFirst || foundInSecond) {
                if (b.orientation == BranchNode.Orientation.HORIZONTAL) {
                    if (foundInFirst) {
                        b.ratio = Math.max(0.15f, Math.min(0.85f, b.ratio + (float) deltaX / getWidth()));
                    } else {
                        b.ratio = Math.max(0.15f, Math.min(0.85f, b.ratio - (float) deltaX / getWidth()));
                    }
                } else {
                    if (foundInFirst) {
                        b.ratio = Math.max(0.15f, Math.min(0.85f, b.ratio + (float) deltaY / getHeight()));
                    } else {
                        b.ratio = Math.max(0.15f, Math.min(0.85f, b.ratio - (float) deltaY / getHeight()));
                    }
                }
            }
        } else if (node instanceof LeafNode) {
            current[0]++;
        }
    }

    @Nullable
    private BranchNode findDividerAt(float x, float y) {
        return TermuxSplitUtils.findDividerAt(
            mRootNode, mDividerTouchSlopPx,
            0, 0, getWidth(), getHeight(),
            x, y
        );
    }

    @Nullable
    private Rect findBranchBounds(SplitNode node, BranchNode target) {
        return findBranchBoundsRecursive(node, target,
            new Rect(getPaddingLeft(), getPaddingTop(),
                getWidth() - getPaddingRight(), getHeight() - getPaddingBottom()));
    }

    @Nullable
    private Rect findBranchBoundsRecursive(SplitNode node, BranchNode target, Rect bounds) {
        if (node == target) return new Rect(bounds);
        if (node instanceof BranchNode) {
            BranchNode b = (BranchNode) node;
            if (b.orientation == BranchNode.Orientation.HORIZONTAL) {
                int totalWidth = bounds.width() - mDividerSizePx;
                int splitX = bounds.left + (int) (totalWidth * b.ratio);
                Rect firstBounds = new Rect(bounds.left, bounds.top, splitX, bounds.bottom);
                Rect secondBounds = new Rect(splitX + mDividerSizePx, bounds.top, bounds.right, bounds.bottom);
                Rect result = findBranchBoundsRecursive(b.first, target, firstBounds);
                if (result != null) return result;
                return findBranchBoundsRecursive(b.second, target, secondBounds);
            } else {
                int totalHeight = bounds.height() - mDividerSizePx;
                int splitY = bounds.top + (int) (totalHeight * b.ratio);
                Rect firstBounds = new Rect(bounds.left, bounds.top, bounds.right, splitY);
                Rect secondBounds = new Rect(bounds.left, splitY + mDividerSizePx, bounds.right, bounds.bottom);
                Rect result = findBranchBoundsRecursive(b.first, target, firstBounds);
                if (result != null) return result;
                return findBranchBoundsRecursive(b.second, target, secondBounds);
            }
        }
        return null;
    }

    private void notifyPaneFocused() {
        if (mCallback != null) {
            TerminalView focusedView = getFocusedTerminalView();
            mCallback.onPaneFocused(focusedView, mFocusedPaneIndex);
        }
    }
}
