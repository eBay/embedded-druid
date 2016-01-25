#embedded-druid - Leveraging Druid capabilities in stand alone application

Druid is an open source data store designed for real-time exploratory analytics on large data sets. The system combines a column-oriented storage layout, a distributed, shared-nothing architecture, and an advanced indexing structure to allow for the arbitrary exploration of billion-row tables with sub-second latencies. Druid supports fast aggregations and sub-second OLAP queries. This project aims to 
offer similar capability (with single JVM process) for reasonably small amount of data without involving complexity of multi nodes setup.

## Motivation
Druid is proven technology for executing OLAP kind of queries involving billion-row data with sub-second response time. Given it is distributed, shared-nothing architecture involving large amount data, Druid has multiple components like real-time node, historical node, broker node, co-ordinator node, Deep storage, MySql, ZooKeeper etc. If input data size is small (say upto tens to hundreds of millions of rows), then amount of work involve to deploy Druid can be bigger overhead and one can prefer to use in-memory database systems like `derby` or `postgresql` if report requirement is very simple (like group by some dimension or retrieving topN values etc). But there are lots of use cases where input is not `Big Data` but medium or small data, but requires OLAP like capability (like group by multiple dimensions, different aggregation functions like percentile etc). For example, in eBay, we generate report for application operational metrics (which runs on multiple machines across data centers). This report contains information about various metrics like total request count, avg request duration etc across different dimensions like type of request, data center, request status, dependency etc. Each application owner would like to view different kind of information from this report like Top hosts with errors, Top slowest requests by request type / data center or requests by different error codes etc. Given dynamic nature of query, if Druid capability can be leveraged without deployment complexity, then it can make developer/debugger/analyzer life easy. 

## Usage

### Requisites
 * Java, Maven

### Build
The following steps need to be followed in order to build the jar file :
 * Clone the project on GitHub
 * Do a maven build at the top level of the project using `mvn clean install`
 * jar file will be available under embedded-druid/target/embedded-druid*.jar

### Running test cases
 * In order to run test cases use `mvn clean test`

### Maven dependency
Following maven dependency needs to be added in pom.xml project file (if it is available in maven repo) :

    <dependency>
        <groupId>io.druid</groupId>
        <artifactId>embedded-druid</artifactId>
        <version>1.0.0</version>
        <scope>compile</scope>
    </dependency>

## embedded-druid in action

### Create Loader
Currently, for “embedded-druid”, there is support for loading CSV file (for which implementation class “CSVLoader” is available). One needs to first provide list of all columns available in CSV file (including metrics), list of dimensions and column specifying timestamp (if available). For example, for Wikipedia schema which provides information about # of characters added for a particular page by a user, CSV file can have data in format shown below:
“Timestamp, Page, Username, Gender, City, metric,value”
In order to load this data in memory, following code needs to be use for creating required Loader object:

    List<String> columns = Arrays.asList("Timestamp", "Page", "Username", "Gender", "City", “metric”, “value”);
    List<String> metrics = Arrays.asList("value");
    List<String> dimensions = new ArrayList<String>(columns);
    dimensions.removeAll(metrics);
    Loader loader = new CSVLoader(reader, columns, dimensions, "Timestamp");


### Create druid Segment/Index files
Once Loader object is created, one needs to create required druid specific segment/index files which will be used for query purpose. In order to create segment file, one needs to specify available dimensions and which kind of aggregator function required for querying. For example, if one is interested in querying values like totalCount, max, min, totalSum and percentiles, then following AggregatorFactory objects need to be created:

    DimensionsSpec dimensionsSpec = new DimensionsSpec(dimensions, null, null);
    AggregatorFactory[] metricsAgg = new AggregatorFactory[] {
        new LongSumAggregatorFactory("agg_count", "count"),
        new MaxAggregatorFactory("agg_max", "max"),
        new MinAggregatorFactory("agg_min", "min"),
        new DoubleSumAggregatorFactory("agg_sum", "sum"),
        new ApproximateHistogramAggregatorFactory("agg_histogram", "value", null, null, null, null)
    };

In order to create segment files locally, one needs to create QueryableIndex object as follow :

    IncrementalIndexSchema indexSchema = new IncrementalIndexSchema(0, QueryGranularity.ALL, dimensionsSpec, metricsAgg);
    QueryableIndex index = IndexHelper.getQueryableIndex(loader, indexSchema);

By default, segment files are created at location `System.getProperty("druid.segment.dir")`. If this property is not set, then it will use temporary location as `System.getProperty("java.io.tmpdir") + File.separator +  "druid-tmp-index-"`. So if one wants to create segment files at provided location, then first set property 'druid.segment.dir'.


### Querying data
Once segment files are created, one can execute different kind of queries using index object. For example, if one wants to execute GroupByQuery for above mentioned schema, then code snippets look like :

    List<DimFilter> filters = new ArrayList<DimFilter>();
    filters.add(DimFilters.dimEquals("Page", "JB"));
    filters.add(DimFilters.dimEquals("Gender", "Male"));
    filters.add(DimFilters.dimEquals("metric", "CharsAdded"));
    GroupByQuery query = GroupByQuery.builder()
        .setDataSource("test")
        .setQuerySegmentSpec(QuerySegmentSpecs.create(new Interval(0, new DateTime().getMillis())))
        .setGranularity(QueryGranularity.NONE)
        .addDimension("City")
        .addAggregator(new LongSumAggregatorFactory("agg_count", "agg_count"))
        .addAggregator(new MaxAggregatorFactory("agg_max", "agg_max"))
        .addAggregator(new MinAggregatorFactory("agg_min", "agg_min"))
        .addAggregator(new DoubleSumAggregatorFactory("agg_sum", "agg_sum"))
        .addAggregator(new ApproximateHistogramFoldingAggregatorFactory("agg_histogram", "agg_histogram", 20, 5, null, null))
        .addPostAggregator(new QuantilesPostAggregator("agg_quantiles", "agg_histogram", new float[] {0.25f, 0.5f, 0.75f, 0.95f, 0.99f}))
        .setFilter(DimFilters.and(filters))
        .build();
    Sequence<Row> sequence = QueryHelper.run(query, index);
    ArrayList<Row> results = Sequences.toList(sequence, Lists.<Row>newArrayList());

Similarly, if one wants to execute TopNQuery, then :

    List<DimFilter> filters = new ArrayList<DimFilter>();
    filters.add(DimFilters.dimEquals("Page", "JB"));
    filters.add(DimFilters.dimEquals("Gender", "Male"));
    filters.add(DimFilters.dimEquals("metric", "CharsAdded"));
    TopNQuery query =
        new TopNQueryBuilder()
        .threshold(5)
        .metric("agg_count")
        .dataSource("test")
        .intervals(QuerySegmentSpecs.create(new Interval(0, new DateTime().getMillis())))
        .granularity(QueryGranularity.NONE)
        .dimension("City")
        .aggregators(Arrays.<AggregatorFactory>asList(
             new LongSumAggregatorFactory("agg_count", "agg_count"),
             new MaxAggregatorFactory("agg_max", "agg_max"),
             new MinAggregatorFactory("agg_min", "agg_min"),
             new DoubleSumAggregatorFactory("agg_sum", "agg_sum"))
        .filters(DimFilters.and(filters))
        . build();
    Sequence<Result> sequence = QueryHelper.run(query, index);
    ArrayList<Result> results = Sequences.toList(sequence, Lists.<Result>newArrayList());

## Future Works
We are planning to extend this work by providing (and/or integrating) REST APIs for ingestion and querying druid data and integrating with easy-to-use UI like Grafana for visualization purpose. This will help user to analyze data quickly and can surface meaningful information promptly.
