package uk.ac.shef.wit.geo.benchmark;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LatLonPointPrototypeQueries;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
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

import java.io.IOException;
import java.util.AbstractMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@State(Scope.Thread)
public class LuceneBenchmark
        extends AbstractBenchmark {

    private final String fieldName = "location";
    private IndexSearcher indexSearcher = null;

    @Setup
    public void setup() {

        try {
            // MMapDirectory makes no difference to performance
//            Path tempDirectory = Files.createTempDirectory(this.getClass().getName());
//            final Directory directory = new MMapDirectory(tempDirectory);
            final Directory directory = new RAMDirectory();
            IndexWriterConfig iwConfig = new IndexWriterConfig();
            IndexWriter indexWriter = new IndexWriter(directory, iwConfig);

            int i = 0;
            for (double[] latlon : getIndexPoints()) {
                Document doc = new Document();
                doc.add(new StoredField("id", ++i));
                doc.add(new LatLonPoint(fieldName, latlon[0], latlon[1]));
                doc.add(new LatLonDocValuesField(fieldName, latlon[0], latlon[1]));
                indexWriter.addDocument(doc);
            }
            indexWriter.commit();
            indexWriter.close();
            final IndexReader indexReader = DirectoryReader.open(directory);
            indexSearcher = new IndexSearcher(indexReader);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Benchmark
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

    @Benchmark
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
}
