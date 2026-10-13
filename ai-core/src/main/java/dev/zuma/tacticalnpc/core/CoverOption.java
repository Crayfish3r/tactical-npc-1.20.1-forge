package dev.zuma.tacticalnpc.core;
/** Server-checked standing position, with an optional exposed firing position. */
public record CoverOption(Vector3 position,Vector3 peek,long expires) {}
