# Reubicacion pendiente: generation -> classification

Estos tres archivos YA EXISTEN en el proyecto real (paquete
`com.glados.backrooms.generation`, confirmado por su uso sin import
calificado dentro de `BackroomsChunkGenerator.java`):

- `ArchitecturalFunction.java`
- `MemoryFunctionClassifier.java`
- `FunctionalMaterialTable.java`

No fueron subidos en esta conversacion (solo se subieron
`BackroomsChunkGenerator.java`, `MemoryRegion.java` y `MemoryLibrary.java`),
por lo que no tengo su contenido real. El Documento de Arquitectura (seccion
2.2) exige reubicarlos a un paquete `classification` propio, sin alterar su
comportamiento interno, porque tres capas distintas del nuevo diseno los
consumen (`analysis`, `placement`, `degradation`) y mantenerlos dentro de
`generation` invertiria la direccion de dependencia permitida.

Como todo el codigo nuevo que dependo de estas clases las usa unicamente a
traves de su API publica ya confirmada por el uso observado en
`BackroomsChunkGenerator.java`:

    ArchitecturalFunction.{AIR, WALL, FLOOR, CEILING, STORAGE, BED, DOOR,
                           WINDOW, LIGHT, FURNITURE, STAIRS}
    MemoryFunctionClassifier.classify(BlockState) -> ArchitecturalFunction
    FunctionalMaterialTable.stateFor(ArchitecturalFunction, RandomSource) -> BlockState

...el cambio necesario es puramente mecanico y no requiere que yo reescriba
su logica interna (que no he visto y que no debo inventar):

1. Mover los 3 archivos de `src/main/java/com/glados/backrooms/generation/`
   a `src/main/java/com/glados/backrooms/classification/`.
2. Cambiar su declaracion de paquete:
   `package com.glados.backrooms.generation;` -> `package com.glados.backrooms.classification;`
3. En `BackroomsChunkGenerator.java` (y en cualquier otro archivo de
   `generation` que los use sin import porque comparte paquete), agregar:
   `import com.glados.backrooms.classification.ArchitecturalFunction;`
   `import com.glados.backrooms.classification.MemoryFunctionClassifier;`
   `import com.glados.backrooms.classification.FunctionalMaterialTable;`

Si el enum `ArchitecturalFunction` tiene mas valores que los 11 listados
arriba (los que pude confirmar por uso), no hay ningun problema: todo el
codigo de `analysis` que entrego en esta tanda agrupa/clasifica por el valor
que devuelva `classify()` sin asumir que la lista de valores es exhaustiva.

Si prefieres que yo mismo reconstruya estos 3 archivos desde cero en lugar
de relocar los reales, dimelo explicitamente: lo hare, pero perderiamos
cualquier logica de clasificacion o tablas de materiales ya afinada que
exista en el proyecto real, ya que no tengo forma de conocerla.
