import org.dacapo.harness.Callback;
import org.dacapo.harness.Benchmark;
import org.dacapo.harness.Config;

public class EnergyCallback extends Callback {

    private long startEnergy;
    private long startTimeNs;

    @Override
    public void iterationStart(Benchmark benchmark, Config config) {
        super.iterationStart(benchmark, config);

        // Only measure if we're not in a warmup iteration
        if (config.getIteration() >= config.getWarmups()) {
            startTimeNs = System.nanoTime();
            startEnergy = RaplPowercap.getRaplEnergyMicroJoules();
        }
    }

    @Override
    public void iterationEnd(Benchmark benchmark, boolean valid) {
        super.iterationEnd(benchmark, valid);

        int iterationNum = benchmark.getConfig().getIteration();
        if (iterationNum >= benchmark.getConfig().getWarmups()) {
            long endEnergy = RaplPowercap.getRaplEnergyMicroJoules();
            long energyUsed = endEnergy - startEnergy;

            long endTimeNs = System.nanoTime();
            double elapsedSecs = (endTimeNs - startTimeNs) / 1e9;

            System.out.printf("Iteration #%d (post-warmup): Time=%.3f s, Energy=%d µJ%n",
                            iterationNum, elapsedSecs, energyUsed);
        }
    }

}
