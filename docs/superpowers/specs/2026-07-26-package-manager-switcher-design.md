# Package Manager Switcher - Design Doc

## Resumen Ejecutivo

El Package Manager Switcher es una funcionalidad que permite a los usuarios de Termux elegir entre APT y PACMAN como gestor de paquetes predeterminado durante la primera ejecución, y cambiar entre ellos posteriormente desde la configuración de la aplicación. Su objetivo principal es eliminar la dependencia exclusiva de APT sin imponer PACMAN, delegando la elección al usuario final.

Actualmente Termux está acoplado a APT tanto en el bootstrap como en el instalador, el intérprete de variables de entorno y la estructura de directorios esperada. Este diseño introduce una capa de abstracción —el modelo canónico PackageModel— que permite convertir bidireccionalmente entre los formatos de base de datos de dpkg y ALPM (libalpm), junto con un mecanismo de intercambio de binarios (BootstrapSwapper) que reemplaza solo los archivos específicos de cada gestor sin reinstalar todo el bootstrap. De esta forma, la experiencia de usuario se mantiene fluida y el tiempo de conmutación es del orden de segundos.

El documento cubre el modelo de datos, los parsers y writers para ambos formatos, el flujo de conversión completo con rollback, la integración con el sistema actual (TermuxInstaller, TermuxBootstrap, Settings UI), y los archivos nuevos y modificados necesarios para su implementación. Está dirigido a desarrolladores que trabajen en el fork o en la integración con termux-pacman.

## Estado Actual

- Termux actualmente usa APT como gestor de paquetes predeterminado
- El fork tiene código comentado en TermuxBootstrap.java preparado para PACMAN pero no activo
- Hay scripts de migración manual en resources/optional/termux-init/bootstrap.md
- El proyecto termux-pacman ofrece bootstraps oficiales con pacman

## Arquitectura General

```
┌──────────────────────────────────────────────────────────────┐
│                       PackageManagerConverter                │
│                   (Orquestador de conversión)                │
└──┬────────────────────────────────────────────────────┬───────┘
   │                                                    │
   ▼                                                    ▼
┌──────────────────────┐                    ┌──────────────────────┐
│     DpkgParser       │                    │     AlpmParser       │
│  /var/lib/dpkg/      │                    │  /var/lib/pacman/    │
│  → PackageModel[]    │                    │  → PackageModel[]    │
└──────────────────────┘                    └──────────────────────┘
          │                                              │
          └──────────► PackageModel ◄────────────────────┘
                     (Modelo Canónico)
          │                                              │
┌──────────────────────┐                    ┌──────────────────────┐
│     DpkgWriter       │                    │     AlpmWriter       │
│  PackageModel[]      │                    │  PackageModel[]      │
│  → /var/lib/dpkg/    │                    │  → /var/lib/pacman/  │
└──────────────────────┘                    └──────────────────────┘
          │                                              │
          └──────────────────┬───────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │ BootstrapSwapper │
                    │ (intercambio     │
                    │  de binarios)    │
                    └─────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │   $PREFIX/      │
                    │  bin/ lib/ etc/ │
                    │  var/lib/       │
                    └─────────────────┘
```

### Componentes

| Componente | Archivo | Responsabilidad |
|-----------|---------|-----------------|
| PackageModel | pkgconv/model/PackageModel.java | Modelo canónico neutral que representa un paquete instalado |
| DpkgParser | pkgconv/parser/DpkgParser.java | Lee /var/lib/dpkg/status + info/* → List<PackageModel> |
| DpkgWriter | pkgconv/writer/DpkgWriter.java | List<PackageModel> → escribe /var/lib/dpkg/status + info/* |
| AlpmParser | pkgconv/parser/AlpmParser.java | Lee /var/lib/pacman/local/*/desc + files → List<PackageModel> |
| AlpmWriter | pkgconv/writer/AlpmWriter.java | List<PackageModel> → escribe /var/lib/pacman/local/*/{desc,files,depends} |
| PackageManagerConverter | pkgconv/PackageManagerConverter.java | Orquestador: detecta origen, parsea, convierte, escribe destino |
| BootstrapSwapper | pkgconv/BootstrapSwapper.java | Intercambia binarios/config específicos del gestor |
| PackageManagerDialog | termux/app/PackageManagerDialog.java | Diálogo "Elige tu gestor" en primera ejecución |
| PackageManagerSettings | termux/app/settings/ | Preferencia en Settings para ver/switch gestor |

## Modelo de Datos Canónico (PackageModel)

```java
class PackageModel {
    String name;
    String version;
    String arch;           // aarch64 | arm | any
    String description;
    String url;
    String license;
    String maintainer;
    long installedSize;    // en bytes
    long installDate;      // unix timestamp
    InstallReason reason;  // EXPLICIT | AUTO | UNKNOWN
    List<String> groups;
    List<Dependency> depends;
    List<Dependency> replaces;
    List<Dependency> conflicts;
    List<Dependency> provides;
    List<FilePath> files;  // paths relativos a $PREFIX
    List<Conffile> conffiles;
    Scripts scripts;
}
```

### Mapeo de Campos Crítico

| Campo | dpkg → Modelo | Modelo → ALPM |
|-------|--------------|---------------|
| Installed-Size | ÷ 1024 (KiB→bytes) | %SIZE% |
| Status | Solo importa "installed" | %REASON% = EXPLICIT |
| Depends | Parsear `pkg (>= 1.0)` → operador | Escribir `pkg>=1.0` |
| Conffiles | Parsear multi-línea `ruta hash` | %BACKUP% en desc |
| Archivos .list | Rutas absolutas → relativas | %FILES% en files |

## Parsers & Writers

### DpkgParser.java
1. Lee /var/lib/dpkg/status → divide en stanzas por \n\n
2. Por cada stanza: extrae campos clave:valor con soporte multi-línea
3. Para cada PackageModel:
   - Lee /var/lib/dpkg/info/<name>.list → files (abs→rel)
   - Lee /var/lib/dpkg/info/<name>.md5sums → hashes
   - Lee /var/lib/dpkg/info/<name>.{prerm,postrm,preinst,postinst} → scripts
4. Returns List<PackageModel>

### AlpmParser.java
1. Lista /var/lib/pacman/local/*/ → c/u es un paquete
2. Extrae nombre (último segmento numérico = pkgrel)
3. Lee desc → parsea secciones %KEY%\nvalores\n\n
4. Lee files → %FILES% (rutas) + %BACKUP% (ruta hash)
5. Lee depends/install si existen
6. Returns List<PackageModel>

### DpkgWriter.java
1. Escribe /var/lib/dpkg/status con stanzas
2. Por cada PackageModel:
   - Crea /var/lib/dpkg/info/<name>.list (rutas absolutas)
   - Crea /var/lib/dpkg/info/<name>.md5sums
   - Crea scripts separados (.prerm, .postrm, .preinst, .postinst)
   - Conffiles → campo en status + <name>.conffiles

### AlpmWriter.java
1. Por cada PackageModel:
   - Crea /var/lib/pacman/local/<name>-<ver>-<rel>/desc
   - Crea /var/lib/pacman/local/<name>-<ver>-<rel>/files
   - Crea depends si tiene dependencias
   - Crea install si tiene scripts

## BootstrapSwapper

Intercambia los archivos específicos de cada gestor sin reinstalar TODO el bootstrap.

### Flujo
1. Determinar origen y destino (APT→PACMAN o viceversa)
2. Extraer el bootstrap ZIP destino desde assets
3. Copiar archivos específicos sobre $PREFIX:
   - APT: bin/{apt,dpkg,pkg*}, lib/apt/, etc/apt/, var/lib/dpkg/
   - PACMAN: bin/pacman*, lib/pacman/, etc/pacman.d/, var/lib/pacman/
4. NO tocar: home/, bin/bash, bin/ls, share/, etc/profile (compartidos)

## Integración con Sistema Actual

### TermuxBootstrap.java
- Descomentar PackageManager.PACMAN("pacman")
- Descomentar PackageVariant.PACMAN_ANDROID_7("pacman-android-7")

### TermuxInstaller.java
- setupBootstrapIfNeeded() modificado:
  - Si NO hay PM guardado → mostrar PackageManagerDialog
  - Guardar elección en TermuxAppSharedPreferences
  - Usar variant para elegir URL de bootstrap:
    - APT: https://github.com/termux/termux-packages/releases/latest/download/bootstrap-{arch}.zip
    - PACMAN: https://github.com/termux-pacman/termux-packages/releases/latest/download/bootstrap-{arch}.zip

### app/build.gradle
- Añadir "pacman-android-7" a variants
- Sección de checksums para pacman bootstrap

### TermuxAppSharedPreferences
- getPackageManagerPreference() / setPackageManagerPreference()
- Key: "package_manager" → "apt" | "pacman"

### Settings UI
- Nueva preferencia en termux_preferences.xml
- Muestra "Current: apt"
- Al hacer clic: diálogo "Switch to pacman?"
- Confirmación + ProgressDialog
- Ejecuta PackageManagerConverter en background

### Variables de entorno
- Descomentar ENV_TERMUX_APP__PACKAGE_MANAGER
- Escribir TERMUX_APP__PACKAGE_MANAGER=apt|pacman en termux.env

## Flujo de Conversión Completo

APT → PACMAN:
1. Scanner detecta dpkg/status
2. DpkgParser.parse() → List<PackageModel>
3. Backup /var/lib/dpkg/
4. BootstrapSwapper: extrae bin/pacman, pacman.conf...
5. AlpmWriter.write(pkgs)
6. Borrar /var/lib/dpkg/
7. Actualizar TERMUX_APP__PACKAGE_MANAGER

PACMAN → APT:
1. Scanner detecta pacman/local/
2. AlpmParser.parse() → List<PackageModel>
3. Backup /var/lib/pacman/
4. BootstrapSwapper: extrae bin/apt, dpkg, sources.list...
5. DpkgWriter.write(pkgs)
6. Borrar /var/lib/pacman/local/
7. Actualizar TERMUX_APP__PACKAGE_MANAGER

### Rollback
- Backup de la DB original antes de la conversión
- Si falla, restaura backup + logs de error
- Si se interrumpe, al reiniciar detecta estado inconsistente y restaura

## Casos Especiales

| Caso | Acción |
|------|--------|
| Dependencias OR (pkg1 \| pkg2) | ALPM no soporta. Elegir primera opción + log |
| Essential: yes → %GROUPS% base | Mapear directo |
| Scripts dpkg separados → ALPM install | Fusionar en funciones post_install(), pre_remove() |
| Architecture all → any | Mapear directo |
| Paquetes con + en nombre | Ambos soportan, normal |

## Archivos a Crear/Modificar

### Nuevos (12 archivos)
| Archivo | Líneas estimadas |
|---------|-----------------|
| pkgconv/model/PackageModel.java | 60 |
| pkgconv/model/Dependency.java | 30 |
| pkgconv/model/FilePath.java | 20 |
| pkgconv/model/Conffile.java | 20 |
| pkgconv/model/Scripts.java | 30 |
| pkgconv/model/InstallReason.java | 10 |
| pkgconv/parser/DpkgParser.java | 250 |
| pkgconv/parser/AlpmParser.java | 200 |
| pkgconv/writer/DpkgWriter.java | 250 |
| pkgconv/writer/AlpmWriter.java | 250 |
| pkgconv/PackageManagerConverter.java | 150 |
| pkgconv/BootstrapSwapper.java | 200 |
| PackageManagerDialog.java | 100 |

### Modificados (7 archivos)
| Archivo | Cambio |
|---------|--------|
| TermuxBootstrap.java | Descomentar PACMAN enums |
| TermuxInstaller.java | Diálogo + URL selector |
| TermuxAppSharedPreferences.java | Nuevos getters/setters |
| termux_preferences.xml | Nueva preferencia |
| TermuxShellEnvironment.java | Descomentar env var |
| app/build.gradle | Nuevo variant pacman |
| TermuxActivity.java | Integrar dialog (menor) |

## Notas Técnicas Adicionales

- El proyecto termux-pacman oficial usa repositorio: https://github.com/termux-pacman/termux-packages
- Los bootstraps de pacman siguen el mismo patrón de URL: https://github.com/termux-pacman/termux-packages/releases/latest/download/bootstrap-{arch}.zip
- El formato de las URLs build-time debe confirmarse con la API de releases de termux-pacman
- Paquetes instalados por el usuario vs base del bootstrap: ambos se tratan igual en la conversión
- HOME ($PREFIX/home) nunca se toca durante la conversión

## Revisión

- [ ] ¿El modelo PackageModel cubre todos los campos necesarios de ambos formatos?
- [ ] ¿El mapeo de dependencias OR está correctamente manejado?
- [ ] ¿El rollback plan cubre fallos a mitad de conversión?
- [ ] ¿Los paths de bootstrap pacman son correctos?
- [ ] ¿La integración con TermuxInstaller respeta el flujo existente?
- [ ] ¿Settings UI es accesible y clara para el usuario?
