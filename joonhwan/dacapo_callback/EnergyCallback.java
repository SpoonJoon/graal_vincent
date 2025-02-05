package joonhwan.dacapo_callback; 

import org.dacapo.harness.Callback;
import org.dacapo.harness.CommandLineArgs;

//Note to future self: sample callback is implemented https://github.com/dacapobench/dacapobench/blob/9.12-bach/benchmarks/harness/src/MyCallback.java
public class EnergyCallback extends Callback {
  private long startTimeNs;
  private long startEnergy;

  public EnergyCallback(CommandLineArgs args) {
    super(args);
  }

  /**
   * Called immediately prior to the start of the benchmark.
   * Note: the working example uses start(String benchmark) only.
   */
  @Override
  public void start(String benchmark) {

    System.out.println("===== Starting " + (isWarmup() ? "WARMUP" : "TIMING") +
                       " iteration for " + benchmark + " =====");

    startTimeNs = System.nanoTime();
    startEnergy = RaplPowercap.getEnergyMicrojoules();

    super.start(benchmark);
  }

  /**
   * Called immediately after the benchmark finishes.
   * The harness passes the duration (in ms).
   */
  @Override
  public void stop(long duration) {

    super.stop(duration);

    long endTimeNs = System.nanoTime();
    long endEnergy = RaplPowercap.getEnergyMicrojoules();
    
    long elapsedNs  = endTimeNs - startTimeNs;
    long energyUsed = endEnergy - startEnergy;

    System.out.printf("Iteration end: Warmup=%b, HarnessDuration=%d ms, MeasuredTime=%d nanoseconds, EnergyUsed=%d microjoules%n",
                      isWarmup(), duration, elapsedNs, energyUsed);
  }

  /**
   * Called after stop(). In the default harness, this prints pass/fail info.
   */
  @Override
  public void complete(String benchmark, boolean valid) {
    super.complete(benchmark, valid);
    System.out.println("Callback complete: " + benchmark + " " + (valid ? "PASSED" : "FAILED"));
  }
}
