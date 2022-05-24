package uk.ac.shef.wit.geo.benchmark;

import me.tongfei.progressbar.ProgressBar;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.sandbox.search.LatLonPointPrototypeQueries;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@State(Scope.Thread)
public class LuceneBenchmark
        extends AbstractBenchmark
        implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private enum LuceneType {
        ram, mmap, niofs;

        public Path getIndexPath(File outputDirectory, String suffix) {
            switch (this) {
                case ram:
                    return null;
                case mmap:
                case niofs:
                    return Paths.get(outputDirectory.getAbsolutePath(), "lucene-" + suffix);
                default:
                    throw new UnsupportedOperationException("LuceneType: " + this);
            }
        }

        public Directory getDirectory(File outputDirectory, String suffix)
                throws IOException {
            switch (this) {
                case ram:
                    return new ByteBuffersDirectory();
                case mmap:
                    //noinspection ConstantConditions
                    return new MMapDirectory(getIndexPath(outputDirectory, suffix));
                case niofs:
                    //noinspection ConstantConditions
                    return NIOFSDirectory.open(getIndexPath(outputDirectory, suffix));
                default:
                    throw new UnsupportedOperationException("LuceneType: " + this);
            }
        }
    }

    private final LuceneType luceneType = LuceneType.niofs;

    private final String fieldName = "location";
    private IndexSearcher indexSearcher = null;

    @Setup
    public void setup() {

        try {
            boolean createIndex = true;
            Path indexPath = luceneType.getIndexPath(getOutputDirectory(), indexPrefix + "-" + numberOfIndexPoints);
            if (indexPath != null && Files.exists(indexPath)) {
                logger.info("Reading Lucene index...");
                int count;

                try (Directory directory = luceneType.getDirectory(getOutputDirectory(), indexPrefix + "-" + numberOfIndexPoints);
                     IndexReader indexReader = DirectoryReader.open(directory)) {
                    count = indexReader.numDocs();
                }

                if (count == 0) {
                    logger.error("Index is empty");
                } else if (numberOfIndexPoints != count) {
                    logger.error("Index contains incorrect number of documents. Expected {}, found {}",
                            numberOfIndexPoints, count);
                } else {
                    logger.info("Index {} contains {} documents", indexPath, count);
                    createIndex = false;
                }
            }

            Directory directory = luceneType.getDirectory(getOutputDirectory(), indexPrefix + "-" + numberOfIndexPoints);
            if (createIndex) {
                IndexWriterConfig iwConfig = new IndexWriterConfig();
                iwConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
                IndexWriter indexWriter = new IndexWriter(directory, iwConfig);

                int i = 0;
                logger.info("Creating or reading {} points", numberOfIndexPoints);
                List<double[]> latlons = getIndexPoints();
                logger.info("Indexing points");
                try (ProgressBar progressBar = new ProgressBar("Documents:", latlons.size())) {
                    for (double[] latlon : latlons) {
                        progressBar.step();
                        Document doc = new Document();
                        doc.add(new StoredField("id", ++i));
                        doc.add(new LatLonPoint(fieldName, latlon[0], latlon[1]));
                        doc.add(new LatLonDocValuesField(fieldName, latlon[0], latlon[1]));
                        indexWriter.addDocument(doc);
                    }
                }
                indexWriter.commit();
                indexWriter.close();
            }
            final IndexReader indexReader = DirectoryReader.open(directory);
            indexSearcher = new IndexSearcher(indexReader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

//    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(value = 1)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void distanceSortQuery() {
        benchmark(latlon -> {
            Query q = LatLonPoint.newDistanceQuery(fieldName, latlon[0], latlon[1], queryRadiusMetres);
            Sort sort = new Sort(LatLonDocValuesField.newDistanceSort(fieldName, latlon[0], latlon[1]));
            try {
                return indexSearcher.search(q, 1, sort);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

//    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(value = 1)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void sortQuery() {
        benchmark(latlon -> {
            Query q = new MatchAllDocsQuery();
            Sort sort = new Sort(LatLonDocValuesField.newDistanceSort(fieldName, latlon[0], latlon[1]));
            try {
                return indexSearcher.search(q, 1, sort);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(value = 1)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void nearest() {
        benchmark(latlon -> {
            try {
                return LatLonPointPrototypeQueries.nearest(indexSearcher, fieldName, latlon[0], latlon[1], 1);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void benchmark(Function<double[], TopDocs> getTopDocsFunction) {

        long candidateCount = 0;
        long nearestCount = 0;
        results.clear();
        for (double[] latlon : getQueryPoints()) {
            int id = 0;
            float distance = -1;
            try {
                TopDocs topDocs = getTopDocsFunction.apply(latlon);

                candidateCount += topDocs.totalHits.value;
                if (topDocs.totalHits.value != 0) {
                    ScoreDoc scoreDoc = topDocs.scoreDocs[0];
                    if (scoreDoc instanceof FieldDoc)
                        distance = ((Double) ((FieldDoc) scoreDoc).fields[0]).floatValue();
                    else
                        distance = scoreDoc.score;
                    Document doc = indexSearcher.doc(scoreDoc.doc);
                    id = Integer.parseInt(doc.get("id"));
                    nearestCount++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            results.add(new AbstractMap.SimpleImmutableEntry<>(id, (double) distance));
        }

        candidateCounts.add(nearestCount == 0 ? 0 : candidateCount / nearestCount);
        nearestCounts.add(nearestCount);
    }

    @TearDown
    public void teardown() {
        super.teardown();
    }

    @Override
    public void close() throws IOException {
        indexSearcher.getIndexReader().close();
    }


    public static void main(String[] args) {
        try (LuceneBenchmark benchmark = new LuceneBenchmark()) {
            long time = System.currentTimeMillis();
            benchmark.setup();
            logger.info("Setup {} points in {}ms", benchmark.numberOfIndexPoints, System.currentTimeMillis() - time);
            time = System.currentTimeMillis();
            benchmark.nearest();
            logger.info("Query {} points in {}ms", benchmark.numberOfQueryPoints, System.currentTimeMillis() - time);
            benchmark.teardown();
        } catch (IOException e) {
            logger.error("", e);
        }
    }
}
