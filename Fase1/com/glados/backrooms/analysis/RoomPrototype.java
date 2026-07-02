package com.glados.backrooms.analysis;

import com.glados.backrooms.classification.FunctionalRole;
import java.util.List;

/**
 * Conjunto de elementos funcionales tipicos de un rol de habitacion, segun
 * la memoria de origen (Documento de Diseno, secciones 4.4 y 10.5).
 * {@code MemoryAnalysis} guarda uno de estos por cada {@link FunctionalRole}
 * detectado en la memoria.
 */
public record RoomPrototype(FunctionalRole functionalRole, List<PrototypeElement> elements) {

    public RoomPrototype {
        elements = List.copyOf(elements);
    }
}
