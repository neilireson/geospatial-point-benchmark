package uk.ac.shef.wit.geo.benchmark;

import me.tongfei.progressbar.ProgressBar;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.spatial.BBOX;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
public class GeotoolsBenchmark
        extends AbstractBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final FilterFactory ff = CommonFactoryFinder.getFilterFactory();
    private static final GeometryFactory gf = new GeometryFactory();
    private SpatialIndexFeatureCollection index;

    @Setup
    public void setup() {
//        SimpleFeatureTypeBuilder polyTypeBuilder = new SimpleFeatureTypeBuilder();
//        polyTypeBuilder.setName("Polygon");
//        polyTypeBuilder.setNamespaceURI("Polygon");
//        polyTypeBuilder.setCRS(DefaultGeographicCRS.WGS84);
//        polyTypeBuilder.add("polyGeom", Polygon.class);
//        polyTypeBuilder.setDefaultGeometry("polyGeom");
//        polyTypeBuilder.add("name", String.class);
//        SimpleFeatureType polygonFeature = polyTypeBuilder.buildFeatureType();
//
//        SimpleFeatureTypeBuilder lineTypeBuilder = new SimpleFeatureTypeBuilder();
//        lineTypeBuilder.setName("Line");
//        lineTypeBuilder.setNamespaceURI("Line");
//        lineTypeBuilder.setCRS(DefaultGeographicCRS.WGS84);
//        lineTypeBuilder.add("lineGeom", LineString.class);
//        lineTypeBuilder.setDefaultGeometry("lineGeom");
//        lineTypeBuilder.add("name", String.class);
//        SimpleFeatureType lineFeature = lineTypeBuilder.buildFeatureType();

        SimpleFeatureTypeBuilder pointTypeBuilder = new SimpleFeatureTypeBuilder();
        pointTypeBuilder.setName("Point");
        pointTypeBuilder.setNamespaceURI("Point");
        pointTypeBuilder.setCRS(DefaultGeographicCRS.WGS84);
        pointTypeBuilder.add("pointGeom", Point.class);
        pointTypeBuilder.setDefaultGeometry("pointGeom");
        pointTypeBuilder.add("id", Integer.class);
        SimpleFeatureType pointFeature = pointTypeBuilder.buildFeatureType();

        List<double[]> latlons = getIndexPoints();
        ArrayList<SimpleFeature> features = new ArrayList<>();
        int i = 0;
        try (ProgressBar pg = new ProgressBar("Points", numberOfIndexPoints)) {
            for (double[] latlon : latlons) {
                Point point = gf.createPoint(new Coordinate(latlon[1], latlon[0]));

                SimpleFeature feature = createSimpleFeature(pointFeature, point);

                if (feature != null) {
                    feature.setAttribute("id", ++i);
                    features.add(feature);
                } else {
                    logger.error("Not a valid feature");
                }
                pg.step();
            }
        }

        logger.info("Indexing {} features...", numberOfIndexPoints);
        SimpleFeatureCollection featureCollection = DataUtilities.collection(features);
        index = new SpatialIndexFeatureCollection(featureCollection.getSchema());
        index.addAll(features);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(value = 1)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void query() throws TransformException {

        // 1 degree is approximately 100km
        float queryRadiusDegrees = queryRadiusMetres / 100000f;

        long candidateCount = 0;
        long nearestCount = 0;
        results.clear();
        for (double[] latlon : getQueryPoints()) {
            Coordinate coord = new Coordinate(latlon[1], latlon[0]);
            SimpleFeature nearestFeature = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            // get all features that are within maxSearchDistance of the query
            SimpleFeatureCollection candidates = getCandidateFeatures(coord, queryRadiusDegrees);
            if (!candidates.isEmpty()) {
                candidateCount += candidates.size();
                nearestCount++;
                // iterate through the candidates
                try (SimpleFeatureIterator itr = candidates.features()) {
                    while (itr.hasNext()) {
                        SimpleFeature feature = itr.next();
                        Point featureGeometry = (Point) feature.getDefaultGeometry();
                        double distance = JTS.orthodromicDistance(coord, featureGeometry.getCoordinate(), DefaultGeographicCRS.WGS84);
                        if (nearestDistance > distance) {
                            nearestDistance = distance;
                            nearestFeature = feature;
                        }
                    }
                }
            }
            if (nearestFeature != null) {
                double distance = JTS.orthodromicDistance(coord, ((Point)nearestFeature.getDefaultGeometry()).getCoordinate(), DefaultGeographicCRS.WGS84);
                results.add(new AbstractMap.SimpleImmutableEntry<>((int) nearestFeature.getAttribute("id"), distance));
            } else {
                results.add(new AbstractMap.SimpleImmutableEntry<>(0, -1.0));
            }
        }

        candidateCounts.add(nearestCount == 0 ? 0 : (candidateCount / nearestCount));
        nearestCounts.add(nearestCount);
    }

    @TearDown
    public void teardown() {
        super.teardown();
    }

    public static SimpleFeature createSimpleFeature(SimpleFeatureType schema, Geometry geometry) {
        if (geometry != null && geometry.isValid()) {
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(schema);
            featureBuilder.add(geometry);
            return featureBuilder.buildFeature(null);
        }
        return null;
    }

    private SimpleFeatureCollection getCandidateFeatures(Coordinate coordinate, double distance) {
        SimpleFeatureType schema = index.getSchema();
        ReferencedEnvelope search =
                new ReferencedEnvelope(new Envelope(coordinate), schema.getCoordinateReferenceSystem());
        search.expandBy(distance);
        BBOX bbox = ff.bbox(ff.property(schema.getGeometryDescriptor().getName()), search);
        return index.subCollection(bbox);
    }
}
