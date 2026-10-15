package com.aionemu.tools.navmesh;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Restrict decorative far-away meshes to native allocated map regions, not guessed coordinates. */
final class WorldBounds {
    final SortedMap<Integer,Integer> sizes = new TreeMap<>();
    final String fingerprint;
    final int region;
    WorldBounds(Path geo) throws Exception {
        Path data = geo.getParent(), xml = data.resolve("static_data/world_maps.xml");
        Path config = data.getParent().resolve("config");
        Properties p = new Properties();
        for (Path file : List.of(config.resolve("main/world.properties"), config.resolve("mygs.properties")))
            if (Files.isRegularFile(file)) try (var in=Files.newInputStream(file)) { p.load(in); }
        region=Integer.parseInt(p.getProperty("gameserver.world.region.size", "128").trim());
        if(region<1 || region>4096) throw new IOException("Invalid native world region size");
        String before=Artifacts.hash(xml);
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var document=factory.newDocumentBuilder().parse(xml.toFile());
        var maps=document.getElementsByTagName("map");
        for(int i=0;i<maps.getLength();i++) {
            var map=(Element)maps.item(i);
            int id=Integer.parseInt(map.getAttribute("id")), size=Integer.parseInt(map.getAttribute("world_size"));
            if(id<1 || size<0 || size>65536 || sizes.putIfAbsent(id,size)!=null) throw new IOException("Invalid native map dimensions");
        }
        if(!before.equals(Artifacts.hash(xml))) throw new IOException("World templates changed while loading");
        fingerprint=before+":region="+region;
    }
    float maximum(int map) throws IOException {
        Integer size=sizes.get(map);
        if(size==null) throw new IOException("Geometry map absent from native world templates: "+map);
        // WorldMap2DInstance/3DInstance allocate x/y regions at 0, region, ... <= world_size.
        return ((size/region)+1)*region;
    }
    float[] min(AionGeometry.MapGeometry map) { var v=map.min(); v[0]=Math.max(0,v[0]); v[2]=Math.max(0,v[2]); return v; }
    float[] max(int id,AionGeometry.MapGeometry map) throws IOException {
        var v=map.max(); float edge=maximum(id); v[0]=Math.min(edge,v[0]); v[2]=Math.min(edge,v[2]); return v;
    }
}
