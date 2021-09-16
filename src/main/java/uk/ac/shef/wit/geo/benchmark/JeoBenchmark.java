package uk.ac.shef.wit.geo.benchmark;

import com.eatthepath.jvptree.DistanceFunction;
import com.eatthepath.jvptree.VPTree;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
public class JeoBenchmark
        extends AbstractBenchmark {

    private VPTree<Point, LatLonPoint> index;

    private interface Point {
    }

    private static class LatLonPoint implements Point {

        final int id;
        final double lat, lon;

        public LatLonPoint(int id, double lat, double lon) {
            this.id = id;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static class HaversineDistanceFunction implements DistanceFunction<Point> {
        @Override
        public double getDistance(Point p1, Point p2) {
            return 0;
        }
    }


    @Setup
    public void setup() {

        List<LatLonPoint> points = new ArrayList<>();
        int i = 0;
        for (double[] latlon : getIndexPoints()) {
            points.add(new LatLonPoint(++i, latlon[0], latlon[1]));
        }
       index  = new VPTree<>(new HaversineDistanceFunction(), points);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(value = 1)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void query() {

        long nearestCount = 0;
        for (double[] latlon : getQueryPoints()) {
            int id = 0;
            LatLonPoint point = new LatLonPoint(0, latlon[0], latlon[1]);
            List<LatLonPoint> neighbours = index.getNearestNeighbors(point, 1);
            if (neighbours != null && !neighbours.isEmpty()) {
                LatLonPoint nearest = neighbours.get(0);
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
