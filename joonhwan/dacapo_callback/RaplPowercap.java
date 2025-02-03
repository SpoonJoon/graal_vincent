import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class RaplPowercap {
    private static final String RAPL_PATH = "/sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj";

    public static long getRaplEnergyMicroJoules() {
        try (BufferedReader reader = new BufferedReader(new FileReader(RAPL_PATH))) {
            return Long.parseLong(reader.readLine().trim());
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
}
