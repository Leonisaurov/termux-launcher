# Package Manager Switcher - Task List

## Phase 1: Model + Parsers (Foundation)

### Task 1: PackageModel y clases del modelo canónico
**Descripción:** Crear todas las clases del modelo canónico en `com.termux.pkgconv.model`

**Archivos:**
- `app/src/main/java/com/termux/pkgconv/model/PackageModel.java`
- `app/src/main/java/com/termux/pkgconv/model/Dependency.java`
- `app/src/main/java/com/termux/pkgconv/model/FilePath.java`
- `app/src/main/java/com/termux/pkgconv/model/Conffile.java`
- `app/src/main/java/com/termux/pkgconv/model/Scripts.java`
- `app/src/main/java/com/termux/pkgconv/model/InstallReason.java`

**Criterios:**
- [ ] PackageModel tiene todos los campos del spec
- [ ] Dependency soporta name, operator (GE/LE/GT/LT/EQ/ANY), version
- [ ] FilePath tiene path (relativo), md5sum, isDirectory
- [ ] Conffile tiene path y md5sum
- [ ] Scripts tiene preInst, postInst, preRm, postRm
- [ ] InstallReason enum: EXPLICIT, AUTO, UNKNOWN

**Dependencias:** Ninguna
**Estimado:** S (6 clases pequeñas)
**Estado:** PENDING

---

### Task 2: DpkgParser
**Descripción:** Parsear /var/lib/dpkg/status y /var/lib/dpkg/info/* → List<PackageModel>

**Archivos:**
- `app/src/main/java/com/termux/pkgconv/parser/DpkgParser.java`

**Criterios:**
- [ ] Divide status en stanzas por \n\n
- [ ] Soportar multi-línea (continuación con espacio)
- [ ] Extraer Package, Status, Version, Depends, Conffiles, Description, etc.
- [ ] Parsear Depends con versionado: `pkg (>= 1.0)` → Dependency
- [ ] Leer /var/lib/dpkg/info/<name>.list → rutas absolutas a relativas
- [ ] Leer /var/lib/dpkg/info/<name>.md5sums → hashes
- [ ] Leer scripts .prerm, .postrm, .preinst, .postinst
- [ ] Convertir Installed-Size de KiB a bytes

**Dependencias:** Task 1
**Estimado:** M (~250 líneas)
**Estado:** PENDING

---

### Task 3: AlpmParser
**Descripción:** Parsear /var/lib/pacman/local/*/ → List<PackageModel>

**Archivos:**
- `app/src/main/java/com/termux/pkgconv/parser/AlpmParser.java`

**Criterios:**
- [ ] Lista subdirectorios /var/lib/pacman/local/*/
- [ ] Algoritmo reverse-split para name-version-rel:
  - Último segmento numérico = pkgrel (si solo dígitos)
  - Penúltimo con dígitos = version
  - Resto unido por '-' = name
- [ ] Parsear desc: secciones %KEY%\nvalores\n\n
- [ ] Parsear files: %FILES% (rutas relativas, / en directorios) + %BACKUP% (ruta hash)
- [ ] Leer depends e install si existen
- [ ] Mapear %REASON% 0→AUTO, 1→EXPLICIT

**Dependencias:** Task 1
**Estimado:** M (~200 líneas)
**Estado:** PENDING

---

### Task 4: Tests de parsers
**Descripción:** Crear datos mock y tests para ambos parsers

**Archivos:**
- `app/src/test/java/com/termux/pkgconv/parser/DpkgParserTest.java`
- `app/src/test/java/com/termux/pkgconv/parser/AlpmParserTest.java`
- Datos mock en `app/src/test/resources/pkgconv/mock/dpkg/`
- Datos mock en `app/src/test/resources/pkgconv/mock/alpm/`

**Criterios:**
- [ ] DpkgParser produce PackageModel correcto desde datos mock
- [ ] AlpmParser produce PackageModel correcto desde datos mock
- [ ] Los campos se mapean correctamente (version, arch, depends, files)

**Dependencias:** Task 2, Task 3
**Estimado:** M (3-5 archivos)
**Estado:** PENDING

---

## Phase 2: Writers + Converter (Core)

### Task 5: DpkgWriter
**Descripción:** Escribir List<PackageModel> → /var/lib/dpkg/status + /var/lib/dpkg/info/*

**Archivos:**
- `app/src/main/java/com/termux/pkgconv/writer/DpkgWriter.java`

**Criterios:**
- [ ] Escribe /var/lib/dpkg/status con stanzas correctamente formateadas
- [ ] Escribe /var/lib/dpkg/info/<name>.list con rutas absolutas
- [ ] Escribe /var/lib/dpkg/info/<name>.md5sums
- [ ] Escribe scripts .prerm, .postrm, .preinst, .postinst
- [ ] Escribe Conffiles campo en status + <name>.conffiles
- [ ] Convierte installedSize de bytes a KiB para Installed-Size

**Dependencias:** Task 1
**Estimado:** M (~250 líneas)
**Estado:** PENDING

---

### Task 6: AlpmWriter
**Descripción:** Escribir List<PackageModel> → /var/lib/pacman/local/*/

**Archivos:**
- `app/src/main/java/com/termux/pkgconv/writer/AlpmWriter.java`

**Criterios:**
- [ ] Crea /var/lib/pacman/local/<name>-<ver>-<rel>/desc con secciones %KEY%
- [ ] Crea /var/lib/pacman/local/<name>-<ver>-<rel>/files con %FILES% y %BACKUP%
- [ ] Crea depends si hay dependencias
- [ ] Crea install si hay scripts (post_install, pre_remove, post_remove)
- [ ] Rutas relativas, directorios con / al final

**Dependencias:** Task 1
**Estimado:** M (~250 líneas)
**Estado:** PENDING

---

### Task 7: PackageManagerConverter
**Descripción:** Orquestador que coordina parseo, swap y escritura

**Archivos:**
- `app/src/main/java/com/termux/pkgconv/PackageManagerConverter.java`

**Criterios:**
- [ ] detectSourcePM(): detecta si existe dpkg/ o pacman/ DB
- [ ] convert(targetPM): orquesta APT→PACMAN o PACMAN→APT
- [ ] Implementa steps: BACKUP_READY(0), PARSED(1), SWAPPED(2), WRITTEN(3), CLEANUP_DONE(4), COMPLETE(5)
- [ ] Escribe y lee lock file en $PREFIX/var/run/pm-convert.lock
- [ ] Crea backup antes de modificar
- [ ] Restaura backup si hay error
- [ ] Manejo de dependencias OR (heurística)

**Dependencias:** Task 2, Task 3, Task 5, Task 6, Task 10
**Estimado:** M (~150 líneas)
**Estado:** PENDING

---

### Task 8: Tests de round-trip
**Descripción:** Tests de conversión bidireccional

**Archivos:**
- `app/src/test/java/com/termux/pkgconv/PackageManagerConverterTest.java`

**Criterios:**
- [ ] dpkg mock → convertir a ALPM → convertir a dpkg → mismo resultado
- [ ] ALPM mock → convertir a dpkg → convertir a ALPM → mismo resultado
- [ ] Paquetes con dependencias OR se manejan correctamente
- [ ] Paquetes con scripts se convierten correctamente

**Dependencias:** Task 7
**Estimado:** M (3-5 archivos)
**Estado:** PENDING

---

## Phase 3: Bootstrap + Swapper (Infrastructure)

### Task 9: TermuxBootstrap - descomentar PACMAN
**Descripción:** Activar los enums de PackageManager y PackageVariant para PACMAN

**Archivos:**
- `termux-shared/src/main/java/com/termux/shared/termux/TermuxBootstrap.java`

**Criterios:**
- [ ] PackageManager.PACMAN("pacman") activo
- [ ] PackageVariant.PACMAN_ANDROID_7("pacman-android-7") activo
- [ ] isAppPackageManagerPACMAN() devuelve true cuando corresponde
- [ ] isAppPackageVariantPACMANAndroid7() devuelve true cuando corresponde
- [ ] No rompe la funcionalidad existente de APT

**Dependencias:** Ninguna
**Estimado:** XS (1 archivo, cambios mínimos)
**Estado:** PENDING

---

### Task 10: BootstrapSwapper
**Descripción:** Intercambiar binarios y config del gestor extrayendo del ZIP destino

**Archivos:**
- `app/src/main/java/com/termux/pkgconv/BootstrapSwapper.java`

**Criterios:**
- [ ] Extrae ZIP bootstrap destino de assets/
- [ ] Para APT→PACMAN: borra bin/{apt,dpkg,pkg}, lib/apt/; extrae bin/pacman*, etc/pacman.d/
- [ ] Para PACMAN→APT: borra bin/pacman*, etc/pacman.d/; extrae bin/{apt,dpkg,pkg}, etc/apt/, lib/apt/
- [ ] NO toca /var/lib/ (responsabilidad de writers)
- [ ] NO toca home/
- [ ] Preserva permisos de archivos existentes
- [ ] Verifica que los binarios críticos se copiaron correctamente

**Dependencias:** Task 9
**Estimado:** M (~200 líneas)
**Estado:** PENDING

---

### Task 11: app/build.gradle - variant pacman
**Descripción:** Añadir soporte para pacman-android-7 en el build system

**Archivos:**
- `app/build.gradle`

**Criterios:**
- [ ] "pacman-android-7" en lista de valores soportados
- [ ] Bloque downloadBootstraps para pacman con URLs de termux-pacman
- [ ] Checksums SHA-256 para cada arquitectura
- [ ] Build exitoso con TERMUX_PACKAGE_VARIANT=pacman-android-7

**Dependencias:** Task 9
**Estimado:** S (1 archivo)
**Estado:** PENDING

---

## Phase 4: UI Integration

### Task 12: PackageManagerDialog + TermuxInstaller
**Descripción:** Diálogo de elección en primera ejecución + integración en installer

**Archivos:**
- `app/src/main/java/com/termux/app/PackageManagerDialog.java` (NUEVO)
- `app/src/main/java/com/termux/app/TermuxInstaller.java` (MODIFICAR)

**Criterios:**
- [ ] Dialog muestra "Choose Package Manager" con apt (recomendado) y pacman
- [ ] Al elegir, guarda en TermuxAppSharedPreferences
- [ ] TermuxInstaller.setupBootstrapIfNeeded() verifica preferencia antes de descargar
- [ ] Si no hay preferencia guardada → muestra dialog
- [ ] Usa la preferencia para elegir URL de bootstrap (termux-packages vs termux-pacman)
- [ ] Compatible con el flujo existente (ProgressDialog, extracción, second-stage)

**Dependencias:** Task 9, Task 13
**Estimado:** M (2 archivos)
**Estado:** PENDING

---

### Task 13: TermuxAppSharedPreferences
**Descripción:** Getters/setters para la preferencia de package manager

**Archivos:**
- `termux-shared/src/main/java/com/termux/shared/settings/preferences/TermuxAppSharedPreferences.java`

**Criterios:**
- [ ] KEY_PACKAGE_MANAGER = "package_manager"
- [ ] getPackageManagerPreference() devuelve "apt", "pacman" o null
- [ ] setPackageManagerPreference(String) guarda el valor
- [ ] Valores válidos: "apt" y "pacman"
- [ ] Compatible con SharedPreferences existente

**Dependencias:** Ninguna
**Estimado:** XS (1 archivo, pocas líneas)
**Estado:** PENDING

---

### Task 14: Settings UI
**Descripción:** Añadir preferencia de Package Manager en Settings

**Archivos:**
- `app/src/main/res/xml/termux_preferences.xml` (MODIFICAR)
- Fragment de Settings correspondiente

**Criterios:**
- [ ] Nueva entrada "Package Manager" en lista de preferencias
- [ ] Summary muestra "Current: apt" o "Current: pacman"
- [ ] Al hacer clic: diálogo "Switch to pacman?" con advertencia
- [ ] Al confirmar: ejecuta PackageManagerConverter con ProgressDialog
- [ ] ProgressDialog muestra pasos: "Parsing...", "Swapping...", "Writing..."
- [ ] Al terminar: toast de éxito y actualiza summary
- [ ] Si falla: muestra error y restaura

**Dependencias:** Task 7, Task 10, Task 13
**Estimado:** M (2-3 archivos)
**Estado:** PENDING

---

### Task 15: TermuxShellEnvironment
**Descripción:** Exponer variable de entorno del gestor de paquetes

**Archivos:**
- `termux-shared/src/main/java/com/termux/shared/termux/TermuxShellEnvironment.java`
- `termux-shared/src/main/java/com/termux/app/termux/TermuxAppShellEnvironment.java`

**Criterios:**
- [ ] Descomentar ENV_TERMUX_APP__PACKAGE_MANAGER
- [ ] Descomentar ENV_TERMUX_APP__PACKAGE_VARIANT
- [ ] Escribir en termux.env: TERMUX_APP__PACKAGE_MANAGER=apt|pacman
- [ ] Escribir en termux.env: TERMUX_APP__PACKAGE_VARIANT=apt-android-7|pacman-android-7

**Dependencias:** Task 9
**Estimado:** XS (2 archivos, cambios mínimos)
**Estado:** PENDING

---

## Phase 5: Rollback + Polish

### Task 16: Sistema de rollback
**Descripción:** Lock file, backup, detección de inconsistencia y restauración

**Archivos:**
- `app/src/main/java/com/termux/pkgconv/PackageManagerConverter.java` (ya incluye rollback)
- `app/src/main/java/com/termux/app/TermuxActivity.java` (detección en onCreate)

**Criterios:**
- [ ] Lock file en $PREFIX/var/run/pm-convert.lock (JSON con step, source, target, backup_path, timestamp)
- [ ] Backup comprimido en $PREFIX/var/backups/pm-convert/ con timestamp
- [ ] TermuxActivity.onCreate() verifica si hay lock con step < 5
- [ ] Restaura según el step:
  - step 0-1: backup existe pero nada se tocó → restaurar backup, borrar lock
  - step 2: swapper ejecutado → restaurar backup completo
  - step 3-4: writers parciales → restaurar backup completo
  - step 5: lock residual → borrar lock y continuar
- [ ] Validación post-restauración (status file existe o local/ tiene contenido)
- [ ] Backup incluye /var/lib/{dpkg,pacman}/ y /etc/{apt,pacman.d}/

**Dependencias:** Task 7
**Estimado:** M (2 archivos)
**Estado:** PENDING

---

### Task 17: Progress + Error handling
**Descripción:** Indicadores de progreso y manejo de errores

**Archivos:**
- Modificaciones en Settings fragment y PackageManagerConverter

**Criterios:**
- [ ] ProgressDialog con título "Converting Package Manager"
- [ ] Steps visibles: "Backing up...", "Reading packages...", "Swapping binaries...", "Writing database...", "Cleaning up..."
- [ ] Si falla: dialog muestra error específico y botón "Restore"
- [ ] Restore exitoso: toast "Restored to previous state"
- [ ] Tiempo estimado se muestra si es posible

**Dependencias:** Task 14, Task 16
**Estimado:** S (1-2 archivos)
**Estado:** PENDING

---

### Task 18: Testing checklist final
**Descripción:** Verificación manual end-to-end

**Criterios:**
- [ ] APT→PACMAN: 10 paquetes de prueba se convierten correctamente
- [ ] PACMAN→APT: 10 paquetes de prueba se convierten correctamente
- [ ] Interrupción (kill app) a mitad: al reiniciar restaura backup
- [ ] Primera instalación: dialog aparece y descarga bootstrap correcto
- [ ] Variable TERMUX_APP__PACKAGE_MANAGER visible en `env`
- [ ] Settings muestra gestor actual
- [ ] Switch desde settings funciona

**Dependencias:** All previous tasks
**Estimado:** S (testing)
**Estado:** PENDING

---

## Summary

| Phase | Tasks | Estado |
|-------|-------|--------|
| Phase 1: Model + Parsers | Tasks 1-4 | PENDING |
| Phase 2: Writers + Converter | Tasks 5-8 | PENDING |
| Phase 3: Bootstrap + Swapper | Tasks 9-11 | PENDING |
| Phase 4: UI Integration | Tasks 12-15 | PENDING |
| Phase 5: Rollback + Polish | Tasks 16-18 | PENDING |
