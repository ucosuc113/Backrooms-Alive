# Backrooms Mod — Minecraft Forge 1.20.1

## Estado actual: **Módulo 1 — Infraestructura del proyecto**

Este commit contiene **únicamente** la base del proyecto. No hay generación de
mundo, Backrooms, corrupción, reconocimiento de estructuras, selección de
regiones ni lógica de portal implementada todavía. Eso queda explícitamente
pendiente para sus módulos correspondientes.

---

## 1. Qué incluye este módulo

- `build.gradle` / `settings.gradle` / `gradle.properties` — proyecto
  ForgeGradle 6 para Forge **1.20.1**, mappings oficiales.
- Dependencias obligatorias declaradas:
  - **Immersive Portals (for Forge)** vía CurseMaven (`3.0.7-all`, proyecto
    CurseForge `355440`, archivo `6368524`).
  - **Cloth Config API** vía Maven de Modrinth (`11.1.136+forge`) — dependencia
    obligatoria de Immersive Portals en 1.19+.
- Paquetes vacíos pero documentados (`package-info.java`) para: `portal`,
  `memory`, `generation`, `world`, `network`, `data`, `event`.
- Registros (`registry/`) ya conectados al bus de eventos:
  - **Bloques**: `Almacén de Mentes` (`mind_storage`), sin comportamiento.
  - **Items**: `Comparador Episódico` (`episodic_comparator`) y el
    `BlockItem` del Almacén de Mentes, sin comportamiento.
  - **Creative Tab**: una sola pestaña `Backrooms` con ambos objetos.
  - **Sounds / Particles / Block Entities / Menus**: `DeferredRegister`
    creados y registrados, **sin entradas** — listos para ampliarse.
- **Dimensión** (`dimension/ModDimensions.java`): solo las claves
  (`ResourceKey`) de la dimensión, para que módulos futuros (portal,
  teletransporte) puedan referenciarla. La definición de datos
  (`data/backrooms/dimension_type` y `data/backrooms/dimension`) usa un
  generador **`minecraft:flat` vacío (una capa de aire)** como placeholder
  obligatorio — el formato de Minecraft exige un generador válido aunque no
  se implemente generación todavía. Se reemplazará por completo en el
  módulo de generación.
- `config/BackroomsConfig.java`: `ForgeConfigSpec` registrado pero sin
  ninguna opción todavía.
- `client/ClientSetup.java` y `common/CommonSetup.java`: listeners de ciclo
  de vida vacíos, listos para ampliarse.
- Texturas placeholder generadas a mano (16×16, no son arte final) para que
  el bloque y el item no se vean con la textura "missing".
- Traducciones `en_us` y `es_es`.

## 2. Supuestos y decisiones tomadas (a confirmar)

No se especificó nombre de mod ni paquete base, así que se asumió:

| Decisión | Valor elegido | Cómo cambiarlo |
|---|---|---|
| `modId` | `backrooms` | `gradle.properties` → `mod_id` |
| Paquete base | `com.glados.backrooms` | `gradle.properties` → `mod_group_id` + mover paquetes |
| Nombre del mod | `Backrooms` | `gradle.properties` → `mod_name` |
| Forge | `1.20.1-47.3.0` | `gradle.properties` → `forge_version` |

⚠️ **Importante sobre Immersive Portals**: en `mods.toml` se declaró la
dependencia con `modId="immersive_portals"`, que es el id interno conocido
del mod, pero no pude verificarlo extrayendo el jar real (sin acceso de red
a CurseForge/Modrinth desde este entorno). Antes de compilar en tu máquina,
confirma este id abriendo el `mods.toml` dentro del jar descargado, o
revisando el listado de mods en el juego.

## 3. ⚠️ Limitación de este entorno (léelo antes de reportar un "error")

No tengo acceso de red a `files.minecraftforge.net`, `maven.minecraftforge.net`,
`libraries.minecraft.net`, `www.cursemaven.com` ni `api.modrinth.com` desde este
sandbox. Esto significa que **no he podido ejecutar `./gradlew build`** ni
descargar el toolchain de Forge para verificar la compilación de forma real.

Lo que sí se validó en este entorno:
- Sintaxis de **todos** los JSON/`.mcmeta` (parseo válido).
- Balance de llaves y estructura de **los 20 archivos `.java`**.
- Coherencia de nombres entre clases (block/item/tab/dimension keys) y los
  archivos de recursos que los referencian (blockstate, modelos, lang).
- Convenciones de Forge 1.20.1 (registries vía `Registries.*` /
  `ForgeRegistries.*`, formato de `mods.toml`, formato de dimensión/dimension
  type) verificadas contra la documentación oficial de Forge.

Lo que **debes verificar tú** la primera vez, en tu máquina con red completa:
1. Generar el wrapper de Gradle: `gradle wrapper --gradle-version 8.1.1`
   (no pude incluir el binario `gradle-wrapper.jar`, solo el `.properties`).
2. `./gradlew build` (o `gradlew.bat build` en Windows) con JDK 17.
3. Si Immersive Portals o Cloth Config fallan al resolverse, revisa que los
   IDs de CurseMaven/Modrinth en `gradle.properties` sigan vigentes (los
   proyectos de CurseForge a veces retiran archivos antiguos).

## 4. Estructura de paquetes

```
com.glados.backrooms
├── BackroomsMod.java        (punto de entrada, conecta los registros)
├── registry/                 (Blocks, Items, CreativeTabs, BlockEntities, Sounds, Particles, Menus)
├── dimension/                 (claves de la dimensión, sin generación)
├── config/                    (ForgeConfigSpec, sin opciones aún)
├── client/                    (setup de cliente, vacío)
├── common/                    (setup común, vacío)
├── util/                      (constantes)
├── portal/        [reservado] (futuro: integración Immersive Portals)
├── memory/        [reservado] (futuro: Comparador Episódico / Almacén de Mentes)
├── generation/     [reservado] (futuro: chunk generator de Backrooms)
├── world/          [reservado] (futuro: estructuras / regiones)
├── network/        [reservado] (futuro: paquetes cliente-servidor)
├── data/           [reservado] (futuro: codecs propios)
└── event/          [reservado] (futuro: subscriptores de eventos)
```

## 5. Próximos módulos (pendientes, no implementados aquí)

- Módulo de portal (integración real con Immersive Portals).
- Módulo de generación (chunk generator de Backrooms).
- Módulo de memoria/corrupción (comportamiento del Almacén de Mentes y el
  Comparador Episódico).
- Módulo de reconocimiento de estructuras / selección de regiones.
- Recetas, sonidos, partículas y block entities concretas.
