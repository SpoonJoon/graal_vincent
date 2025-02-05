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
  public void start(String benchmark) {
    System.out.println("Starting " + (isWarmup() ? "warm-up" : "measurement") + " iteration for " + benchmark + ".");
    startTimeNs = System.nanoTime();
    startEnergy = RaplPowercap.getRaplEnergyMicroJoules();
    super.start(benchmark);
  }

  @Override
  public void stop(long duration) {
    super.stop(duration);
    long elapsedNs = System.nanoTime() - startTimeNs;
    long energyUsed = RaplPowercap.getRaplEnergyMicroJoules() - startEnergy;
    System.out.printf("%s iteration: Duration = %d ms, Elapsed = %d ns, Energy = %d micro joules%n",
                      (isWarmup() ? "Warm-up" : "Measurement"), duration, elapsedNs, energyUsed);
  }

  @Override
  public void complete(String benchmark, boolean valid) {
    super.complete(benchmark, valid);
    System.out.println("Callback complete: " + benchmark + " " + (valid ? "PASSED" : "FAILED");
  }
}
