package com.termux.app.terminal.split;

import android.view.KeyEvent;
import android.view.MotionEvent;

import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

public class SplitTerminalViewClient implements TerminalViewClient {

    private final TermuxTerminalViewClient mDelegate;
    private final TermuxSplitLayout mSplitLayout;
    private final TermuxActivity mActivity;

    private boolean mInCommandMode = false;
    private long mCommandModeStartTime = 0;
    private static final long COMMAND_MODE_TIMEOUT_MS = 1000;

    private int mPrefixCodePoint = 'b';

    public SplitTerminalViewClient(TermuxTerminalViewClient delegate,
                                   TermuxSplitLayout splitLayout,
                                   TermuxActivity activity) {
        mDelegate = delegate;
        mSplitLayout = splitLayout;
        mActivity = activity;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
        int metaState = e.getMetaState();
        boolean ctrlPressed = (metaState & KeyEvent.META_CTRL_ON) != 0
            || (metaState & KeyEvent.META_CTRL_LEFT_ON) != 0
            || (metaState & KeyEvent.META_CTRL_RIGHT_ON) != 0;

        if (mInCommandMode && System.currentTimeMillis() - mCommandModeStartTime > COMMAND_MODE_TIMEOUT_MS) {
            mInCommandMode = false;
            updateCommandModeIndicator();
        }

        if (mInCommandMode) {
            mInCommandMode = false;
            updateCommandModeIndicator();

            boolean consumed = handleCommandKey(keyCode, e);
            if (consumed) return true;

            return mDelegate.onKeyDown(keyCode, e, session);
        }

        if (ctrlPressed && keyCode == KeyEvent.KEYCODE_B) {
            int unicodeChar = e.getUnicodeChar(0);
            if (unicodeChar == mPrefixCodePoint || unicodeChar == Character.toLowerCase(mPrefixCodePoint)) {
                mInCommandMode = true;
                mCommandModeStartTime = System.currentTimeMillis();
                updateCommandModeIndicator();
                return true;
            }
        }

        return mDelegate.onKeyDown(keyCode, e, session);
    }

    private boolean handleCommandKey(int keyCode, KeyEvent e) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_APOSTROPHE:
                if (e.isShiftPressed()) {
                    mSplitLayout.splitFocusedPane(BranchNode.Orientation.VERTICAL);
                    mActivity.termuxSessionListNotifyUpdated();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_5:
                if (e.isShiftPressed()) {
                    mSplitLayout.splitFocusedPane(BranchNode.Orientation.HORIZONTAL);
                    mActivity.termuxSessionListNotifyUpdated();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_O:
                mSplitLayout.focusNext();
                mActivity.termuxSessionListNotifyUpdated();
                return true;
            case KeyEvent.KEYCODE_SEMICOLON:
                mSplitLayout.focusPrevious();
                mActivity.termuxSessionListNotifyUpdated();
                return true;
            case KeyEvent.KEYCODE_X:
                mSplitLayout.closeFocusedPane();
                mActivity.termuxSessionListNotifyUpdated();
                return true;
            case KeyEvent.KEYCODE_Z:
                toggleZoomPane();
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                mSplitLayout.resizeFocusedPane(0, -10);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                mSplitLayout.resizeFocusedPane(0, 10);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mSplitLayout.resizeFocusedPane(-10, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mSplitLayout.resizeFocusedPane(10, 0);
                return true;
        }
        return false;
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
        if (mInCommandMode) {
            if (System.currentTimeMillis() - mCommandModeStartTime > COMMAND_MODE_TIMEOUT_MS) {
                mInCommandMode = false;
                updateCommandModeIndicator();
            } else {
                mInCommandMode = false;
                updateCommandModeIndicator();

                boolean consumed = handleCommandCodePoint(codePoint);
                if (consumed) return true;

                return mDelegate.onCodePoint(codePoint, ctrlDown, session);
            }
        }
        return mDelegate.onCodePoint(codePoint, ctrlDown, session);
    }

    private boolean handleCommandCodePoint(int codePoint) {
        switch (codePoint) {
            case '"':
                mSplitLayout.splitFocusedPane(BranchNode.Orientation.VERTICAL);
                mActivity.termuxSessionListNotifyUpdated();
                return true;
            case '%':
                mSplitLayout.splitFocusedPane(BranchNode.Orientation.HORIZONTAL);
                mActivity.termuxSessionListNotifyUpdated();
                return true;
            case 'o':
            case 'O':
                mSplitLayout.focusNext();
                mActivity.termuxSessionListNotifyUpdated();
                return true;
            case ';':
                mSplitLayout.focusPrevious();
                mActivity.termuxSessionListNotifyUpdated();
                return true;
            case 'x':
            case 'X':
                mSplitLayout.closeFocusedPane();
                mActivity.termuxSessionListNotifyUpdated();
                return true;
            case 'z':
            case 'Z':
                toggleZoomPane();
                return true;
            case 27:
                return true;
        }
        return false;
    }

    private void toggleZoomPane() {
        if (mSplitLayout != null) {
            mSplitLayout.toggleZoomFocusedPane();
        }
        if (mActivity != null) {
            mActivity.termuxSessionListNotifyUpdated();
        }
    }

    private void updateCommandModeIndicator() {
        if (mInCommandMode) {
            mActivity.getWindow().setTitle("[TMUX] Termux");
        } else {
            mActivity.getWindow().setTitle("Termux");
        }
    }

    @Override
    public float onScale(float scale) {
        return mDelegate.onScale(scale);
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        mDelegate.onSingleTapUp(e);
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return mDelegate.shouldBackButtonBeMappedToEscape();
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return mDelegate.shouldEnforceCharBasedInput();
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return mDelegate.shouldUseCtrlSpaceWorkaround();
    }

    @Override
    public boolean isTerminalViewSelected() {
        return mDelegate.isTerminalViewSelected();
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
        mDelegate.copyModeChanged(copyMode);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        return mDelegate.onKeyUp(keyCode, e);
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return mDelegate.onLongPress(event);
    }

    @Override
    public boolean onShowContextMenu(TerminalView view) {
        return mDelegate.onShowContextMenu(view);
    }

    @Override
    public boolean readControlKey() {
        return mDelegate.readControlKey();
    }

    @Override
    public boolean readAltKey() {
        return mDelegate.readAltKey();
    }

    @Override
    public boolean readShiftKey() {
        return mDelegate.readShiftKey();
    }

    @Override
    public boolean readFnKey() {
        return mDelegate.readFnKey();
    }

    @Override
    public void onEmulatorSet() {
        mDelegate.onEmulatorSet();
    }

    @Override
    public void logError(String tag, String message) {
        mDelegate.logError(tag, message);
    }

    @Override
    public void logWarn(String tag, String message) {
        mDelegate.logWarn(tag, message);
    }

    @Override
    public void logInfo(String tag, String message) {
        mDelegate.logInfo(tag, message);
    }

    @Override
    public void logDebug(String tag, String message) {
        mDelegate.logDebug(tag, message);
    }

    @Override
    public void logVerbose(String tag, String message) {
        mDelegate.logVerbose(tag, message);
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {
        mDelegate.logStackTraceWithMessage(tag, message, e);
    }

    @Override
    public void logStackTrace(String tag, Exception e) {
        mDelegate.logStackTrace(tag, e);
    }

    public boolean isInCommandMode() {
        return mInCommandMode;
    }

    public void setPrefixCodePoint(int codePoint) {
        mPrefixCodePoint = codePoint;
    }
}
