package joonhwan.dacapo_callback;

import org.dacapo.harness.Callback;
import org.dacapo.harness.CommandLineArgs;

public class EnergyCallback extends Callback {
  private long startTimeNs;
  private long startEnergy;

  public EnergyCallback(CommandLineArgs args) {
    super(args);
  }

  @Override
  protected void start(String benchmark, boolean warmup) {
    System.out.println("Starting " + (warmup ? "warm-up" : "FR") + " iteration for " + benchmark + ".");
    startTimeNs = System.nanoTime();
    startEnergy = RaplPowercap.getRaplEnergyMicroJoules();
    super.start(benchmark, warmup);
  }

  @Override
  public void stop(long duration, boolean warmup) {
    super.stop(duration, warmup);
    long endTimeNs = System.nanoTime();
    long endEnergy = RaplPowercap.getRaplEnergyMicroJoules();
    long elapsedNs = endTimeNs - startTimeNs;
    long energyUsed = endEnergy - startEnergy;
    System.out.printf("%s iteration complete: Harness duration = %d ms, Elapsed time = %d ns, Energy used = %d micro joules%n",
                      (warmup ? "Warm-up" : "FR"), duration, elapsedNs, energyUsed);
  }

  @Override
  protected void complete(String benchmark, boolean valid, boolean warmup) {
    super.complete(benchmark, valid, warmup);
    System.out.println("Callback complete: " + benchmark + " " + (valid ? "PASSED" : "FAILED") +
                       " (" + (warmup ? "warm-up" : "FR") + ")");
  }
}
