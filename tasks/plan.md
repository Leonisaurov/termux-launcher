# Implementation Plan: Package Manager Switcher

## Overview
Implementar un sistema que permita:
1. Elegir APT o PACMAN durante la primera instalación del bootstrap
2. Convertir la base de datos de paquetes entre dpkg y ALPM (Java puro, sin shell commands)
3. Intercambiar los binarios del gestor (apt ↔ pacman) extrayendo del bootstrap ZIP destino
4. Cambiar desde Settings fácilmente
5. Rollback automático si algo falla (lock-based con steps)

## Architecture Decisions
- **Java puro para parsers/writers**: Sin shell commands para que funcione aunque el gestor esté roto
- **PackageModel como modelo canónico**: Un modelo neutral que permite conversión bidireccional sin pérdida crítica
- **Lock-based rollback**: Archivo de lock con 6 steps (0-5) para detectar y recuperar de interrupciones
- **termux-pacman oficial como fuente**: Los bootstraps de pacman se descargan del repo oficial `github.com/termux-pacman/termux-packages`
- **BootstrapSwapper no toca DBs**: Solo intercambia bin/ y etc/, no var/lib/ (responsabilidad de writers)

## Task List (5 Fases, 18 Tasks)

### Phase 1: Model + Parsers (Foundation)
- [ ] Task 1: PackageModel y clases del modelo canónico
- [ ] Task 2: DpkgParser - parsear /var/lib/dpkg/status + info/*
- [ ] Task 3: AlpmParser - parsear /var/lib/pacman/local/*/
- [ ] Task 4: Tests de parsers con datos mock

### Checkpoint: Parsers
- [ ] Ambos parsers leen datos mock correctamente
- [ ] PackageModel se construye sin errores

### Phase 2: Writers + Converter (Core)
- [ ] Task 5: DpkgWriter - escribir /var/lib/dpkg/status + info/*
- [ ] Task 6: AlpmWriter - escribir /var/lib/pacman/local/*/
- [ ] Task 7: PackageManagerConverter - orquestador bidireccional con steps
- [ ] Task 8: Tests de round-trip (dpkg→PackageModel→dpkg y ALPM→PackageModel→ALPM)

### Checkpoint: Converter
- [ ] Round-trip test pasa: dpkg→PackageModel→dpkg produce mismo contenido
- [ ] Round-trip test pasa: ALPM→PackageModel→ALPM produce mismo contenido

### Phase 3: Bootstrap + Swapper (Infrastructure)
- [ ] Task 9: TermuxBootstrap - descomentar PACMAN enums
- [ ] Task 10: BootstrapSwapper - intercambiar binarios y config desde ZIP
- [ ] Task 11: app/build.gradle - añadir variant pacman-android-7

### Checkpoint: Infrastructure
- [ ] Build succeeds con variant pacman-android-7
- [ ] BootstrapSwapper puede extraer binarios del ZIP pacman

### Phase 4: UI Integration
- [ ] Task 12: PackageManagerDialog + integración en TermuxInstaller
- [ ] Task 13: TermuxAppSharedPreferences - get/set package manager
- [ ] Task 14: Settings UI - preferencia "Package Manager" con switch
- [ ] Task 15: TermuxShellEnvironment - descomentar TERMUX_APP__PACKAGE_MANAGER

### Checkpoint: Integration
- [ ] Dialog aparece en primera ejecución (si no hay PM guardado)
- [ ] Settings muestra el gestor actual y permite cambiarlo
- [ ] Variable de entorno TERMUX_APP__PACKAGE_MANAGER se escribe en termux.env

### Phase 5: Rollback + Polish
- [ ] Task 16: Sistema de rollback (lock, backup, restore, detección)
- [ ] Task 17: Progress indicators y manejo de errores
- [ ] Task 18: Testing checklist completo

### Checkpoint: Complete
- [ ] Conversión APT→PACMAN funciona end-to-end
- [ ] Conversión PACMAN→APT funciona end-to-end
- [ ] Rollback recupera estado anterior si el proceso se interrumpe
- [ ] Primera instalación ofrece elegir gestor
- [ ] Build clean, sin warnings

## Dependency Graph

```
PackageModel (model classes) ──┬── DpkgParser ──┐
                                ├── AlpmParser ──┤
                                ├── DpkgWriter ──┤
                                ├── AlpmWriter ──┤
                                │               │
                                └── PackageManagerConverter
                                        │
                                    BootstrapSwapper ─── TermuxBootstrap
                                        │
                                    PackageManagerDialog
                                        │
                                    TermuxInstaller ─── app/build.gradle
                                        │
                                    TermuxAppSharedPreferences
                                        │
                                    Settings UI ─── TermuxShellEnvironment
```

## Risks and Mitigations
| Risk | Impact | Mitigation |
|------|--------|------------|
| termux-pacman cambia URL de bootstrap | Medio | Validar URL en runtime, checksums SHA-256 |
| ALPM database format changes | Medio | Versionar formato, tests de compatibilidad |
| Usuario mata app durante conversión | Alto | Lock-based rollback con steps y backup |
| Paquetes con dependencias OR | Alto | Heurística: preferir paquete ya instalado |
| Scripts dpkg sin equivalente exacto en ALPM | Alto | Mapeo documentado: postinst→post_install, prerm→pre_remove, postrm→post_remove, preinst se omite |

## Open Questions
- [ ] URLs exactas de releases de termux-pacman (confirmar patrón /releases/latest/download/)
- [ ] ¿Se necesita soporte para apt-android-5 o solo android-7?
