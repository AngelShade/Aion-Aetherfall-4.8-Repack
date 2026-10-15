package com.aionemu.tools.navmesh;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Separate offline acceptance runner: does not change the generator's artifact identity. */
public final class VerifyBatch {
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("GEO_DIRECTORY BATCH_DIRECTORY RESULT_FILE");
        Path geo=Path.of(args[0]).toRealPath(), batch=Path.of(args[1]).toRealPath(), result=Path.of(args[2]);
        Properties report=Artifacts.read(batch.resolve("report.properties"));
        var input=new AionGeometry(geo);var worlds=new WorldBounds(geo);
        String models=Artifacts.hash(geo.resolve("models.mesh")),tool=Artifacts.toolHash();
        require(tool.equals(report.getProperty("tool.sha256")),"Batch belongs to a different generator");
        require(worlds.fingerprint.equals(report.getProperty("world.bounds")),"Native world bounds changed");
        require(Integer.toString(input.mapIds().size()).equals(report.getProperty("maps.discovered")),"Batch does not cover all discovered maps");
        // Reject an unfinished batch before loading any maps, rather than reporting a partial pass.
        for(int id:input.mapIds())require("BUILT_REQUIRES_RUNTIME_VALIDATION".equals(report.getProperty("map."+id+".status")),"Map incomplete/failed: "+id);
        for(var entry:worlds.sizes.entrySet())if(!input.mapIds().contains(entry.getKey()))
            require(entry.getValue()==0 && "NO_GEOMETRY_ZERO_SIZE_NATIVE_MAP".equals(report.getProperty("map."+entry.getKey()+".status")),"Unaccounted native map "+entry.getKey());
        Properties proof=new Properties();int failed=0,maps=0,tiles=0;long bytes=0;
        proof.setProperty("tool.sha256",tool);proof.setProperty("world.bounds",worlds.fingerprint);
        for(int id:input.mapIds()){
            long began=System.nanoTime();String key="map."+id;
            try{
                String stamp=input.fingerprint(id,models);
                String fingerprint=Artifacts.hashText(tool+Settings.DEFAULT+stamp+worlds.fingerprint);
                Path dir=batch.resolve(Integer.toString(id)).resolve(fingerprint);
                require(dir.toRealPath().equals(batch.resolve(report.getProperty(key+".directory")).toRealPath()),"Wrong map revision "+id);
                Properties manifest=Artifacts.read(dir.resolve("map.properties"));
                require(fingerprint.equals(manifest.getProperty("fingerprint")) && stamp.equals(manifest.getProperty("input"))
                        && tool.equals(manifest.getProperty("tool.sha256")) && worlds.fingerprint.equals(manifest.getProperty("world.bounds"))
                        && Settings.DEFAULT.toString().equals(manifest.getProperty("settings")),"Map provenance changed "+id);
                int expected=Math.multiplyExact(Integer.parseInt(manifest.getProperty("tiles.x")),Integer.parseInt(manifest.getProperty("tiles.y")));
                require(Integer.toString(expected).equals(manifest.getProperty("tiles.processed")),"Map receipt count mismatch "+id);
                var mesh=Inspect.load(dir);var structure=VerifyMap.check(mesh);int nonempty=mesh.getTileCount();long mapBytes=0;
                try(var files=Files.newDirectoryStream(dir,"*.navtile")){for(Path file:files)mapBytes+=Files.size(file);}
                proof.setProperty(key+".status","VERIFIED_OFFLINE");proof.setProperty(key+".tiles",Integer.toString(expected));
                proof.setProperty(key+".nonempty",Integer.toString(nonempty));proof.setProperty(key+".encoded.bytes",Long.toString(mapBytes));
                proof.setProperty(key+".polygons",Integer.toString(structure.polygons()));proof.setProperty(key+".crossTileLinks",Integer.toString(structure.crossTileLinks()));
                proof.setProperty(key+".manifest.sha256",Artifacts.hash(dir.resolve("map.properties")));
                maps++;tiles+=nonempty;bytes+=mapBytes;
                System.out.println(id+" verified "+nonempty+" nonempty / "+expected+" tile receipts; "+mapBytes+" bytes");
            }catch(Exception e){failed++;proof.setProperty(key+".status","FAILED: "+e);System.err.println(id+" FAILED: "+e);}
            proof.setProperty(key+".milliseconds",Long.toString((System.nanoTime()-began)/1_000_000));
            proof.setProperty("status","VERIFYING");Artifacts.store(result,proof);
        }
        require(models.equals(Artifacts.hash(geo.resolve("models.mesh"))),"Models changed during verification");
        require(worlds.fingerprint.equals(new WorldBounds(geo).fingerprint),"World bounds changed during verification");
        proof.setProperty("maps.verified",Integer.toString(maps));proof.setProperty("maps.failed",Integer.toString(failed));
        proof.setProperty("tiles.nonempty",Integer.toString(tiles));proof.setProperty("encoded.bytes",Long.toString(bytes));
        proof.setProperty("status",failed==0?"VERIFIED_OFFLINE_NOT_RUNTIME_ACCEPTANCE":"FAILED");Artifacts.store(result,proof);
        System.out.println("Verified="+maps+", failed="+failed+", nonempty tiles="+tiles+", bytes="+bytes);
        if(failed!=0)System.exit(1);
    }
    private static void require(boolean valid,String why)throws IOException{if(!valid)throw new IOException(why);}
}
