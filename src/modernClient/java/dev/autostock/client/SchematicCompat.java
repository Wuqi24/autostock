package dev.autostock.client;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import java.nio.file.Path;

/** Normalizes Litematica's File-to-Path API transition. */
final class SchematicCompat {
    private SchematicCompat() { }

    static Path file(LitematicaSchematic schematic) {
        //? if <=1.21.4 {
        /*var file = schematic.getFile();
        return file == null ? null : file.toPath();
        *///?} else {
        return schematic.getFile();
        //?}
    }

    static LitematicaSchematic load(Path file) {
        //? if <=1.21.4 {
        /*return LitematicaSchematic.createFromFile(file.getParent().toFile(), file.getFileName().toString());
        *///?} else {
        return LitematicaSchematic.createFromFile(file.getParent(), file.getFileName().toString());
        //?}
    }
}
