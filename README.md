# Backrooms Mod - Minecraft Forge 1.20.1

## Estado actual

El proyecto ya tiene la base técnica montada para el mod `backrooms`:

- punto de entrada del mod en `src/main/java/com/glados/backrooms/BackroomsMod.java`
- registros iniciales de bloques, items, tabs, block entities, sonidos, partículas, menús y chunk generators
- configuración COMMON lista para crecer
- paquetes de dominio ya separados para generación, memoria, portal, mundo, red, datos y eventos

Todavía no hay una experiencia completa implementada para Backrooms, pero la arquitectura ya está preparada para crecer por módulos. El objetivo del proyecto en este punto no es “terminar el contenido” de inmediato, sino establecer una base sólida de infraestructura, contenido mínimo registrable y una dirección clara para la lógica procedural y narrativa del mod.

## Contexto general del proyecto

Este mod está pensado como una propuesta de experiencia de Backrooms en Minecraft Forge 1.20.1. La idea central es combinar:

- un espacio generado proceduralmente con tono arquitectónico y dislocado
- un sistema de memoria o selección de regiones que pueda evolucionar hacia mecánicas más complejas
- una integración con portales y estructuras de acceso que hagan sentir que el espacio no es estándar

En otras palabras, el código actual apunta a una infraestructura de mod flexible: lo suficiente para registrar contenido, preparar dimensiones y dejar abierta la puerta a futuras mecánicas de exploración, generación y narrativa.

## Arquitectura breve

La raíz del código vive en `com.glados.backrooms` y está organizada por responsabilidad, no por funcionalidad de gameplay única. La idea es separar:

- bootstrap y registro: `BackroomsMod`, `client`, `common`, `registry`
- configuración y metadatos: `config`, `dimension`, `util`
- lógica de dominio: `generation`, `memory`, `portal`, `world`
- extensibilidad futura: `data`, `event`, `network`

Esta estructura favorece que Claude o cualquier colaborador entienda primero la infraestructura y luego los módulos de gameplay.

## Jerarquía del código en com.glados.backrooms

```text
com.glados.backrooms
├── BackroomsMod.java                      # punto de entrada del mod y conexión de DeferredRegister
├── client/
│   └── ClientSetup.java                  # inicialización exclusiva del cliente
├── common/
│   └── CommonSetup.java                  # inicialización compartida cliente/servidor
├── config/
│   └── BackroomsConfig.java              # configuración COMMON de Forge
├── data/
│   └── package-info.java                 # documento de propósito del paquete de datos
├── dimension/
│   └── ModDimensions.java                # claves y referencias de dimensión
├── event/
│   └── package-info.java                 # punto de entrada para eventos del mod
├── generation/
│   ├── ArchitecturalFunction.java        # funciones arquitectónicas del chunk generator
│   ├── BackroomsChunkGenerator.java      # generador procedural del mundo
│   ├── FunctionalMaterialTable.java      # tabla de materiales/funciones del espacio
│   ├── MemoryFunctionClassifier.java     # clasificación de funciones por memoria
│   └── package-info.java                 # propósito del paquete de generación
├── memory/
│   ├── MemoryBlockSnapshot.java          # snapshot de bloques para memoria
│   ├── MemoryCommands.java               # comandos relacionados con memoria
│   ├── MemoryConnector.java              # conexión entre regiones o nodos
│   ├── MemoryConnectorAnalyzer.java      # análisis de conectores
│   ├── MemoryConnectorType.java          # tipos de conector de memoria
│   ├── MemoryLibrary.java                # biblioteca central de memorias/regiones
│   ├── MemoryRegion.java                 # modelo de región de memoria
│   ├── MemorySelection.java              # selección específica de memoria
│   ├── MemorySelections.java             # colección de selecciones
│   ├── MemorySelectorItem.java           # item funcional para marcar/seleccionar memoria
│   └── package-info.java                 # propósito del paquete de memoria
├── network/
│   └── package-info.java                 # paquete reservado para red cliente/servidor
├── portal/
│   ├── BackroomsPortalEvents.java        # eventos y lógica asociada a portales
│   └── package-info.java                 # propósito del paquete de portal
├── registry/
│   ├── ModBlockEntities.java            # registros de block entities
│   ├── ModBlocks.java                   # registro de bloques
│   ├── ModChunkGenerators.java          # registro del chunk generator
│   ├── ModCreativeTabs.java             # pestaña creativa del mod
│   ├── ModItems.java                    # registro de items
│   ├── ModMenus.java                    # registro de menús UI
│   ├── ModParticles.java                # registro de partículas
│   └── ModSounds.java                    # registro de sonidos
├── util/
│   └── ModConstants.java                 # constantes compartidas del mod
└── world/
    └── package-info.java                 # paquete reservado para lógica futura del mundo
```

## Qué representa cada capa

- `BackroomsMod`, `client` y `common`: arranque del mod y sus callbacks de Forge.
- `registry`: punto único para registrar contenido vanilla/Forge del mod.
- `config`, `dimension`, `util`: infraestructura y referencias compartidas.
- `generation`: lógica procedural para construir el espacio Backrooms.
- `memory`: sistema de selección, análisis y biblioteca de regiones/recuerdos.
- `portal`: integración del acceso al espacio y eventos asociados.
- `data`, `event`, `network`, `world`: módulos de extensión y placeholders para futuras capas.

## Scripts y recursos auxiliares

En la carpeta `scripts` hay utilidades de apoyo que no forman parte del runtime del mod, pero ayudan a preparar contenido y recursos:

- `scripts/gen_placeholder_textures.py`: genera texturas placeholder simples para bloques e items mientras no existe arte final. Sirve para evitar texturas faltantes y mantener el mod visualmente funcional durante el desarrollo.

## Notas para Claude

- El mod usa `modId = backrooms`.
- El paquete base es `com.glados.backrooms`.
- El proyecto apunta a Forge `1.20.1`.
- Si vas a trabajar en gameplay, empieza por `generation`, `memory` y `portal`; si vas a trabajar en infraestructura, revisa `registry`, `config` y `common`.
- Si necesitas entender el flujo general, sigue este orden: `BackroomsMod` → `registry` → `common/client` → `generation/memory/portal`.

