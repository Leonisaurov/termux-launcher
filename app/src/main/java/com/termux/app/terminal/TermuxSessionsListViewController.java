package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.split.LeafNode;
import com.termux.app.terminal.split.TermuxSplitLayout;
import com.termux.shared.theme.ThemeUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TermuxSessionsListViewController extends BaseAdapter
    implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    private final TermuxActivity mActivity;
    private final List<TermuxSession> mSessions;
    private TermuxSplitLayout mSplitLayout;
    private final List<ListItem> mItems = new ArrayList<>();

    static final int TYPE_HEADER = 0;
    static final int TYPE_SESSION = 1;
    static final int TYPE_SPLIT_GROUP = 2;

    final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
    final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

    static class ListItem {
        int type;
        String headerText;
        String title;
        TermuxSession session;
        boolean isStandalone;
        int paneOrder;
        boolean isChild;
        int grandchildCount;
    }

    public TermuxSessionsListViewController(TermuxActivity activity, List<TermuxSession> sessions) {
        mActivity = activity;
        mSessions = sessions;
    }

    public void setSplitLayout(TermuxSplitLayout layout) {
        mSplitLayout = layout;
        notifyDataSetChanged();
    }

    private void rebuildGroupedList() {
        mItems.clear();

        if (mSplitLayout == null || mSplitLayout.getRootNode() == null || mSessions == null || mSessions.isEmpty()) {
            for (TermuxSession s : mSessions) {
                ListItem item = new ListItem();
                item.type = TYPE_SESSION;
                item.session = s;
                item.isStandalone = true;
                item.paneOrder = -1;
                item.isChild = false;
                mItems.add(item);
            }
            return;
        }

        Map<TermuxSession, Integer> sessionToPaneOrder = new HashMap<>();
        Map<TerminalView, LeafNode> viewToLeaf = mSplitLayout.getViewToLeafMap();
        if (viewToLeaf != null) {
            for (Map.Entry<TerminalView, LeafNode> entry : viewToLeaf.entrySet()) {
                TerminalView tv = entry.getKey();
                TermuxSession termuxSession = findTermuxSession(tv.getCurrentSession());
                if (termuxSession != null) {
                    int order = mSplitLayout.getLeafOrder(entry.getValue());
                    sessionToPaneOrder.put(termuxSession, order);
                }
            }
        }

        List<TermuxSession> splitSessions = new ArrayList<>();
        List<TermuxSession> standaloneSessions = new ArrayList<>();
        for (TermuxSession s : mSessions) {
            if (sessionToPaneOrder.containsKey(s)) {
                splitSessions.add(s);
            } else {
                standaloneSessions.add(s);
            }
        }

        splitSessions.sort(Comparator.comparingInt(s -> sessionToPaneOrder.getOrDefault(s, 0)));

        if (splitSessions.size() > 1) {
            ListItem group = new ListItem();
            group.type = TYPE_SPLIT_GROUP;
            group.title = "Split Window [" + splitSessions.size() + "]";
            group.grandchildCount = splitSessions.size();
            mItems.add(group);

            for (int i = 0; i < splitSessions.size(); i++) {
                TermuxSession s = splitSessions.get(i);
                ListItem item = new ListItem();
                item.type = TYPE_SESSION;
                item.session = s;
                item.isStandalone = false;
                item.paneOrder = sessionToPaneOrder.getOrDefault(s, i) + 1;
                item.isChild = true;
                mItems.add(item);
            }
        } else if (splitSessions.size() == 1) {
            TermuxSession s = splitSessions.get(0);
            ListItem item = new ListItem();
            item.type = TYPE_SESSION;
            item.session = s;
            item.isStandalone = true;
            item.paneOrder = -1;
            item.isChild = false;
            mItems.add(item);
        }

        for (TermuxSession s : standaloneSessions) {
            ListItem item = new ListItem();
            item.type = TYPE_SESSION;
            item.session = s;
            item.isStandalone = true;
            item.paneOrder = -1;
            item.isChild = false;
            mItems.add(item);
        }
    }

    private TermuxSession findTermuxSession(TerminalSession terminalSession) {
        if (terminalSession == null) return null;
        for (TermuxSession s : mSessions) {
            if (s.getTerminalSession() == terminalSession) return s;
        }
        return null;
    }

    @Override
    public void notifyDataSetChanged() {
        rebuildGroupedList();
        super.notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int position) {
        if (position < 0 || position >= mItems.size()) return null;
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position).type;
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ListItem item = mItems.get(position);

        if (item.type == TYPE_SPLIT_GROUP) {
            TextView tv;
            if (convertView instanceof TextView && convertView.getTag() != null && (int) convertView.getTag() == TYPE_SPLIT_GROUP) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(mActivity);
                tv.setTag(TYPE_SPLIT_GROUP);
                tv.setPadding(16, 12, 16, 8);
                tv.setTextSize(13);
                tv.setTextColor(0xFFFFFFFF);
                tv.setTypeface(null, Typeface.BOLD);
            }
            tv.setText(item.title);
            return tv;
        }

        if (convertView == null || convertView.getTag() == null || (int) convertView.getTag() != TYPE_SESSION) {
            convertView = LayoutInflater.from(mActivity).inflate(R.layout.item_terminal_sessions_list, parent, false);
            convertView.setTag(TYPE_SESSION);
        }

        TextView dragIcon = convertView.findViewById(R.id.drag_icon);
        TextView paneLabel = convertView.findViewById(R.id.pane_label);
        TextView sessionTitleView = convertView.findViewById(R.id.session_title);

        TermuxSession termuxSession = item.session;
        TerminalSession sessionAtRow = (termuxSession != null) ? termuxSession.getTerminalSession() : null;

        if (sessionAtRow == null) {
            sessionTitleView.setText("null session");
            return convertView;
        }

        int sessionIndex = mSessions.indexOf(termuxSession);
        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();
        String numberPart = "[" + (sessionIndex + 1) + "] ";
        String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
        String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));
        String fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart;
        SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
        fullSessionTitleStyled.setSpan(boldSpan, 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        fullSessionTitleStyled.setSpan(italicSpan, numberPart.length() + sessionNamePart.length(), fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sessionTitleView.setText(fullSessionTitleStyled);

        boolean sessionRunning = sessionAtRow.isRunning();
        if (sessionRunning) {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }

        int defaultColor = MaterialColors.getColor(mActivity, com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(mActivity, R.color.termux_on_surface));
        int color = sessionRunning || sessionAtRow.getExitStatus() == 0
            ? defaultColor
            : ThemeUtils.getSystemAttrColor(mActivity, com.termux.shared.R.attr.termuxColorError,
                ContextCompat.getColor(mActivity, R.color.termux_error));
        sessionTitleView.setTextColor(color);

        if (item.isStandalone) {
            dragIcon.setVisibility(View.VISIBLE);
            dragIcon.setOnLongClickListener(v -> {
                startDragForSession(termuxSession, v);
                return true;
            });
        } else {
            dragIcon.setVisibility(View.GONE);
        }

        if (!item.isStandalone && item.paneOrder > 0) {
            paneLabel.setVisibility(View.VISIBLE);
            paneLabel.setText("Pane " + item.paneOrder);
        } else {
            paneLabel.setVisibility(View.GONE);
        }

        float density = mActivity.getResources().getDisplayMetrics().density;
        if (item.isChild) {
            convertView.setPadding(
                (int) (24 * density + 0.5f),
                convertView.getPaddingTop(),
                convertView.getPaddingRight(),
                convertView.getPaddingBottom()
            );
        } else {
            convertView.setPadding(
                (int) (6 * density + 0.5f),
                convertView.getPaddingTop(),
                convertView.getPaddingRight(),
                convertView.getPaddingBottom()
            );
        }

        return convertView;
    }

    private void startDragForSession(TermuxSession session, View view) {
        if (mSessionDragListener != null) {
            mSessionDragListener.onDragStart(session, view);
        }
    }

    public interface OnSessionDragListener {
        void onDragStart(TermuxSession session, View view);
    }

    private OnSessionDragListener mSessionDragListener;

    public void setOnSessionDragListener(OnSessionDragListener listener) {
        mSessionDragListener = listener;
    }

    public boolean isStandaloneItem(int position) {
        if (position < 0 || position >= mItems.size()) return false;
        ListItem item = mItems.get(position);
        return item.type == TYPE_SESSION && item.isStandalone;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mItems.size()) return;
        ListItem item = mItems.get(position);

        if (item.type == TYPE_SPLIT_GROUP) {
            if (mSplitLayout != null) {
                TerminalSession focusedSession = mSplitLayout.getFocusedSession();
                if (focusedSession != null) {
                    mActivity.getTermuxTerminalSessionClient().setCurrentSession(focusedSession);
                }
            }
            mActivity.getDrawer().closeDrawers();
        } else if (item.type == TYPE_SESSION && item.session != null) {
            mActivity.getTermuxTerminalSessionClient().setCurrentSession(item.session.getTerminalSession());
            mActivity.getDrawer().closeDrawers();
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mItems.size()) return false;
        ListItem item = mItems.get(position);
        if (item.type == TYPE_SESSION && item.session != null) {
            mActivity.getTermuxTerminalSessionClient().renameSession(item.session.getTerminalSession());
            return true;
        }
        return false;
    }

    public TermuxSession getSessionAt(int position) {
        if (position < 0 || position >= mItems.size()) return null;
        ListItem item = mItems.get(position);
        return item.type == TYPE_SESSION ? item.session : null;
    }
}
