package dev.autostock.core;
import java.util.*;
public record RegionBinding(UUID request,String dimension,Map<RegionRole,List<RegionBox>> regions,Map<RegionRole,Integer> containers,long emptyBoxes,int emptySlots,String error){}