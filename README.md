## Benchmark of Java geospatial in-memory point indexes

[JMH](http://openjdk.java.net/projects/code-tools/jmh/) is used to compute the benchmarks

Currently 4 libraries are included in the benchmark:

1. [Geotools](https://geotools.org)

2. [Jeospatial](https://jchambers.github.io/jeospatial)

3. [Java Spatial Index (JSI)](https://github.com/aled/jsi)

4. [Lucene](https://lucene.apache.org)

### Parameters

The benchmarks vary two parameters

* Number of index points
* Query distance - only locations within this distance are considered. This is not used by all the methods.

### Results

The use of JMH is slightly subverted so that the output of the methods can be compared and this shows that the libraries do not agree on the nearest locations.

It seems that Geotools and JSI differ from Jeospatial and Lucene in the way they measure distance.

This could be due to precision, use of different geospatial models or distance functions.

```
mvn clean install
java -jar target/benchmarks.jar -foe true -rf csv -rff benchmark.csv
```

The indexing results involved messing about with the code so aren't produced from the above command.

#### Geotools

##### Indexing

```
Benchmark                (indexPoints)   Mode  Cnt   Score   Error  Units
GeotoolsBenchmark.index          10000  thrpt       22.342          ops/s
GeotoolsBenchmark.index         100000  thrpt        2.468          ops/s
GeotoolsBenchmark.index        1000000  thrpt        0.196          ops/s
```

##### Querying

Relatively good performance but sensitive to queryRadius as distances have to be calculated and compared for all the returned candidate locations
```
Benchmark                 (indexPoints)  (queryRadius)   Mode  Cnt    Score   Error  Units
GeotoolsBenchmark                 10000           1000  thrpt       427.472          ops/s
GeotoolsBenchmark                 10000          10000  thrpt       120.791          ops/s
GeotoolsBenchmark                 10000         100000  thrpt         2.895          ops/s
GeotoolsBenchmark                100000           1000  thrpt       203.445          ops/s
GeotoolsBenchmark                100000          10000  thrpt        18.585          ops/s
GeotoolsBenchmark                100000         100000  thrpt         0.165          ops/s
GeotoolsBenchmark               1000000           1000  thrpt        67.026          ops/s
GeotoolsBenchmark               1000000          10000  thrpt         1.651          ops/s
GeotoolsBenchmark               1000000         100000  thrpt         0.014          ops/s
GeotoolsBenchmark              10000000          10000  thrpt         0.040          ops/s
```

#### Jeospatial

##### Indexing

The benchmark timed out when generating an index for 1 million locations.

```
Benchmark                (indexPoints)   Mode  Cnt   Score   Error  Units
JeoBenchmark.index               10000  thrpt        0.631          ops/s
JeoBenchmark.index              100000  thrpt        0.012          ops/s
JeoBenchmark.index             1000000  thrpt        ?????          ops/s
```

##### Querying

Performance seems to be relatively invariant to index size, queryRadius is not used
```
Benchmark                 (indexPoints)  (queryRadius)   Mode  Cnt    Score   Error  Units
JeoBenchmark                      10000           1000  thrpt        70.986          ops/s
JeoBenchmark                      10000          10000  thrpt        68.877          ops/s
JeoBenchmark                      10000         100000  thrpt        72.272          ops/s
JeoBenchmark                     100000           1000  thrpt        64.820          ops/s
JeoBenchmark                     100000          10000  thrpt        59.836          ops/s
JeoBenchmark                     100000         100000  thrpt        60.836          ops/s
```

#### JsiBenchmark

JSI is fast but only considers points in a 2 dimensional Cartesian space. 
So latitude/longitude distance will be approximations and any calculation crossing the north/south poles or data line will be incorrect

##### Indexing

```
Benchmark                (indexPoints)   Mode  Cnt   Score   Error  Units
JsiBenchmark.index               10000  thrpt       27.462          ops/s
JsiBenchmark.index              100000  thrpt        2.766          ops/s
JsiBenchmark.index             1000000  thrpt        0.251          ops/s
```

##### Querying

The best performance, and deals much better with varying queryRadius than Geotools
```
Benchmark                 (indexPoints)  (queryRadius)   Mode  Cnt    Score   Error  Units
JsiBenchmark                      10000           1000  thrpt       407.832          ops/s
JsiBenchmark                      10000          10000  thrpt       324.198          ops/s
JsiBenchmark                      10000         100000  thrpt       272.265          ops/s
JsiBenchmark                      10000        1000000  thrpt       193.283          ops/s
JsiBenchmark                     100000           1000  thrpt       283.871          ops/s
JsiBenchmark                     100000          10000  thrpt       226.186          ops/s
JsiBenchmark                     100000         100000  thrpt       152.018          ops/s
JsiBenchmark                     100000        1000000  thrpt        62.200          ops/s
JsiBenchmark                    1000000           1000  thrpt       134.291          ops/s
JsiBenchmark                    1000000          10000  thrpt        82.053          ops/s
JsiBenchmark                    1000000         100000  thrpt        43.162          ops/s
JsiBenchmark                    1000000        1000000  thrpt        25.767          ops/s
JsiBenchmark                   10000000           1000  thrpt        42.420          ops/s
JsiBenchmark                   10000000          10000  thrpt        25.435          ops/s
JsiBenchmark                   10000000         100000  thrpt        11.390          ops/s
JsiBenchmark                   10000000        1000000  thrpt         8.906          ops/s
```
#### Lucene

##### Indexing
```
Benchmark              (indexPoints)   Mode  Cnt   Score   Error  Units
LuceneBenchmark.index          10000  thrpt       47.530          ops/s
LuceneBenchmark.index         100000  thrpt        4.931          ops/s
LuceneBenchmark.index        1000000  thrpt        0.456          ops/s
```

##### Querying
Performance of sort queries is not good. Using query distance helps, particularly with a larger index.
Using the BKDReader method, currently in the sandbox, produces much better performance but still not comparable with the other libraries.
It seems as if one of the main differences between JSI and Lucene nearest is that JSI has a query distance limit, while Lucene considers all indexed points.

```
Benchmark                 (indexPoints)  (queryRadius)   Mode  Cnt    Score   Error  Units
Lucene_SortQuery                  10000           1000  thrpt         3.228          ops/s
Lucene_SortQuery                  10000          10000  thrpt         3.115          ops/s
Lucene_SortQuery                  10000         100000  thrpt         3.160          ops/s
Lucene_SortQuery                 100000           1000  thrpt         0.351          ops/s
Lucene_SortQuery                 100000          10000  thrpt         0.316          ops/s
Lucene_SortQuery                 100000         100000  thrpt         0.349          ops/s
Lucene_SortQuery                1000000           1000  thrpt         0.035          ops/s
Lucene_SortQuery                1000000          10000  thrpt         0.031          ops/s
Lucene_SortQuery                1000000         100000  thrpt         0.035          ops/s
Lucene_DistanceSortQuery          10000           1000  thrpt         2.916          ops/s
Lucene_DistanceSortQuery          10000          10000  thrpt         3.284          ops/s
Lucene_DistanceSortQuery          10000         100000  thrpt         2.933          ops/s
Lucene_DistanceSortQuery         100000           1000  thrpt         2.789          ops/s
Lucene_DistanceSortQuery         100000          10000  thrpt         3.120          ops/s
Lucene_DistanceSortQuery         100000         100000  thrpt         1.240          ops/s
Lucene_DistanceSortQuery        1000000           1000  thrpt         2.443          ops/s
Lucene_DistanceSortQuery        1000000          10000  thrpt         1.860          ops/s
Lucene_DistanceSortQuery        1000000         100000  thrpt         0.210          ops/s
Lucene_DistanceSortQuery       10000000          10000  thrpt         0.223          ops/s
Lucene_Nearest                    10000           1000  thrpt        28.933          ops/s
Lucene_Nearest                    10000          10000  thrpt        26.681          ops/s
Lucene_Nearest                    10000         100000  thrpt        28.874          ops/s
Lucene_Nearest                   100000           1000  thrpt        25.887          ops/s
Lucene_Nearest                   100000          10000  thrpt        26.177          ops/s
Lucene_Nearest                   100000         100000  thrpt        26.657          ops/s
Lucene_Nearest                  1000000           1000  thrpt        13.406          ops/s
Lucene_Nearest                  1000000          10000  thrpt        13.010          ops/s
Lucene_Nearest                  1000000         100000  thrpt        13.223          ops/s
Lucene_Nearest                 10000000           1000  thrpt         7.449          ops/s
Lucene_Nearest                 10000000          10000  thrpt         7.932          ops/s
Lucene_Nearest                 10000000         100000  thrpt         7.463          ops/s
```
