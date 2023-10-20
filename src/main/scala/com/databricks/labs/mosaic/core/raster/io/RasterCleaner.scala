package com.databricks.labs.mosaic.core.raster.io

import com.databricks.labs.mosaic.core.raster.gdal.MosaicRasterGDAL
import com.databricks.labs.mosaic.core.types.model.MosaicRasterTile
import org.gdal.gdal.Dataset

trait RasterCleaner {

    def cleanUp(): Unit

    def destroy(): Unit

}

object RasterCleaner {

    /**
      * Flushes the cache and deletes the dataset. Note that this does not
      * unlink virtual files. For that, use gdal.unlink(path).
      *
      * @param ds
      *   The dataset to destroy.
      */
    def destroy(ds: Dataset): Unit = {
        if (ds != null) {
            try {
                ds.FlushCache()
                // Not to be confused with physical deletion, this is just deletes jvm object
                ds.delete()
            } catch {
                case _: Any => ()
            }
        }
    }

    def dispose(raster: Any): Unit = {
        raster match {
            case r: MosaicRasterGDAL      =>
                try {
                    r.destroy()
                    r.cleanUp()
                } catch {
                    case _: Any => ()
                }
            case rt: MosaicRasterTile =>
                try {
                    rt.raster.destroy()
                    rt.raster.cleanUp()
                } catch {
                    case _: Any => ()
                }
            // NOOP for simpler code handling in expressions, removes need for repeated if/else
            case _                    => ()
        }
    }

}