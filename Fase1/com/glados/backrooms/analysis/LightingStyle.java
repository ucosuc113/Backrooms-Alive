package com.glados.backrooms.analysis;

/**
 * Estilo de iluminacion estructural extraido de una memoria
 * (Documento de Diseno, seccion 4.5, parte de {@link StyleFingerprint}).
 */
public enum LightingStyle {
    /** Luces en el bloque del techo. */
    TECHO_PLANO,
    /** Luces individuales dispersas. */
    PUNTUAL,
    /** Luces en paredes a media altura. */
    PARED,
    /** Sin iluminacion estructural. */
    NINGUNO
}
