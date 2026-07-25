# Implementation Plan: Split Drag & Drop + MiniSplitView

## Overview

Add a visual mini-representation of the split layout to the drawer, group sessions by split/standalone, and enable drag-and-drop from the drawer to create splits. This enhances the split-screen multiplexer experience by providing visual feedback of the current pane layout and a convenient way to create splits by dragging standalone sessions onto the terminal area.

## Architecture Decisions

1. **MiniSplitView as custom View** — draws directly via `onDraw()` using `Path`/`Canvas` for efficiency rather than nesting Android Views. The mini diagram renders proportional rectangles matching the split tree structure, with a green border for the focused pane and the focused session title below.

2. **ListItem model in adapter** — `TermuxSessionsListViewController` switches from a flat `ArrayAdapter<TermuxSession>` to a `BaseAdapter` backed by a `List<ListItem>` sealed class with `TYPE_HEADER`, `TYPE_SESSION`, and `TYPE_SEPARATOR` variants for grouped display (Split Window vs Standalone).

3. **Radial drop zone model** — When a drag enters the terminal area, `determineDropOrientation(x, y)` compares `|dx|` vs `|dy|` relative to the pane center to decide `VERTICAL` (left/right split) or `HORIZONTAL` (top/bottom split). This avoids overlapping zone calculations.

4. **PendingDragSession** — The dragged `TerminalSession` is stored temporarily in `TermuxSplitLayout` (`mPendingDragSession`) before the split occurs. It is cleaned up in `try/finally` to prevent orphan sessions if the split fails.

---

## Phase 1: Foundation (MiniSplitView + XML)

### Task 1: Create MiniSplitView.java

**Description:** Custom View that draws a miniature diagram of the split layout.

**Rationale:** A dedicated `View` subclass provides full control over rendering. The view recursively walks the `SplitNode` tree and draws proportional rectangles using `Canvas.drawRect()` with appropriate colors. The focused pane gets a green stroke border. Below the diagram, the focused session title is rendered as single-line text.

**Acceptance Criteria:**
- View draws proportional rectangles matching split tree layout
- Focused pane has a green border highlight (`0xFF4CAF50`, matching `TermuxSplitLayout.FOCUS_BORDER_COLOR`)
- Shows focused session title below the diagram in a single line, ellipsized if too long
- Updates when layout changes (pane count, focus, session title) via a public `update(SplitNode root, int focusedPaneIndex, String sessionTitle)` method
- Single pane shows one rectangle (no split = simple display)
- Background is set programmatically or via XML drawable

**Verification:**
1. Open drawer with splits → see mini diagram matching layout
2. Change focus (tap different pane) → mini updates highlight
3. Close pane → mini updates shape and count
4. Start with single pane → mini shows one rectangle

**Dependencies:** None (leaf task)

**Files:**
- `app/src/main/java/com/termux/app/terminal/split/MiniSplitView.java` (NEW)

**Implementation Notes:**
- Package: `com.termux.app.terminal.split`
- Extend `View`, provide 3 constructors matching the `View` pattern (Context, AttributeSet, defStyleAttr)
- Store references to `SplitNode mRootNode`, `int mFocusedPaneIndex`, `String mSessionTitle`
- In `onDraw()`:
  - Walk tree recursively, computing proportional rectangles within the view's width (minus padding)
  - Total height available for diagram area: `getHeight() - textHeight - padding`
  - For each `LeafNode`, draw a filled rectangle; if it matches `mFocusedPaneIndex`, draw a stroke border in green
  - Draw session title text at bottom using `Paint` with `ELLIPSIZE_END`
- Expose `update(SplitNode root, int focusedPaneIndex, String sessionTitle)` that sets fields and calls `invalidate()`
- Expose `setSessionTitle(String title)` for partial updates

### Task 2: Create mini_split_background.xml

**Description:** Shape drawable background for MiniSplitView.

**Acceptance criteria:**
- Rounded corners (8dp)
- Semi-transparent background matching drawer theme (`?attr/termuxColorSurfacePanelHigh` or equivalent dark scrim)
- Subtle border/stroke (1dp, semi-transparent white)

**Dependencies:** None

**Files:**
- `app/src/main/res/drawable/mini_split_background.xml` (NEW)

**Implementation Notes:**
- Use `<shape xmlns:android="...">` with `<corners android:radius="8dp" />`
- Use `<solid android:color="#33FFFFFF" />` or a theme-aware color
- Use `<stroke android:width="1dp" android:color="#19FFFFFF" />`
- Place in `app/src/main/res/drawable/`

### Task 3: Modify activity_termux.xml

**Description:** Add MiniSplitView to drawer layout.

**Acceptance criteria:**
- MiniSplitView appears in drawer between settings button and session list
- Height is 120dp
- Has background drawable (`@drawable/mini_split_background`)
- Bottom margin before session list (8dp)
- Layout weight of session list adjusted to accommodate fixed-height MiniSplitView

**Dependencies:** Task 1 (needs the View class), Task 2 (needs the background drawable)

**Files:**
- `app/src/main/res/layout/activity_termux.xml`

**Implementation Notes:**
- Add `<com.termux.app.terminal.split.MiniSplitView android:id="@+id/mini_split_view" ... />` inside the `left_drawer` LinearLayout, after the settings button row and before the `terminal_sessions_list` ListView
- Set `android:layout_width="match_parent"`, `android:layout_height="120dp"`
- Set `android:background="@drawable/mini_split_background"`
- Set `android:layout_marginBottom="8dp"` and `android:layout_marginHorizontal="4dp"` (or similar)
- Add `android:visibility="gone"` initially — will be set to `VISIBLE` when there are splits
- The `terminal_sessions_list` ListView already has `layout_height="0dp"` and `layout_weight="1"`, so adding a fixed-height view above it works without changing the ListView's layout parameters

---

## Phase 2: Session Grouping

### Task 4: Refactor TermuxSessionsListViewController.java

**Description:** Add grouped list model with headers for "Split Window" and "Standalone".

**Rationale:** The current adapter uses `ArrayAdapter<TermuxSession>` directly. To distinguish split-pane sessions from standalone sessions, we need a richer model. We introduce a sealed `ListItem` class with `TYPE_HEADER`, `TYPE_SESSION`, and `TYPE_SEPARATOR` variants. The adapter switches to a `BaseAdapter` that populates the list from session data + split tree state.

**Acceptance criteria:**
- Sessions in the split tree appear under "Split Window" header with pane labels ("Pane 1", "Pane 2", etc.)
- Sessions NOT in the split tree appear under "Standalone" header
- Separator line between groups
- Standalone sessions have drag icon visible
- List updates correctly when sessions change (`notifyDataSetChanged`)
- Only one group header shown if there are no sessions in the other group

**Verification:**
1. Drawer shows grouped list matching current split state
2. Changing layout (split/close) updates groups correctly
3. Standalone sessions show drag icon; split pane sessions show pane number label
4. Tapping session still switches to it

**Dependencies:** Task 5 (needs the item layout with drag icon and pane label)

**Files:**
- `app/src/main/java/com/termux/app/terminal/TermuxSessionsListViewController.java`

**Implementation Notes:**
- Add inner class `ListItem` with fields `int type`, `TerminalSession session` (nullable for headers), `String text` (header text or pane label)
- Define constants: `TYPE_HEADER = 0`, `TYPE_SESSION = 1`, `TYPE_SEPARATOR = 2`
- Change `extends ArrayAdapter<TermuxSession>` to `extends BaseAdapter`
- Add `setSplitSessionIndices(Set<Integer> splitIndices)` method to receive which session indices are in the split tree
- In `getCount()` and `getItem()`: delegate to the internal `List<ListItem>`
- In `getView()`: switch on `item.type`:
  - `TYPE_HEADER`: inflate a header row (bold text, e.g. "Split Window" or "Standalone")
  - `TYPE_SESSION`: inflate the existing item layout, set title, show/hide drag icon and pane label based on whether the session is in the split tree
  - `TYPE_SEPARATOR`: inflate a simple divider line
- `getViewTypeCount()` returns 3; `getItemViewType()` returns the type
- On `notifyDataSetChanged()`, rebuild the `ListItem` list from the session data and split indices set
- Keep `onItemClick()` and `onItemLongClick()` behavior, adjusting positions for the grouped model

### Task 5: Modify item_terminal_sessions_list.xml

**Description:** Add drag icon and pane label to session list items.

**Acceptance criteria:**
- Layout has `drag_icon` TextView (Visibility: GONE by default)
- Layout has `pane_label` TextView (Visibility: GONE by default)
- Session title preserved as `session_title` TextView
- Proper layout with Left-to-Right order: `[drag_icon] [pane_label] [session_title]`
- Drag icon uses a grid/drag handle character or Material icon

**Dependencies:** None

**Files:**
- `app/src/main/res/layout/item_terminal_sessions_list.xml`

**Implementation Notes:**
- Change root from `<com.google.android.material.textview.MaterialTextView>` to a horizontal `<LinearLayout>` with the original `MaterialTextView` as a child
- Add `<TextView android:id="@+id/drag_icon" ...>` with text "⠿" (or a drag handle Unicode), visibility GONE, width 32dp, centered
- Add `<TextView android:id="@+id/pane_label" ...>` with text "Pane 1" etc., visibility GONE, width 48dp, centered, text color accent/green
- The `session_title` gets `layout_weight="1"` and `layout_width="0dp"` to fill remaining space
- Preserve existing styling (padding, textSize, background)

---

## Phase 3: Drag & Drop

### Task 6: Modify TermuxSplitLayout.java for drop handling

**Description:** Add `OnDragListener`, `determineDropOrientation()`, and pending session support.

**Rationale:** The split layout needs to accept drag events from the drawer. When a session is dragged onto a pane, the radial drop model determines whether to split vertically or horizontally. The dragged session is stored as a pending reference and passed to `splitFocusedPane()`.

**Acceptance criteria:**
- Layout accepts drag events with `"termux-session"` MIME type
- `determineDropOrientation(x, y)` returns `VERTICAL` or `HORIZONTAL` based on radial model
- On `ACTION_DROP`: calls `splitFocusedPane(orientation, pendingSession)` with the pending session
- Pending session is cleaned up in `try/finally`
- Existing functionality unchanged (backward compatible — `splitFocusedPane(orientation)` still works without a pending session)

**Verification:**
1. Drag session from drawer onto left half → vertical split (left/right)
2. Drag session from drawer onto top half → horizontal split (top/bottom)
3. Drag outside terminal area → no split
4. Existing keyboard splits still work

**Dependencies:** None (but must coordinate with Task 7 for wiring)

**Files:**
- `app/src/main/java/com/termux/app/terminal/split/TermuxSplitLayout.java`

**Implementation Notes:**
- Add `private TerminalSession mPendingDragSession` field
- Add `public void setPendingDragSession(TerminalSession session)` setter
- Add `public boolean hasPendingDragSession()` getter
- In constructor or init: `setOnDragListener(...)` with:
  - `ACTION_DRAG_STARTED`: check MIME type, return true
  - `ACTION_DRAG_ENTERED`: optional visual feedback (dimmed overlay)
  - `ACTION_DRAG_LOCATION`: could show drop indicator
  - `ACTION_DRAG_EXITED`: remove visual feedback
  - `ACTION_DROP`: call `performDrop(event)` private method
  - `ACTION_DRAG_ENDED`: clean up
- `performDrop(DragEvent event)`:
  - Get x, y from event
  - Determine orientation via `determineDropOrientation(x, y)`
  - Call `splitFocusedPane(orientation)` with the pending session
  - Wrap in `try/finally`: set `mPendingDragSession = null`
- `determineDropOrientation(float x, float y)`:
  - Get the focused pane's bounds from `mPaneRects.get(mFocusedPaneIndex)`
  - Calculate `dx = x - paneCenterX`, `dy = y - paneCenterY`
  - If `|dx| > |dy|` → VERTICAL (split left/right)
  - Else → HORIZONTAL (split top/bottom)
  - If outside pane bounds entirely, default to VERTICAL
- Add overloaded `splitFocusedPane(BranchNode.Orientation orientation, TerminalSession session)` that:
  - Creates the new `TerminalView` but attaches the given session instead of creating a new one
  - Falls back to `createNewTerminalView()` if session is null

### Task 7: Modify TermuxActivity.java for wiring

**Description:** Connect MiniSplitView updates, drag initiation from adapter, and drop handling.

**Rationale:** This is the integration task that ties together the MiniSplitView, the grouped adapter, the drag-and-drop initiation, and the split layout's drop handler. It also ensures the drawer closes after a successful drop.

**Acceptance criteria:**
- MiniSplitView receives layout change callbacks (`onPaneCountChanged`, `onPaneFocused`)
- Long-press on standalone session items starts drag-and-drop via `startDragAndDrop()`
- Drag creates proper `ClipData` with session index and `"termux-session"` MIME type
- Drop correctly triggers split with the associated session
- Drawer closes after successful drop
- MiniSplitView visibility toggles: shown when `paneCount > 1`, hidden for single pane

**Verification:**
1. Long-press standalone session → drag starts with shadow
2. Drop on terminal → split created with the dragged session
3. MiniSplitView updates after split (shows new pane)
4. Drawer closes after successful drop
5. MiniSplitView hidden when only one pane exists

**Dependencies:** All previous tasks (1–6)

**Files:**
- `app/src/main/java/com/termux/app/TermuxActivity.java`

**Implementation Notes:**
- In `setTermuxTerminalViewAndClients()`, after `mSplitLayout.setSplitLayoutCallback(...)`:
  - Add MiniSplitView initialization:
    ```java
    mMiniSplitView = findViewById(R.id.mini_split_view);
    ```
  - In `onPaneCountChanged`: call `mMiniSplitView.update(...)` and toggle visibility
  - In `onPaneFocused`: call `mMiniSplitView.update(...)` with new focus index and session title
  - Get session title from the focused `TerminalSession`'s `getTitle()`
- Add `mMiniSplitView` field to `TermuxActivity`
- Set up long-click on session list items for drag initiation:
  - In `TermuxSessionsListViewController`, override `getView()` to set an `OnLongClickListener` on the session row
  - For sessions NOT in the split tree, start drag with `startDragAndDrop()`:
    ```java
    ClipData data = ClipData.newPlainText("termux-session", String.valueOf(sessionIndex));
    view.startDragAndDrop(data, new View.DragShadowBuilder(view), null, 0);
    ```
  - Store the session index in the drag data
- In `TermuxSplitLayout`'s `OnDragListener`:
  - On `ACTION_DROP`, extract session index from `ClipData`, find the `TerminalSession` via `TermuxService`, call `setPendingDragSession()` then `splitFocusedPane()`
- After successful drop, call `getDrawer().closeDrawers()` to dismiss the drawer

---

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| `View.startDragAndDrop()` requires SDK 24+ | High | Already met (`minSdkVersion` is 24+ in this project) |
| Drag shadow may not display correctly on some devices | Medium | Test on multiple API levels; `DragShadowBuilder` from the view usually works; can implement custom `View.DragShadowBuilder` if needed |
| Session index vs leaf order mismatch | High | Use `mViewToLeafMap` (already in `TermuxSplitLayout`) to map `TerminalView` → `LeafNode` → `sessionIndex`, ensuring correct session attachment |
| MiniSplitView layout breaks on RTL | Low | Document that RTL is not supported; use `Gravity.START`/`Gravity.END` where possible |
| Adapter `notifyDataSetChanged` overhead with grouped model | Low | The list is small (< 20 items); no optimization needed |
| Drag initiation requires visible drag icon on standalone items | Low | Add margin/padding so the drag icon doesn't interfere with tap-to-select behavior |

---

## File Summary

| File | Action | Phase |
|------|--------|-------|
| `app/src/main/java/com/termux/app/terminal/split/MiniSplitView.java` | CREATE | 1 |
| `app/src/main/res/drawable/mini_split_background.xml` | CREATE | 1 |
| `app/src/main/res/layout/activity_termux.xml` | MODIFY | 1 |
| `app/src/main/java/com/termux/app/terminal/TermuxSessionsListViewController.java` | MODIFY | 2 |
| `app/src/main/res/layout/item_terminal_sessions_list.xml` | MODIFY | 2 |
| `app/src/main/java/com/termux/app/terminal/split/TermuxSplitLayout.java` | MODIFY | 3 |
| `app/src/main/java/com/termux/app/TermuxActivity.java` | MODIFY | 3 |

---

## Testing Strategy

**Integration Tests:**
1. App launches with MiniSplitView hidden in drawer (single pane)
2. Create split (Ctrl+B % or Ctrl+B ") → MiniSplitView appears, shows two rectangles
3. Tap different pane → MiniSplitView highlight moves
4. Open drawer → grouped list shows "Split Window" header with pane labels, "Standalone" header if standalone sessions exist
5. Long-press standalone session → drag shadow follows finger
6. Drop on left side of terminal → vertical split appears with the dragged session
7. Close pane → MiniSplitView updates, groups update in drawer

**Edge Cases:**
- Drag onto very small pane (after multiple splits)
- All sessions in split tree (no "Standalone" header)
- No sessions in split tree (no "Split Window" header)
- Drag and drop then immediately close pane
- Rotate device during drag
