package com.glados.backrooms.analysis;

import java.util.List;
import java.util.Map;

/**
 * Implementa el umbral de calidad de la seccion 4.7 del Documento de Diseno:
 * una memoria es usable como primaria solo si produjo al menos un
 * {@link WallPrototype} de longitud &gt;= 4 bloques Y al menos una
 * habitacion detectada. Las memorias que no cumplen esto se marcan
 * {@code isUsableAsPrimary = false} y solo se usan como fuente de
 * fragmentos de degradacion, nunca como memoria primaria de un distrito.
 */
public final class QualityThresholdEvaluator {

    private static final int MINIMUM_WALL_LENGTH = 4;

    private QualityThresholdEvaluator() {
    }

    public static boolean isUsableAsPrimary(Map<WallRole, WallPrototype> wallPrototypes,
            List<InteriorVolumeDetector.DetectedVolume> detectedVolumes) {
        boolean hasUsableWall = wallPrototypes.values().stream()
                .anyMatch(prototype -> prototype.referenceLength() >= MINIMUM_WALL_LENGTH);
        boolean hasRoom = detectedVolumes.stream()
                .anyMatch(volume -> volume.shape() == InteriorVolumeDetector.VolumeShape.ROOM);
        return hasUsableWall && hasRoom;
    }
}
