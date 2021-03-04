package uk.ac.shef.wit.geo.benchmark;

import com.eatthepath.jeospatial.GeospatialIndex;
import com.eatthepath.jeospatial.SimpleGeospatialPoint;
import com.eatthepath.jeospatial.VPTreeGeospatialIndex;
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

import java.util.AbstractMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
public class JeoBenchmark
        extends AbstractBenchmark {

    private final GeospatialIndex<MySimpleGeospatialPoint> index = new VPTreeGeospatialIndex<>();

    private static class MySimpleGeospatialPoint
            extends SimpleGeospatialPoint {

        int id;

        public MySimpleGeospatialPoint(int id, double lat, double lon) {
            super(lat, lon);
            this.id = id;
        }
    }


    @Setup
    public void setup() {

        int i = 0;
        for (double[] latlon : getIndexPoints()) {
            MySimpleGeospatialPoint point = new MySimpleGeospatialPoint(++i, latlon[0], latlon[1]);
            index.add(point);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(value = 1)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void benchmark() {

        long nearestCount = 0;
        for (double[] latlon : getQueryPoints()) {
            int id = 0;
            SimpleGeospatialPoint point = new SimpleGeospatialPoint(latlon[0], latlon[1]);
            List<MySimpleGeospatialPoint> neighbours = index.getNearestNeighbors(point, 1);
            if (neighbours != null && !neighbours.isEmpty()) {
                MySimpleGeospatialPoint nearest = neighbours.get(0);
                nearestCount++;
                id = nearest.id;
            }
            results.add(new AbstractMap.SimpleImmutableEntry<>(id, -1.0));
        }

        candidateCounts.add(nearestCount);
        nearestCounts.add(nearestCount);
    }

    @TearDown
    public void teardown() {
        super.teardown();
    }
}
