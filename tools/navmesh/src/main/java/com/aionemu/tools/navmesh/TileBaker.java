package com.aionemu.tools.navmesh;

import java.util.Arrays;
import org.recast4j.detour.*;
import org.recast4j.recast.*;
import org.recast4j.recast.geom.SimpleInputGeomProvider;

/** Pure offline triangle-to-Detour conversion. Never boots the game or mutates geometry. */
public final class TileBaker {
    private TileBaker() {}
    public static MeshData build(AionGeometry.Triangles floors, AionGeometry.Triangles blockers, Settings settings,
                                 float[] mapMin, float[] mapMax, int tileX, int tileY) {
        var clipped=DomainClip.clip(floors.vertices(), floors.indices(), mapMin, mapMax);
        if (clipped.indices().length == 0) return null;
        var blocking=DomainClip.clip(blockers.vertices(), blockers.indices(), mapMin, mapMax);
        var geometry = new SimpleInputGeomProvider(clipped.vertices(), clipped.indices());
        var config = new RecastBuilderConfig(settings.recast(), mapMin, mapMax, tileX, tileY);
        // RecastBuilder's own voxelizer can only rate triangles by slope, so a blocker's flat top would count as floor.
        var recast = settings.recast();
        var telemetry = new Telemetry();
        var solid = new Heightfield(config.width, config.height, config.bmin, config.bmax, recast.cs, recast.ch, recast.borderSize);
        int count = clipped.indices().length / 3;
        RecastRasterization.rasterizeTriangles(solid, clipped.vertices(), clipped.indices(),
                Recast.markWalkableTriangles(telemetry, recast.walkableSlopeAngle, clipped.vertices(), clipped.indices(), count, recast.walkableAreaMod),
                count, recast.walkableClimb, telemetry);
        int blocked = blocking.indices().length / 3;
        if (blocked > 0) RecastRasterization.rasterizeTriangles(solid, blocking.vertices(), blocking.indices(), new int[blocked], blocked, recast.walkableClimb, telemetry);
        var result = new RecastBuilder().build(tileX, tileY, geometry, recast, solid, telemetry);
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
