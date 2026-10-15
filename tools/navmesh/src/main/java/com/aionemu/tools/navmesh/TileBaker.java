package com.aionemu.tools.navmesh;

import java.util.Arrays;
import org.recast4j.detour.*;
import org.recast4j.recast.*;
import org.recast4j.recast.geom.SimpleInputGeomProvider;

/** Pure offline triangle-to-Detour conversion. Never boots the game or mutates geometry. */
public final class TileBaker {
    private TileBaker() {}
    public static MeshData build(float[] vertices, int[] triangles, Settings settings,
                                 float[] mapMin, float[] mapMax, int tileX, int tileY) {
        var clipped=DomainClip.clip(vertices, triangles, mapMin, mapMax);
        if (clipped.indices().length == 0) return null;
        var geometry = new SimpleInputGeomProvider(clipped.vertices(), clipped.indices());
        var config = new RecastBuilderConfig(settings.recast(), mapMin, mapMax, tileX, tileY);
        var result = new RecastBuilder().build(geometry, config);
        var poly = result.getMesh();
        var detail = result.getMeshDetail();
        PolygonCleanup.removeDegenerate(poly, detail);
        if (poly.npolys == 0) return null;
        Arrays.fill(poly.flags, 1);
        var params = new NavMeshDataCreateParams();
        params.verts = poly.verts; params.vertCount = poly.nverts;
        params.polys = poly.polys; params.polyAreas = poly.areas; params.polyFlags = poly.flags;
        params.polyCount = poly.npolys; params.nvp = poly.nvp;
        params.detailMeshes = detail.meshes; params.detailVerts = detail.verts;
        params.detailVertsCount = detail.nverts; params.detailTris = detail.tris;
        params.detailTriCount = detail.ntris;
        params.walkableHeight = settings.height(); params.walkableRadius = settings.radius();
        params.walkableClimb = settings.climb(); params.bmin = poly.bmin; params.bmax = poly.bmax;
        params.cs = settings.cell(); params.ch = settings.cellHeight(); params.buildBvTree = true;
        params.tileX = tileX; params.tileZ = tileY;
        return NavMeshBuilder.createNavMeshData(params);
    }
}
