package uk.ac.shef.wit.geo.benchmark;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@State(Scope.Benchmark)
public abstract class AbstractBenchmark {

    static final String indexPrefix = "benchmark-index-points";
    static final String queryPrefix = "benchmark-query-points";
    static final String outputDirectoryName = "out";

    @Param({"10000", "100000", "1000000", "10000000"})
    int numberOfIndexPoints = 10000000;

    int numberOfQueryPoints = 1000;

    @Param({"1000", "10000", "100000", "1000000"})
    int queryRadiusMetres = 1000;

    // roughly the UK
    int minLat = 48;
    int maxLat = 58;
    int minLon = -5;
    int maxLon = 5;

    final List<Long> candidateCounts = new ArrayList<>();
    final List<Long> nearestCounts = new ArrayList<>();
    final List<Map.Entry<Integer, Double>> results = new ArrayList<>();

    File getOutputDirectory() {
        return createDirectory(outputDirectoryName);
    }

    private File createDirectory(String directoryPath) {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            if (!path.toFile().mkdirs()) {
                throw new RuntimeException("Failed to create output directory: " + path.toAbsolutePath());
            }
        } else if (!Files.isDirectory(path)) {
            throw new RuntimeException("Output directory is not a directory: " + path.toAbsolutePath());
        }
        return path.toFile();
    }

    synchronized List<double[]> getIndexPoints() {
        return getPoints(indexPrefix, numberOfIndexPoints);
    }

    synchronized List<double[]> getQueryPoints() {
        return getPoints(queryPrefix, numberOfQueryPoints);
    }

    private List<double[]> getPoints(String prefix, int numberOfPoints) {
        getOutputDirectory();
        String filename = prefix + "-" + numberOfPoints + ".csv";
        Path path = Paths.get(outputDirectoryName, filename);
        List<double[]> indexPoints = new ArrayList<>();
        if (Files.exists(path)) {
            try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] latlon = line.split(",");
                    indexPoints.add(new double[]{Double.parseDouble(latlon[0]), Double.parseDouble(latlon[1])});
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (indexPoints.size() != numberOfPoints) {
                throw new RuntimeException("File contains incorrect number of points. Expected " +
                        numberOfPoints + " found " + indexPoints.size());
            }
        } else {
            for (int i = 0; i < numberOfPoints; i++) {
                indexPoints.add(createRandomLatLon());
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                for (double[] latlon : indexPoints) {
                    writer.write(latlon[0] + "," + latlon[1]);
                    writer.newLine();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return indexPoints;
    }

    private double[] createRandomLatLon() {
        final double latitude = ThreadLocalRandom.current().nextDouble(minLat, maxLat);
        final double longitude = ThreadLocalRandom.current().nextDouble(minLon, maxLon);
        return new double[]{latitude, longitude};
    }

    protected void teardown() {
        System.out.format("%n%s: average number of candidates within query distance (if used) = %.0f/%d, " +
                        "number of nearest locations found = %.0f/%d%n",
                getClass().getSimpleName(),
                candidateCounts.stream().mapToLong(x -> x).average().orElse(0),
                numberOfIndexPoints,
                nearestCounts.stream().mapToLong(x -> x).average().orElse(0),
                numberOfQueryPoints);

        // write results
        String filename = "results" +
                "-" + this.getClass().getSimpleName() +
                "-" + numberOfIndexPoints +
                "-" + queryRadiusMetres +
                ".csv";
        Path path = Paths.get(outputDirectoryName, filename);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
            for (Map.Entry<Integer, Double> result : results) {
                writer.write(String.valueOf(result.getKey()));
                writer.write('\t');
                writer.write(String.valueOf(result.getValue()));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) throws RunnerException {

        Options opt = new OptionsBuilder()
//                .include(GeotoolsBenchmark.class.getSimpleName())
//                .include(JeoBenchmark.class.getSimpleName())
//                .include(JsiBenchmark.class.getSimpleName())
                .include(LuceneBenchmark.class.getSimpleName())
                .build();

        Collection<RunResult> runResults = new Runner(opt).run();

//        for (RunResult runResult : runResults) {
//            for (BenchmarkResult benchmarkResult : runResult.getBenchmarkResults()) {
//                Result primaryResult = benchmarkResult.getPrimaryResult();
//                BenchmarkParams params = benchmarkResult.getParams();
//                System.out.format("%s\t%s\t%.3f\t%s%n",
//                        params.getBenchmark(),
//                        params.getMode().shortLabel(),
//                        primaryResult.getScore(),
//                        primaryResult.getScoreUnit());
//            }
//        }
    }
}
