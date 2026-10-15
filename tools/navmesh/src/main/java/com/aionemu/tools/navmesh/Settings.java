package com.aionemu.tools.navmesh;

import org.recast4j.recast.*;
import org.recast4j.recast.RecastConstants.PartitionType;

/** Metres, Aion ground actor dimensions; independent of population and live CPU budget. */
public record Settings(float cell, float cellHeight, int tileCells, float height, float radius,
                       float climb, float slope) {
    public static final Settings DEFAULT = new Settings(.25f, .1f, 256, 1.8f, .3f, .4f, 45);
    public Settings {
        if (!Float.isFinite(cell) || cell < .1f || cell > 1 || !Float.isFinite(cellHeight)
                || cellHeight < .05f || cellHeight > .25f || tileCells < 32 || tileCells > 512
                || !Float.isFinite(height) || height < 1.5 || height > 4
                || !Float.isFinite(radius) || radius < .3 || radius > 2
                || !Float.isFinite(climb) || climb < 0 || climb > .5
                || !Float.isFinite(slope) || slope <= 0 || slope > 45)
            throw new IllegalArgumentException("Invalid ground mesh dimensions");
    }
    public float tileMetres() { return cell * tileCells; }
    public float borderMetres() { return RecastConfig.calcBorder(radius, cell) * cell; }
    public RecastConfig recast() {
        return new RecastConfig(true, tileCells, tileCells, RecastConfig.calcBorder(radius, cell),
                PartitionType.WATERSHED, cell, cellHeight, slope, true, true, true,
                height, radius, climb, 0, 16, 12, 1.3f, 6, true, 6, 1, new AreaModification(1));
    }
}
