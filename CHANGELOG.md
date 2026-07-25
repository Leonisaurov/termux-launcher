# Changelog

## [0.3.0] - 2026-07-25
### Added
- xsel clipboard tool for Termux (ContentProvider + shell script)
- Version tracking system (VERSION file, CHANGELOG.md, git tags)

### Fixed
- Dynamic ContentProvider authority (${applicationId}.clipboard)
- Error handling in clipboard provider

## [0.2.0] - 2026-07-25
### Added
- Split/standalone view switching (togglable views)
- Collapsible Split Window tree in drawer (▶/▼)
- MiniSplitView visual layout inside expanded group
- Drag-and-drop standalone sessions from drawer to create splits
- Grouped session list with pane labels
- Standalone session support
- Back button returns to split from standalone mode

### Fixed
- StackOverflowError in adapter (infinite recursion)
- Double closeFocusedPane in closePaneForSession
- Focus transfer between split and standalone views
- Black screen on standalone TerminalView
- "null" title when session has no name
- NPE in onPaneFocused during early startup
- mSplitTerminalViewClient initialization ordering
- toggleZoomPane infinite loop risk
- Keyboard shortcut notifications (termuxSessionListNotifyUpdated)
- MAX_PANES guard in splitFocusedPane
- handleDrop uses correct drop target pane

### Changed
- Refactored TermuxSessionsListViewController to BaseAdapter
- Wrapped split layout in FrameLayout for standalone view

## [0.1.0] - 2026-07-24
### Added
- Initial split-screen implementation
- Vertical/horizontal splits via Ctrl+B shortcuts
- Split tree model (BranchNode/LeafNode)
- Focus tracking and routing
- Drag-to-resize dividers
- Minimized APK CI build via GitHub Actions
- Release management (nightly-split-latest)
- mViewToLeafMap for consistent leaf indexing

### Fixed
- Terminal black screen on startup
- APK installer (FileProvider)
- Download progress integer overflow
- Session cleanup on exit
- Nested split close
- Cancel download button

## [0.4.0] - 2026-07-25
### Added
- TermuxClipboardServer: TCP socket for instant clipboard access
- xsel via /dev/tcp (no external dependencies, no delays)
- Versioned releases per build (v0.4.0-build-N)
- Auto-cleanup old releases (keeps last 10)

### Fixed
- Release management now creates unique releases per build
- nightly-split-latest tag stays updated for download button
