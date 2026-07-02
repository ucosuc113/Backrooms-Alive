package com.glados.backrooms.analysis;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Representacion visual de una abertura detectada en una memoria
 * (Documento de Diseno, secciones 4.3 y 10.2): si tiene marco, de que
 * material, y si hay dintel en el bloque superior. La *posicion* de cada
 * abertura concreta en el mundo no vive aqui: eso lo calcula
 * {@code graph.OpeningPlacer} para cada {@code Apertura} del grafo de
 * habitaciones (Documento de Diseno, seccion 6.8). Esta clase solo describe
 * "como se ve" una abertura tipica de esta memoria.
 */
public record OpeningPrototype(boolean hasFrame, BlockState frameMaterial, boolean hasLintel) {

    public OpeningPrototype {
        if (!hasFrame && (frameMaterial != null || hasLintel)) {
            throw new IllegalArgumentException(
                    "Si hasFrame es false, frameMaterial debe ser null y hasLintel debe ser false (abertura = aire desnudo).");
        }
    }

    /** Caso 10.1/4.3: si {@code openingPrototype} es null en {@code MemoryAnalysis}, "las aberturas son simplemente aire". */
    public static OpeningPrototype bare() {
        return new OpeningPrototype(false, null, false);
    }
}
