package com.termux.app.terminal.split;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.termux.terminal.TerminalSession;

public class MiniSplitView extends View {

    private static final int DIAGRAM_PADDING_DP = 16;
    private static final int TITLE_MARGIN_DP = 8;
    private static final int TITLE_TEXT_SIZE_DP = 12;
    private static final int DIVIDER_LINE_DP = 2;
    private static final int FOCUS_BORDER_DP = 3;
    private static final int FOCUS_BORDER_COLOR = 0xFF4CAF50;
    private static final int DIVIDER_COLOR = 0xFF37474F;

    private final int mDiagramPaddingPx;
    private final int mTitleMarginPx;
    private final int mTitleTextSizePx;
    private final int mDividerLinePx;
    private final int mFocusBorderPx;

    private final Paint mPaneBackgroundPaint;
    private final Paint mDividerPaint;
    private final Paint mFocusBorderPaint;
    private final Paint mTitlePaint;

    private TermuxSplitLayout mSplitLayout;

    public MiniSplitView(Context context) {
        this(context, null);
    }

    public MiniSplitView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MiniSplitView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = Resources.getSystem().getDisplayMetrics().density;
        mDiagramPaddingPx = (int) (DIAGRAM_PADDING_DP * density);
        mTitleMarginPx = (int) (TITLE_MARGIN_DP * density);
        mTitleTextSizePx = (int) (TITLE_TEXT_SIZE_DP * density);
        mDividerLinePx = (int) (DIVIDER_LINE_DP * density);
        mFocusBorderPx = (int) (FOCUS_BORDER_DP * density);

        boolean isNight = (context.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int paneBgColor = isNight ? 0x20FFFFFF : 0x20000000;

        mPaneBackgroundPaint = new Paint();
        mPaneBackgroundPaint.setColor(paneBgColor);
        mPaneBackgroundPaint.setStyle(Paint.Style.FILL);

        mDividerPaint = new Paint();
        mDividerPaint.setColor(DIVIDER_COLOR);
        mDividerPaint.setStyle(Paint.Style.FILL);

        mFocusBorderPaint = new Paint();
        mFocusBorderPaint.setColor(FOCUS_BORDER_COLOR);
        mFocusBorderPaint.setStyle(Paint.Style.STROKE);
        mFocusBorderPaint.setStrokeWidth(mFocusBorderPx);

        mTitlePaint = new Paint();
        mTitlePaint.setColor(isNight ? 0xFFFFFFFF : 0xFF000000);
        mTitlePaint.setTextSize(mTitleTextSizePx);
        mTitlePaint.setAntiAlias(true);
        mTitlePaint.setTypeface(Typeface.DEFAULT);
    }

    public void setSplitLayout(TermuxSplitLayout layout) {
        mSplitLayout = layout;
        updateFromLayout();
    }

    public void updateFromLayout() {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mSplitLayout == null || mSplitLayout.getRootNode() == null) {
            drawNoSessions(canvas);
            return;
        }

        int diagramWidth = getWidth() - mDiagramPaddingPx * 2;
        int diagramHeight = getHeight() - mDiagramPaddingPx * 2 - mTitleMarginPx - mTitleTextSizePx;
        if (diagramWidth <= 0 || diagramHeight <= 0) {
            drawNoSessions(canvas);
            return;
        }

        int diagramLeft = mDiagramPaddingPx;
        int diagramTop = mDiagramPaddingPx;
        int diagramRight = diagramLeft + diagramWidth;
        int diagramBottom = diagramTop + diagramHeight;

        Rect diagramBounds = new Rect(diagramLeft, diagramTop, diagramRight, diagramBottom);
        int[] leafIndex = new int[]{0};
        int focusedIndex = mSplitLayout.getFocusedPaneIndex();

        drawNode(canvas, mSplitLayout.getRootNode(), diagramBounds, leafIndex, focusedIndex);

        TerminalSession session = mSplitLayout.getFocusedSession();
        if (session != null) {
            String title = session.getTitle();
            if (title != null) {
                float titleY = diagramBottom + mTitleMarginPx + mTitleTextSizePx;
                canvas.drawText(title, diagramLeft, titleY, mTitlePaint);
            }
        }
    }

    private void drawNode(Canvas canvas, SplitNode node, Rect bounds,
                          int[] leafIndex, int focusedIndex) {
        if (node instanceof LeafNode) {
            int currentIndex = leafIndex[0];
            leafIndex[0]++;

            canvas.drawRect(bounds, mPaneBackgroundPaint);

            if (currentIndex == focusedIndex) {
                float halfStroke = mFocusBorderPx / 2f;
                canvas.drawRect(
                    bounds.left + halfStroke,
                    bounds.top + halfStroke,
                    bounds.right - halfStroke,
                    bounds.bottom - halfStroke,
                    mFocusBorderPaint
                );
            }
        } else if (node instanceof BranchNode) {
            BranchNode b = (BranchNode) node;
            Rect firstBounds = new Rect();
            Rect secondBounds = new Rect();

            if (b.orientation == BranchNode.Orientation.HORIZONTAL) {
                int totalWidth = bounds.width() - mDividerLinePx;
                int splitX = bounds.left + (int) (totalWidth * b.ratio);

                firstBounds.set(bounds.left, bounds.top, splitX, bounds.bottom);
                secondBounds.set(splitX + mDividerLinePx, bounds.top, bounds.right, bounds.bottom);

                canvas.drawRect(splitX, bounds.top, splitX + mDividerLinePx, bounds.bottom, mDividerPaint);
            } else {
                int totalHeight = bounds.height() - mDividerLinePx;
                int splitY = bounds.top + (int) (totalHeight * b.ratio);

                firstBounds.set(bounds.left, bounds.top, bounds.right, splitY);
                secondBounds.set(bounds.left, splitY + mDividerLinePx, bounds.right, bounds.bottom);

                canvas.drawRect(bounds.left, splitY, bounds.right, splitY + mDividerLinePx, mDividerPaint);
            }

            drawNode(canvas, b.first, firstBounds, leafIndex, focusedIndex);
            drawNode(canvas, b.second, secondBounds, leafIndex, focusedIndex);
        }
    }

    private void drawNoSessions(Canvas canvas) {
        String text = "No sessions";
        float textWidth = mTitlePaint.measureText(text);
        float x = (getWidth() - textWidth) / 2f;
        float y = (getHeight() + mTitleTextSizePx) / 2f;
        canvas.drawText(text, x, y, mTitlePaint);
    }
}
