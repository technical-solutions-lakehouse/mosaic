package com.databricks.labs.mosaic.expressions.raster

import com.databricks.labs.mosaic.core.raster.operator.retile.BalancedSubdivision
import com.databricks.labs.mosaic.core.types.model.MosaicRasterTile
import com.databricks.labs.mosaic.expressions.base.{GenericExpressionFactory, WithExpressionInfo}
import com.databricks.labs.mosaic.expressions.raster.base.RasterGeneratorExpression
import com.databricks.labs.mosaic.functions.MosaicExpressionConfig
import org.apache.spark.sql.catalyst.analysis.FunctionRegistry.FunctionBuilder
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.expressions.{Expression, NullIntolerant}

/**
  * Returns a set of new rasters with the specified tile size (tileWidth x
  * tileHeight).
  */
case class RST_Subdivide(
    rasterExpr: Expression,
    sizeInMB: Expression,
    expressionConfig: MosaicExpressionConfig
) extends RasterGeneratorExpression[RST_Subdivide](rasterExpr, expressionConfig)
      with NullIntolerant
      with CodegenFallback {

    /**
      * Returns a set of new rasters with the specified tile size (tileWidth x
      * tileHeight). Rasters are disposed in super class.
      */
    override def rasterGenerator(tile: MosaicRasterTile): Seq[MosaicRasterTile] = {
        val targetSize = sizeInMB.eval().asInstanceOf[Int]
        BalancedSubdivision.splitRaster(tile, targetSize)
    }

    override def children: Seq[Expression] = Seq(rasterExpr, sizeInMB)

}

/** Expression info required for the expression registration for spark SQL. */
object RST_Subdivide extends WithExpressionInfo {

    override def name: String = "rst_subdivide"

    override def usage: String =
        """
          |_FUNC_(expr1) - Returns a set of new rasters with same aspect ratio that are not larger than the threshold memory footprint.
          |""".stripMargin

    override def example: String =
        """
          |    Examples:
          |      > SELECT _FUNC_(a, b);
          |        /path/to/raster_tile_1.tif
          |        /path/to/raster_tile_2.tif
          |        /path/to/raster_tile_3.tif
          |        ...
          |  """.stripMargin

    override def builder(expressionConfig: MosaicExpressionConfig): FunctionBuilder = {
        GenericExpressionFactory.getBaseBuilder[RST_Subdivide](2, expressionConfig)
    }

}