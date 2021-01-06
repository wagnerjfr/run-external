package com.myproject.runner.samples;

import com.myproject.runner.RunExternal;
import com.myproject.runner.exception.RunnerException;
import com.myproject.runner.util.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This sample runs "java -version" as external program and grab the response in a String
 */
public class JavaVersion {

    private static final Logger LOG = LoggerFactory.getLogger(JavaVersion.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String RUN_PATH = System.getProperty("user.home") + "/test/java-version/";
    private static final String LOG_PATH = RUN_PATH;

    private List<String> cmd;

    public static void main(String[] args) {
        JavaVersion oracleCloudCLI = new JavaVersion();
        oracleCloudCLI.run();
    }

    private void run() {
        RunExternal re = new RunExternal();

        Map<String, String> envMap = new HashMap<>();
        envMap.put("LC_ALL", "en_US.UTF-8");
        envMap.put("LANG", "en_US.UTF-8");
        re.setAdditionalEnvVars(envMap);

        // Getting the OCI CLI version running: "oci -v"
        cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-version");

        re.setExecCmd(cmd);
        re.setExecPath(new File(RUN_PATH));
        re.setLogPath(new File(LOG_PATH));
        re.setLogNamePrefix("java");
        re.doJoinedOutAndErr(true);

        LOG.info(String.join(" ", cmd));
        String response = null;
        re.startRun();

        try {
            re.waitFor(TIMEOUT);
            response = re.getRecordedOutstream();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }

        LOG.info(response);

        // To delete the log files
        try {
            re.deleteLogs();
        } catch (RunnerException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }
}