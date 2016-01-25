/*
 * Copyright 2015 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.embedded;

import java.io.File;
import java.io.IOException;

import io.druid.data.input.InputRow;
import io.druid.query.aggregation.histogram.ApproximateHistogramFoldingSerde;
import io.druid.segment.IndexIO;
import io.druid.segment.IndexMerger;
import io.druid.segment.IndexSpec;
import io.druid.segment.QueryableIndex;
import io.druid.segment.incremental.IncrementalIndex;
import io.druid.segment.incremental.IncrementalIndexSchema;
import io.druid.segment.incremental.OnheapIncrementalIndex;
import io.druid.segment.serde.ComplexMetrics;

import io.druid.embedded.load.Loader;

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
	    IncrementalIndex<?> incIndex = new OnheapIncrementalIndex(indexSchema, Integer.MAX_VALUE);

	    for (InputRow row : loader) {
	      incIndex.add(row);
	    }
	    String tmpDir = System.getProperty("druid.segment.dir");
	    if(tmpDir == null) {
	    	tmpDir = System.getProperty("java.io.tmpdir") + File.separator +  "druid-tmp-index-";
	    }
	    File tmpIndexDir = new File(tmpDir + loader.hashCode());
	    IndexMerger.persist(incIndex, tmpIndexDir, new IndexSpec());
	    return IndexIO.loadIndex(tmpIndexDir);
	  }

	  /**
	   * Get QueryableIndex from index directory.
	   *
	   * @param indexDir
	   * @return
	   * @throws IOException
	   */
	  public static QueryableIndex getQueryableIndex(File indexDir) throws IOException {
	    QueryableIndex index = IndexIO.loadIndex(indexDir);
	    return index;
	  }
}
