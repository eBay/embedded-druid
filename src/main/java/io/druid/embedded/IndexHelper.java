package io.druid.embedded;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.druid.data.input.InputRow;
import io.druid.query.aggregation.histogram.ApproximateHistogramFoldingSerde;
import io.druid.segment.IndexIO;
import io.druid.segment.IndexMerger;
import io.druid.segment.IndexSpec;
import io.druid.segment.QueryableIndex;
import io.druid.segment.column.ColumnConfig;
import io.druid.segment.incremental.IncrementalIndex;
import io.druid.segment.incremental.IncrementalIndexSchema;
import io.druid.segment.incremental.OnheapIncrementalIndex;
import io.druid.segment.serde.ComplexMetrics;
import io.druid.embedded.load.Loader;
import io.druid.jackson.DefaultObjectMapper;

/**
 * This is a Helper class which reads content of file and generates required index/segment files and persist it.
 * It also provides queryable index object after loading file into memory.
 *
 */
public class IndexHelper {

	  /**
	   * Initialization (handled by Guice in Druid system)
	   */
	  static {
	    ApproximateHistogramFoldingSerde serde = new ApproximateHistogramFoldingSerde();
	    ComplexMetrics.registerSerde(serde.getTypeName(), serde);
	  }
	  static ObjectMapper objectMapper = new DefaultObjectMapper();
	  static ColumnConfig columnConfig = new ColumnConfig() {
			
			@Override
			public int columnCacheSizeBytes() {
				// TODO Auto-generated method stub
				return 0;
			}
	  };
	  static IndexIO indexIO = new IndexIO(objectMapper, columnConfig );
	  static IndexMerger merger = new IndexMerger(objectMapper, indexIO);

	  /**
	   * The only way to get a QueryableIndex from IncrementalIndex is to persist the IncrementalIndex
	   * and reload it. This methods does that.
	   *
	   * @param loader
	   * @param aggregates
	   * @return
	   * @throws IOException
	   */
	  public static QueryableIndex getQueryableIndex(Loader loader, IncrementalIndexSchema indexSchema)
	      throws IOException {
//	    IncrementalIndex<?> incIndex =
//	        new OffheapIncrementalIndex(indexSchema, Utils.getBufferPool(), true, maxTotalBufferSize);
	    IncrementalIndex<?> incIndex = new OnheapIncrementalIndex(indexSchema, true ,Integer.MAX_VALUE);

	    for (InputRow row : loader) {
	      incIndex.add(row);
	    }
	    String tmpDir = System.getProperty("druid.segment.dir");
	    if(tmpDir == null) {
	    	tmpDir = System.getProperty("java.io.tmpdir") + File.separator +  "druid-tmp-index-";
	    }
	    File tmpIndexDir = new File(tmpDir + loader.hashCode());
	    
	    merger.persist(incIndex, tmpIndexDir, new IndexSpec());
	    return indexIO.loadIndex(tmpIndexDir);
	  }

	  /**
	   * Get QueryableIndex from index directory.
	   *
	   * @param indexDir
	   * @return
	   * @throws IOException
	   */
	  public static QueryableIndex getQueryableIndex(File indexDir) throws IOException {
	    QueryableIndex index = indexIO.loadIndex(indexDir);
	    return index;
	  }
}
