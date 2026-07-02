# Handoff — Estado del proyecto al cierre de sesion

---

## CAPA 0 (analysis) — 100% COMPLETA

Todos los archivos implementados y en src/:

    analysis/
      CornerPrototype.java
      InteriorVolumeDetector.java
      LightingStyle.java
      MemoryAnalysis.java
      OpeningPrototype.java
      PrototypeElement.java
      QualityThresholdEvaluator.java
      RoomPrototype.java
      StyleFingerprint.java
      WallColumn.java
      WallPrototype.java
      WallRole.java
      WallSegmentAnalyzer.java
      FunctionalDistributionAnalyzer.java
      StyleFingerprintBuilder.java
      MemoryAnalyzer.java
      MemoryAnalysisRepository.java
      NeutralAnalysisProvider.java

    classification/
      FunctionalRole.java
      ArchitecturalFunction.java
      MemoryFunctionClassifier.java
      FunctionalMaterialTable.java

    util/
      HashUtil.java
      NoiseField.java
      GeometryUtil.java
      VoronoiLookup.java        (corregido: quitado @FunctionalInterface incorrecto)

---

## CAPA 1 (district) — 100% COMPLETA

Paquete: src/main/java/com/glados/backrooms/district/

    district/
      District.java               record inmutable de datos (campos de seccion 5.2)
      DistrictGrid.java           rejilla 192x192, implementa CellCenterProvider
      DistrictPropertyDeriver.java deriva todos los campos de un distrito desde hashes
      GlobalDegradationField.java  ruido Simplex 3 octavas (64/32/16), escala a [0.0, 0.7]
      TransitionZoneCalculator.java boost de degradacion por proximidad a limite
      DistrictLookup.java         FACHADA PUBLICA del paquete

Notas de implementacion:
- degradacionBase usa min(raw1, raw2) para sesgar distribucion hacia valores bajos.
- totalDegradation = clamp01(max(degradacionBase, globalNoise) + transitionBoost).
- transitionBoost usa el "gap" entre distancia al 1er y 2do distrito como metrica de borde.
- BUILD SUCCESSFUL confirmado.

### Pendiente de limpieza (no urgente):
BackroomsChunkGenerator.java sigue usando las copias viejas de generation/ para
ArchitecturalFunction, MemoryFunctionClassifier y FunctionalMaterialTable.
Las copias en generation/ no rompen nada. Solo borrarlas si se quiere limpiar.
Cuando se haga, agregar estos imports a BackroomsChunkGenerator.java:

    import com.glados.backrooms.classification.ArchitecturalFunction;
    import com.glados.backrooms.classification.MemoryFunctionClassifier;
    import com.glados.backrooms.classification.FunctionalMaterialTable;

---

## CAPA 2 (graph) — 100% COMPLETA

Paquete: src/main/java/com/glados/backrooms/graph/

La capa mas compleja. Son 8 pasos secuenciales de generacion del RoomGraph.
Ver seccion 6 del Documento de Diseno para logica detallada.

### Clases de datos / enums (sin logica compleja):

    WallDirection.java      enum: NORTE, SUR, ESTE, OESTE
    HierarchyLevel.java     enum: PRINCIPAL, SECUNDARIO
    FunctionalRole.java     enum (ya existe en classification/ — REUSAR, no duplicar)
    ZoneId.java             record: par de long redondeado a cuadricula de 192
    Apertura.java           record, seccion 10.10
    AxisAlignedSegment.java record, seccion 10.12
    PlannedElement.java     record, seccion 10.9
    RoomNode.java           record, seccion 10.8
    CorridorEdge.java       record, seccion 10.11
    RoomGraph.java          record, seccion 10.7

### Generadores (logica de los 8 pasos, todos package-private):

    RoomCenterDistributor.java   Paso 1: Poisson-disk simplificado, N entre 5 y 15
    RoomSizeAssigner.java        Paso 2: tamanos desde StyleFingerprint, variacion +-40%
    CollisionResolver.java       Paso 3: repulsion iterativa, max 50 iter, elimina problematicos
    DelaunayTriangulator.java    Paso 4a: triangulacion incremental aproximada
    MinimumSpanningTreeBuilder.java Paso 4b: MST sobre aristas de Delaunay
    ConnectionSelector.java      Paso 5: +35% aristas secundarias, jerarquia, ancho de pasillo
    CorridorRouter.java          Paso 6: routing axis-aligned, intersecciones entre pasillos
    OpeningPlacer.java           Paso 7: posicion exacta de aberturas, restriccion anti-chunk-border
    FunctionalRoleAssigner.java  Paso 8: rol funcional, PlannedElements con posiciones absolutas

### Orquestadores (publicos):

    RoomGraphGenerator.java     secuencia los 8 pasos y produce RoomGraph
    RoomGraphCache.java         FACHADA PUBLICA: ConcurrentHashMap + computeIfAbsent

### Estado actual: 100% COMPLETA — BUILD SUCCESSFUL confirmado

    WallDirection.java          HECHO
    HierarchyLevel.java         HECHO
    ZoneId.java                 HECHO
    Apertura.java               HECHO
    AxisAlignedSegment.java     HECHO
    PlannedElement.java         HECHO
    RoomNode.java               HECHO
    CorridorEdge.java           HECHO
    RoomGraph.java              HECHO
    RoomCenterDistributor.java  HECHO  (Poisson-disk simplificado, N entre 5 y 15)
    RoomSizeAssigner.java       HECHO  (+-40% variacion, pares, 6-20, MutableRoom interno)
    CollisionResolver.java      HECHO  (repulsion, max 50 iter, elimina problematicas)
    DelaunayTriangulator.java   HECHO  (k=3 vecinos mas cercanos por punto, aprox)
    MinimumSpanningTreeBuilder.java HECHO  (Kruskal + Union-Find con path compression)
    ConnectionSelector.java     HECHO  (MST=PRINCIPAL, +35% no-MST=SECUNDARIO)
    CorridorRouter.java         HECHO  (1 o 2 segmentos, codo deterministico)
    OpeningPlacer.java          HECHO  (centrada, anti-chunk-border, margen 2 bloques)
    FunctionalRoleAssigner.java HECHO  (LOBBY/INTERSECCION si >=3 conexiones)
    RoomGraphGenerator.java     HECHO  (orquesta los 8 pasos)
    RoomGraphCache.java         HECHO  (FACHADA, ConcurrentHashMap + computeIfAbsent)

### Dependencias clave para los generadores:
- HashUtil: para toda la aleatoriedad determinista
- GeometryUtil: para interseccion de rectangulos y segmentos
- MemoryAnalysisRepository: para leer StyleFingerprint y RoomPrototype
- District (district/): para semilla, densidadBase y memoria asignada
- MemoryAnalysis / StyleFingerprint (analysis/): para tamanios y elementos funcionales

---

## CORRECCIONES APLICADAS A CAPA 1 Y 2 (pre-capa3, BUILD SUCCESSFUL confirmado)

    TransitionZoneCalculator.java  CORREGIDO: distancia al borde Voronoi = (d2-d1)/2,
                                               formulas [0,16) y [16,32) segun doc 5.4
    ZoneId.java                    CORREGIDO: campos long (no int) segun doc arquitectura
    RoomGraphCache.java            ACTUALIZADO: getOrGenerate(long,long)
    RoomGraphGenerator.java        ACTUALIZADO: cast (int) para bounds
    OpeningPlacer.java             CORREGIDO: ancho = min(corridorWidth, wallLen-4) segun 6.8
    RoomCenterDistributor.java     CORREGIDO: N con variacion +-1 determinista via hash

---

## CAPA 3 (context) — 100% COMPLETA — BUILD SUCCESSFUL confirmado

    context/
      ColumnRole.java               HECHO  enum: ABIERTO, INTERIOR_HABITACION,
                                            PARED_HABITACION, INTERIOR_PASILLO,
                                            PARED_PASILLO, ABERTURA
      ChunkGenerationContext.java   HECHO  contenedor de datos del chunk (seccion 10.13)
                                            arrays 16x16 de rol, room, corridor;
                                            4 arrays de degradacion; listas activas
      DistrictOverlapResolver.java  HECHO  paso 7.1: 4 vertices del chunk, max 2 distritos
      RoomGraphResolver.java        HECHO  paso 7.2: obtiene/genera RoomGraph del cache
      ActiveElementFilter.java      HECHO  paso 7.3: interseccion con margen=1
      RoleMapBuilder.java           HECHO  paso 7.4: prioridad ABIERTO->HABITACION->ABERTURA->PASILLO
      DegradationMapBuilder.java    HECHO  paso 7.5: 4 capas de degradacion, ruido local
      OpeningCollector.java         HECHO  paso 7.6: aberturas activas en el chunk
      ChunkContextBuilder.java      HECHO  FACHADA PUBLICA, orquesta 7.1-7.6

---

## CAPA 4 (placement) — ESPECIFICACION COMPLETA

Paquete: src/main/java/com/glados/backrooms/placement/
Recibe: ChunkGenerationContext (del paquete context)
No depende de: degradation, generation (solo context y, transitivamente, analysis/classification)

### Responsabilidad global
Coloca la arquitectura "perfecta", sin ninguna degradacion. Opera exclusivamente
sobre el chunk propio (set() guarda bounds). El orden de colocacion es fijo e
invariante (doc diseno seccion 8.1):
  paredes habitacion -> paredes pasillo -> limpiar interior pasillo
  -> elementos funcionales -> iluminacion estructural

### Constantes de referencia (heredadas de BackroomsChunkGenerator)
  FLOOR_Y   = 47   (nivel del suelo solido)
  CEILING_Y = 52   (nivel del techo solido)
  Interior util: Y=48 a Y=51 (4 niveles, indices 0-3 en WallColumn)

### Fachada publica
  StructuralPlacer.java  [F]
  - Un unico metodo publico: place(ChunkGenerationContext ctx)
  - Llama en orden a los 5 sub-placers
  - No almacena estado entre invocaciones

### Sub-placers (todos package-private, sin estado entre invocaciones)

#### RoomWallPlacer.java  (doc diseno 8.2)
Entrada: context.activeRooms, context.roleMap, context.roomMap
Para cada RoomNode activo:
  Para cada uno de sus 4 segmentos de pared (norte=minZ, sur=maxZ, este=maxX, oeste=minX):
    Calcular la interseccion del segmento con el chunk actual (rango [minX..maxX] o [minZ..maxZ])
    Para cada posicion a lo largo del segmento que cae dentro del chunk:
      Si roleMap[localX][localZ] == ABERTURA -> dejar aire en Y=48..51
      En caso contrario:
        Resolver WallColumn para esa posicion usando WallPatternMapper
        Colocar blocksByHeight[0..3] en Y=48..51
  Los 2 primeros y 2 ultimos bloques del segmento usan endColumnsLeft/Right del prototipo
  Si la posicion es exactamente una esquina (extremo de DOS segmentos): usar CornerPrototype
  Si openingPrototype.hasFrame && posicion adyacente a ABERTURA: colocar marco

Seleccion de WallPrototype: context.memoryAnalysisById[room.memoryAnalysisId].wallPrototypeFor(WallRole.EXTERIOR)
  -> si null, WallRole.INTERIOR como fallback
  -> si aun null, usar NeutralWallColumn (smooth sandstone en los 4 niveles)

#### WallPatternMapper.java  (helper puro, package-private)
  Entrada: posicion a lo largo del segmento (0-based), WallPrototype, longitud total del segmento
  Logica de proyeccion (doc diseno 8.2):
    Si pos < 2 -> endColumnsLeft.get(pos)       (extremo izquierdo)
    Si pos >= length-2 -> endColumnsRight.get(length-pos-1 apuntando desde el final)
    En otro caso -> columna del rango tileable:
      Si segmento mas largo que tileableRange -> modulo sobre tileableRange (patron se repite)
      Si segmento mas corto que tileableRange -> escalar (columna = tileableStart + pos * tileableLen / segLen)
  Fallback: si el WallPrototype es null o tileableRange esta vacio -> WallColumn neutral

#### CorridorWallPlacer.java  (doc diseno 8.3)
Entrada: context.activeCorridors
Para cada CorridorEdge activo:
  Para cada AxisAlignedSegment del corridor que intersecta el chunk:
    Si axis==X: las paredes son 2 lineas de bloques en Z=wallCoordA y Z=wallCoordB,
                a lo largo de startCoord..endCoord en X
    Si axis==Z: las paredes son 2 lineas de bloques en X=wallCoordA y X=wallCoordB,
                a lo largo de startCoord..endCoord en Z
    Para cada bloque de pared dentro del chunk (verificar bounds localX/localZ):
      Colocar WallColumn del prototipo de PASILLO de la habitacion mas cercana
      (fromRoom o toRoom: elegir el que tenga el centro mas proximo al bloque actual)
      Si no hay prototipo disponible: smooth sandstone en Y=48..51

#### CorridorInteriorCleaner.java  (doc diseno 8.4)
Entrada: context.activeCorridors
Para cada CorridorEdge activo:
  Para cada AxisAlignedSegment que intersecta el chunk:
    Para todas las columnas DENTRO del segmento (entre wallCoordA+1 y wallCoordB-1 en eje perpendicular)
    que caigan dentro del chunk:
      Forzar AIR en Y=48..51
Esto limpia cualquier bloque que haya entrado en el interior del pasillo por
colocacion de paredes de habitaciones adyacentes.

#### FunctionalElementPlacer.java  (doc diseno 8.5)
Entrada: context.activeRooms (cada RoomNode tiene List<PlannedElement> ya con posiciones absolutas)
Para cada RoomNode activo:
  Para cada PlannedElement del nodo:
    Si worldX/worldZ del elemento NO esta dentro del chunk actual -> ignorar (otro chunk lo coloca)
    int wx = element.worldX(), wz = element.worldZ()
    int y = FLOOR_Y + 1 + element.heightFromFloor()  (heightFromFloor es [1,4])
    int localX = wx - chunkMinX, localZ = wz - chunkMinZ
    Verificar que roleMap[localX][localZ] no sea PARED_HABITACION, PARED_PASILLO ni ABERTURA
      -> si lo es: buscar la celda libre mas proxima dentro del INTERIOR_HABITACION de la misma
         habitacion (BFS sobre el roomMap del contexto, max 4 pasos de radio)
      -> si no se encuentra libre: descartar silenciosamente
    Colocar element.blockState() en la posicion resuelta
    Si element.blockEntityData() != null:
      copiar el tag, actualizar "x", "y", "z" con coords mundo absolutas
      llamar chunk.setBlockEntityNbt(tag)

#### LightingPlacer.java  (doc diseno 8.6)
Entrada: context.activeRooms, context.activeCorridors, context.memoryAnalysisById
Para cada RoomNode activo:
  Leer lightingStyle de su StyleFingerprint
  TECHO_PLANO:
    Colocar Sea Lantern (o el bloque de luz de la memoria si disponible) en Y=51
    en posiciones de la habitacion separadas por espaciado N
    N = clamp(4, 8, 4 + (int)(wallComplexity * 4))
    Posiciones: cada N bloques comenzando desde el interior, deterministico via HashUtil
  PUNTUAL:
    Posiciones especificas derivadas del RoomPrototype de LIGHT
    Si no hay prototipo de luz: una unica luz en el centro de la habitacion en Y=51
  PARED:
    Colocar bloques de luz en Y=49 (segundo nivel interior) en posiciones de pared
    del RoomNode (donde roleMap == PARED_HABITACION), cada 6 bloques de separacion
  NINGUNO:
    No colocar ningun bloque de luz en esta habitacion

Para cada CorridorEdge activo:
  Usar el lightingStyle del fromRoom (o toRoom si fromRoom no esta activo)
  TECHO_PLANO o PUNTUAL: luces en Y=51 cada 8 bloques a lo largo del eje del pasillo
  PARED: luces en Y=49 alternando entre wallCoordA+1 y wallCoordB-1 cada 8 bloques
  NINGUNO: sin luces en pasillos

---

## CAPA 5 (degradation) — ESPECIFICACION COMPLETA

Paquete: src/main/java/com/glados/backrooms/degradation/
Recibe: ChunkGenerationContext (del paquete context)
Opera sobre los bloques ya colocados por placement; los lee y los sobreescribe.
No depende de placement directamente: usa los datos de context (roleMap, arrays
de degradacion, activeOpenings).

### Fachada publica
  DegradationPipeline.java  [F]
  - Un unico metodo publico: apply(ChunkGenerationContext ctx)
  - Ejecuta los 5 pasos en orden estricto (doc diseno seccion 9.2):
      1. StructuralDegrader.apply(ctx)
      2. MaterialDegrader.apply(ctx)
      3. AdditiveEffectsApplier.apply(ctx)
      4. FunctionalDegrader.apply(ctx)
      5. ConnectivitySafeguard.apply(ctx)   <- SIEMPRE el ultimo, sin excepcion
  - No almacena estado entre invocaciones

### Umbral variable con ruido local (usado por StructuralDegrader y AdditiveEffectsApplier)
Para evitar huecos en linea recta, el umbral de eliminacion de bloques se perturba
con un ruido local de periodo 4 (diferente al de degradation maps, que es de
periodo 8 o 4 segun el eje). Este ruido es instancia de campo en StructuralDegrader
construida con (worldSeed ^ 0xABCDABCDL, 4, 1).
  threshold(degradation) = degradation + noise * 0.15f - 0.075f
  clamp a [0.05, 0.95] para nunca ser trivialmente falso ni trivialmente cierto.

### StructuralDegrader.java  (eje 1, doc diseno 9.1)
Para cada columna (localX, localZ) del chunk:
  Si roleMap[lx][lz] != PARED_HABITACION && roleMap[lx][lz] != PARED_PASILLO -> skip
  float deg = context.degradationStructural[lx][lz]
  Si deg <= 0.0f -> no se toca nada en esta columna
  Calcular umbralEliminacion:
    A deg=0.0: umbral=1.0 (nunca se elimina)
    A deg=1.0: umbral=0.2 (el 80% de los bloques se eliminan)
    Lineal entre medias: umbral = 1.0f - 0.8f * deg
  Aplicar perturbacion de ruido local: umbral_final = clamp(umbral + noise*0.15f - 0.075f, 0.05, 0.95)
  Para cada nivel Y de 48 a 51:
    Evaluar un hash deterministico: h = HashUtil.hash(worldSeed, wx, Y, wz, "structElim")
    float r = HashUtil.floatFromHash(h)
    Si r > umbral_final -> set(chunk, wx, Y, wz, AIR)
    (nótese: r > umbral = porcentaje que SE ELIMINA sube cuando umbral baja)

### MaterialDegrader.java  (eje 2, doc diseno 9.1)
Para cada columna (localX, localZ) del chunk:
  Si roleMap[lx][lz] != PARED_HABITACION && roleMap[lx][lz] != PARED_PASILLO -> skip
  float deg = context.degradationMaterial[lx][lz]
  Si deg <= 0.05f -> no se sustituye material en esta columna
  Para cada nivel Y de 48 a 51:
    BlockState actual = chunk.getBlockState(pos)
    Si actual.isAir() -> ya fue eliminado por StructuralDegrader, skip
    long h = HashUtil.hash(worldSeed, wx, Y, wz, "matDeg")
    float r = HashUtil.floatFromHash(h)
    Si r > deg -> no sustituir (probabilidad de sustitucion = deg)
    En otro caso:
      Obtener StyleFingerprint del distrito primario del chunk
      Si deg < 0.6f:
        El sustituto es el bloque de acento (dominantAccentBlock())
        Si no hay acento disponible: siguiente bloque primario en la lista
      Si deg >= 0.6f:
        El sustituto puede ser del distrito secundario si existe (30% de probabilidad)
        h2 = HashUtil.hash(worldSeed, wx, Y, wz, "matDegSecondary")
        Si chance(h2, 0.3f) && secondaryMemoryId != null:
          Usar dominantPrimaryBlock() de la memoria secundaria
        Si no: usar dominantAccentBlock() de la primaria o smooth sandstone como ultimo fallback
      set(chunk, wx, Y, wz, sustituto)

### AdditiveEffectsApplier.java  (eje 3, doc diseno 9.1)
Requiere: mismo umbral variable que StructuralDegrader (se instancia un campo NoiseField propio).

#### Efecto 1 — Duplicacion de pared
Para cada columna (localX, localZ) con roleMap == PARED_HABITACION:
  float deg = context.degradationAdditive[lx][lz]
  Si deg < 0.35f -> skip
  long h = HashUtil.hash(worldSeed, wx, wz, "wallDup")
  Si !chance(h, (deg - 0.35f) / 0.65f) -> skip  (probabilidad escala 0->1 entre deg=0.35 y deg=1.0)
  Determinar la direccion perpendicular a la pared:
    Si roleMap del vecino en X es INTERIOR_HABITACION o INTERIOR_PASILLO: dir = +X o -X segun vecino
    Si el vecino en Z: dir = +Z o -Z
  Colocar copias del bloque en posicion perpendicular +2 (la posicion +1 se deja como aire,
  creando "doble pared con hueco de 1 bloque entre medias no transitable").
  Aplicar tambien degradacion estructural sobre la pared duplicada
  (misma logica de umbral pero con degAdditive en vez de degStructural).

#### Efecto 2 — Extension erronea
Para cada segmento de pared en cada RoomNode activo:
  Para cada extremo del segmento que cae dentro del chunk:
    float deg = context.degradationAdditive[lx_extremo][lz_extremo]
    Si deg < 0.5f -> skip
    long h = HashUtil.hash(worldSeed, wx_extremo, wz_extremo, "wallExt")
    Si !chance(h, (deg - 0.5f) * 1.5f) -> skip
    Extension de 1 a 3 bloques (derivado del hash) hacia el interior de la habitacion
    (perpendicular al segmento, en la misma direccion que el interior de la habitacion).
    Para cada bloque de extension:
      Colocar el mismo material que el bloque de pared del extremo
      Si el bloque de destino ya es PARED_HABITACION: detener la extension

#### Efecto 3 — Acumulacion de esquina
Para cada esquina de cada RoomNode activo que cae dentro del chunk:
  Esquinas: (minX,minZ), (minX,maxZ), (maxX,minZ), (maxX,maxZ)
  float deg = context.degradationAdditive[lx_esquina][lz_esquina]
  Si deg < 0.4f -> skip
  long h = HashUtil.hash(worldSeed, wx_esquina, wz_esquina, "cornerAccum")
  Si !chance(h, (deg - 0.4f) / 0.6f) -> skip
  Colocar 1 bloque extra del material de la pared en la posicion del interior
  adyacente a la esquina (el bloque diagonal que queda justo dentro del interior).
  No verificar si hay un PlannedElement: la degradacion gana.

### FunctionalDegrader.java  (eje 4, doc diseno 9.1)
Para cada RoomNode activo:
  Para cada PlannedElement del nodo con posicion dentro del chunk actual:
    float deg = context.degradationFunctional[localX][localZ]
    Si deg <= 0.1f -> no se toca este elemento
    long h = HashUtil.hash(worldSeed, element.worldX(), element.worldZ(), "funcDeg")

    Nivel bajo (deg en (0.1, 0.4]):
      Probabilidad = (deg - 0.1f) / 0.3f de aplicar desplazamiento
      Si aplica: desplazar 1-2 bloques en una direccion (X o Z) determinista desde h
      Destino: si el destino tiene roleMap INTERIOR_HABITACION: colocar ahi
               si no: no desplazar (quedarse en posicion original)

    Nivel medio (deg en (0.4, 0.7]):
      Probabilidad = (deg - 0.4f) / 0.3f de sustituir el tipo de elemento
      Si aplica: elegir una ArchitecturalFunction diferente a la actual con
                 FunctionalMaterialTable (usando el random derivado del hash para
                 la seed del RandomSource)
      La posicion no cambia; solo el bloque colocado

    Nivel alto (deg en (0.7, 1.0]):
      Probabilidad = (deg - 0.7f) / 0.3f de colocacion absurda
      Elegir tipo de colocacion absurda con h:
        (0) Pegado al techo: Y = CEILING_Y - 1 (Y=51)
        (1) Dentro de pared: elegir el bloque de pared mas cercano dentro del roleMap PARED_HABITACION,
            setear AIR en esa pared para acomodar el elemento, luego colocar el elemento
        (2) En interior de pasillo: si hay alguna columna con INTERIOR_PASILLO en el chunk,
            colocar el elemento ahi a Y=48
      Si blockEntityData != null: preservar siempre el tag (solo actualizar x,y,z)

### ConnectivitySafeguard.java  (paso 5 final, doc diseno 9.3)
Entrada: context.activeOpenings
Para cada Apertura en activeOpenings:
  Para cada bloque en el volumen de la abertura:
    wx = apertura.startWorldCoord()..apertura.endWorldCoord()  (en la direccion paralela a la pared)
    fixedCoord = apertura.fixedCoord()
    Para Y = 48 a 51:
      Si WallDirection es NORTE o SUR: posicion = (wx, Y, fixedCoord)
      Si WallDirection es ESTE u OESTE: posicion = (fixedCoord, Y, wx)
      Verificar que la posicion esta dentro del chunk actual
      Si lo esta: forzar set(chunk, x, Y, z, AIR) sin condiciones
Esta salvaguarda es la implementacion fisica de los Invariantes 4 y 5 del doc de diseno.
No tiene ninguna condicion de salto basada en degradacion: siempre fuerza aire.

---

## ESTADO DE IMPLEMENTACION

    placement/
      StructuralPlacer.java         PENDIENTE  fachada, orquesta 5 sub-placers
      WallPatternMapper.java        PENDIENTE  helper puro, proyeccion de WallPrototype
      RoomWallPlacer.java           PENDIENTE  coloca paredes de habitacion con aberturas
      CorridorWallPlacer.java       PENDIENTE  coloca paredes laterales de pasillos
      CorridorInteriorCleaner.java  PENDIENTE  limpia interior de pasillos (aire Y=48..51)
      FunctionalElementPlacer.java  PENDIENTE  coloca PlannedElements, BFS si posicion bloqueada
      LightingPlacer.java           PENDIENTE  iluminacion segun lightingStyle

    degradation/
      DegradationPipeline.java      PENDIENTE  fachada, ejecuta 5 pasos en orden
      StructuralDegrader.java       PENDIENTE  eje 1: huecos en paredes
      MaterialDegrader.java         PENDIENTE  eje 2: sustitucion de materiales
      AdditiveEffectsApplier.java   PENDIENTE  eje 3: duplicacion, extension, esquinas
      FunctionalDegrader.java       PENDIENTE  eje 4: desplazamiento/sustitucion/absurdo
      ConnectivitySafeguard.java    PENDIENTE  paso 5: forzar aire en activeOpenings

---

## INTEGRACION FINAL (generacion) — PENDIENTE

Cuando placement y degradation esten completos, modificar BackroomsChunkGenerator:
  - Inicializar en arranque (ServerLifecycleHooks o equivalente):
      MemoryAnalysisRepository repo = ...  (ya existe NeutralAnalysisProvider)
      DistrictLookup districtLookup = new DistrictLookup(worldSeed)
      RoomGraphCache graphCache = new RoomGraphCache(districtLookup, repo)
      ChunkContextBuilder contextBuilder = new ChunkContextBuilder(worldSeed, districtLookup, graphCache, repo)
      StructuralPlacer placer = new StructuralPlacer()
      DegradationPipeline degradation = new DegradationPipeline(worldSeed)
  - buildSurface nuevo (reemplaza applyRememberedFunctions y el resto):
      ChunkGenerationContext ctx = contextBuilder.build(chunk, region, random)
      placer.place(ctx)
      degradation.apply(ctx)
      Heightmap.primeHeightmaps(chunk, ...)
  - fillFromNoise: SIN CAMBIOS (carveImpossibleHouse no se toca)

---

## ADVERTENCIA CONOCIDA

BackroomsChunkGenerator.java no tiene newline al final del archivo.
No afecta compilacion.
