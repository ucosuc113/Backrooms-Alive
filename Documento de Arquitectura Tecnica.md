# Documento de Arquitectura de Software
## Backrooms Generation Engine v2 — Plano técnico de implementación

**Basado en:** "Documento de Diseño Técnico — Rediseño del Sistema de Generación de Backrooms" v1.0
**Naturaleza de este documento:** arquitectura de software únicamente. No contiene algoritmos nuevos, no contiene código, no contiene pseudocódigo. Toda decisión de comportamiento ya está fijada por el documento de diseño; este documento solo decide **dónde vive cada responsabilidad y cómo se comunican las partes**.

---

## 0. Principio rector

El documento de diseño ya identificó tres escalas (mundo, edificio, bloque) y cinco capas de ejecución. La arquitectura de software debe ser un espejo exacto de esa separación: **un paquete por capa conceptual**, y dentro de cada paquete, **una clase por responsabilidad nombrada explícitamente en el documento de diseño** (un detector, un constructor, un colocador, un degradador).

Regla de oro que gobierna todas las decisiones de este documento:

> Cada capa solo puede conocer la *salida* de la capa inferior, nunca sus clases internas. Cada paquete expone una única clase fachada (o un puñado muy pequeño) a través de la cual el resto del sistema interactúa con él. Todo lo demás dentro del paquete es un detalle de implementación oculto.

Esto es lo que permite que "el siguiente Claude" implemente capa por capa sin tener que entender el sistema completo de una vez: cada paquete es una caja negra con una entrada y una salida documentadas.

---

## 1. Estructura de paquetes

```
com.glados.backrooms
├── util            (capa 0 — fundación, sin dependencias internas)
├── classification  (capa 0 — fundación, sin dependencias internas)
├── memory          (existente — captura cruda)
├── analysis        (Capa 0 del diseño — pre-análisis de memorias)
├── district        (Capa 1 del diseño — estructura del mundo)
├── graph           (Capa 2 del diseño — grafo de habitaciones)
├── context         (Capa 3 del diseño — contexto de chunk)
├── placement       (Capa 4 del diseño — colocación estructural)
├── degradation     (Capa 5 del diseño — sistema de degradación)
└── generation      (orquestación de nivel superior + integración con Minecraft)
```

### Justificación de cada paquete

**`util`** — Matemática y utilidades sin ningún conocimiento de Backrooms: hashing determinista, ruido Simplex, geometría de rectángulos/segmentos, BFS genérico sobre rejillas, búsqueda de Voronoi. Existe porque tanto `analysis` (BFS para volúmenes interiores) como `graph` (intersección de pasillos) como `district` (Voronoi, ruido) necesitan las mismas primitivas matemáticas. Si esta lógica no se centraliza aquí, se duplicará de forma sutilmente distinta en cada paquete, rompiendo el Invariante 2 (determinismo total).

**`classification`** — El vocabulario común (`ArchitecturalFunction`, `FunctionalMaterialTable`, `MemoryFunctionClassifier`) ya existente en el sistema actual. Se promueve a paquete propio porque, en el diseño nuevo, **tres capas distintas lo consumen** (`analysis` para construir prototipos, `placement` para colocar luces/escaleras por defecto, `degradation` para decidir en qué se corrompe un elemento funcional). Mantenerlo dentro de `generation` como hoy obligaría a que `analysis` y `degradation` dependan de `generation`, invirtiendo la dirección de dependencia que exige la arquitectura por capas.

**`memory`** — Se conserva tal cual conceptualmente (captura y persistencia NBT de regiones del Overworld). Es la única capa que toca el mundo real para *leer* recuerdos; todo lo demás trabaja sobre datos ya derivados.

**`analysis`** — Implementa la Capa 0 del diseño. Es el paquete que transforma "evidencia arqueológica cruda" en "conocimiento de estilo". Existe separado de `memory` porque tiene un ciclo de vida distinto: `memory` solo necesita ejecutarse cuando se *captura* o se *carga* un recuerdo; `analysis` se ejecuta una vez por recuerdo cargado, nunca más.

**`district`** — Implementa la Capa 1. Es deliberadamente un paquete sin estado, de funciones puras parametrizadas por la semilla del mundo. Se separa de `graph` porque su naturaleza es completamente distinta: `district` nunca cachea nada (se reevalúa on-demand en cualquier punto), mientras que `graph` cachea agresivamente. Mezclarlos forzaría a que una clase sin estado y una clase con caché compartan paquete y responsabilidades, dificultando razonar sobre cuál se puede llamar con qué frecuencia.

**`graph`** — Implementa la Capa 2. Es el paquete con más piezas porque la generación del `RoomGraph` tiene ocho pasos secuenciales bien diferenciados en el diseño. Se aísla en su propio paquete porque es el único lugar del sistema con estado cacheado por zona, y ese estado debe tener una frontera de responsabilidad muy clara (un único `ConcurrentHashMap`, una única clase que lo gestiona).

**`context`** — Implementa la Capa 3. Es el paquete "traductor": convierte el conocimiento global e independiente del chunk (`district` + `graph`) en una instantánea local específica de un chunk concreto. Se separa de `placement`/`degradation` porque su responsabilidad es puramente de *lectura y resumen* (consulta los datos globales, no escribe ningún bloque), mientras que `placement` y `degradation` son puramente de *escritura*. Esta separación es la que permite que `placement` y `degradation` sean intercambiables o testeables de forma aislada: ambas reciben el mismo `ChunkGenerationContext` ya resuelto y no necesitan saber cómo se construyó.

**`placement`** — Implementa la Capa 4. Coloca la arquitectura "perfecta". No tiene ninguna noción de degradación.

**`degradation`** — Implementa la Capa 5. Transforma lo que `placement` ya escribió. Se mantiene como paquete hermano de `placement` (no como subpaquete ni como dependiente directo) porque ambos dependen únicamente de `context`, nunca el uno del otro — esto es lo que permite, a futuro, sustituir el sistema de degradación completo sin tocar una sola línea de `placement`.

**`generation`** — El único paquete que conoce la existencia de todos los demás. Contiene el punto de entrada de Minecraft (`ChunkGenerator`) y el orquestador del pipeline completo. Es deliberadamente delgado: no contiene lógica de generación propia, solo secuencia llamadas a las fachadas de los demás paquetes.

---

## 2. Clases por paquete

Para cada clase: responsabilidad, qué información almacena (si almacena alguna; muchas son funciones puras sin estado), qué la usa, de qué depende. Las clases marcadas **[F]** son la *fachada pública* del paquete — el resto de clases del paquete son, en principio, package-private.

### 2.1 `util`

| Clase | Responsabilidad | Almacena | Usada por | Depende de |
|---|---|---|---|---|
| `HashUtil` **[F]** | Produce valores deterministas a partir de combinaciones (semilla de mundo, coordenadas, sales arbitrarias). Es la única fuente de "aleatoriedad determinista" de todo el sistema. | Nada (sin estado) | `district`, `graph`, `context` | Ninguna |
| `NoiseField` **[F]** | Envuelve una implementación de ruido Simplex multi-octava configurable (periodo base, número de octavas). Una instancia = una configuración de ruido reutilizable. | La configuración de octavas/periodo con la que fue construida | `district` (campo de degradación global), `context` (variación local de degradación) | Ninguna |
| `GeometryUtil` **[F]** | Intersección de rectángulos y segmentos axis-aligned, BFS genérico sobre una rejilla de posiciones con un predicado de vecindad configurable. | Nada (sin estado) | `analysis` (detección de volúmenes y muros), `graph` (intersección de pasillos, componentes de Delaunay), `context` (filtrado de elementos activos) | Ninguna |
| `VoronoiLookup` **[F]** | Dado un punto y una función que produce centros por celda de rejilla, determina el centro más cercano entre las 9 celdas vecinas. Pura geometría, sin saber qué es un "distrito". | Nada (sin estado) | `district` | `HashUtil` |

### 2.2 `classification` (ya existente, reubicado)

| Clase | Responsabilidad | Almacena | Usada por | Depende de |
|---|---|---|---|---|
| `ArchitecturalFunction` | Enumerado de roles funcionales de bloque. | — | Todos los paquetes de capas superiores | Ninguna |
| `MemoryFunctionClassifier` **[F]** | Clasifica un `BlockState` en un `ArchitecturalFunction`. | Nada (sin estado) | `analysis`, `placement`, `degradation` | `ArchitecturalFunction` |
| `FunctionalMaterialTable` **[F]** | Para un `ArchitecturalFunction`, ofrece candidatos de `BlockState` por defecto (caso sin memoria / fallback). | Tabla estática inmutable función→bloques candidatos | `analysis` (umbral de calidad / análisis neutro), `placement` (iluminación/escaleras por defecto), `degradation` (sustituciones funcionales) | `ArchitecturalFunction` |

### 2.3 `memory` (sin cambios de responsabilidad, solo de rol en el flujo)

| Clase | Responsabilidad | Almacena | Usada por | Depende de |
|---|---|---|---|---|
| `MemoryRegion` | Recuerdo crudo capturado (ya existente). | Bloques, bounds, metadatos | `analysis` (única consumidora tras el rediseño) | — |
| `MemoryBlockSnapshot` | Snapshot de un bloque individual (ya existente). | Posición relativa, estado, block entity | `MemoryRegion`, `analysis` | — |
| `MemoryLibrary` **[F]** | Persistencia y ciclo de vida de los recuerdos crudos por servidor. Tras el rediseño, **ya no es consultada por la generación de chunks**; solo la consulta el proceso de arranque que alimenta a `analysis`. | Mapa UUID→`MemoryRegion` | Arranque del servidor (`generation`), `analysis` | — |

### 2.4 `analysis` — Capa 0

| Clase | Responsabilidad | Almacena | Usada por | Depende de |
|---|---|---|---|---|
| `MemoryAnalysisRepository` **[F]** | Único punto de acceso a los análisis ya calculados. Expone: análisis por id, lista de análisis usables como primarios, y el análisis neutro de fallback si la lista está vacía. Inmutable tras construcción. | Mapa id→`MemoryAnalysis` (inmutable) | `district` (selección de memoria por distrito), `graph` (prototipos), `placement` (prototipos de pared/iluminación), `degradation` (StyleFingerprint) | `MemoryAnalyzer`, `NeutralAnalysisProvider` |
| `MemoryAnalyzer` | Orquesta el pipeline de análisis de una memoria: invoca al detector de volúmenes, al analizador de muros, al analizador de distribución funcional y al constructor de `StyleFingerprint`; ensambla el `MemoryAnalysis` resultante. | Nada (sin estado propio entre ejecuciones) | `MemoryAnalysisRepository` (en el arranque del servidor) | `InteriorVolumeDetector`, `WallSegmentAnalyzer`, `FunctionalDistributionAnalyzer`, `StyleFingerprintBuilder`, `QualityThresholdEvaluator` |
| `InteriorVolumeDetector` | Implementa 4.2: BFS acotado para encontrar volúmenes de aire cerrados y clasificarlos en habitación/pasillo/descartado. | Nada (sin estado; produce una lista de volúmenes por invocación) | `MemoryAnalyzer` | `GeometryUtil` (BFS genérico) |
| `WallSegmentAnalyzer` | Implementa 4.3: BFS horizontal de planos verticales sólidos, extracción de patrón de columna, detección de aberturas, tratamiento de extremos. Produce `WallPrototype` y `CornerPrototype`. | Nada (sin estado) | `MemoryAnalyzer` | `GeometryUtil`, `MemoryFunctionClassifier` |
| `FunctionalDistributionAnalyzer` | Implementa 4.4: para cada habitación detectada, agrupa bloques funcionales y calcula tendencia de posición, densidad y altura típica. Produce `RoomPrototype` por rol funcional. | Nada (sin estado) | `MemoryAnalyzer` | `MemoryFunctionClassifier` |
| `StyleFingerprintBuilder` | Implementa 4.5: agrega frecuencias de bloque, complejidad de muro, estilo de iluminación y dimensiones típicas en un `StyleFingerprint`. | Nada (sin estado) | `MemoryAnalyzer` | — |
| `QualityThresholdEvaluator` | Implementa 4.7: decide si una memoria es usable como primaria. | Nada (sin estado) | `MemoryAnalyzer` | — |
| `NeutralAnalysisProvider` | Implementa el `MemoryAnalysis` ficticio de 13.1 (smooth sandstone, sin acento, iluminación puntual cada 8 bloques, dimensiones por defecto). | El `MemoryAnalysis` neutro, construido una vez de forma estática | `MemoryAnalysisRepository` | `FunctionalMaterialTable` |
| `MemoryAnalysis` | Contenedor de datos inmutable (sección 10.1 del diseño). | id, `wallPrototypes`, `cornerPrototype`, `openingPrototype`, `roomPrototypes`, `styleFingerprint`, `isUsableAsPrimary` | Todas las capas superiores, por referencia | — |
| `WallPrototype`, `WallColumn`, `CornerPrototype`, `OpeningPrototype`, `RoomPrototype`, `PrototypeElement`, `StyleFingerprint` | Contenedores de datos inmutables descritos en 10.2–10.6. | Sus campos respectivos, tal como se listan en el diseño | `MemoryAnalysis`, `graph`, `placement`, `degradation` | — |

### 2.5 `district` — Capa 1

| Clase | Responsabilidad | Almacena | Usada por | Depende de |
|---|---|---|---|---|
| `DistrictLookup` **[F]** | Único punto de entrada del paquete. Dado `(x, z)`, responde: distrito dominante, distritos solapantes (si hay transición), y degradación combinada en ese punto (base del distrito, campo global, boost de transición). Ninguna otra clase de `district` es visible fuera del paquete. | Nada (sin estado; delega en las clases internas en cada invocación) | `graph` (propiedades del distrito al generar su `RoomGraph`), `context` (Bloque A y degradación) | `DistrictGrid`, `DistrictPropertyDeriver`, `GlobalDegradationField`, `TransitionZoneCalculator` |
| `DistrictGrid` | Implementa 5.1: define la rejilla de celdas de 192×192 y calcula, para una celda, el centro de distrito desplazado. | Nada (sin estado; constantes de configuración) | `DistrictLookup`, `VoronoiLookup` (vía `util`) | `HashUtil` |
| `DistrictPropertyDeriver` | Implementa 5.2: dado un centro de distrito, deriva `memoriaAsignada` (consultando `MemoryAnalysisRepository`), `densidadBase`, `degradacionBase`, `semilla`, memoria secundaria opcional. | Nada (sin estado; produce un `District` por invocación) | `DistrictLookup` | `HashUtil`, `MemoryAnalysisRepository` |
| `GlobalDegradationField` | Implementa 5.3: evalúa el ruido Simplex de tres octavas (periodos 64/32/16) para cualquier `(x, z)`. | La instancia de `NoiseField` configurada al construirse (una vez por mundo) | `DistrictLookup` | `NoiseField` |
| `TransitionZoneCalculator` | Implementa 5.4: dado un punto, encuentra el segundo distrito más cercano y la distancia, y calcula el boost de degradación de transición. | Nada (sin estado) | `DistrictLookup` | `DistrictGrid`, `VoronoiLookup` |
| `District` | Contenedor de datos inmutable: bounds del distrito, `memoriaAsignada`, memoria secundaria opcional, `densidadBase`, `degradacionBase`, `semilla`, y la propiedad derivada `esEspacioAbierto` (5.5, calculada una vez al construirse a partir de `densidadBase`/`degradacionBase`, evitando una clase clasificadora aparte). | Sus campos | `graph`, `context` | — |

### 2.6 `graph` — Capa 2

| Clase | Responsabilidad | Almacena | Usada por | Depende de |
|---|---|---|---|---|
| `RoomGraphCache` **[F]** | Único punto de entrada del paquete junto a `RoomGraph` en sí. Da acceso "obtener o generar" un `RoomGraph` por id de zona, usando `ConcurrentHashMap.computeIfAbsent` para garantizar generación única bajo concurrencia. | `ConcurrentHashMap<ZoneId, RoomGraph>` | `context` (Bloque A, paso 2) | `RoomGraphGenerator` |
| `RoomGraphGenerator` | Orquesta los ocho pasos de 6.2 a 6.9 en orden fijo y ensambla el `RoomGraph` final. | Nada entre invocaciones (todo su estado de trabajo es local a una generación) | `RoomGraphCache` (exclusivamente) | `RoomCenterDistributor`, `RoomSizeAssigner`, `CollisionResolver`, `DelaunayTriangulator`, `MinimumSpanningTreeBuilder`, `ConnectionSelector`, `CorridorRouter`, `OpeningPlacer`, `FunctionalRoleAssigner`, `District` (vía `DistrictLookup`), `MemoryAnalysisRepository` |
| `RoomCenterDistributor` | Paso 1: Poisson-disk simplificado para centros de habitación. | Nada (sin estado) | `RoomGraphGenerator` | `HashUtil` |
| `RoomSizeAssigner` | Paso 2: asigna ancho/profundidad por habitación a partir del `StyleFingerprint` del distrito. | Nada (sin estado) | `RoomGraphGenerator` | `HashUtil`, `StyleFingerprint` |
| `CollisionResolver` | Paso 3: repulsión iterativa de bounds solapados, hasta 50 iteraciones, con eliminación de las más problemáticas si persiste el solape. | Nada entre invocaciones | `RoomGraphGenerator` | `GeometryUtil` |
| `DelaunayTriangulator` | Paso 4 (parte 1): triangulación incremental aproximada de los centros ya resueltos. | Nada (sin estado) | `RoomGraphGenerator` | — |
| `MinimumSpanningTreeBuilder` | Paso 4 (parte 2): árbol de expansión mínima sobre las aristas de Delaunay. | Nada (sin estado) | `RoomGraphGenerator` | — |
| `ConnectionSelector` | Paso 5: añade el 35% adicional de aristas secundarias y clasifica jerarquía + ancho de pasillo por arista. | Nada (sin estado) | `RoomGraphGenerator` | `HashUtil` |
| `CorridorRouter` | Paso 6: calcula la ruta axis-aligned (uno o dos segmentos) de cada arista, y resuelve intersecciones entre pasillos ampliando la zona de cruce. | Nada (sin estado) | `RoomGraphGenerator` | `GeometryUtil`, `HashUtil` |
| `OpeningPlacer` | Paso 7: calcula la posición exacta de cada abertura, aplicando la restricción de no coincidir con límites de chunk (6.8). | Nada (sin estado) | `RoomGraphGenerator` | — |
| `FunctionalRoleAssigner` | Paso 8: asigna rol funcional por habitación y, usando el `RoomPrototype` correspondiente de `MemoryAnalysis`, calcula las posiciones absolutas en coordenadas del mundo de cada `PlannedElement` (la única vez que esta posición se calcula — queda fija para siempre, ver Invariante 1). | Nada (sin estado) | `RoomGraphGenerator` | `HashUtil`, `RoomPrototype`, `MemoryAnalysisRepository` |
| `RoomGraph` | Contenedor inmutable: zoneId, semilla, bounds, lista de `RoomNode`, lista de `CorridorEdge`, mapa id→`RoomNode`. | Sus campos (sección 10.7) | `context`, `RoomGraphCache` | `RoomNode`, `CorridorEdge` |
| `RoomNode`, `CorridorEdge`, `AxisAlignedSegment`, `Apertura` | Contenedores de datos inmutables (secciones 10.8, 10.10–10.12). | Sus campos respectivos | `context`, `placement`, `degradation` | — |
| `WallDirection`, `HierarchyLevel`, `FunctionalRole` | Enumerados de soporte. | — | `graph`, `context`, `placement` | — |
| `ZoneId` | Valor inmutable (par de long redondeado a cuadrícula) usado como clave del caché. | Las dos coordenadas redondeadas | `RoomGraphCache`, `context` | — |

### 2.7 `context` — Capa 3

| Clase | Responsabilidad | Almacena | Usada por | Depende de |
|---|---|---|---|---|
| `ChunkContextBuilder` **[F]** | Único punto de entrada del paquete. Recibe el chunk/región/random de Minecraft y produce un `ChunkGenerationContext` completo, ejecutando en orden los pasos 7.1 a 7.6. | Nada entre invocaciones | `generation` (orquestador del pipeline) | `DistrictOverlapResolver`, `RoomGraphResolver`, `ActiveElementFilter`, `RoleMapBuilder`, `DegradationMapBuilder`, `OpeningCollector` |
| `DistrictOverlapResolver` | Paso 7.1: evalúa `DistrictLookup` en los cuatro vértices del chunk, determina uno o dos distritos solapantes y la porción de cobertura de cada uno (vía Voronoi). | Nada (sin estado) | `ChunkContextBuilder` | `DistrictLookup` |
| `RoomGraphResolver` | Paso 7.2: para cada distrito solapante, obtiene su `RoomGraph` del caché. | Nada (sin estado) | `ChunkContextBuilder` | `RoomGraphCache` |
| `ActiveElementFilter` | Paso 7.3: filtra `RoomNode` y segmentos de `CorridorEdge` que intersectan el chunk (con margen de 1 bloque). | Nada (sin estado) | `ChunkContextBuilder` | `GeometryUtil` |
| `RoleMapBuilder` | Paso 7.4: construye `roleMap`, `roomMap` y `corridorMap` de 16×16 según el orden de prioridad documentado (habitaciones → aberturas sobreescriben paredes → pasillos). | Nada (sin estado; produce los arrays por invocación) | `ChunkContextBuilder` | — |
| `DegradationMapBuilder` | Paso 7.5: calcula los cuatro arrays de degradación de 16×16 combinando `GlobalDegradationField`, `TransitionZoneCalculator` y ruido local de periodos 8 y 4. | Nada entre invocaciones | `ChunkContextBuilder` | `DistrictLookup`, `NoiseField` |
| `OpeningCollector` | Paso 7.6: compila `activeOpenings` a partir de los `RoomGraph` activos. | Nada (sin estado) | `ChunkContextBuilder` | — |
| `ChunkGenerationContext` | Contenedor de datos del chunk en curso (sección 10.13). Es el **único objeto que cruza la frontera entre `context`, `placement` y `degradation`**. | Todos los campos listados en 10.13 | `placement`, `degradation` | `RoomNode`, `CorridorEdge`, `Apertura`, `MemoryAnalysis` (por referencia) |

### 2.8 `placement` — Capa 4

| Clase | Responsabilidad | Almacena | Usada por | Depende de |
|---|---|---|---|---|
| `StructuralPlacer` **[F]** | Único punto de entrada del paquete. Ejecuta, en el orden fijo de 8.1, los cinco sub-pasos de colocación sobre el `ChunkGenerationContext` recibido. | Nada entre invocaciones | `generation` | `RoomWallPlacer`, `CorridorWallPlacer`, `CorridorInteriorCleaner`, `FunctionalElementPlacer`, `LightingPlacer` |
| `RoomWallPlacer` | 8.2: coloca paredes de habitación, respetando aberturas (roleMap) y usando `WallPatternMapper` para proyectar el `WallPrototype` sobre la longitud real del segmento. | Nada (sin estado) | `StructuralPlacer` | `WallPatternMapper`, `MemoryAnalysis` (vía contexto) |
| `WallPatternMapper` | Helper puro: dada una posición a lo largo de un muro y un `WallPrototype`, decide qué `WallColumn` corresponde (tileable por módulo, o escalado, según longitudes), y resuelve el caso de esquina vía `CornerPrototype`. | Nada (sin estado) | `RoomWallPlacer`, `CorridorWallPlacer` | — |
| `CorridorWallPlacer` | 8.3: coloca paredes laterales de pasillo, resolviendo qué material usar por segmento según la habitación más cercana. | Nada (sin estado) | `StructuralPlacer` | `WallPatternMapper` |
| `CorridorInteriorCleaner` | 8.4: garantiza aire en el interior del pasillo tras colocar sus paredes. | Nada (sin estado) | `StructuralPlacer` | — |
| `FunctionalElementPlacer` | 8.5: coloca cada `PlannedElement` de las `activeRooms` (las posiciones ya vienen fijas desde `graph`); si la posición cae en rol no permitido, busca la posición libre más cercana dentro de `INTERIOR_HABITACION` consultando `roleMap`. | Nada (sin estado) | `StructuralPlacer` | `roleMap` (vía contexto) |
| `LightingPlacer` | 8.6: coloca iluminación estructural según `lightingStyle` del `StyleFingerprint` del distrito/habitación. | Nada (sin estado) | `StructuralPlacer` | `StyleFingerprint` (vía contexto) |

### 2.9 `degradation` — Capa 5

| Clase | Responsabilidad | Almacena | Usada por | Depende de |
|---|---|---|---|---|
| `DegradationPipeline` **[F]** | Único punto de entrada del paquete. Ejecuta, en el orden fijo de 9.2, los cuatro ejes de degradación y, al final, la salvaguarda de conectividad. | Nada entre invocaciones | `generation` | `StructuralDegrader`, `MaterialDegrader`, `AdditiveEffectsApplier`, `FunctionalDegrader`, `ConnectivitySafeguard` |
| `StructuralDegrader` | Eje 1 (9.1): elimina bloques de pared según `degradationStructural` y umbral variable con ruido local. | Nada (sin estado) | `DegradationPipeline` | `degradationStructural` (vía contexto) |
| `MaterialDegrader` | Eje 2: sustituye materiales según `degradationMaterial`, usando primario/acento del `StyleFingerprint` propio y, en degradación alta, del distrito secundario si existe. | Nada (sin estado) | `DegradationPipeline` | `StyleFingerprint` (vía contexto, incluyendo el del distrito secundario si lo hay) |
| `AdditiveEffectsApplier` | Eje 3: duplicación de pared, extensión errónea, acumulación de esquina, según `degradationAdditive`. | Nada (sin estado) | `DegradationPipeline` | `StructuralDegrader` (reutiliza la misma noción de umbral para la degradación estructural de la pared duplicada) |
| `FunctionalDegrader` | Eje 4: desplazamiento, sustitución o colocación absurda de elementos funcionales según `degradationFunctional`, preservando siempre los datos de block entity. | Nada (sin estado) | `DegradationPipeline` | `MemoryFunctionClassifier`, `FunctionalMaterialTable` |
| `ConnectivitySafeguard` | 9.3: fuerza aire en todo el volumen de cada `Apertura` en `activeOpenings`. Es la implementación física de los Invariantes 4 y 5. | Nada (sin estado) | `DegradationPipeline` (siempre el último paso, sin excepción) | `activeOpenings` (vía contexto) |

### 2.10 `generation`

| Clase | Responsabilidad | Almacena | Usada por | Depende de |
|---|---|---|---|---|
| `BackroomsChunkGenerator` **[F]** | Punto de integración con la API de `ChunkGenerator` de Minecraft (ya existente). `fillFromNoise` no cambia. `buildSurface` delega íntegramente en `GenerationPipeline`. | Lo mínimo exigido por la API de Minecraft (el `Holder<Biome>`) | Minecraft (registro de generadores) | `GenerationPipeline` |
| `GenerationPipeline` | El único orquestador de los Bloques A–D de 11.3: construir contexto → colocar estructura → degradar → finalizar heightmaps. Es la única clase de todo el sistema que importa de los seis paquetes de capas (district, graph, analysis, context, placement, degradation) simultáneamente. | Nada entre invocaciones | `BackroomsChunkGenerator` | `ChunkContextBuilder`, `StructuralPlacer`, `DegradationPipeline` |
| `ServerLifecycleHooks` | Implementa 11.1: en el arranque del servidor, ordena a `MemoryLibrary` cargar las memorias, dispara `MemoryAnalyzer` sobre cada una para poblar `MemoryAnalysisRepository`, e inicializa `RoomGraphCache` vacío. | Nada (es un punto de arranque, no un objeto persistente) | Ciclo de vida del servidor (evento de Minecraft Forge/Fabric) | `MemoryLibrary`, `MemoryAnalysisRepository`, `RoomGraphCache` |

---

## 3. Flujo completo: de la solicitud de Minecraft al chunk terminado

**Arranque del servidor (una sola vez, antes de que exista ningún chunk):**

1. `ServerLifecycleHooks` se dispara en el evento de arranque del servidor.
2. Pide a `MemoryLibrary` cargar todos los `.nbt` de `world/backrooms/memory/`.
3. Por cada `MemoryRegion` cargada, invoca a `MemoryAnalyzer`, que ejecuta el pipeline de Capa 0 (detección de volúmenes → análisis de muros → distribución funcional → huella de estilo → evaluación de calidad) y produce un `MemoryAnalysis`.
4. Todos los `MemoryAnalysis` resultantes se insertan en `MemoryAnalysisRepository`, que pasa a ser inmutable a partir de ese momento. Si ninguna memoria es usable como primaria, el repositorio expone el análisis de `NeutralAnalysisProvider` como único disponible.
5. `RoomGraphCache` se inicializa vacío. No se genera ningún `RoomGraph` todavía: la generación de grafos es perezosa.

**Por cada chunk que Minecraft solicita generar:**

6. Minecraft invoca `fillFromNoise` sobre `BackroomsChunkGenerator`. Esto ejecuta `carveImpossibleHouse` exactamente como en el sistema actual (suelo, techo, aire interior), sin ningún cambio ni dependencia de las capas nuevas.
7. Minecraft invoca `buildSurface`. `BackroomsChunkGenerator` delega de inmediato en `GenerationPipeline`, pasándole el chunk, la región y el `RandomSource` ya sembrado para ese chunk.

   **Bloque A — `ChunkContextBuilder` construye el contexto:**
   - `DistrictOverlapResolver` consulta `DistrictLookup` (paquete `district`) en los cuatro vértices del chunk. `DistrictLookup` internamente consulta `DistrictGrid` (cuadrícula 192×192) y `VoronoiLookup` para determinar a qué distrito(s) pertenece cada vértice, y `DistrictPropertyDeriver` para materializar las propiedades de cada distrito tocado (esto es una evaluación on-demand, no hay caché en este nivel).
   - `RoomGraphResolver` toma cada distrito identificado y pide su `RoomGraph` a `RoomGraphCache`. Si la zona nunca se generó, `RoomGraphCache` invoca a `RoomGraphGenerator`, que ejecuta sus ocho pasos internos (consultando `MemoryAnalysisRepository` para los prototipos de estilo) y el resultado se guarda en caché para siempre (mientras el servidor esté vivo).
   - `ActiveElementFilter` recorta, de cada `RoomGraph` obtenido, solo los `RoomNode` y segmentos de `CorridorEdge` que tocan este chunk.
   - `RoleMapBuilder` construye los arrays `roleMap`/`roomMap`/`corridorMap` de 16×16 a partir de los elementos activos.
   - `DegradationMapBuilder` construye los cuatro arrays de degradación de 16×16, consultando de nuevo `DistrictLookup` (para el campo global y el boost de transición) más ruido local de grano fino.
   - `OpeningCollector` compila `activeOpenings`.
   - El resultado de todo el Bloque A es un único `ChunkGenerationContext` inmutable a efectos prácticos (sus arrays no se vuelven a tocar desde fuera de los Bloques B y C).

   **Bloque B — `StructuralPlacer` coloca la arquitectura perfecta:**
   - `RoomWallPlacer` coloca las paredes de cada `RoomNode` activo, dejando aire en las columnas marcadas `ABERTURA`.
   - `CorridorWallPlacer` coloca las paredes laterales de cada segmento de pasillo activo.
   - `CorridorInteriorCleaner` fuerza aire en el interior de esos pasillos.
   - `FunctionalElementPlacer` coloca los `PlannedElement` de las habitaciones activas (las posiciones ya estaban fijadas desde la generación del `RoomGraph`, en `FunctionalRoleAssigner`).
   - `LightingPlacer` coloca la iluminación estructural según el `lightingStyle` correspondiente.

   **Bloque C — `DegradationPipeline` transforma el resultado:**
   - `StructuralDegrader` → `MaterialDegrader` → `AdditiveEffectsApplier` → `FunctionalDegrader`, estrictamente en ese orden, cada uno leyendo su capa de degradación correspondiente del `ChunkGenerationContext`.
   - `ConnectivitySafeguard` se ejecuta siempre al final, sin excepción, forzando aire en `activeOpenings`.

   **Bloque D — Finalización:**
   - `GenerationPipeline` invoca `Heightmap.primeHeightmaps` exactamente como hace hoy el sistema actual.

8. El chunk queda terminado y se devuelve a Minecraft. Ningún dato de este chunk sobrevive más allá de este punto, salvo lo que ya estaba cacheado a nivel de zona (`RoomGraph`) o de servidor (`MemoryAnalysis`).

Este flujo es completamente determinista porque en ningún punto se consulta un estado mutable que dependa del orden de llegada de los chunks: lo único compartido entre chunks es `MemoryAnalysisRepository` (inmutable) y `RoomGraphCache` (cuyo contenido, una vez generado para una zona, es siempre el mismo sin importar qué chunk lo disparó primero, por construcción de `RoomGraphGenerator` a partir únicamente de la semilla de zona).

---

## 4. Dependencias entre módulos

Diagrama de capas (cada flecha significa "depende de", y solo se permite apuntar hacia abajo):

```
generation
    │
    ├──────────────┬───────────────┐
    ▼              ▼               ▼
placement     degradation      (ambos dependen únicamente de context)
    │              │
    └──────┬───────┘
           ▼
        context
           │
           ▼
         graph
           │
           ▼
        district
           │
           ▼
        analysis
           │
           ▼
         memory
           │
    ┌──────┴───────┐
    ▼               ▼
classification     util
```

Reglas concretas:

- **`util` y `classification`** no dependen de ningún otro paquete del proyecto. Son la fundación. Cualquier capa puede depender de ellas directamente, sin pasar por capas intermedias, porque son vocabulario y matemática puros, no conocimiento de dominio sobre Backrooms.
- **`memory`** depende solo de Minecraft (NBT, `ServerLevel`) y no de ningún otro paquete propio.
- **`analysis`** depende de `memory` (lee `MemoryRegion`) y de `classification` (clasifica bloques). No depende de `district`, `graph`, `context`, `placement` ni `degradation`. Esto es deliberado: el análisis de una memoria debe poder ejecutarse y testearse sin que exista todavía ningún mundo, distrito o chunk.
- **`district`** depende de `analysis` (para elegir qué memoria asignar a un distrito, consulta `MemoryAnalysisRepository`) y de `util`. No depende de `graph` ni de capas superiores.
- **`graph`** depende de `district` (lee las propiedades de un `District` para sembrar y dimensionar su grafo) y de `analysis` (lee `RoomPrototype`/`StyleFingerprint` para tamaños y elementos funcionales). No depende de `context` ni de capas superiores — el grafo no sabe que existen los chunks.
- **`context`** depende de `district` y `graph` (sus dos fuentes de información) y de `util`. No depende de `placement` ni `degradation`.
- **`placement`** y **`degradation`** dependen únicamente de `context` (y, transitivamente a través de los objetos que `context` referencia, de `analysis` y `classification` para leer prototipos y tablas de materiales). **No dependen entre sí.** Esta es la regla más importante de toda la arquitectura: nada en `placement` debe importar una clase de `degradation`, y viceversa. Si en algún momento parece necesario, es una señal de que esa lógica pertenece en realidad a `context` (debería calcularse una vez y guardarse en el `ChunkGenerationContext`) o a `generation` (debería ser el orquestador quien decida el orden, no una llamada cruzada entre paquetes hermanos).
- **`generation`** es el único paquete que puede importar de los seis paquetes de capas. Ningún otro paquete debe importar nada de `generation`. Esto evita que la lógica de cada capa termine acoplada a la API de `ChunkGenerator` de Minecraft.

No existen dependencias circulares posibles con este esquema porque cada paquete solo aparece una vez en la cadena vertical del diagrama; no hay dos paquetes que se necesiten mutuamente.

---

## 5. Ciclo de vida de los datos

```
Archivos .nbt en disco
        │  (lectura una vez, al arranque)
        ▼
MemoryRegion          (memory)       — bloques crudos, inmutable
        │  (análisis una vez por memoria, al arranque)
        ▼
MemoryAnalysis        (analysis)     — prototipos + huella de estilo, inmutable para siempre
        │  (consultado on-demand, nunca cacheado per se — el District sí se reconstruye cada vez)
        ▼
District              (district)     — propiedades derivadas de un centro de distrito, recalculado en cada consulta a DistrictLookup (es barato: solo hashes y una evaluación de ruido)
        │  (consultado una vez por zona, cacheado para siempre tras la primera vez)
        ▼
RoomGraph             (graph)        — topología fija en coordenadas de mundo, inmutable desde que se genera, vive en RoomGraphCache mientras el servidor esté vivo
        │  (consultado y recortado en cada chunk)
        ▼
ChunkGenerationContext (context)     — instantánea local de un chunk: qué le toca de qué grafo, mapas de rol y degradación de 16×16
        │  (consumido por Bloque B)
        ▼
Bloques colocados en el ChunkAccess  (placement)  — arquitectura perfecta sin degradar
        │  (consumido y transformado por Bloque C)
        ▼
Bloques finales en el ChunkAccess    (degradation) — arquitectura degradada + conectividad garantizada
        │
        ▼
Chunk entregado a Minecraft
```

Puntos clave de la transformación:

- En cada flecha hacia abajo, **la cantidad de información se reduce y se especializa**: una memoria entera (miles de bloques) se condensa en un puñado de prototipos y números (`MemoryAnalysis`); un distrito entero (192×192 bloques) se condensa en una topología de habitaciones y pasillos (`RoomGraph`); un `RoomGraph` entero se recorta a los ~16×16 bloques que importan a un chunk (`ChunkGenerationContext`).
- Una vez que un dato baja un escalón, **nunca vuelve a subir**. `placement` y `degradation` nunca modifican el `RoomGraph` ni el `District` ni el `MemoryAnalysis`. Esto es lo que garantiza el Invariante 6.
- El único dato que es simultáneamente "de arriba" y se sigue consultando "abajo" es `MemoryAnalysis`: tanto `district` (al elegir memoria), como `graph` (al dimensionar habitaciones y posicionar elementos), como `placement` y `degradation` (al pintar bloques) lo leen directamente por referencia. Por eso es crítico que sea completamente inmutable: es el único objeto que viaja sin transformarse a través de todas las capas.

---

## 6. Objetos persistentes

| Objeto | Cardinalidad | Mutabilidad | Cuándo se crea |
|---|---|---|---|
| `MemoryAnalysisRepository` | Uno por servidor (vive mientras el servidor esté arriba) | Inmutable tras el arranque | Al arranque del servidor, antes del primer chunk |
| `RoomGraphCache` | Uno por servidor | El mapa interno es mutable (crece), pero cada `RoomGraph` individual ya almacenado es inmutable | Vacío al arranque; se va poblando bajo demanda |
| `RoomGraph` (cada instancia) | Una por zona explorada | Inmutable desde el instante en que `RoomGraphGenerator` termina de construirla | La primera vez que cualquier chunk consulta esa zona |
| `MemoryAnalysis` (cada instancia) | Una por memoria cargada | Inmutable desde el instante en que `MemoryAnalyzer` termina | Al arranque del servidor |
| `NoiseField` (instancia del campo global) | Una por servidor | Inmutable (la configuración no cambia) | Al arranque, junto con `GlobalDegradationField` |
| `MemoryRegion` (datos crudos) | Una por memoria cargada | Inmutable | Al arranque |

**Lo que se reconstruye constantemente, por diseño, y no debe cachearse:** `District`. El documento de diseño es explícito en 5.1: "nunca se pre-genera el mapa completo de distritos". `DistrictPropertyDeriver` es deliberadamente una función pura barata (un par de hashes y, como mucho, una evaluación de ruido); cachear sus resultados añadiría complejidad de invalidación sin beneficio de rendimiento medible. Si en el futuro el perfilado demuestra lo contrario, el único lugar que debería cambiar es `DistrictLookup` (añadiéndole una caché LRU interna), sin que ningún otro paquete note la diferencia.

**Lo que se calcula bajo demanda y nunca se guarda en ningún sitio:** `ChunkGenerationContext` y todo lo que `placement`/`degradation` construyen. Ver sección 7.

**Nota de diseño importante:** dado que nada de `district` ni `graph` se persiste en disco (todo se deriva de la semilla del mundo en memoria volátil), un reinicio del servidor implica que `RoomGraphCache` se vacía y **se va a regenerar exactamente igual** (mismo seed → mismo resultado, por el Invariante 2) salvo que el propio código del mod cambie entre reinicios. Esto se discute como riesgo en la sección 9.

---

## 7. Objetos temporales

Todo lo que vive exclusivamente durante la generación de un chunk:

- El propio `ChunkGenerationContext` y sus seis arrays de 16×16 (`roleMap`, `roomMap`, `corridorMap`, los cuatro arrays de degradación).
- Las listas `activeRooms`, `activeCorridors`, `activeOpenings` recortadas para ese chunk.
- Cualquier estructura de trabajo interna de `RoomWallPlacer`, `CorridorWallPlacer`, etc. (por ejemplo, mapas intermedios columna→estado al proyectar un `WallPrototype`).
- El `RandomSource` sembrado específicamente para ese chunk.

Ciclo de vida: todos estos objetos se crean al entrar en `GenerationPipeline.buildSurface` y quedan sin ninguna referencia viva en cuanto ese método retorna (no se guardan en ningún campo de `BackroomsChunkGenerator`, ni en ningún caché). Esto los hace elegibles para recolección de basura inmediatamente. Como cada chunk es independiente y los datos "caros" (`MemoryAnalysis`, `RoomGraph`) ya están resueltos antes de empezar, el coste de generar un chunk es siempre: unas pocas consultas a cachés ya calientes + la construcción de un puñado de arrays pequeños (16×16) + escritura directa de bloques. Esto evita que la generación de chunks acumule basura de larga vida: el único crecimiento de memoria sostenido en el tiempo es el de `RoomGraphCache`, que crece con el área *explorada*, no con el número de chunks *generados* (muchos chunks comparten la misma zona y por tanto el mismo `RoomGraph`).

---

## 8. Escalabilidad — puntos de extensión para futuros niveles

El diseño no cambia, pero la arquitectura debe dejar costuras explícitas para que un futuro "Nivel 2" de Backrooms (con sus propias memorias, su propio estilo de degradación, o su propia forma de conectar habitaciones) no obligue a tocar el núcleo. Las costuras son interfaces colocadas exactamente en las fronteras de paquete ya descritas, no lógica nueva:

- **Fuente de memorias por nivel**: `MemoryLibrary` debería depender de una interfaz `MemorySource` (en vez de asumir siempre `world/backrooms/memory/`), de forma que cada nivel registre su propia carpeta/origen de memorias sin que `analysis` necesite saberlo.
- **Algoritmo de conectividad intercambiable**: los pasos 4 y 5 de `RoomGraphGenerator` (Delaunay + MST + selección de aristas secundarias) deberían quedar detrás de una interfaz `ConnectivityAlgorithm` que `RoomGraphGenerator` invoca, en lugar de instanciar `DelaunayTriangulator`/`MinimumSpanningTreeBuilder` directamente. Un nivel futuro con una topología distinta (por ejemplo, pasillos en rejilla regular tipo oficina) implementaría esa interfaz sin tocar `RoomCenterDistributor`, `CollisionResolver` ni `OpeningPlacer`.
- **Etapas de degradación añadibles**: `DegradationPipeline` debería ejecutar una lista ordenada de implementaciones de una interfaz `DegradationStage` (de la cual `StructuralDegrader`, `MaterialDegrader`, `AdditiveEffectsApplier` y `FunctionalDegrader` son las cuatro implementaciones del diseño actual), en vez de llamarlas por nombre fijo. Un nivel futuro podría insertar una etapa adicional (por ejemplo, "distorsión por humedad") sin modificar el orquestador, solo registrando la nueva etapa en la posición correcta de la lista. `ConnectivitySafeguard` se mantiene siempre fuera de esta lista, como paso final no configurable, dado su carácter de invariante no negociable.
- **Selección de memoria por distrito intercambiable**: `DistrictPropertyDeriver` debería delegar la elección de `memoriaAsignada` en una interfaz `MemoryAssignmentStrategy`, de forma que un nivel futuro pueda sesgar la selección (por ejemplo, "preferir siempre memorias propias de este nivel") sin tocar el resto de la derivación de propiedades del distrito.
- **Aislamiento de estado por nivel/dimensión**: hoy `MemoryAnalysisRepository` y `RoomGraphCache` se conciben como un único objeto por servidor. Para soportar varios niveles de Backrooms coexistiendo (cada uno como una dimensión distinta de Minecraft), ambos deberían quedar indexados por `(servidor, dimensión)` en lugar de solo por servidor — un cambio puramente de clave de caché, no de lógica.

Con estas costuras, añadir un nivel nuevo es: registrar un nuevo `MemorySource`, opcionalmente una nueva implementación de `ConnectivityAlgorithm` o `DegradationStage`, y una nueva instancia de `BackroomsChunkGenerator`/dimensión — sin tocar ninguna clase de `analysis`, `district` (más allá de la indexación), `context`, `placement` ni la columna vertebral de `graph`.

---

## 9. Riesgos y mitigaciones

**Crecimiento no acotado de `RoomGraphCache`.** Cada zona de 192×192 explorada queda en memoria para siempre mientras el servidor esté vivo. En sesiones muy largas o con muchos jugadores explorando en direcciones distintas, esto puede crecer indefinidamente. Mitigación: si el perfilado en producción lo justifica, añadir una política de expulsión (por ejemplo, LRU con límite de zonas, o expulsión de zonas sin jugadores cerca durante X minutos) exclusivamente dentro de `RoomGraphCache`, sin que ningún otro paquete note el cambio — la interfaz "obtener o generar por zoneId" se mantiene igual.

**`MemoryRegion` (datos crudos) ocupando memoria sin uso tras el arranque.** Una vez completado el análisis de Capa 0, ninguna clase de las capas superiores vuelve a leer los bloques crudos de una memoria — solo se lee `MemoryAnalysis`. Mitigación: si no existe ninguna otra funcionalidad del mod (comandos de inspección, edición en vivo de memorias, etc.) que necesite los bloques crudos después del arranque, `MemoryLibrary` puede liberar las referencias a `MemoryRegion` tras invocar a `MemoryAnalyzer`, reteniendo solo lo necesario para volver a escribir el `.nbt` si el recuerdo se actualiza.

**Determinismo entre reinicios del servidor frente a evolución del código.** Como `District` y `RoomGraph` nunca se persisten en disco (solo se derivan en caliente desde la semilla del mundo), cualquier cambio futuro en el código de `district` o `graph` (incluso una corrección de bug aparentemente inocua) hará que, tras un reinicio del servidor, zonas ya exploradas por los jugadores se regeneren con una geometría distinta a la que vieron antes. Esto es coherente con la temática de las Backrooms ("el edificio cambia"), pero debe ser una decisión consciente, no un efecto secundario accidental. Mitigación si en algún momento se desea estabilidad entre reinicios: persistir `RoomGraph` ya generados a disco indexados por `zoneId` (análogo a como hoy se persisten las `MemoryRegion`), y que `RoomGraphCache` los cargue en lugar de regenerarlos. Esto sería un cambio contenido enteramente dentro de `RoomGraphCache`/`RoomGraphGenerator`.

**Contención en `RoomGraphCache` bajo concurrencia.** `computeIfAbsent` garantiza generación única, pero mientras un hilo genera el `RoomGraph` de una zona (los ocho pasos de `RoomGraphGenerator`, incluida una triangulación de Delaunay), cualquier otro hilo de generación de chunks que necesite esa misma zona queda bloqueado esperando. Si el tiempo de generación de un grafo resultara perceptible, mitigación: medir primero (es una operación que ocurre una vez cada 192 bloques explorados, no por chunk, por lo que el impacto esperado es bajo); si hiciera falta, se podría adelantar la generación del `RoomGraph` de las zonas vecinas de forma asíncrona cuando un jugador se acerca al borde de una zona ya generada, sin cambiar la API pública de `RoomGraphCache`.

**Acoplamiento accidental entre `placement` y `degradation`.** El riesgo arquitectónico más probable durante la implementación es que, al escribir `FunctionalDegrader` o `AdditiveEffectsApplier`, alguien sienta la tentación de "recalcular" algo que en realidad ya decidió `placement` (por ejemplo, releer dónde puso `FunctionalElementPlacer` un elemento en vez de leerlo del `RoomNode.plannedElements` original). Mitigación organizativa: ambos paquetes deben depender exclusivamente de tipos de `context`/`graph`/`analysis`, nunca importar clases concretas el uno del otro; esto se puede hacer cumplir con una regla de build (por ejemplo, un check de arquitectura tipo ArchUnit) que falle la compilación si `degradation` importa algo de `placement` o viceversa.

**Coste por chunk de `DegradationMapBuilder`.** Calcular cuatro arrays de 16×16 con ruido en cada chunk (incluyendo el campo global de tres octavas) es trabajo adicional que el sistema actual no hacía. Mitigación: `NoiseField`/`GlobalDegradationField` deben ser instancias de larga vida (una por servidor, reutilizada en cada chunk), nunca reconstruidas por chunk; si el coste de evaluar ruido en 16×16×4 puntos por chunk resultara significativo en perfilado, se puede reducir la resolución de alguno de los cuatro mapas (por ejemplo, evaluar `degradationFuncional` por habitación en lugar de por columna, tal como ya sugiere 7.5 — "varía por habitación más que por posición" —, evitando recalcularlo columna a columna).

**Tamaño y dispersión del paquete `graph`.** Es el paquete con más clases (diez más los contenedores de datos). Mitigación: mantener estrictamente que solo `RoomGraphCache` y los contenedores de datos (`RoomGraph`, `RoomNode`, `CorridorEdge`, etc.) sean públicos; las diez clases de los ocho pasos deben ser package-private, visibles únicamente para `RoomGraphGenerator`. Así, desde fuera del paquete, la superficie de API real es pequeña aunque el paquete internamente tenga muchas piezas.

---

*Fin del documento de arquitectura.*
*Todas las decisiones de comportamiento provienen del documento de diseño v1.0 y no han sido alteradas. Este documento únicamente fija paquetes, clases, responsabilidades y direcciones de dependencia.*