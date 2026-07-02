package com.glados.backrooms.analysis;

/**
 * Rol de un muro dentro de una memoria analizada (Documento de Diseno,
 * secciones 4.3 y 10.1). El Documento de Arquitectura no le asigna paquete
 * explicito porque solo lo usan {@code MemoryAnalysis}/{@code WallPrototype}
 * (producidos por {@code analysis}) y, mas arriba en la cadena de
 * dependencias, {@code placement} (al elegir que prototipo de muro
 * proyectar). Vive en {@code analysis} porque ahi es donde se produce y
 * porque ninguna capa por debajo de {@code analysis} lo necesita.
 */
public enum WallRole {
    EXTERIOR,
    INTERIOR,
    PASILLO
}
