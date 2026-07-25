# Diseño: Drag & Drop + MiniSplitView para el Drawer de Split-Screen

**Fecha**: 2026-07-25  
**Autor**: Equipo de desarrollo — Leonisaurov/termux-launcher  
**Estado**: Borrador  
**Versión**: 1.0

---

## 1. Resumen Ejecutivo

El drawer lateral de Termux muestra actualmente una lista plana de todas las sesiones del servicio `TermuxService`, sin ninguna indicación visual de cómo están organizadas en el layout de split-screen. Los controles de split existen solo como atajos de teclado (Ctrl+B) y botones en el drawer, pero no hay forma de mover sesiones existentes al layout mediante interacción directa.

Este documento describe el diseño de dos funcionalidades complementarias:

1. **MiniSplitView** — una vista en miniatura dentro del drawer que representa gráficamente la disposición actual de los paneles divididos, con el panel enfocado resaltado.
2. **Drag & Drop desde el drawer** — el usuario puede arrastrar una sesión independiente (standalone) desde la lista del drawer y soltarla sobre el layout `TermuxSplitLayout` para crear un split, determinando la orientación según la zona de drop.

El objetivo es hacer que el sistema de split-screen sea más descubrible y fácil de usar, reduciendo la fricción para nuevos usuarios y ofreciendo una experiencia más visual.

---

## 2. Requerimientos

### 2.1 Funcionales

| ID | Requerimiento | Prioridad |
|----|---------------|-----------|
| F1 | El drawer debe mostrar un diagrama en miniatura de la disposición actual de paneles | Alta |
| F2 | El MiniSplitView debe leer el árbol de splits desde `TermuxSplitLayout.getRootNode()` | Alta |
| F3 | Los rectángulos en el MiniSplitView deben ser proporcionales al layout real | Alta |
| F4 | El panel enfocado debe resaltarse con borde verde (#4CAF50) en el MiniSplitView | Alta |
| F5 | Debajo del diagrama debe mostrarse el título de la sesión enfocada | Alta |
| F6 | Si solo hay un panel (sin split), el MiniSplitView muestra un rectángulo único | Alta |
| F7 | El MiniSplitView debe auto-actualizarse cuando cambien los paneles | Alta |
| F8 | La lista de sesiones debe agruparse en "Split Window" y "Standalone" | Alta |
| F9 | Las sesiones en el split deben mostrar su número de panel ("Pane 1", "Pane 2") | Alta |
| F10 | Las sesiones standalone deben tener un icono de arrastre (☰) | Alta |
| F11 | Arrastrar una sesión standalone desde el drawer debe iniciar un drag de Android | Alta |
| F12 | Soltar sobre `TermuxSplitLayout` debe dividir el panel enfocado según la zona de drop | Alta |
| F13 | La orientación del split se determina por la posición del drop relativa al centro del layout | Alta |
| F14 | Después del drop, se crea una nueva `TerminalSession` para el nuevo panel | Alta |
| F15 | El drawer debe actualizarse para reflejar el nuevo layout después del drop | Alta |

### 2.2 No Funcionales

| ID | Requerimiento | Prioridad |
|----|---------------|-----------|
| NF1 | El MiniSplitView debe renderizarse sin lag en el hilo de UI | Media |
| NF2 | El drag & drop debe usar el framework nativo de Android (no librerías externas) | Alta |
| NF3 | La sesión arrastrada debe permanecer en `TermuxService` — solo se re-asigna al nuevo panel | Alta |
| NF4 | El MiniSplitView debe respetar el tema oscuro/claro de la app | Media |

---

## 3. Arquitectura

### 3.1 Diagrama de Componentes

```
┌─────────────────────────────────────────────────────────────────┐
│                        DrawerLayout                              │
│  ┌──────────────────────────────────────┐  ┌──────────────────┐  │
│  │      TermuxSplitLayout (content)      │  │    Drawer         │  │
│  │  ┌────────────────────────────────┐   │  │  (RelativeLayout) │  │
│  │  │  TerminalView (pane 0)         │   │  │                   │  │
│  │  │  ┌────────────┬──────────────┐ │   │  │  MiniSplitView    │  │
│  │  │  │  Pane 0    │  Pane 1      │ │   │  │  (nuevo View)    │  │
│  │  │  │  (focus)   │              │ │   │  ├──────────────────┤  │
│  │  │  └────────────┴──────────────┘ │   │  │  ListView         │  │
│  │  └────────────────────────────────┘   │  │  (modificado)     │  │
│  │                                       │  │  ├ "Split Window" │  │
│  │  [OnDragListener]                     │  │  │  - Pane 1: ... │  │
│  │                                       │  │  │  - Pane 2: ... │  │
│  │                                       │  │  ├ "Standalone"   │  │
│  │                                       │  │  │  ☰ Session 3   │  │
│  │                                       │  │  │  ☰ Session 4   │  │
│  │                                       │  │  ├──────────────────┤  │
│  │                                       │  │  │ Split buttons   │  │
│  │                                       │  │  │ Bottom buttons  │  │
│  │                                       │  │  └──────────────────┘  │
│  └──────────────────────────────────────┘  └──────────────────┘     │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.2 Árbol de Decisión de Drop Zones

```
                    ┌────────────────────────────┐
                    │   Drop en TermuxSplitLayout  │
                    │   (x, y) relativas al View   │
                    └────────────┬───────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │  Calcular posición       │
                    │  relativa al centro:      │
                    │  cx = width / 2           │
                    │  cy = height / 2          │
                    │  dx = x - cx              │
                    │  dy = y - cy              │
                    └────────────┬────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                   │
         |dx| > |dy|        |dx| ≈ |dy|        |dx| < |dy|
         (más horizontal)   (centro 20%)      (más vertical)
              │                  │                   │
              ▼                  ▼                   ▼
     ┌────────────────┐  ┌──────────────┐  ┌────────────────┐
     │ dx > 0 → RIGHT │  │ Default:     │  │ dy > 0 → BOTTOM│
     │ dx < 0 → LEFT  │  │ VERTICAL     │  │ dy < 0 → TOP   │
     │ → VERTICAL     │  │ (por simetría│  │ → HORIZONTAL   │
     └────────────────┘  │  con teclado) │  └────────────────┘
                         └──────────────┘

    Orientación resultante:
      - LEFT/RIGHT   → BranchNode.Orientation.VERTICAL
      - TOP/BOTTOM   → BranchNode.Orientation.HORIZONTAL
```

### 3.3 Modelo de Datos (Agrupación de Sesiones)

```
TermuxService.getTermuxSessions()
         │
         ▼
┌─────────────────────────────────────┐
│  List<TermuxSession>                │
│  (todas las sesiones, plana)         │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────────────┐
│  TermuxSessionsListViewController (modificado)            │
│                                                           │
│  1. Obtener LeafNode del split tree                       │
│  2. Extraer sessionIndex de cada LeafNode                 │
│  3. Clasificar cada TermuxSession:                        │
│       - SessionIndex en split tree → Grupo "Split Window" │
│       - SessionIndex NO en split tree → Grupo "Standalone"│
│  4. Mostrar en ListView con headers                       │
│  5. Items standalone tienen OnLongClickListener → drag    │
└──────────────────────────────────────────────────────────┘
```

---

## 4. Diseño Detallado por Componente

### 4.1 MiniSplitView (`MiniSplitView.java`)

**Paquete**: `com.termux.app.terminal.split`  
**Extiende**: `android.view.View`  
**Archivo**: `app/src/main/java/com/termux/app/terminal/split/MiniSplitView.java`

#### 4.1.1 Ciclo de Vida

```
Constructor → setSplitLayout(TermuxSplitLayout)
    │
    ▼
onAttachedToWindow()
    │
    ├── Registrar OnPaneCountChangedListener
    └── Llamar updateFromLayout()
              │
              ▼
         invalidate() → onDraw()
```

#### 4.1.2 API Pública

```java
public class MiniSplitView extends View {

    /**
     * Asocia este MiniSplitView con el TermuxSplitLayout para leer el árbol
     * de splits y recibir notificaciones de cambios.
     */
    public void setSplitLayout(TermuxSplitLayout layout);

    /**
     * Fuerza una re-lectura del árbol de splits y redibuja la vista.
     * Llamado cuando cambia el layout (split, close, focus).
     */
    public void updateFromLayout();

    @Override
    protected void onDraw(Canvas canvas);
}
```

#### 4.1.3 Lógica de Renderizado (`onDraw`)

```
onDraw(Canvas):
    if rootNode == null → return

    padding = 8dp
    availableWidth = width - 2*padding
    availableHeight = height - 2*padding (reservar espacio para título)

    // Título de sesión enfocada
    focusedTitle = getFocusedSessionTitle()
    drawText(focusedTitle, ...)  // debajo del diagrama

    // Área del diagrama (proporcional)
    diagramHeight = availableHeight - titleHeight - 4dp
    diagramBounds = Rect(padding, padding,
                         width - padding, padding + diagramHeight)

    // Recorrer el árbol y dibujar rectángulos
    drawNode(canvas, rootNode, diagramBounds)

drawNode(canvas, node, bounds):
    if node instanceof LeafNode:
        drawPaneRect(canvas, bounds, isFocused(node))

    if node instanceof BranchNode:
        if orientation == HORIZONTAL:
            splitX = bounds.left + (bounds.width - dividerSize) * ratio
            drawDivider(canvas, splitX, bounds.top, splitX + divider, bounds.bottom)
            drawNode(canvas, node.first,  Rect(bounds.left, top, splitX, bottom))
            drawNode(canvas, node.second, Rect(splitX+divider, top, bounds.right, bottom))

        if orientation == VERTICAL:
            splitY = bounds.top + (bounds.height - dividerSize) * ratio
            drawDivider(canvas, bounds.left, splitY, bounds.right, splitY + divider)
            drawNode(canvas, node.first,  Rect(left, bounds.top, right, splitY))
            drawNode(canvas, node.second, Rect(left, splitY+divider, right, bounds.bottom))

drawPaneRect(canvas, bounds, isFocused):
    // Color de fondo: semi-transparente (tema)
    // Borde: 2dp, verde (#4CAF50) si focused, gris si no

drawDivider(canvas, ...):
    // Rectángulo de 2dp, color #37474F (mismo que el divider real)
```

#### 4.1.4 Constants / Styling

| Constante | Valor | Propósito |
|-----------|-------|-----------|
| `PADDING_DP` | 8dp | Margen interior |
| `DIVIDER_SIZE_DP` | 2dp | Grosor del divider en miniatura |
| `BORDER_SIZE_DP` | 2dp | Grosor del borde de focus |
| `FOCUS_COLOR` | `0xFF4CAF50` | Verde — mismo que `TermuxSplitLayout.FOCUS_BORDER_COLOR` |
| `DIVIDER_COLOR` | `0xFF37474F` | Gris oscuro — mismo que `TermuxSplitLayout.DIVIDER_COLOR` |
| `PANE_COLOR` | `0x20FFFFFF` / `0x20000000` | Fondo semi-transparente (tema) |
| `TEXT_COLOR` | Color del tema | Título de sesión |
| `TEXT_SIZE_DP` | 11sp | Tamaño del título |
| `HEIGHT_DP` | ~120dp | Altura total recomendada del MiniSplitView |

#### 4.1.5 Integración con Cambios

El MiniSplitView necesita saber cuándo cambia el layout para redibujarse. Hay dos estrategias:

1. **Callback desde TermuxSplitLayout**: Extender `SplitLayoutCallback` con un método `onSplitLayoutChanged()` que se llame en `splitFocusedPane()`, `closeFocusedPane()`, `focusNext()`, `focusPrevious()`, etc.
2. **Polling con View.postDelayed**: Menos eficiente, no recomendado.

**Estrategia elegida**: Opción 1 — el `TermuxActivity` pasa el MiniSplitView al callback y llama a `updateFromLayout()` cuando sea necesario.

### 4.2 MiniSplitDrawable (`mini_split_background.xml`)

**Archivo**: `app/src/main/res/drawable/mini_split_background.xml`

Drawable shape para el fondo del MiniSplitView:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="?attr/termuxColorSurfacePanelHigh" />
    <corners android:radius="8dp" />
    <stroke
        android:width="1dp"
        android:color="?attr/termuxColorOutlineVariant" />
</shape>
```

### 4.3 TermuxSessionsListViewController (Modificado)

**Archivo**: `app/src/main/java/com/termux/app/terminal/TermuxSessionsListViewController.java`

#### 4.3.1 Cambios en la Estructura

El adaptador actual extiende `ArrayAdapter<TermuxSession>` y muestra una lista plana. Los cambios necesarios:

1. **Agregar modelo de ítems**: Crear una clase interna `ListItem` que pueda ser:
   - `HEADER` — texto de encabezado ("Split Window", "Standalone")
   - `SESSION` — una sesión con metadatos (pane index, isStandalone)

2. **Agrupar sesiones en `getCount()` / `getItem()`**:
   - Obtener la lista completa de `TermuxService.getTermuxSessions()`
   - Obtener los `sessionIndex` del split tree
   - Construir una lista plana de `ListItem`:
     ```
     [
       HEADER("Split Window"),
       SESSION(sesionEnPane0, paneIndex=0),
       SESSION(sesionEnPane1, paneIndex=1),
       HEADER("Standalone"),
       SESSION(sesionStandalone, paneIndex=-1),
       ...
     ]
     ```

3. **Vistas diferentes para header y session**: Usar `getViewTypeCount()` = 2 y `getItemViewType()`.

4. **Agregar drag icon a items standalone**: En el layout XML, incluir un `ImageView` con el icono ☰ (o un drawable). Este icono tiene un `OnLongClickListener` que inicia el drag.

#### 4.3.2 API y Métodos Nuevos

```java
public class TermuxSessionsListViewController extends ArrayAdapter<TermuxSession> {

    // --- Nuevos tipos de vista ---
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_SESSION = 1;

    // --- Clase interna para items agrupados ---
    private static class ListItem {
        enum Type { HEADER, SESSION }
        final Type type;
        final TermuxSession session;  // null si HEADER
        final int paneIndex;          // -1 si standalone
        final String headerText;      // solo si HEADER

        // Constructor para HEADER
        ListItem(String headerText) { ... }

        // Constructor para SESSION
        ListItem(TermuxSession session, int paneIndex) { ... }
    }

    private List<ListItem> mGroupedItems;
    private TermuxSplitLayout mSplitLayout;

    /**
     * Setear la referencia al split layout para consultar el árbol.
     */
    public void setSplitLayout(TermuxSplitLayout layout);

    /**
     * Reconstruir la lista agrupada. Llamar después de cada split/close/focus.
     */
    public void rebuildGroupedList();

    @Override
    public int getViewTypeCount() { return 2; }

    @Override
    public int getItemViewType(int position) {
        return mGroupedItems.get(position).type == Type.HEADER
            ? VIEW_TYPE_HEADER : VIEW_TYPE_SESSION;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (type == HEADER) {
            // Inflar layout de header
        } else {
            // Inflar layout de session con pane label
        }
    }
}
```

#### 4.3.3 Algoritmo de Agrupación

```
rebuildGroupedList():
    mGroupedItems.clear()

    allSessions = mActivity.getTermuxService().getTermuxSessions()  // List<TermuxSession>
    splitIndices = Set de sessionIndex del split tree
    splitSessions = []
    standaloneSessions = []

    for i, session in allSessions:
        // Obtener el sessionIndex real de la sesión
        sessionIndex = findSessionIndexInService(session)
        if sessionIndex in splitIndices:
            // paneOrder: in-order tree traversal order (same as findLeafIndexInOrder)
            // Session 0 = leftmost/topmost pane, numbered by split tree order
            paneOrder = getPaneOrder(sessionIndex, splitTreeRoot)
            splitSessions.add((session, paneOrder))
        else:
            standaloneSessions.add(session)

    if splitSessions NOT empty:
        mGroupedItems.add(HEADER("Split Window"))
        sort splitSessions by paneOrder
        for (session, paneIndex) in splitSessions:
            mGroupedItems.add(SESSION(session, paneIndex))

    // Add "Split Window" header only if there are split sessions
    if splitSessions NOT empty:
        mGroupedItems.add(HEADER("Split Window"))

    // Add "Standalone" header only if there are standalone sessions
    if standaloneSessions NOT empty:
        // Add separator line between groups
        mGroupedItems.add(SEPARATOR)
        mGroupedItems.add(HEADER("Standalone"))

    for session in standaloneSessions:
        mGroupedItems.add(SESSION(session, paneIndex=-1))

    notifyDataSetChanged()
```

### 4.4 Layout del Item de Sesión (Modificado)

**Archivo**: `app/src/main/res/layout/item_terminal_sessions_list.xml`

```xml
<!-- Layout actual: solo MaterialTextView -->
<com.google.android.material.textview.MaterialTextView
    android:id="@+id/session_title"
    ... />

<!-- Nuevo layout: LinearLayout horizontal con drag icon + texto -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="?android:attr/listPreferredItemHeight"
    android:orientation="horizontal"
    android:gravity="center_vertical">

    <!-- Drag icon (visible solo para standalone) -->
    <ImageView
        android:id="@+id/drag_icon"
        android:layout_width="32dp"
        android:layout_height="match_parent"
        android:src="@drawable/ic_drag_indicator_24"
        android:visibility="gone"
        android:gravity="center"
        android:clickable="true"
        android:longClickable="true"
        android:padding="4dp"
        app:tint="?attr/termuxColorOnSurfaceVariant" />

    <!-- Contenido principal: título + pane label -->
    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:orientation="vertical"
        android:gravity="center_vertical"
        android:padding="6dip">

        <TextView
            android:id="@+id/session_title"
            ... (existente) />

        <TextView
            android:id="@+id/pane_label"
            android:layout_width="wrap_content"
        android:layout_height="120dp"
            android:textSize="11sp"
            android:textColor="?attr/termuxColorPrimary"
            android:visibility="gone" />
    </LinearLayout>
</LinearLayout>
```

### 4.5 TermuxSplitLayout — OnDragListener

**Archivo**: `app/src/main/java/com/termux/app/terminal/split/TermuxSplitLayout.java`

#### 4.5.1 Nuevos Métodos

```java
/**
 * Inicializa el listener de drag & drop.
 * Llamado desde TermuxActivity.onCreate() después de inflar el layout.
 */
public void setupDragAndDrop() {
    setOnDragListener((v, event) -> {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                // Solo aceptar si trae el extra "SESSION_INDEX"
                return event.getClipDescription() != null
                    && event.getClipDescription().getLabel().equals("SESSION_DRAG");

            case DragEvent.ACTION_DRAG_LOCATION:
                // Opcional: mostrar indicador visual de zona
                highlightDropZone(event.getX(), event.getY());
                return true;

            case DragEvent.ACTION_DROP:
                // Obtener el sessionIndex del ClipData
                ClipData data = event.getClipData();
                CharSequence sessionIdText = data.getItemAt(0).getText();
                if (sessionIdText == null) return false;
                int sessionIndex;
                try {
                    sessionIndex = Integer.parseInt(sessionIdText.toString());
                } catch (NumberFormatException e) {
                    return false;
                }
                float dropX = event.getX();
                float dropY = event.getY();

                // Determinar orientación según zona de drop
                BranchNode.Orientation orientation = determineDropOrientation(dropX, dropY);

                // Ejecutar split con try/finally para limpiar mPendingDragSession
                try {
                    mPendingDragSession = draggedSession;
                    splitFocusedPane(orientation);
                } finally {
                    mPendingDragSession = null;
                }

                // La nueva sesión se crea en createNewTerminalView() del callback
                // Necesitamos reemplazar la sesión por defecto con la arrastrada
                // Esto requiere modificar la lógica de createNewTerminalView()

                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                clearDropZoneHighlight();
                return true;
        }
        return false;
    });
}

/**
 * Determina la orientación del split basado en la posición del drop
 * relativa al centro del layout.
 */
private BranchNode.Orientation determineDropOrientation(float dropX, float dropY) {
    float cx = getWidth() / 2f;
    float cy = getHeight() / 2f;
    float dx = dropX - cx;
    float dy = dropY - cy;

    // Centro (20% del área): default VERTICAL
    float centerThreshold = Math.min(getWidth(), getHeight()) * 0.1f;
    if (Math.abs(dx) < centerThreshold && Math.abs(dy) < centerThreshold) {
        return BranchNode.Orientation.VERTICAL;  // default
    }

    // Si el desplazamiento horizontal domina → VERTICAL (split izq/der)
    // Si el desplazamiento vertical domina → HORIZONTAL (split arriba/abajo)
    return (Math.abs(dx) >= Math.abs(dy))
        ? BranchNode.Orientation.VERTICAL
        : BranchNode.Orientation.HORIZONTAL;
}
```

#### 4.5.2 Consideración Crítica: Reemplazar la Sesión por Defecto

Cuando `splitFocusedPane()` llama a `mCallback.createNewTerminalView()`, este método crea una **nueva** `TerminalSession` con `mTermuxService.createTermuxSession()`. Pero en el caso de drag & drop, queremos que el nuevo pane use la **sesión arrastrada**, no una nueva.

**Solución propuesta**: Agregar un flag/mecanismo en `TermuxSplitLayout`:

```java
// Nuevo campo en TermuxSplitLayout
private TerminalSession mPendingDragSession;

/**
 * Establece la sesión que debe usarse en el próximo split.
 * Llamado antes de splitFocusedPane() durante el drag & drop.
 */
public void setPendingDragSession(TerminalSession session) {
    mPendingDragSession = session;
}

// Modificar splitFocusedPane para usar mPendingDragSession
// El callback createNewTerminalView() debe verificar si hay sesión pendiente
```

**Modificación en el callback `createNewTerminalView()` de `TermuxActivity`**:

```java
@Override
public TerminalView createNewTerminalView() {
    TerminalView newView = new TerminalView(TermuxActivity.this, null);
    newView.setTerminalViewClient(mSplitTerminalViewClient);
    newView.setFocusable(true);
    newView.setFocusableInTouchMode(true);
    newView.setTextSize(mPreferences.getFontSize());

    TerminalSession sessionToAttach = null;

    // Verificar si hay una sesión pendiente de drag & drop
    TerminalSession dragSession = mSplitLayout.getPendingDragSession();
    if (dragSession != null) {
        sessionToAttach = dragSession;
        mSplitLayout.clearPendingDragSession();
    } else {
        // Comportamiento normal: crear nueva sesión
        if (mTermuxService != null) {
            String workingDir = getCurrentSession() != null
                ? getCurrentSession().getCwd() : null;
            TermuxSession newTermuxSession = mTermuxService.createTermuxSession(
                null, null, null, workingDir, false, null);
            if (newTermuxSession != null) {
                sessionToAttach = newTermuxSession.getTerminalSession();
            }
        }
    }

    if (sessionToAttach != null) {
        newView.attachSession(sessionToAttach);
    }
    return newView;
}
```

### 4.6 TermuxActivity — Integración

**Archivo**: `app/src/main/java/com/termux/app/TermuxActivity.java`

#### 4.6.1 Nuevos Métodos y Campos

```java
// --- Nuevo campo ---
private MiniSplitView mMiniSplitView;

// --- En onCreate() / setTermuxTerminalViewAndClients() ---
private void setupDrawerSplitFeatures() {
    // 1. Configurar MiniSplitView
    mMiniSplitView = findViewById(R.id.mini_split_view);
    mMiniSplitView.setSplitLayout(mSplitLayout);
    mMiniSplitView.updateFromLayout();

    // 2. Configurar agrupación de sesiones
    mTermuxSessionListViewController.setSplitLayout(mSplitLayout);
    mTermuxSessionListViewController.rebuildGroupedList();

    // 3. Configurar drag & drop en el split layout
    mSplitLayout.setupDragAndDrop();

    // 4. Configurar long-press en items standalone
    setupSessionDrag();
}

// --- Configurar drag desde items de sesión ---
private void setupSessionDrag() {
    // El drag se inicia desde el icono ☰ en cada item standalone
    // Usamos View.OnLongClickListener en el adapter
    mTermuxSessionListViewController.setDragStartListener(
        (session, view) -> {
            // Crear ClipData con el sessionIndex
            int sessionIndex = mTermuxService.getTermuxSessions().indexOf(session);
            String clipLabel = String.valueOf(sessionIndex);
            ClipData clipData = ClipData.newPlainText("SESSION_DRAG", clipLabel);

            // Crear shadow builder para el drag
            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);

            // Iniciar drag
            view.startDragAndDrop(clipData, shadowBuilder, null, 0);
        }
    );
}

// --- Modificar onPaneCountChanged para actualizar MiniSplitView ---
@Override
public void onPaneCountChanged(int newCount) {
    if (mMiniSplitView != null) {
        mMiniSplitView.updateFromLayout();
    }
    if (mTermuxSessionListViewController != null) {
        mTermuxSessionListViewController.rebuildGroupedList();
    }
}
```

#### 4.6.2 Modificaciones en `onPaneFocused`

El callback `onPaneFocused` ya existe y llama a `termuxSessionListNotifyUpdated()`. Debemos agregar:

```java
@Override
public void onPaneFocused(TerminalView view, int paneIndex) {
    mTerminalView = view;
    termuxSessionListNotifyUpdated();

    // NUEVO: Actualizar MiniSplitView
    if (mMiniSplitView != null) {
        mMiniSplitView.updateFromLayout();
    }

    if (view != null) {
        view.requestFocus();
        if (mTermuxTerminalViewClient != null) {
            mTermuxTerminalViewClient.showKeyboardForFocusedPane();
        }
    }
}
```

### 4.7 Layout del Drawer (activity_termux.xml)

**Archivo**: `app/src/main/res/layout/activity_termux.xml`

#### 4.7.1 Ubicación del MiniSplitView

El MiniSplitView se inserta dentro del `left_drawer` LinearLayout, **entre** el header de settings y el ListView de sesiones:

```xml
<LinearLayout
    android:id="@+id/left_drawer"
    ...>

    <!-- Settings button header (existente) -->
    <LinearLayout ...>
        <ImageButton android:id="@+id/settings_button" ... />
    </LinearLayout>

    <!-- ════════════════════════════════════════════ -->
    <!-- NUEVO: MiniSplitView                        -->
    <!-- ════════════════════════════════════════════ -->
    <com.termux.app.terminal.split.MiniSplitView
        android:id="@+id/mini_split_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_marginEnd="8dp"
        android:layout_marginTop="4dp"
        android:layout_marginBottom="4dp"
        android:background="@drawable/mini_split_background"
        android:padding="8dp"
        android:minHeight="80dp" />

    <!-- Separador -->
    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="?android:attr/listDivider"
        android:layout_marginStart="8dp"
        android:layout_marginEnd="8dp" />

    <!-- ListView de sesiones (existente, modificado) -->
    <ListView
        android:id="@+id/terminal_sessions_list"
        ... />

    <!-- Split buttons (existente) -->
    ...
</LinearLayout>
```

---

## 5. Flujo de Drag & Drop

### 5.1 Diagrama de Secuencia Textual

```
Usuario                     Drawer                 Adapter          TermuxSplitLayout     TermuxService
  │                          │                      │                    │                    │
  │  Abre drawer             │                      │                    │                    │
  │─────────────────────────>│                      │                    │                    │
  │                          │  rebuildGroupedList()│                    │                    │
  │                          │─────────────────────>│                    │                    │
  │                          │                      │  getSessions()     │                    │
  │                          │                      │───────────────────>│                    │
  │                          │                      │  List<Session>     │                    │
  │                          │                      │<───────────────────│                    │
  │                          │                      │                    │                    │
  │                          │                      │  getRootNode()     │                    │
  │                          │                      │───────────────────>│                    │
  │                          │                      │  SplitNode         │                    │
  │                          │                      │<───────────────────│                    │
  │                          │                      │                    │                    │
  │                          │  Render grouped list │                    │                    │
  │                          │<─────────────────────│                    │                    │
  │                          │                      │                    │                    │
  │  Ve sesión standalone    │                      │                    │                    │
  │  con icono ☰             │                      │                    │                    │
  │                          │                      │                    │                    │
  │  Long-press en ☰         │                      │                    │                    │
  │─────────────────────────>│                      │                    │                    │
  │                          │  onLongClick()       │                    │                    │
  │                          │─────────────────────>│                    │                    │
  │                          │                      │                    │                    │
  │                          │  startDragAndDrop(   │                    │                    │
  │                          │    ClipData(SESSION_DRAG, sessionIndex),  │                    │
  │                          │    shadowBuilder)     │                    │                    │
  │                          │──────────────────────┼────────────────────│                    │
  │                          │                      │                    │                    │
  │  ─── INICIA DRAG ───     │                      │                    │                    │
  │                          │                      │                    │                    │
  │  Arrastra sobre el       │                      │                    │                    │
  │  TermuxSplitLayout       │                      │                    │                    │
  │─────────────────────────────────────────────────────────────────────>│                    │
  │                          │                      │                    │                    │
  │  ACTION_DRAG_STARTED     │                      │                    │                    │
  │                          │                      │  Valida clipLabel  │                    │
  │                          │                      │<===================│                    │
  │                          │                      │  return true       │                    │
  │                          │                      │===================>│                    │
  │                          │                      │                    │                    │
  │  Mueve el dedo sobre     │                      │                    │                    │
  │  el layout               │                      │                    │                    │
  │─────────────────────────────────────────────────────────────────────>│                    │
  │                          │                      │                    │                    │
  │  ACTION_DRAG_LOCATION    │                      │                    │                    │
  │  (múltiples eventos)     │                      │  highlightDropZone │                    │
  │                          │                      │<===================│                    │
  │                          │                      │                    │                    │
  │  Suelta en zona          │                      │                    │                    │
  │  (ej: lado izquierdo)    │                      │                    │                    │
  │─────────────────────────────────────────────────────────────────────>│                    │
  │                          │                      │                    │                    │
  │  ACTION_DROP             │                      │                    │                    │
  │                          │                      │                    │                    │
  │  1. determineDropOrientation(x, y) → VERTICAL   │                    │                    │
  │                          │                      │                    │                    │
  │  2. setPendingDragSession(session)              │                    │                    │
  │                          │                      │                    │                    │
  │  3. splitFocusedPane(VERTICAL)                  │                    │                    │
  │─────────────────────────────────────────────────────────────────────>│                    │
  │                          │                      │                    │                    │
  │                          │  createNewTerminalView()                  │                    │
  │                          │──────────────────────────────────────────>│                    │
  │                          │                      │                    │                    │
  │                          │  getPendingDragSession() → session        │                    │
  │                          │  attachSession(dragSession)                │                    │
  │                          │<──────────────────────────────────────────│                    │
  │                          │                      │                    │                    │
  │                          │  onPaneCountChanged(2)                    │                    │
  │                          │──────────────────────────────────────────>│                    │
  │                          │                      │                    │                    │
  │                          │  updateFromLayout()  │  rebuildList()     │                    │
  │                          │  ────────────        │  ─────────         │                    │
  │                          │  MiniSplitView       │  ListView          │                    │
  │                          │  se redibuja         │  se actualiza      │                    │
  │                          │                      │                    │                    │
  │  ─── FIN DRAG ───        │                      │                    │                    │
```

### 5.2 Modelo de Decisión de Drop Zones (Radial)

El modelo de decisión es radial, no por cuadrantes fijos. Se compara la magnitud del desplazamiento horizontal vs. vertical desde el centro del layout:

```
DropZoneDecision(centerX, centerY, dropX, dropY):
    dx = dropX - centerX
    dy = dropY - centerY
    margin = 0.2 * min(width, height) / 2  // center "dead zone" of 20%

    if |dx| < margin AND |dy| < margin → VERTICAL (default, center zone)
    elif |dx| >= |dy| → VERTICAL split (dx positive = new pane on right, negative = on left)
    else → HORIZONTAL split (dy positive = new pane below, negative = above)
```

Este modelo coincide exactamente con la implementación de `determineDropOrientation()` en la Sección 4.5.1 y el árbol de decisión de la Sección 3.2.

---

## 6. Archivos a Modificar/Crear

### 6.1 Archivos Nuevos

| # | Archivo | Propósito | Líneas Estimadas |
|---|---------|-----------|-------------------|
| 1 | `app/src/main/java/com/termux/app/terminal/split/MiniSplitView.java` | Vista personalizada que dibuja el diagrama de split en miniatura | ~250 |
| 2 | `app/src/main/res/drawable/mini_split_background.xml` | Shape drawable para el fondo del MiniSplitView | ~15 |

### 6.2 Archivos a Modificar

| # | Archivo | Cambios | Líneas Aprox. |
|---|---------|---------|----------------|
| 3 | `app/src/main/java/com/termux/app/terminal/TermuxSessionsListViewController.java` | - Agregar `ListItem` (HEADER/SESSION) <br> - `rebuildGroupedList()` <br> - `getViewTypeCount()`, `getItemViewType()` <br> - Pane labels en items <br> - Drag start listener | ~200 |
| 4 | `app/src/main/java/com/termux/app/terminal/split/TermuxSplitLayout.java` | - `setupDragAndDrop()` <br> - `determineDropOrientation()` <br> - `setPendingDragSession()` / `getPendingDragSession()` <br> - Drop zone highlight | ~100 |
| 5 | `app/src/main/res/layout/activity_termux.xml` | - Agregar `MiniSplitView` al drawer <br> - Separador | ~20 |
| 6 | `app/src/main/res/layout/item_terminal_sessions_list.xml` | - Agregar `ImageView` drag icon (☰) <br> - Agregar `TextView` pane_label <br> - Cambiar a `LinearLayout` contenedor | ~40 |
| 7 | `app/src/main/java/com/termux/app/TermuxActivity.java` | - `setupDrawerSplitFeatures()` <br> - `setupSessionDrag()` <br> - Modificar `onPaneFocused` <br> - Modificar `onPaneCountChanged` <br> - Modificar `createNewTerminalView()` | ~100 |

---

## 7. Consideraciones y Riesgos

### 7.1 Riesgos Técnicos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| **TerminalView es `final`** — No podemos extenderlo para agregar lógica de drag | Alto | No necesitamos extenderlo; el drag se maneja desde el adapter y el `OnDragListener` del layout |
| **Sincronización de índices** — `LeafNode.sessionIndex` debe coincidir con la posición en `TermuxService.getTermuxSessions()` después del drag | Alto | Al hacer drop, la sesión arrastrada se re-asigna al nuevo `LeafNode`. El índice debe actualizarse en `LeafNode` o usar referencia por objeto en vez de índice |
| **Re-creación de Activity** — `onRestoreInstanceState` debe restaurar el drag state | Medio | El drag es un gesto transitorio; no necesita persistencia. El MiniSplitView se reconstruye desde `mSplitLayout.getRootNode()` |
| **Rendimiento del MiniSplitView** — `onDraw()` se llama en cada frame | Bajo | El árbol de splits es pequeño (< 10 paneles). El renderizado es O(n) y usa solo operaciones básicas de Canvas |
| **Drawer abierto durante drag** — El drawer y el contenido están en el mismo `DrawerLayout` pero en posiciones diferentes | Medio | `startDragAndDrop()` funciona a nivel de ventana, no de vista. El shadow del drag es visible sobre toda la Activity. La coordenada del drop se recibe relativa al `TermuxSplitLayout` |

### 7.2 Consideraciones de UX

| Aspecto | Decisión | Justificación |
|---------|----------|---------------|
| **¿Qué pasa si el usuario arrastra una sesión que está en el split?** | Solo las sesiones standalone tienen drag icon | Las sesiones en el split ya están en uso |
| **¿Feedback visual durante el drag?** | Usar `DragShadowBuilder` con el snapshot del item | Es el comportamiento estándar de Android |
| **¿Qué pasa si el usuario hace drop fuera del layout?** | El drag termina sin acción | `ACTION_DROP` solo se dispara si el drop es sobre el `TermuxSplitLayout` |
| **¿Animación al cerrar el drawer?** | No por ahora | Se puede agregar en una iteración futura |
| **¿El MiniSplitView es clickeable?** | No — solo es informativo | Los controles están en los botones de split y el teclado |

### 7.3 Dependencias

| Dependencia | Propósito |
|-------------|-----------|
| `Android SDK 24+` | `View.startDragAndDrop()` requiere API 24 |
| `TermuxSplitLayout.getRootNode()` | Leer el árbol de splits |
| `TermuxSplitLayout.splitFocusedPane()` | Ejecutar el split después del drop |
| `TermuxService.getTermuxSessions()` | Obtener la lista de sesiones |
| `TermuxService.createTermuxSession()` | Crear sesiones nuevas (modo normal, no drag) |
| `WindowInsets` / tema | Colores del MiniSplitView deben adaptarse al tema |

### 7.4 Casos Edge

| Caso | Comportamiento Esperado |
|------|------------------------|
| **paneCount == 1** | MiniSplitView muestra un solo rectángulo. ListView muestra "Standalone" con la única sesión |
| **Todas las sesiones están en el split** | Solo aparece "Split Window" en el ListView. No hay ítems standalone |
| **No hay sesiones standalone disponibles** | El icono de drag no aparece en ningún ítem |
| **Drop en el centro exacto** | Default a VERTICAL (split izquierda/derecha) |
| **La sesión arrastrada es la misma que la enfocada** | No debería ocurrir porque la enfocada está en el split y no tiene drag icon. Pero si ocurre por error, el split duplica la sesión — aceptable |
| **El usuario cierra el drawer durante el drag** | El drag continúa a nivel de ventana. El drawer puede cerrarse; el layout sigue visible |

### 7.5 Posibles Mejoras Futuras

1. **Drop entre paneles específicos**: En lugar de dividir siempre el panel enfocado, permitir drop en un panel específico (detectado por coordenadas).
2. **Swap de sesiones**: Arrastrar una sesión del split a otra posición para intercambiar.
3. **Drag para reordenar sesiones standalone**: Arrastrar items standalone para cambiar su orden en la lista.
4. **MiniSplitView interactivo**: Tocar un panel en el MiniSplitView para enfocarlo.
5. **Zoom temporal del MiniSplitView**: Al hacer hover, agrandar el diagrama para mejor visualización.

---

## 8. Apéndice: Pseudocódigo de `determineDropOrientation`

```
fun determineDropOrientation(dropX: Float, dropY: Float): Orientation {
    val cx = width / 2f
    val cy = height / 2f
    val dx = dropX - cx    // positivo → right
    val dy = dropY - cy    // positivo → bottom

    // Zona central neutral (20% del tamaño menor)
    val neutralRadius = min(width, height) * 0.1f
    if (abs(dx) < neutralRadius && abs(dy) < neutralRadius) {
        return VERTICAL
    }

    // Si |dx| >= |dy| → el movimiento es principalmente horizontal
    //   → VERTICAL (split vertical que divide izquierda/derecha)
    // Si |dx| < |dy| → el movimiento es principalmente vertical
    //   → HORIZONTAL (split horizontal que divide arriba/abajo)
    return if (abs(dx) >= abs(dy)) VERTICAL else HORIZONTAL
}
```

---

## 9. Apéndice: Índice de LeafNode a Sesión

El modelo actual usa `LeafNode.sessionIndex` como un índice numérico dentro de la lista de sesiones. Sin embargo, las sesiones pueden ser reordenadas o eliminadas, lo que hace que los índices numéricos sean frágiles.

**Para el drag & drop**, la sesión arrastrada se identifica por su posición en `TermuxService.getTermuxSessions()` en el momento del drag. Después del split, el `LeafNode` apuntará a ese índice. Para robustez futura, considerar cambiar a una referencia por objeto (ej. `TerminalSession` directamente), pero eso requeriría cambios más profundos en la serialización de `SplitNode`.

**Decisión**: Mantener `sessionIndex` como posición en la lista, pero recalcular índices después de cada operación que modifique la lista de sesiones.

---

## 10. Historial de Revisiones

| Fecha | Versión | Cambios | Autor |
|------|---------|---------|-------|
| 2026-07-25 | 1.0 | Documento inicial | — |
