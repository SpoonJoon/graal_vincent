#! bin/bash
javac joonhwan/agent/AgentJoon.java
jar cvfm joonhwan/agent-joon.jar joonhwan/MANIFEST.MF -C . joonhwan/agent/AgentJoon.class 
