package joonhwan.agent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class AgentJoon {
    private static final String BUFFER_CLASS = "jdk.graal.compiler.hotspot.meta.joonhwan.BuboCache";

    public static void premain(String agentArgs, Instrumentation inst) {
        initBuffer();
        if (agentArgs != null) {
            initMethodList(agentArgs);
        }
        addShutdownHook();
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        initBuffer();
        if (agentArgs != null) {
            initMethodList(agentArgs);
        }
        addShutdownHook();
    }

    private static void initBuffer() {
        try {
            Class.forName(BUFFER_CLASS);
            System.out.println("VincentBuffer initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize VincentBuffer: " + e.getMessage());
        }
    }

    private static void initMethodList(String agentArgs) {
        try {
            Class<?> bufferClass = Class.forName(BUFFER_CLASS);
            Method initMethodList = bufferClass.getMethod("initMethodList", List.class);

            // Parse the arguments into a list of method names
            List<String> methods = Arrays.asList(agentArgs.split(","));
            initMethodList.invoke(null, methods);
            System.out.println("Method list initialized with arguments: " + methods);
        } catch (Exception e) {
            System.err.println("Failed to initialize method list: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Agent: JVM is shutting down, dumping buffer...");
            try {
                Class.forName(BUFFER_CLASS)
                    .getMethod("print")
                    .invoke(null);
                System.out.println("Agent: Buffer dump completed successfully");
            } catch (Exception e) {
                System.err.println("Agent: Failed to dump buffer: " + e.getMessage());
                e.printStackTrace();
            }
        }));
    }
}
