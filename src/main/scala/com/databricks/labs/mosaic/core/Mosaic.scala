package com.databricks.labs.mosaic.core

import scala.annotation.tailrec
import scala.collection.JavaConverters.asScalaBufferConverter

import com.databricks.labs.mosaic.core.geometry.MosaicGeometry
import com.databricks.labs.mosaic.core.geometry.api.GeometryAPI
import com.databricks.labs.mosaic.core.geometry.linestring.MosaicLineString
import com.databricks.labs.mosaic.core.geometry.multilinestring.MosaicMultiLineString
import com.databricks.labs.mosaic.core.index.IndexSystem
import com.databricks.labs.mosaic.core.types.model.{GeometryTypeEnum, MosaicChip}
import com.databricks.labs.mosaic.core.types.model.GeometryTypeEnum.{LINESTRING, MULTILINESTRING}

/**
  * Single abstracted logic for mosaic fill via [[IndexSystem]]. [[IndexSystem]]
  * is in charge of implementing the individual steps of the logic.
  */
object Mosaic {

    def mosaicFill(geometry: MosaicGeometry, resolution: Int, indexSystem: IndexSystem, geometryAPI: GeometryAPI): Seq[MosaicChip] = {

        val radius = indexSystem.getBufferRadius(geometry, resolution, geometryAPI)

        // do not modify the radius
        val carvedGeometry = geometry.buffer(-radius)
        // add 1% to the radius to ensure union of carved and border geometries does not have holes inside the original geometry areas
        val borderGeometry =
            if (carvedGeometry.isEmpty) {
                geometry.buffer(radius * 1.01).simplify(0.01 * radius)
            } else {
                geometry.boundary.buffer(radius * 1.01).simplify(0.01 * radius)
            }

        val coreIndices = indexSystem.polyfill(carvedGeometry, resolution)
        val borderIndices = indexSystem.polyfill(borderGeometry, resolution)

        val coreChips = indexSystem.getCoreChips(coreIndices)
        val borderChips = indexSystem.getBorderChips(geometry, borderIndices, geometryAPI)

        coreChips ++ borderChips
    }

    def lineFill(geometry: MosaicGeometry, resolution: Int, indexSystem: IndexSystem, geometryAPI: GeometryAPI): Seq[MosaicChip] = {
        GeometryTypeEnum.fromString(geometry.getGeometryType) match {
            case LINESTRING      => lineDecompose(geometry.asInstanceOf[MosaicLineString], resolution, indexSystem, geometryAPI)
            case MULTILINESTRING =>
                val multiLine = geometry.asInstanceOf[MosaicMultiLineString]
                multiLine.flatten.flatMap(line => lineDecompose(line.asInstanceOf[MosaicLineString], resolution, indexSystem, geometryAPI))
        }
    }

    private def lineDecompose(
        line: MosaicLineString,
        resolution: Int,
        indexSystem: IndexSystem,
        geometryAPI: GeometryAPI
    ): Seq[MosaicChip] = {
        val start = line.getBoundaryPoints.head
        val startIndex = indexSystem.pointToIndex(start.getX, start.getY, resolution)

        @tailrec
        def traverseLine(
            line: MosaicLineString,
            queue: Seq[java.lang.Long],
            traversed: Set[java.lang.Long],
            chips: Seq[MosaicChip]
        ): Seq[MosaicChip] = {
            val newTraversed = traversed ++ queue
            val (newQueue, newChips) = queue.foldLeft(
              (Seq.empty[java.lang.Long], chips)
            )((accumulator: (Seq[java.lang.Long], Seq[MosaicChip]), current: java.lang.Long) => {
                val indexGeom = indexSystem.indexToGeometry(current, geometryAPI)
                val lineSegment = line.intersection(indexGeom)
                if (!lineSegment.isEmpty) {
                    val chip = MosaicChip(isCore = false, current, lineSegment)
                    val kRing = indexSystem.kRing(current, 1)
                    val toQueue = kRing.asScala.filterNot(newTraversed.contains)
                    (toQueue, accumulator._2 ++ Seq(chip))
                } else {
                    accumulator
                }
            })
            if (newQueue.isEmpty) {
                newChips
            } else {
                traverseLine(line, newQueue, newTraversed, newChips)
            }
        }

        val result = traverseLine(line, Seq(startIndex), Set.empty[java.lang.Long], Seq.empty[MosaicChip])
        result
    }

}