## Benchmark of Java point geospatial in-memory indexes.

[JMH](http://openjdk.java.net/projects/code-tools/jmh/) is used to compute the benchmarks.

Currently 4 libraries are included in the benchmark:

1. [Geotools](https://geotools.org)

2. [Lucene](https://lucene.apache.org)

3. [Jeospatial](https://jchambers.github.io/jeospatial)

4. [Java Spatial Index (JSI)](https://github.com/aled/jsi)

### Parameters

The benchmarks vary two parameters

* Number of index points
* Query distance - only locations within this distance are considered. This is not used by all the methods.

### Results

The use of JMH is slightly subverted so that the output of the methods can be compared and this shows that the libraries do not agree on the nearest locations.

This could be due to precision, use of different geospatial models or distance functions.

```
mvn clean install
java -jar target/benchmarks.jar -foe true -rf csv -rff benchmark.csv
```

#### Geotools
Relatively good performance but sensitive to queryRadius as distances have to be calculated and compared for all the returned candidate locations
```
Benchmark                                     (indexPoints)  (queryRadius)   Mode  Cnt    Score   Error  Units
GeotoolsBenchmark.benchmark                           10000           1000  thrpt       427.472          ops/s
GeotoolsBenchmark.benchmark                           10000          10000  thrpt       120.791          ops/s
GeotoolsBenchmark.benchmark                           10000         100000  thrpt         2.895          ops/s
GeotoolsBenchmark.benchmark                          100000           1000  thrpt       203.445          ops/s
GeotoolsBenchmark.benchmark                          100000          10000  thrpt        18.585          ops/s
GeotoolsBenchmark.benchmark                          100000         100000  thrpt         0.165          ops/s
```

#### Jeospatial
Performance seems to be invariant to index size, queryRadius is not used
```
Benchmark                                     (indexPoints)  (queryRadius)   Mode  Cnt    Score   Error  Units
JeoBenchmark.benchmark                                10000           1000  thrpt        70.986          ops/s
JeoBenchmark.benchmark                                10000          10000  thrpt        68.877          ops/s
JeoBenchmark.benchmark                                10000         100000  thrpt        72.272          ops/s
JeoBenchmark.benchmark                               100000           1000  thrpt        64.820          ops/s
JeoBenchmark.benchmark                               100000          10000  thrpt        59.836          ops/s
JeoBenchmark.benchmark                               100000         100000  thrpt        60.836          ops/s
```

#### JsiBenchmark
The best performance, and deals much better with varying queryRadius then geotools
```
Benchmark                                     (indexPoints)  (queryRadius)   Mode  Cnt    Score   Error  Units
JsiBenchmark.benchmark                                10000          10000  thrpt       324.198          ops/s
JsiBenchmark.benchmark                                10000         100000  thrpt       272.265          ops/s
JsiBenchmark.benchmark                               100000           1000  thrpt       283.871          ops/s
JsiBenchmark.benchmark                               100000          10000  thrpt       226.186          ops/s
JsiBenchmark.benchmark                               100000         100000  thrpt       152.018          ops/s
```
#### Lucene
Performance of sort queries is not good. Using Query distance helps, particularly with a larger index.
Using the BKDReader method currently in the sandbox produces much better performance but still not comparable with the other libraries.
```
Benchmark                                     (indexPoints)  (queryRadius)   Mode  Cnt    Score   Error  Units
LuceneBenchmark.benchmark1_DistanceSortQuery          10000           1000  thrpt         2.916          ops/s
LuceneBenchmark.benchmark1_DistanceSortQuery          10000          10000  thrpt         3.284          ops/s
LuceneBenchmark.benchmark1_DistanceSortQuery          10000         100000  thrpt         2.933          ops/s
LuceneBenchmark.benchmark1_DistanceSortQuery         100000           1000  thrpt         2.789          ops/s
LuceneBenchmark.benchmark1_DistanceSortQuery         100000          10000  thrpt         3.120          ops/s
LuceneBenchmark.benchmark1_DistanceSortQuery         100000         100000  thrpt         1.240          ops/s
LuceneBenchmark.benchmark2_SortQuery                  10000           1000  thrpt         3.228          ops/s
LuceneBenchmark.benchmark2_SortQuery                  10000          10000  thrpt         3.115          ops/s
LuceneBenchmark.benchmark2_SortQuery                  10000         100000  thrpt         3.160          ops/s
LuceneBenchmark.benchmark2_SortQuery                 100000           1000  thrpt         0.351          ops/s
LuceneBenchmark.benchmark2_SortQuery                 100000          10000  thrpt         0.316          ops/s
LuceneBenchmark.benchmark2_SortQuery                 100000         100000  thrpt         0.349          ops/s
LuceneBenchmark.benchmark3_Nearest                    10000           1000  thrpt        28.933          ops/s
LuceneBenchmark.benchmark3_Nearest                    10000          10000  thrpt        26.681          ops/s
LuceneBenchmark.benchmark3_Nearest                    10000         100000  thrpt        28.874          ops/s
LuceneBenchmark.benchmark3_Nearest                   100000           1000  thrpt        25.887          ops/s
LuceneBenchmark.benchmark3_Nearest                   100000          10000  thrpt        26.177          ops/s
LuceneBenchmark.benchmark3_Nearest                   100000         100000  thrpt        26.657          ops/s
```
