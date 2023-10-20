package com.databricks.labs.mosaic.expressions.raster.base

import com.databricks.labs.mosaic.core.index.IndexSystemFactory
import com.databricks.labs.mosaic.core.raster.io.RasterCleaner
import com.databricks.labs.mosaic.core.types.model.MosaicRasterTile
import com.databricks.labs.mosaic.functions.MosaicExpressionConfig
import org.apache.spark.sql.types.{DataType, StructType}

trait RasterExpressionSerialization {

    def serialize(
        data: Any,
        returnsRaster: Boolean,
        outputDataType: DataType,
        expressionConfig: MosaicExpressionConfig
    ): Any = {
        if (returnsRaster) {
            val tile = data.asInstanceOf[MosaicRasterTile]
            val checkpoint = expressionConfig.getRasterCheckpoint
            val rasterType = outputDataType.asInstanceOf[StructType].fields(1).dataType
            val result = tile
                .formatCellId(IndexSystemFactory.getIndexSystem(expressionConfig.getIndexSystem))
                .serialize(rasterType, checkpoint)
            RasterCleaner.dispose(tile)
            result
        } else {
            data
        }
    }

}