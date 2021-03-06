package uk.ac.shef.wit.geo.benchmark;

import com.infomatiq.jsi.Point;
import com.infomatiq.jsi.Rectangle;
import com.infomatiq.jsi.rtree.RTree;
import me.tongfei.progressbar.ProgressBar;
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

import java.lang.invoke.MethodHandles;
import java.util.AbstractMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Thread)
public class JsiBenchmark
        extends AbstractBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final RTree rtree;

    public JsiBenchmark() {
        rtree = new RTree();
        rtree.init(null);
    }

    @Setup
    public void setup() {
        int i = 0;
        logger.info("Creating or reading {} points", numberOfIndexPoints);
        List<double[]> latlons = getIndexPoints();
        logger.info("Indexing points");
        try (ProgressBar pg = new ProgressBar("Points", numberOfIndexPoints)) {
            for (double[] latlon : latlons) {
                Rectangle rect = new Rectangle(
                        (float) latlon[0], (float) latlon[1],
                        (float) latlon[0], (float) latlon[1]);
                rtree.add(rect, ++i);
                pg.step();
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(value = 1)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void query() {

        // 1 degree is approximately 100km
        float queryRadiusDegrees = queryRadiusMetres / 100000f;

        long nearestCount = 0;
        for (double[] latlon : getQueryPoints()) {
            Point p = new Point((float) latlon[0], (float) latlon[1]);

            AtomicInteger id = new AtomicInteger();
            rtree.nearest(p, v -> {
                id.set(v);
                return true;
            }, queryRadiusDegrees);
            if (id.get() > 0) {
                nearestCount++;
            }
            results.add(new AbstractMap.SimpleImmutableEntry<>(id.get(), -1.0));
        }

        candidateCounts.add(nearestCount);
        nearestCounts.add(nearestCount);
    }

    @TearDown
    public void teardown() {
        super.teardown();
    }
}
