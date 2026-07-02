# Documento de Diseño Técnico — Rediseño del Sistema de Generación de Backrooms

**Versión:** 1.0  
**Estado:** Listo para implementación  
**Destinatario:** Desarrollador/Claude implementador  

---

## Índice

1. Diagnóstico del sistema actual
2. Filosofía del nuevo sistema
3. Visión general de la arquitectura
4. Capa 0 — Pre-análisis de memorias
5. Capa 1 — Estructura del mundo
6. Capa 2 — El grafo de habitaciones
7. Capa 3 — Contexto de chunk
8. Capa 4 — Colocación estructural
9. Capa 5 — Sistema de degradación
10. Estructuras de datos de referencia
11. Flujo completo de generación
12. Invariantes y salvaguardas
13. Casos límite

---

## 1. Diagnóstico del sistema actual

Antes de diseñar la solución, es necesario entender con precisión por qué el sistema actual produce resultados insatisfactorios. No es un problema de bugs — el código está bien corregido. Es un problema de concepto arquitectónico.

### 1.1 El problema raíz: la escala única

El sistema actual opera exclusivamente en la escala de bloque. No existe ninguna representación intermedia entre "este chunk existe" y "qué bloque va en (x, y, z)". Sin una escala intermedia que represente habitaciones, pasillos y conexiones, es imposible que el resultado parezca un edificio. Un edificio es una jerarquía de intenciones: planta → salas → paredes → bloques. El sistema actual salta directamente de chunk a bloques.

### 1.2 Aislamiento total entre chunks

Cada chunk genera su contenido de forma completamente independiente. No hay ningún mecanismo que permita que el chunk (2, 0) sepa que el chunk (1, 0) tiene una habitación cuya pared debería continuar en su territorio. El resultado es que cada 16 bloques, la arquitectura reinicia desde cero. Esto es exactamente lo contrario a la sensación de recorrer un edificio continuo.

### 1.3 Transferencia geométrica en lugar de transferencia de estilo

El sistema intenta escalar y proyectar la geometría exacta de la memoria NBT dentro del chunk. Esto es fundamentalmente incorrecto. Una memoria representa un edificio que podría tener 40 metros de largo comprimido en 16 bloques, o una estructura de 4 metros de altura que se aplasta en 4 bloques de interior. No se puede transferir geometría de forma útil a esta escala. Lo que sí se puede transferir es el estilo: qué materiales usaba el arquitecto, qué proporción guardaban sus espacios, cómo trataba las esquinas, dónde ponía la luz.

### 1.4 Selección de memoria independiente por chunk

Cada chunk selecciona una memoria aleatoria de forma independiente. Dos chunks adyacentes pueden usar memorias completamente distintas sin ninguna transición. El resultado es una colisión de estilos cada 16 bloques.

### 1.5 Ausencia de jerarquía espacial

No existe ninguna distinción entre "aquí debería haber un pasillo" y "aquí debería haber una habitación amplia" y "aquí debería haber espacio abierto". Todos los chunks se tratan de forma idéntica. Un edificio real tiene esta jerarquía siempre presente.

### 1.6 Degradación como postsistema desconectado

La corrupción/misremembering se aplica a bloques individuales al azar, sin ninguna relación con la estructura del espacio. Un bloque de mobiliario puede corromperse aunque esté en el centro de un pasillo, o no corromperse aunque esté en una posición absurda. La degradación necesita ser un sistema de primer nivel que opere sobre la intención arquitectónica, no sobre bloques individuales.

### 1.7 Lo que sí funciona y debe conservarse

El esquema fillFromNoise (carveImpossibleHouse) → buildSurface es correcto: primero se establece la capa base (suelo + techo + aire interior), luego se añade contenido. El sistema de clasificación funcional (MemoryFunctionClassifier, ArchitecturalFunction, FunctionalMaterialTable) es una base válida. La salvaguarda de escritura dentro del chunk actual es correcta.

---

## 2. Filosofía del nuevo sistema

### 2.1 Las tres escalas

Todo el diseño nuevo gira en torno a operar simultáneamente en tres escalas:

**Escala mundo**: Dónde están los edificios, cómo se agrupan, qué zonas existen entre ellos, qué estilo tiene cada zona. Esta escala determina el mapa general del espacio.

**Escala edificio**: La planta del edificio — qué habitaciones existen, cómo se conectan, cuál es el papel de cada espacio, dónde van los pasillos. Esta escala es un grafo.

**Escala bloque**: Qué bloques específicos forman cada elemento de la escala edificio. Esta es la única escala que el sistema actual tiene.

El sistema actual colapsa las tres escalas en una. El nuevo sistema las mantiene separadas y conectadas en orden: mundo → edificio → bloque.

### 2.2 Las memorias como diccionario de estilo

Una memoria NBT no es una plantilla que insertar. Es evidencia arqueológica de cómo construía alguien. Del análisis de esa memoria se extrae un conocimiento que permite responder preguntas como: "¿qué bloque pongo en el segundo nivel de una pared interior de este estilo?" o "¿qué tan denso es el mobiliario en este tipo de espacio?" Ese conocimiento se usa para construir habitaciones nuevas, de cualquier tamaño, que pertenezcan convincentemente al mismo edificio que la memoria original.

### 2.3 El olvido como campo continuo

La degradación no es ruido por bloque. Es un campo continuo que varía en el mundo, con zonas de mayor y menor degradación. Dentro de una zona degradada, la arquitectura sigue siendo reconocible como arquitectura — simplemente las proporciones son extrañas, los materiales son ligeramente incorrectos, los muebles están en lugares que no tienen sentido. Una zona de degradación extrema tiene paredes fragmentadas y elementos absurdamente colocados. Pero incluso allí, el sistema generó primero una arquitectura completa y luego la degradó — nunca generó caos directamente.

### 2.4 La paradoja del olvido

Un detalle crucial que distingue a las Backrooms de un edificio en ruinas: cuando el arquitecto olvida cómo construir, a veces hace demasiado en lugar de hacer menos. Una pared se duplica a 3 bloques de distancia, sin razón. Un mueble aparece dos veces. Una habitación tiene una pared adicional que no lleva a ningún lado. El olvido genera tanto ausencias como excesos. El sistema de degradación debe modelar ambos.

---

## 3. Visión general de la arquitectura

El nuevo sistema consta de cinco capas que se ejecutan en secuencia. Las capas 0, 1 y 2 producen datos que se cachean. Las capas 3, 4 y 5 se ejecutan por cada chunk.

**Capa 0 — Pre-análisis de memorias**: Ejecutada una vez al cargar cada memoria NBT. Produce un objeto MemoryAnalysis con todo el conocimiento estilístico extraído de esa memoria.

**Capa 1 — Estructura del mundo**: Un sistema de distritos y zonas de edificio que divide el mundo en regiones con carácter propio. Esta capa es completamente procedimental y sin estado — se puede evaluar en cualquier punto (x, z) con solo la semilla del mundo.

**Capa 2 — Grafo de habitaciones**: Para cada zona de edificio activa, un grafo que define exactamente qué habitaciones y pasillos existen, sus bounds en coordenadas del mundo, y cómo se conectan. Se genera una vez por zona y se cachea.

**Capa 3 — Contexto de chunk**: Para un chunk específico, consulta las capas 1 y 2 para construir un ChunkGenerationContext que describe exactamente qué le corresponde a ese chunk.

**Capa 4 — Colocación estructural**: Con el contexto disponible, coloca todos los bloques estructurales y funcionales en el chunk actual.

**Capa 5 — Degradación**: Transforma el resultado perfecto de la capa 4 aplicando el campo de degradación de la capa 1.

---

## 4. Capa 0 — Pre-análisis de memorias

### 4.1 Cuándo y cómo se ejecuta

El análisis de memorias se ejecuta una única vez por memoria, al cargar el servidor, antes de que se genere ningún chunk. MemoryLibrary debe extenderse para que, además de almacenar las MemoryRegion originales, produzca y almacene un MemoryAnalysis por cada una. El análisis se guarda en un mapa inmutable indexado por id de memoria. Después de la construcción, los objetos MemoryAnalysis nunca se modifican, por lo que no requieren sincronización.

### 4.2 Detección de volúmenes interiores

El análisis comienza identificando los volúmenes de aire acotados dentro de la memoria. Un volumen de aire está acotado si, en un radio de búsqueda máximo configurable (8 bloques en todas las direcciones), se encuentra bloques sólidos en todas las direcciones cardinales. Se usa un BFS que expande desde bloques de aire y se detiene cuando alcanza un bloque sólido o el límite de búsqueda.

Los volúmenes resultantes se clasifican por forma. Si la dimensión mínima horizontal del volumen es menor a 2 bloques, se ignora (demasiado estrecho). Si la relación entre las dos dimensiones horizontales supera 1:2.5, el volumen es un pasillo. De lo contrario, es una habitación.

La altura interior del volumen — distancia vertical entre el primer bloque sólido superior y el primer bloque sólido inferior — se registra como referencia de escala.

### 4.3 Análisis de segmentos de pared

Para cada plano vertical de bloques sólidos contiguo (detectado por BFS horizontal en una altura Y dada):

**Patrón de columna**: Se registra, para cada posición a lo largo del muro (columna de pared), qué bloque aparece en cada altura relativa desde el suelo hasta el techo interior. Esto produce una representación columna→lista-de-BlockState que es el núcleo del WallPrototype.

**Aberturas**: Posiciones donde el patrón de columna está interrumpido (el bloque es aire). Para cada abertura se registra su posición relativa a lo largo del muro (como fracción del total), su anchura en bloques, y si abarca toda la altura interior (puerta) o solo parte (ventana).

**Tratamiento de extremos**: Los 2 primeros y 2 últimos bloques de cada muro se registran por separado. Si hay pilares, molduras o cambios de material en los extremos, esto se captura en el CornerPrototype.

### 4.4 Análisis de distribución funcional

Para cada habitación detectada, se registra la posición relativa normalizada (0.0 a 1.0 en X y Z dentro de la habitación) de cada bloque funcional. Se agrupan por ArchitecturalFunction.

Del conjunto de posiciones relativas de cada función se extrae:
- **Tendencia de posición**: ¿Los bloques de esta función tienden a aparecer junto a las paredes? ¿En el centro? ¿En esquinas? Esto se codifica como un vector de peso (centro vs. borde).
- **Densidad típica**: Cuántos elementos de esta función aparecen por unidad de área interior.
- **Altura típica**: En qué nivel Y relativo (1-4 desde el suelo) aparecen.

### 4.5 La huella de estilo (StyleFingerprint)

La StyleFingerprint resume numéricamente el carácter de la memoria:

- **primaryBlocks**: Lista de hasta 3 pares (BlockState, frecuencia relativa). Los bloques más frecuentes en paredes.
- **accentBlocks**: Lista de hasta 3 pares (BlockState, frecuencia relativa). Bloques que aparecen con menos del 15% de frecuencia pero de forma no aleatoria (agrupados o en posiciones repetidas).
- **functionalDensity**: Float. Número de bloques funcionales por metro cuadrado de interior. 
- **wallComplexity**: Float 0-1. Qué tan heterogéneo es el patrón de pared. 0 = un solo bloque repetido, 1 = múltiples materiales con variaciones frecuentes.
- **lightingStyle**: Enumerado: TECHO_PLANO (luces en el bloque del techo), PUNTUAL (luces individuales dispersas), PARED (luces en paredes a media altura), NINGUNO.
- **typicalRoomWidth, typicalRoomDepth**: Dimensiones promedio de las habitaciones detectadas.
- **typicalCorridorWidth**: Anchura promedio de los pasillos detectados (si no hay pasillos, valor por defecto 2).

### 4.6 Prototipos extraídos

El MemoryAnalysis final almacena:

**WallPrototypes**: Uno por cada tipo de muro detectado (exterior habitación, interior habitación, lateral de pasillo). Cada prototipo tiene una longitud de referencia (la longitud real del segmento de muro en la memoria) y su patrón de columnas completo.

**CornerPrototype**: El patrón de los 2×2 bloques horizontales que forman una esquina de habitación, en toda su altura. Puede ser nulo.

**OpeningPrototype**: La representación visual de una abertura: si tiene marco, de qué material es, si hay dintel en el bloque superior de la abertura.

**RoomPrototypes**: Uno por rol funcional detectado. Cada prototipo contiene la lista de elementos funcionales con sus posiciones relativas normalizadas y sus tipos.

### 4.7 Umbral de calidad

Una memoria se considera insuficiente para uso primario si no produce al menos un WallPrototype con longitud ≥4 bloques y al menos una habitación detectada. Las memorias insuficientes se marcan y solo se usan como fuente de fragmentos de degradación, nunca como memoria primaria de un distrito.

---

## 5. Capa 1 — Estructura del mundo

### 5.1 El sistema de distritos

El mundo se divide en distritos usando una cuadrícula de celdas de 192×192 bloques. Cada celda de la cuadrícula produce exactamente un centro de distrito. La posición del centro dentro de la celda se deriva de: hash(worldSeed, cellX, cellZ) mapeado a un offset de ±64 bloques en ambos ejes.

Para determinar a qué distrito pertenece cualquier punto (x, z), se consultan las 9 celdas de la cuadrícula más cercanas, se calcula la distancia del punto a cada uno de sus centros de distrito, y se asigna el punto al distrito más cercano. Esta es la definición estándar de un diagrama de Voronoi discreto.

Nunca se pre-genera el mapa completo de distritos. La función de asignación de distrito se evalúa on-demand para cualquier (x, z).

### 5.2 Propiedades de los distritos

Las propiedades de cada distrito se derivan determinísticamente de: hash(worldSeed, districtCenterX, districtCenterZ).

**memoriaAsignada**: Qué MemoryAnalysis define el estilo de este distrito. Se selecciona usando el hash del distrito como índice en la lista de memorias disponibles. Si hay más de una memoria disponible, algunos distritos pueden tener una memoria secundaria (con probabilidad del 30%), que se mezcla en zonas de alta degradación.

**densidadBase**: Float 0.3-0.9. Controla qué tan densamente se puebla el grafo de habitaciones. Derivado del hash.

**degradacionBase**: Float 0.0-0.8. El nivel mínimo de degradación de toda la zona. Derivado del hash, con distribución sesgada hacia valores bajos (la mayoría de distritos son relativamente coherentes).

**semilla**: Long. La semilla específica del distrito, usada para generar su RoomGraph. Derivada del hash.

### 5.3 El mapa de degradación global

Sobre el sistema de distritos se superpone un campo de degradación continuo generado con ruido Simplex de tres octavas. Este campo es independiente de los distritos — un distrito con degradaciónBase baja puede tener un punto de degradación alta si el campo global es alto en ese punto.

El campo de degradación global tiene un rango efectivo de 0.0 a 0.7. La degradación total en cualquier punto es max(degradaciónBase_del_distrito, campo_global_en_ese_punto). Esto garantiza que los distritos marcados como degradados nunca mejoren por el campo global.

El campo global debe tener las siguientes características: islas de alta degradación de 32-96 bloques de diámetro, separadas por corredores de baja degradación. No debe ser uniforme en ninguna región amplia. La frecuencia base del ruido corresponde a un período de 64 bloques, con octavas que añaden detalle a períodos de 32 y 16.

### 5.4 Transiciones entre distritos

Los límites entre distritos no son muros. Son zonas de transición de 16-32 bloques de ancho donde:
- La arquitectura del distrito A se fragmenta progresivamente
- Aparecen fragmentos aislados sin conexión entre sí
- La degradación es artificialmente elevada (mínimo 0.6 en el centro de la transición)
- No hay habitaciones ni pasillos completos

Implementación: al consultar la degradación de un punto, se verifica adicionalmente cuál es el segundo distrito más cercano y a qué distancia está. Si la distancia al segundo distrito es menor que el umbral de transición (16 bloques), se aplica un boost de degradación proporcional a cuán cerca está el límite.

### 5.5 Áreas de espacio abierto

No todo el mundo es zonas de edificio. Un distrito con densidadBase < 0.4 y degradaciónBase > 0.6 se trata como "espacio vacío" — solo tiene el suelo y el techo, con fragmentos ocasionales de pared que aparecen aislados, sin formar habitaciones. Estos fragmentos son el resultado del sistema de degradación operando sobre un grafo de habitaciones extremadamente simple (una o dos habitaciones pequeñas en la zona).

---

## 6. Capa 2 — El grafo de habitaciones

El RoomGraph es la representación central del nuevo sistema. Es un grafo donde los nodos son habitaciones y los vértices son pasillos. Todo está expresado en coordenadas del mundo, no coordenadas relativas a chunk. Cualquier chunk puede consultar el grafo y encontrar exactamente qué le corresponde.

### 6.1 Triggering de la generación del grafo

El RoomGraph se genera la primera vez que cualquier chunk necesita información de una zona de edificio específica. Un caché thread-safe (ConcurrentHashMap) lo almacena, indexado por identificador de zona. ConcurrentHashMap.computeIfAbsent garantiza que si dos threads intentan generar el mismo grafo simultáneamente, solo uno lo genera y el otro espera.

El identificador de zona es la tupla (centroX_redondeado_a_cuadricula, centroZ_redondeado_a_cuadricula). Esto garantiza que cualquier chunk que consulte la misma zona obtenga exactamente el mismo identificador.

### 6.2 Paso 1: Distribución de centros de habitación

Usando la semilla de la zona, se genera un conjunto de centros de habitación dentro del área del distrito (con margen de 16 bloques en los bordes). La distribución usa una variante simplificada de Poisson-disk sampling: se intenta colocar N centros (N entre 5 y 15, determinista desde la semilla) garantizando que ningún par de centros esté a menos de 20 bloques entre sí. Si no cabe otro punto tras K intentos, se aceptan los que hay.

### 6.3 Paso 2: Asignación de tamaños de habitación

Cada habitación recibe un tamaño (ancho, profundidad) basado en su semilla individual (hash de semilla_zona + índice_habitación). Los tamaños se derivan de la StyleFingerprint de la memoria del distrito:

El tamaño base es (typicalRoomWidth, typicalRoomDepth) de la StyleFingerprint. Se aplica una variación de ±40% en ambas dimensiones, redondeada al par más cercano (siempre dimensiones pares). Los bounds mínimos son 6×6. Los bounds máximos son 20×20. Los tamaños siempre son pares para facilitar la simetría.

### 6.4 Paso 3: Resolución de colisiones

Las habitaciones con sus bounds (centro ± dimensiones/2) pueden solapar después de la asignación inicial. Se ejecuta un proceso de repulsión iterativa: para cada par de habitaciones que solapa (incluyendo un margen de 4 bloques), se aplica un vector de empuje desde el centro de una hacia el centro de la otra. Las habitaciones se mueven hasta que no haya solapamientos. El número máximo de iteraciones es 50 — si tras eso quedan colisiones, se eliminan las habitaciones más problemáticas.

Los bounds finales de cada habitación se fijan en este momento y no cambian después. La posición de cada pared de habitación es exacta en coordenadas del mundo y permanecerá igual para siempre, independientemente de qué chunk la consulte.

### 6.5 Paso 4: Grafo de conectividad

Se construye la triangulación de Delaunay de los centros de habitación (aproximada, no exacta — una implementación simple de triangulación incremental es suficiente). Del grafo de Delaunay:

- Se calcula el árbol de expansión mínima (MST). Todas las aristas del MST se mantienen como conexiones obligatorias. El MST garantiza que cada habitación sea alcanzable desde cualquier otra.
- Del conjunto de aristas de Delaunay que no están en el MST, se selecciona aleatoriamente (con semilla) un 35% adicional. Estas crean bucles y rutas alternativas.

### 6.6 Paso 5: Clasificación jerárquica de conexiones

Las aristas del MST se clasifican como PRINCIPAL. Las aristas adicionales se clasifican como SECUNDARIO. El nivel jerárquico determina el ancho del pasillo:
- PRINCIPAL: 3 o 4 bloques de ancho (determinado por la semilla de la arista)
- SECUNDARIO: 2 o 3 bloques de ancho (determinado por la semilla de la arista)

### 6.7 Paso 6: Routing de pasillos

Para cada arista del grafo, se determina la ruta del pasillo entre las dos habitaciones. Los pasillos son siempre axis-aligned (ortogonales). La ruta se compone de uno o dos segmentos rectos:

- Si las dos habitaciones están relativamente alineadas en X o Z (diferencia < 8 bloques en un eje), el pasillo es un único segmento en el otro eje.
- Si no están alineadas, el pasillo hace un codo: primero va en X, luego en Z (o viceversa, determinado por la semilla).

Cada segmento de pasillo queda definido como un rectángulo en coordenadas del mundo: (minX, minZ, maxX, maxZ). La anchura del pasillo determina cuántos bloques tiene el rectángulo en la dimensión perpendicular al movimiento.

Se detectan intersecciones entre pasillos. Si dos segmentos de pasillo se cruzan, la intersección se amplía para que ambos pasillos pasen limpiamente. Esto crea cruces y esquinas que contribuyen a la sensación de edificio.

### 6.8 Paso 7: Posicionamiento de aberturas

Para cada conexión entre un pasillo y una habitación, se determina exactamente qué columna(s) de la pared de la habitación son la abertura.

La abertura debe estar en la pared que apunta hacia el pasillo (determinada por qué lado de la habitación es más cercano al punto donde el pasillo toca la habitación). La posición a lo largo de esa pared se calcula como el centro del segmento de pared disponible, con la restricción de que los 2 primeros y 2 últimos bloques de la pared no pueden ser abertura.

La anchura de la abertura coincide con el ancho del pasillo, limitado a máximo (longitud_de_pared - 4) bloques.

La posición de abertura se especifica en coordenadas del mundo absolutas. Esta especificación es compartida por todos los chunks que vayan a ver esa abertura.

**Restricción crítica**: Las posiciones de abertura no pueden coincidir con límites de chunk (múltiplos de 16 en X o Z). Si el cálculo produce una abertura que toca un límite de chunk, se desplaza 1 bloque hacia el interior de la habitación. Esta restricción simplifica dramáticamente el problema de sincronización entre chunks.

### 6.9 Paso 8: Asignación de rol funcional

El rol funcional de cada habitación (LOBBY, OFICINA, ALMACEN, DORMITORIO, UTILITARIO, INTERSECCION) se asigna basándose en:
- La densidad relativa de conexiones: habitaciones con 3+ conexiones son LOBBY o INTERSECCION.
- La semilla de la habitación mapeada a los roles disponibles en la StyleFingerprint de la memoria.

Cada rol tiene una lista de PlannedElements derivada del RoomPrototype correspondiente de la memoria. Las posiciones de los PlannedElements se calculan mapeando las posiciones normalizadas del prototipo al espacio interior de la habitación (bounds excluyendo las paredes).

---

## 7. Capa 3 — Contexto de chunk

Cuando buildSurface comienza para un chunk específico, lo primero que hace es construir el ChunkGenerationContext.

### 7.1 Identificación de zonas solapantes

Se evalúa la función de asignación de distrito (Capa 1) para los cuatro vértices del chunk. Si todos apuntan al mismo distrito, el chunk pertenece a un único distrito. Si los vértices apuntan a distritos distintos (chunk en zona de transición), el chunk puede pertenecer a hasta dos distritos.

Se calcula qué porción del chunk pertenece a cada distrito usando el mapa de Voronoi. Esta información se guarda en el contexto.

### 7.2 Obtención de grafos de habitaciones

Para cada distrito solapante, se obtiene (o genera) el RoomGraph usando el caché. Los grafos se guardan en el contexto.

### 7.3 Filtrado por intersección

De cada RoomGraph, se extraen:
- Las RoomNode cuyo bounding box intersecta con el chunk (incluyendo la propia pared, es decir, ampliar el check 1 bloque extra en cada dirección).
- Los CorridorEdge (específicamente, sus AxisAlignedSegments) que intersectan con el chunk.

### 7.4 Cálculo del mapa de roles

Se construye un array 16×16 de enumerados de rol para cada columna (localX, localZ) del chunk. El proceso es:

Inicializar todo como ABIERTO.

Para cada RoomNode activo: marcar las columnas dentro de sus bounds (excluyendo el borde de 1 bloque) como INTERIOR_HABITACION. Marcar las columnas en el borde exacto como PARED_HABITACION.

Para cada Apertura activa: sobreescribir las columnas de la abertura como ABERTURA. Las aberturas sobreescriben las paredes.

Para cada CorridorEdge activo: marcar las columnas en el interior del segmento como INTERIOR_PASILLO. Marcar las columnas en los bordes laterales del segmento como PARED_PASILLO.

Para posiciones donde un pasillo toca una habitación (la abertura), el rol ya es ABERTURA (asignado antes), lo cual es correcto.

### 7.5 Cálculo del mapa de degradación

Para cada columna (localX, localZ) del chunk, se calculan cuatro valores de degradación [0.0, 1.0]:

- **degradaciónEstructural**: Controla huecos en paredes. Combinación del campo global, la degradaciónBase del distrito, y un ruido local de período 8.
- **degradaciónMaterial**: Controla sustitución de materiales. Similar pero con variación más fina (período 4).
- **degradaciónFuncional**: Controla desplazamiento y corrupción de elementos funcionales. Varía por habitación más que por posición.
- **degradaciónAditiva**: Controla los efectos "exceso" del olvido. Correlaciona con degradaciónEstructural pero no es idéntico.

Todos estos se calculan en el momento de construir el contexto y se guardan en el ChunkGenerationContext para uso en las Capas 4 y 5.

### 7.6 Lista de aberturas activas

Se extrae la lista de todas las Aperturas del RoomGraph que intersectan el chunk actual. Esta lista se usa al final de la Capa 5 para la salvaguarda de conectividad.

---

## 8. Capa 4 — Colocación estructural

Con el ChunkGenerationContext disponible, se procede a la colocación de bloques. En esta capa se coloca la arquitectura perfecta, sin degradación. La degradación viene en la Capa 5.

### 8.1 Prioridad de colocación

El orden es siempre: paredes de habitación → paredes de pasillo → interiores de pasillo (limpiar) → elementos funcionales. Los pasos posteriores pueden sobreescribir los anteriores solo en la dirección correcta (los interiores de pasillo limpian lo que hubiera, los elementos funcionales nunca sobreescriben paredes).

### 8.2 Colocación de paredes de habitación

Para cada RoomNode activo en el contexto:

Se iteran los cuatro segmentos de pared de la habitación (norte: minZ constante, sur: maxZ constante, este: maxX constante, oeste: minX constante). Para cada segmento, se calcula su intersección con el chunk actual. Solo se procesa la parte del segmento dentro del chunk.

Para cada posición a lo largo del segmento dentro del chunk, se consulta si esa posición tiene rol ABERTURA. Si lo tiene, se dejan los bloques en Y=48 a Y=51 como aire. Si no, se coloca un bloque en cada nivel de altura (Y=48, 49, 50, 51) según el WallPrototype de la memoria asignada a esa habitación.

El WallPrototype tiene una longitud de referencia. Para mapear la posición actual a la posición en el prototipo, se usa módulo si la pared es más larga que el prototipo (el patrón se repite), o se escala si la pared es más corta. El rango "tileable" del prototipo (columnas centrales, no los extremos) es el que se repite.

Los 2 primeros y 2 últimos bloques del segmento de pared usan las columnas de extremo del prototipo (o CornerPrototype si la posición es exactamente una esquina de la habitación).

### 8.3 Colocación de paredes de pasillo

Para cada CorridorEdge activo en el contexto:

Se iteran sus AxisAlignedSegments que intersectan el chunk. Para un segmento en dirección X, las paredes laterales son dos líneas de bloques a distancias fijas en Z (una a cada lado del centro). Para un segmento en dirección Z, lo análogo en X.

Los bloques de pared lateral del pasillo usan el mismo material que las habitaciones a las que conecta. Si el pasillo conecta habitaciones de diferente material, se usa el material de la habitación más cercana para cada segmento.

### 8.4 Limpieza del interior de pasillos

Después de colocar las paredes de pasillo, se garantiza que el espacio interior del pasillo (entre las paredes, a todas las alturas Y=48-51) sea aire. Si carveImpossibleHouse ya dejó aire, esto no hace nada. Si algún bloque de una habitación vecina entró en el espacio del pasillo por redondeos, se limpia.

### 8.5 Colocación de elementos funcionales

Para cada PlannedElement de cada RoomNode activo:

Se calcula la posición en coordenadas del mundo a partir de la posición normalizada y los bounds de la habitación. El nivel de altura se calcula sumando heightFromFloor al FLOOR_Y+1.

Si la posición central del elemento está dentro del chunk actual (no solo cerca): se coloca el bloque correspondiente. Si el PlannedElement tiene datos de block entity (baúles, cofres), se incluyen como en el sistema actual.

No se coloca ningún elemento funcional en posiciones con rol PARED, ABERTURA, o INTERIOR_PASILLO. Si la posición calculada cae en uno de estos roles, se busca la posición libre más cercana dentro del INTERIOR_HABITACION de la misma habitación.

### 8.6 Iluminación estructural

La iluminación sigue el lightingStyle de la StyleFingerprint:

- TECHO_PLANO: cada N bloques a lo largo del techo de los pasillos y de forma cuasi-regular dentro de las habitaciones, se coloca un bloque de luz en Y=51 (el nivel justo bajo el techo en Y=52). N se determina por la memoria.
- PUNTUAL: luces individuales en posiciones específicas de habitación, derivadas del RoomPrototype.
- PARED: luces a Y=49 (medio interior) en posiciones de pared predefinidas.
- NINGUNO: no se coloca iluminación estructural.

Las posiciones de luz son deterministas desde la semilla del grafo y la posición de cada habitación.

---

## 9. Capa 5 — Sistema de degradación

La degradación toma la arquitectura perfecta de la Capa 4 y la transforma. Opera sobre el resultado ya colocado en el chunk.

### 9.1 Los cinco ejes

**Degradación estructural**: Para cada bloque de pared colocado (rol PARED_HABITACION o PARED_PASILLO), se evalúa degradaciónEstructural en esa posición. Si supera un umbral (variable con ruido local para evitar huecos en línea recta), el bloque se reemplaza por aire. A degradación 0.0, el umbral es 1.0 (nunca se eliminan bloques). A degradación 1.0, el umbral baja a 0.2 (el 80% de los bloques de pared son eliminados).

**Degradación de material**: Para cada bloque de pared que sobrevivió la degradación estructural, con probabilidad proporcional a degradaciónMaterial, se reemplaza el material correcto por uno "adyacente". Los materiales adyacentes se seleccionan de la StyleFingerprint del distrito — si el bloque correcto es el primario, el sustituto es el acento, y viceversa. En degradación alta, el sustituto puede ser el material de otro distrito (la memoria empieza a mezclar estilos que no deberían estar juntos).

**Efectos aditivos de degradación**: Este eje no elimina bloques, los añade.

- *Duplicación de pared*: Si hay una pared en posición P y degradaciónAditiva en P supera un umbral, se coloca un bloque del mismo material en P+2 en la misma dirección perpendicular, creando una "doble pared" fantasma sin espacio entre ambas suficiente para moverse. La pared extra puede tener huecos (se aplica también degradación estructural sobre ella).

- *Extensión errónea*: Si un segmento de pared termina en un punto T (esquina o extremo), con baja probabilidad se extiende 1-3 bloques más allá de T hacia el interior de la habitación, sin conectar con nada.

- *Acumulación de esquina*: En cada esquina de habitación, con probabilidad derivada de degradaciónAditiva, se añade 1 bloque extra en el interior justo junto a la esquina.

**Degradación funcional**: Para cada elemento funcional ya colocado:

- A degradación baja: puede desplazarse 1-2 bloques de su posición planificada.
- A degradación media: puede ser reemplazado por otro tipo de elemento funcional (un baúl se convierte en una cama, una silla en una estantería).
- A degradación alta: puede ser colocado en una posición absurda: en Y=51 (pegado al techo), dentro de un bloque de pared (el bloque de pared se elimina para acomodarlo), o en el interior de un pasillo.

En todos los casos, si el elemento funcional tiene datos de block entity, estos se mantienen (no se corrompe el contenido, solo la posición y el tipo).

**Degradación de conectividad**: Ya no es un eje de degradación activo — la salvaguarda de conectividad al final del proceso garantiza que las aberturas del grafo siempre sean transitables. Sin embargo, los efectos aditivos pueden hacer que aberturas se vean "estrechas" o "incompletas" visualmente sin llegar a bloquearse.

### 9.2 Orden de aplicación

1. Degradación estructural (eliminar bloques de pared)
2. Degradación de material (sustituir materiales)
3. Efectos aditivos (añadir bloques extra)
4. Degradación funcional (corromper elementos)
5. Salvaguarda de conectividad

### 9.3 Salvaguarda de conectividad

Como último paso, antes de los heightmaps, se itera sobre la lista activeOpenings del contexto. Para cada abertura:

Se verifican todos los bloques en el volumen de la abertura (su anchura × su posición en Y=48 a Y=51). Si cualquiera de esos bloques no es aire, se reemplaza por aire. Esta operación usa el método set() del sistema, que verifica bounds antes de escribir.

Esta salvaguarda garantiza que el jugador nunca quede atrapado por efectos de degradación. Es no-negociable.

---

## 10. Estructuras de datos de referencia

Esta sección describe las estructuras de datos necesarias para la implementación. No prescribe los tipos de colección exactos, sino la información que debe almacenarse y sus relaciones.

### 10.1 MemoryAnalysis

Información extraída de una memoria NBT. Inmutable después de construcción.

- **id**: String — nombre del archivo NBT
- **wallPrototypes**: Mapa de WallRole→WallPrototype (roles: EXTERIOR, INTERIOR, PASILLO)
- **cornerPrototype**: CornerPrototype, puede ser null
- **openingPrototype**: OpeningPrototype, puede ser null (si null, las aberturas son simplemente aire)
- **roomPrototypes**: Mapa de FunctionalRole→RoomPrototype
- **styleFingerprint**: StyleFingerprint
- **isUsableAsPrimary**: boolean — si cumple el umbral de calidad

### 10.2 WallPrototype

- **columns**: Lista ordenada de WallColumn, de longitud correspondiente al segmento de pared original
- **tileableStart, tileableEnd**: Índices del rango de columnas centrales que pueden repetirse para paredes más largas
- **endColumnsLeft, endColumnsRight**: Las columnas de extremo (no se repiten)

### 10.3 WallColumn

- **blocks**: Array[4] de BlockState, uno por cada altura interior (índice 0 = Y=48, índice 3 = Y=51)
- **isOpeningColumn**: boolean

### 10.4 StyleFingerprint

- **primaryBlocks**: Lista de pares (BlockState, float frecuencia), máximo 3
- **accentBlocks**: Lista de pares (BlockState, float frecuencia), máximo 3
- **lightingStyle**: Enumerado LightingStyle
- **functionalDensity**: float
- **wallComplexity**: float [0,1]
- **typicalRoomWidth, typicalRoomDepth**: int
- **typicalCorridorWidth**: int

### 10.5 RoomPrototype

- **functionalRole**: FunctionalRole
- **elements**: Lista de PrototypeElement

### 10.6 PrototypeElement

- **normalizedX, normalizedZ**: float [0,1] — posición relativa en el espacio interior
- **function**: ArchitecturalFunction
- **blockState**: BlockState concreto
- **heightFromFloor**: int [1,4]
- **blockEntityData**: CompoundTag, puede ser null

### 10.7 RoomGraph

- **zoneId**: identificador de zona (par de long)
- **semilla**: long
- **bounds**: rectángulo de la zona entera en coords mundo
- **rooms**: Lista de RoomNode
- **corridors**: Lista de CorridorEdge
- **roomById**: Mapa id→RoomNode para lookups rápidos

### 10.8 RoomNode

- **id**: int — índice único en el grafo
- **minX, minZ, maxX, maxZ**: int — bounds exactos en coords mundo
- **wallMinX, wallMinZ, wallMaxX, wallMaxZ**: idénticos a los anteriores (la pared está en el borde del bound)
- **memoryAnalysisId**: String — qué MemoryAnalysis usar para este nodo
- **functionalRole**: FunctionalRole
- **plannedElements**: Lista de PlannedElement
- **openings**: Lista de Apertura
- **degradationLevel**: float — override local del nivel de degradación

### 10.9 PlannedElement

- **worldX, worldZ**: int — posición exacta en coords mundo
- **heightFromFloor**: int
- **function**: ArchitecturalFunction
- **blockState**: BlockState
- **blockEntityData**: CompoundTag, puede ser null

### 10.10 Apertura

- **wallDirection**: Enumerado (NORTE, SUR, ESTE, OESTE)
- **startWorldCoord**: int — primera coordenada del mundo de la abertura (en la dirección paralela a la pared)
- **endWorldCoord**: int — última coordenada
- **fixedCoord**: int — la coordenada perpendicular (la posición exacta de la pared)
- **fromRoomId**: int — habitación que contiene esta abertura
- **toCorridorId o toRoomId**: int — a qué conecta

### 10.11 CorridorEdge

- **id**: int
- **fromRoomId, toRoomId**: int
- **hierarchyLevel**: Enumerado (PRINCIPAL, SECUNDARIO)
- **width**: int (2, 3, o 4)
- **segments**: Lista de AxisAlignedSegment
- **openingAtFrom**: Apertura en la habitación de origen
- **openingAtTo**: Apertura en la habitación de destino

### 10.12 AxisAlignedSegment

- **axis**: Enumerado (X, Z)
- **fixedCoord**: int — la coordenada perpendicular al movimiento (Z si el eje es X, X si el eje es Z)
- **startCoord, endCoord**: int — inicio y fin en la dirección del movimiento
- **wallCoordA, wallCoordB**: int — las dos coords donde van las paredes laterales (una a cada lado)
- **minX, minZ, maxX, maxZ**: derivados de los anteriores, precomputados para lookup rápido

### 10.13 ChunkGenerationContext

- **chunk**: ChunkAccess
- **region**: WorldGenRegion
- **random**: RandomSource
- **roleMap**: Array[16][16] de RoleEnum
- **roomMap**: Array[16][16] de int (id de habitación, -1 si ninguna)
- **corridorMap**: Array[16][16] de int (id de pasillo, -1 si ninguno)
- **degradationStructural**: float[16][16]
- **degradationMaterial**: float[16][16]
- **degradationFunctional**: float[16][16]
- **degradationAdditive**: float[16][16]
- **activeRooms**: Lista de RoomNode que intersectan el chunk
- **activeCorridors**: Lista de CorridorEdge que intersectan el chunk
- **activeOpenings**: Lista de Apertura que intersectan el chunk
- **memoryAnalysisById**: Mapa id→MemoryAnalysis (referencia al mapa global de MemoryLibrary)

---

## 11. Flujo completo de generación

### 11.1 Carga del servidor

Al inicializar el servidor:
1. MemoryLibrary carga todos los archivos NBT de `world/backrooms/memory/`
2. Por cada memoria, ejecuta el análisis de Capa 0 y almacena el MemoryAnalysis
3. Si una memoria no cumple el umbral de calidad, se marca isUsableAsPrimary=false y se loguea
4. El caché de RoomGraphs se inicializa vacío

### 11.2 fillFromNoise (sin cambios respecto al sistema actual)

`carveImpossibleHouse` opera solo sobre el chunk propio. Coloca suelo en FLOOR_Y, techo en CEILING_Y, y aire en Y=48-51. No se toca esto.

### 11.3 buildSurface (nuevo)

**Bloque A — Construir contexto** (Capa 3):
1. Calcular qué distritos (Capa 1) solapan con el chunk
2. Para cada distrito, obtener o generar el RoomGraph (Capa 2)
3. Filtrar habitaciones y pasillos que intersectan el chunk
4. Construir roleMap, roomMap, corridorMap
5. Calcular los cuatro mapas de degradación
6. Compilar activeOpenings

**Bloque B — Colocación estructural** (Capa 4):
1. Iterar activeRooms → colocar paredes de habitación con aberturas correctas
2. Iterar activeCorridors → colocar paredes de pasillo
3. Limpiar interiores de pasillo (aire)
4. Colocar elementos funcionales de todas las activeRooms
5. Colocar iluminación estructural

**Bloque C — Degradación** (Capa 5):
1. Aplicar degradación estructural (huecos en paredes)
2. Aplicar degradación de material (sustituciones)
3. Aplicar efectos aditivos (duplicaciones, extensiones)
4. Aplicar degradación funcional (desplazamiento/corrupción de elementos)
5. Salvaguarda de conectividad (forzar aire en activeOpenings)

**Bloque D — Finalización**:
1. Heightmap.primeHeightmaps (igual que el sistema actual)

### 11.4 Thread safety

Los únicos recursos compartidos entre threads son:
- El mapa de MemoryAnalysis: inmutable, sin sincronización necesaria
- El caché de RoomGraphs: ConcurrentHashMap con computeIfAbsent

Todos los demás datos (ChunkGenerationContext, arrays de degradación, etc.) son locales a cada thread de generación.

---

## 12. Invariantes y salvaguardas

Estas son las propiedades que DEBEN mantenerse en cualquier implementación correcta. Si alguna de ellas falla, el sistema produce resultados incorrectos.

**Invariante 1 — Coordenadas de pared fijas**: Las posiciones de pared de cada RoomNode (minX, minZ, maxX, maxZ) nunca cambian después de la generación del RoomGraph. Cualquier chunk que consulte el mismo RoomGraph obtiene exactamente las mismas coordenadas.

**Invariante 2 — Determinismo total**: Para una semilla de mundo dada, cualquier chunk generado en cualquier orden produce exactamente el mismo resultado. Esto se garantiza no usando ningún estado mutable compartido excepto el caché de RoomGraphs (que es idempotente).

**Invariante 3 — Escritura solo en el chunk actual**: Ningún bloque se escribe fuera del bounding box del chunk actual. El método set() ya implementa esta guardia — debe mantenerse en todas las operaciones.

**Invariante 4 — Aberturas siempre transitables**: Después de la salvaguarda de conectividad, todo bloque en el volumen de cualquier Apertura activa es aire. Esta invariante se verifica y fuerza al final de buildSurface.

**Invariante 5 — Pasillos nunca obstruidos**: El interior de todos los AxisAlignedSegments activos es siempre aire en Y=48-51. La Capa 4 limpia el interior del pasillo después de colocar las paredes, y la salvaguarda de conectividad verifica las aberturas de los extremos.

**Invariante 6 — El RoomGraph no cambia después de generado**: Ningún código de la Capa 4 o 5 puede modificar el RoomGraph. El grafo es de solo lectura después de computeIfAbsent. Los arrays de degradación son locales al ChunkGenerationContext y no modifican el grafo.

---

## 13. Casos límite

### 13.1 Sin memorias cargadas

Si MemoryLibrary no tiene ninguna memoria usable como primaria:
- El sistema de distritos asigna un MemoryAnalysis ficticio "neutral" generado internamente
- El MemoryAnalysis neutral tiene: paredes de smooth sandstone, sin acento, sin prototipos funcionales, wallComplexity=0, lightingStyle=PUNTUAL con un único bloque de luz cada 8 bloques
- El RoomGraph se genera con las mismas reglas pero usando las dimensiones por defecto (10x10 habitaciones, pasillos de 2 bloques)

### 13.2 Chunk en zona de transición entre dos distritos

Si el chunk tiene columnas asignadas a dos distritos diferentes:
- Cada columna usa el MemoryAnalysis del distrito al que pertenece
- Las paredes en la zona de transición usan la degradación elevada calculada en 5.4
- El roleMap puede contener habitaciones de dos grafos distintos en el mismo chunk
- Si dos habitaciones de grafos distintos se solapan (no debería ocurrir si los distritos tienen margen suficiente, pero es posible en bordes), la habitación del distrito dominante (mayor cobertura del chunk) tiene prioridad

### 13.3 Memoria muy pequeña o simple

Una memoria con un único bloque de pared y sin habitaciones detectadas supera el análisis de Capa 0 pero no cumple el umbral de calidad (isUsableAsPrimary=false). Si todos los distritos del mundo apuntan a memorias de este tipo, se aplica el fallback del caso 13.1.

### 13.4 Grafo de habitaciones con una sola habitación

Posible si el proceso de resolución de colisiones elimina todas las habitaciones excepto una, o si la densidadBase es muy baja. Una habitación única sin conexiones produce un espacio válido: una sala cerrada sin aberturas. La salvaguarda de conectividad no falla porque no hay activeOpenings. El jugador puede entrar desde un chunk adyacente que sí tiene pasillos conectados a esta habitación, o desde el espacio abierto fuera de la habitación.

### 13.5 Pasillo que cruza exactamente el borde de chunk

Los AxisAlignedSegments están definidos en coordenadas del mundo. Un segmento que va de X=10 a X=35 cruza el límite de chunk en X=16 y X=32. Chunk (0,0) procesa el segmento en X=10-15 (lo que intersecta con su rango). Chunk (1,0) procesa X=16-31. Chunk (2,0) procesa X=32-35. Cada chunk coloca su porción de paredes laterales y limpia su porción de interior. El resultado es un pasillo continuo sin interrupciones.

### 13.6 Abertura que cae en un borde de chunk

La restricción de posicionamiento de aberturas (sección 6.8) garantiza que ninguna abertura tenga su posición fija (fixedCoord) exactamente en un múltiplo de 16. Sin embargo, startWorldCoord o endWorldCoord de la abertura sí pueden cruzar un límite de chunk. En ese caso, cada chunk limpia (fuerza aire) los bloques del volumen de la abertura que caen en su territorio. La salvaguarda de conectividad de ambos chunks garantiza que el resultado sea transitable.

### 13.7 Degradación tan alta que destruye todas las paredes de una habitación

El sistema no repone paredes destruidas por degradación. La habitación queda como un espacio abierto delimitado solo por el suelo y el techo. Esto es correcto y deseable — en las zonas de degradación extrema, las habitaciones "se disuelven" en el espacio general. Las aberturas siguen siendo garantizadas (la salvaguarda fuerza aire donde ya no hay nada), y el jugador puede atravesar el espacio sin impedimento.

---

*Fin del documento de diseño.*  
*Este documento es suficiente para implementar el sistema completo sin tomar decisiones de diseño adicionales.*  
*Las únicas decisiones delegadas al implementador son de naturaleza puramente técnica: selección de tipos de colección específicos, detalles de la función hash, y optimizaciones de rendimiento que no afectan el comportamiento observable.*