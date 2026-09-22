package dev.autostock.client;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.GuiBase;

final class Schematics {
    static LitematicaSchematic selected;
    static java.util.List<LitematicaSchematic> loaded(){
        var result=new java.util.LinkedHashSet<LitematicaSchematic>(fi.dy.masa.litematica.data.SchematicHolder.getInstance().getAllSchematics());
        for(var placement:fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager().getAllSchematicsPlacements())if(placement.getSchematic()!=null)result.add(placement.getSchematic());
        return result.stream().sorted(java.util.Comparator.comparing(s->s.getMetadata().getName())).toList();
    }    static void browse(GuiBase parent){GuiBase.openGui(new fi.dy.masa.litematica.gui.GuiSchematicLoad().setParent(parent));}
    static void materials(LitematicaSchematic schematic,GuiBase parent){
        var list=new fi.dy.masa.litematica.materials.MaterialListSchematic(schematic,true);list.reCreateMaterialList();
        fi.dy.masa.litematica.data.DataManager.setMaterialList(list);GuiBase.openGui(new fi.dy.masa.litematica.gui.GuiMaterialList(list).setParent(parent));
    }
    static void reload(LitematicaSchematic schematic){
        var file=SchematicCompat.file(schematic);if(file==null||!java.nio.file.Files.isRegularFile(file))throw new IllegalArgumentException("原理图没有可重载的源文件");
        if(SchematicCompat.load(file)==null)throw new IllegalArgumentException("源文件无效，未重载");
        if(!schematic.readFromFile())throw new IllegalArgumentException("重载失败，请检查原文件");
        fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(schematic);LitematicaResult.clear();
    }
    static void unload(LitematicaSchematic schematic){
        if(!fi.dy.masa.litematica.data.SchematicHolder.getInstance().removeSchematic(schematic))throw new IllegalArgumentException("原理图已卸载");LitematicaResult.clear();
    }
}
