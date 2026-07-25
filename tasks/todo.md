# Task Checklist: Split Drag & Drop + MiniSplitView

## Phase 1: Foundation (MiniSplitView + XML)

### Task 1: Create MiniSplitView.java

**Description:** Custom View that draws a miniature diagram of the split layout.

**Acceptance criteria:**
- [ ] View draws proportional rectangles matching split tree layout
- [ ] Focused pane has a green border highlight
- [ ] Shows focused session title below the diagram
- [ ] Updates when layout changes (pane count, focus, session title)
- [ ] Single pane shows one rectangle (no split = simple display)
- [ ] View extends `View` and belongs to `com.termux.app.terminal.split` package
- [ ] Exposes `update(SplitNode root, int focusedPaneIndex, String sessionTitle)` public method
- [ ] Exposes `setSessionTitle(String title)` for partial updates without tree walk

**Verification:**
- [ ] Opens drawer with splits → sees mini diagram matching layout
- [ ] Changes focus → mini updates highlight
- [ ] Closes pane → mini updates shape
- [ ] Single pane shows one rectangle (no split = simple display)

**Dependencies:** None

**Files:**
- `app/src/main/java/com/termux/app/terminal/split/MiniSplitView.java` (NEW)

---

### Task 2: Create mini_split_background.xml

**Description:** Shape drawable background for MiniSplitView.

**Acceptance criteria:**
- [ ] Rounded corners (8dp)
- [ ] Semi-transparent background matching drawer theme
- [ ] Subtle border/stroke (1dp, semi-transparent)
- [ ] Drawable placed in `res/drawable/` directory

**Verification:**
- [ ] MiniSplitView renders with rounded corners
- [ ] Background blends with drawer theme
- [ ] Stroke border visible on light and dark themes

**Dependencies:** None

**Files:**
- `app/src/main/res/drawable/mini_split_background.xml` (NEW)

---

### Task 3: Modify activity_termux.xml

**Description:** Add MiniSplitView to drawer layout.

**Acceptance criteria:**
- [ ] MiniSplitView appears in drawer between settings button and session list
- [ ] Height is 120dp
- [ ] Has background drawable (`@drawable/mini_split_background`)
- [ ] Bottom margin before session list (8dp)
- [ ] Initially `android:visibility="gone"`
- [ ] `android:id="@+id/mini_split_view"`

**Verification:**
- [ ] Drawer layout shows MiniSplitView in correct position
- [ ] Layout respects the 120dp height
- [ ] Background drawable applied correctly
- [ ] Build succeeds without errors

**Dependencies:** Task 1 (needs the View class), Task 2 (needs the background drawable)

**Files:**
- `app/src/main/res/layout/activity_termux.xml`

---

## Phase 2: Session Grouping

### Task 4: Refactor TermuxSessionsListViewController.java

**Description:** Add grouped list model with headers for "Split Window" and "Standalone".

**Acceptance criteria:**
- [ ] Sessions in the split tree appear under "Split Window" header with pane labels (Pane 1, Pane 2, etc.)
- [ ] Sessions NOT in the split tree appear under "Standalone" header
- [ ] Separator line between groups
- [ ] Standalone sessions have drag icon visible
- [ ] Split pane sessions show pane label (e.g. "Pane 1") instead of drag icon
- [ ] List updates correctly when sessions change (`notifyDataSetChanged`)
- [ ] Only one group header shown if there are no sessions in the other group
- [ ] Backward compatible: tapping session still switches to it
- [ ] Adapter extends `BaseAdapter` (not `ArrayAdapter`)
- [ ] Inner `ListItem` class with `TYPE_HEADER`, `TYPE_SESSION`, `TYPE_SEPARATOR`
- [ ] `getViewTypeCount()` returns 3
- [ ] Pane labels follow leaf order (Pane 1, Pane 2, ...)

**Verification:**
- [ ] Drawer shows grouped list matching current split state
- [ ] Changing layout (split/close) updates groups
- [ ] No standalone sessions → "Split Window" header only
- [ ] No split sessions → "Standalone" header only
- [ ] Tapping a session item switches to it and closes drawer

**Dependencies:** Task 5 (needs the item layout with drag icon and pane label)

**Files:**
- `app/src/main/java/com/termux/app/terminal/TermuxSessionsListViewController.java`

---

### Task 5: Modify item_terminal_sessions_list.xml

**Description:** Add drag icon and pane label to session list items.

**Acceptance criteria:**
- [ ] Layout has `drag_icon` TextView (Visibility: GONE by default)
- [ ] Layout has `pane_label` TextView (Visibility: GONE by default)
- [ ] Session title preserved as `session_title` TextView
- [ ] Proper layout with Left-to-Right: `[drag_icon] [pane_label] [session_title]`
- [ ] Drag icon shows Unicode drag handle character
- [ ] Pane label centered, green accent color
- [ ] All existing styling preserved (padding, textSize, background)

**Verification:**
- [ ] Standalone sessions show drag icon in list
- [ ] Split sessions show pane label in list
- [ ] Session title still displayed correctly
- [ ] Build succeeds without errors

**Dependencies:** None

**Files:**
- `app/src/main/res/layout/item_terminal_sessions_list.xml`

---

## Phase 3: Drag & Drop

### Task 6: Modify TermuxSplitLayout.java for drop handling

**Description:** Add `OnDragListener`, `determineDropOrientation()`, and pending session support.

**Acceptance criteria:**
- [ ] Layout accepts drag events with "termux-session" MIME type
- [ ] `determineDropOrientation(x, y)` returns `VERTICAL` or `HORIZONTAL` based on radial model
- [ ] On `ACTION_DROP`: calls `splitFocusedPane(orientation, pendingSession)`
- [ ] `setPendingDragSession(TerminalSession)` stores session for upcoming split
- [ ] Pending session is cleaned up in `try/finally` after drop
- [ ] Existing `splitFocusedPane(Orientation)` still works without pending session
- [ ] New overloaded `splitFocusedPane(Orientation, TerminalSession)` attaches the given session
- [ ] Drag outside pane bounds → defaults to `VERTICAL` split
- [ ] No visual artifacts during drag (no permanent overlay)

**Verification:**
- [ ] Drag session from drawer onto left half → vertical split
- [ ] Drag session from drawer onto top half → horizontal split
- [ ] Drag session from drawer onto right half → vertical split
- [ ] Drag session from drawer onto bottom half → horizontal split
- [ ] Drop outside terminal area → no split
- [ ] Existing keyboard splits (Ctrl+B % / Ctrl+B ") still work correctly

**Dependencies:** None (but must coordinate with Task 7 for wiring)

**Files:**
- `app/src/main/java/com/termux/app/terminal/split/TermuxSplitLayout.java`

---

### Task 7: Modify TermuxActivity.java for wiring

**Description:** Connect MiniSplitView updates, drag initiation from adapter, and drop handling.

**Acceptance criteria:**
- [ ] MiniSplitView receives layout change callbacks (`onPaneCountChanged`, `onPaneFocused`)
- [ ] `mMiniSplitView` field declared and initialized from layout
- [ ] MiniSplitView visibility toggles: shown when `paneCount > 1`, hidden for single pane
- [ ] Long-press on standalone session items starts drag-and-drop via `startDragAndDrop()`
- [ ] Drag creates `ClipData` with session index and `"termux-session"` MIME type
- [ ] `DragShadowBuilder` created from the list item view
- [ ] On `ACTION_DROP`: session index extracted from `ClipData`, `TerminalSession` retrieved, pending session set, split triggered
- [ ] Drawer closes after successful drop
- [ ] `termuxSessionListNotifyUpdated()` called after drop to update list

**Verification:**
- [ ] Long-press standalone session → drag starts with shadow
- [ ] Drag shadow follows finger across screen
- [ ] Drop on terminal → split created with the dragged session
- [ ] New pane shows the dragged session, not a new empty session
- [ ] MiniSplitView updates after split (shows new pane highlight)
- [ ] Drawer closes after successful drop
- [ ] MiniSplitView hidden when only one pane exists
- [ ] MiniSplitView shown when pane count > 1

**Dependencies:** All previous tasks (1–6)

**Files:**
- `app/src/main/java/com/termux/app/TermuxActivity.java`

---

## Integration Verification

### Overall System Check
- [ ] App launches without crashes
- [ ] Drawer opens/closes smoothly
- [ ] MiniSplitView renders correctly on first draw
- [ ] Session list correctly grouped
- [ ] Drag & drop works end-to-end
- [ ] All existing keyboard shortcuts still function
- [ ] Logcat shows no exceptions during drag/drop
- [ ] Orientation change does not break split state

### Regression Tests
- [ ] Single pane: keyboard works, session list shows standalone
- [ ] Split (Ctrl+B %): creates horizontal split, MiniSplitView updates
- [ ] Split (Ctrl+B "): creates vertical split, MiniSplitView updates
- [ ] Focus next/previous (Ctrl+B o / ;): updates MiniSplitView highlight
- [ ] Close pane (Ctrl+B x): MiniSplitView updates, list re-groups
- [ ] Resize pane (Ctrl+B arrows): MiniSplitView updates proportions
- [ ] Session finishes (exit) → pane closed automatically
- [ ] Settings button still opens settings
- [ ] New session button still creates new session
- [ ] Keyboard toggle button still works
- [ ] Update APK button still works
