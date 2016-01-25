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

import io.druid.data.input.Row;
import io.druid.data.input.impl.DimensionsSpec;
import io.druid.embedded.load.Loader;
import io.druid.embedded.load.impl.CSVLoader;
import io.druid.granularity.QueryGranularity;
import io.druid.query.Result;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.aggregation.DoubleSumAggregatorFactory;
import io.druid.query.aggregation.LongSumAggregatorFactory;
import io.druid.query.aggregation.MaxAggregatorFactory;
import io.druid.query.aggregation.MinAggregatorFactory;
import io.druid.query.aggregation.PostAggregator;
import io.druid.query.aggregation.histogram.ApproximateHistogramAggregatorFactory;
import io.druid.query.aggregation.histogram.ApproximateHistogramFoldingAggregatorFactory;
import io.druid.query.aggregation.histogram.QuantilePostAggregator;
import io.druid.query.aggregation.histogram.QuantilesPostAggregator;
import io.druid.query.filter.DimFilter;
import io.druid.query.filter.DimFilters;
import io.druid.query.groupby.GroupByQuery;
import io.druid.query.spec.QuerySegmentSpecs;
import io.druid.query.topn.TopNQuery;
import io.druid.query.topn.TopNQueryBuilder;
import io.druid.segment.QueryableIndex;
import io.druid.segment.incremental.IncrementalIndexSchema;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Interval;

import com.google.common.collect.Lists;
import com.metamx.common.guava.Sequence;
import com.metamx.common.guava.Sequences;

import org.testng.Assert;
import org.testng.annotations.Test;

public class EmbeddedDruidTest {

	public static QueryableIndex createDruidSegments() throws IOException {
	//  Create druid segments from raw data
		Reader reader = new BufferedReader(new FileReader(new File("./src/test/resources/report.csv")));

	    List<String> columns = Arrays.asList("colo", "pool", "report", "URL", "TS", "metric", "value", "count", "min", "max", "sum");
	    List<String> exclusions = Arrays.asList("_Timestamp", "_Machine", "_ThreadId", "_Query");
	    List<String> metrics = Arrays.asList("value", "count", "min", "max", "sum");
	    List<String> dimensions = new ArrayList<String>(columns);
	    dimensions.removeAll(exclusions);
	    dimensions.removeAll(metrics);
	    Loader loader = new CSVLoader(reader, columns, dimensions, "TS");

	    DimensionsSpec dimensionsSpec = new DimensionsSpec(dimensions, null, null);
	    AggregatorFactory[] metricsAgg = new AggregatorFactory[] {
	        new LongSumAggregatorFactory("agg_count", "count"),
	        new MaxAggregatorFactory("agg_max", "max"),
	        new MinAggregatorFactory("agg_min", "min"),
	        new DoubleSumAggregatorFactory("agg_sum", "sum"),
	        new ApproximateHistogramAggregatorFactory("agg_histogram", "value", null, null, null, null)
	    };
	    IncrementalIndexSchema indexSchema = new IncrementalIndexSchema(0, QueryGranularity.ALL, dimensionsSpec, metricsAgg);
	    QueryableIndex index = IndexHelper.getQueryableIndex(loader, indexSchema);
	    return index;
	}
	
	@Test
	public void groupByQuery() throws IOException {
		QueryableIndex index = createDruidSegments();
		List<DimFilter> filters = new ArrayList<DimFilter>();
		filters.add(DimFilters.dimEquals("report", "URLTransaction"));
		filters.add(DimFilters.dimEquals("pool", "r1cart"));
		filters.add(DimFilters.dimEquals("metric", "Duration"));
		GroupByQuery query = GroupByQuery.builder()
	      .setDataSource("test")
	      .setQuerySegmentSpec(QuerySegmentSpecs.create(new Interval(0, new DateTime().getMillis())))
	      .setGranularity(QueryGranularity.NONE)
	      .addDimension("URL")
	      .addAggregator(new LongSumAggregatorFactory("agg_count", "agg_count"))
	      .addAggregator(new MaxAggregatorFactory("agg_max", "agg_max"))
	      .addAggregator(new MinAggregatorFactory("agg_min", "agg_min"))
	      .addAggregator(new DoubleSumAggregatorFactory("agg_sum", "agg_sum"))
	      .addAggregator(new ApproximateHistogramFoldingAggregatorFactory("agg_histogram", "agg_histogram", 20, 5, null, null))
	      .addPostAggregator(new QuantilesPostAggregator("agg_quantiles", "agg_histogram", new float[] {0.25f, 0.5f, 0.75f, 0.95f, 0.99f}))
	      .setDimFilter(DimFilters.and(filters))
	      .build();

	    @SuppressWarnings("unchecked")
	    Sequence<Row> sequence = QueryHelper.run(query, index);
	    ArrayList<Row> results = Sequences.toList(sequence, Lists.<Row>newArrayList());
	    Assert.assertEquals(results.size(), 2);
	    
	    if(results.get(0).getDimension("URL").get(0).equals("abc")) {
	    	Assert.assertEquals(results.get(0).getLongMetric("agg_sum"), 247);
	    	Assert.assertEquals(results.get(0).getLongMetric("agg_min"), 0);
	    	Assert.assertEquals(results.get(0).getLongMetric("agg_max"), 124);
	    	Assert.assertEquals(results.get(0).getLongMetric("agg_count"), 12);	
	    	Assert.assertEquals(results.get(1).getLongMetric("agg_sum"), 123);
	    	Assert.assertEquals(results.get(1).getLongMetric("agg_min"), 0);
	    	Assert.assertEquals(results.get(1).getLongMetric("agg_max"), 123);
	    	Assert.assertEquals(results.get(1).getLongMetric("agg_count"), 3);	    		    	

	    } else {
	    	Assert.assertEquals(results.get(0).getLongMetric("agg_sum"), 123);
	    	Assert.assertEquals(results.get(0).getLongMetric("agg_min"), 0);
	    	Assert.assertEquals(results.get(0).getLongMetric("agg_max"), 123);
	    	Assert.assertEquals(results.get(0).getLongMetric("agg_count"), 3);
	    	Assert.assertEquals(results.get(1).getLongMetric("agg_sum"), 247);
	    	Assert.assertEquals(results.get(1).getLongMetric("agg_min"), 0);
	    	Assert.assertEquals(results.get(1).getLongMetric("agg_max"), 124);
	    	Assert.assertEquals(results.get(1).getLongMetric("agg_count"), 12);	
	    }
	}

	@Test
	public void topNQuery() throws IOException {
		QueryableIndex index = createDruidSegments();
		List<DimFilter> filters = new ArrayList<DimFilter>();
		filters.add(DimFilters.dimEquals("report", "URLTransaction"));
		filters.add(DimFilters.dimEquals("pool", "r1cart"));
		filters.add(DimFilters.dimEquals("metric", "Duration"));
	    TopNQuery query =
	        new TopNQueryBuilder()
	            .threshold(5)
	            .metric("agg_count")
	            .dataSource("test")
	            .intervals(QuerySegmentSpecs.create(new Interval(0, new DateTime().getMillis())))
	            .granularity(QueryGranularity.NONE)
	            .dimension("colo")
	            .aggregators(
	                Arrays.<AggregatorFactory>asList(
	                    new LongSumAggregatorFactory("agg_count", "agg_count"),
	                    new MaxAggregatorFactory("agg_max", "agg_max"),
	                    new MinAggregatorFactory("agg_min", "agg_min"),
	                    new DoubleSumAggregatorFactory("agg_sum", "agg_sum"),
	                    new ApproximateHistogramFoldingAggregatorFactory("agg_histogram", "agg_histogram", 5, 10, null, null)))
	            .postAggregators(
	                Arrays.<PostAggregator>asList(new QuantilePostAggregator("agg_quantiles","agg_histogram", 0.5f)))
	            .filters(DimFilters.and(filters)).build();
	    @SuppressWarnings("unchecked")
	    Sequence<Result> sequence = QueryHelper.run(query, index);
	    ArrayList<Result> results = Sequences.toList(sequence, Lists.<Result>newArrayList());
	    Assert.assertEquals(results.size(), 1);
	}

}
