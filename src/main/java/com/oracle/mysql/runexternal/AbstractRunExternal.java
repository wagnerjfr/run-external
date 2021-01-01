/* Copyright (c) 2009, 2020, Oracle and/or its affiliates. */

package com.oracle.mysql.runexternal;

import com.google.common.util.concurrent.Uninterruptibles;
import event.SingleEvent;
import exception.RunnerException;
import util.Environment;
import util.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for running external (non java) commands
 *
 * Can do ProcessBuilder.start, and will stream process output to logpath.
 */
public abstract class AbstractRunExternal implements Runnable {

    private static final int MAX_FORK_RETRY = 3;

    /**
     *
     */
    static final int MAX_UNIQUE_FILENAME = 100000;
    private List<String> execCmd = null;
    private File execPath = null;

    /**
     *
     */
    Process myProc = null;
    private Duration duration = Duration.ZERO;
    private int exitCode = -1;
    private int forkRetries = 1;
    private String exceptionMsg = "";
    private Exception gotException = null;
    private final Map<String, String> addEnvs = new TreeMap<>();

    private boolean shellEnvironment = false; // Execute command in a shell environment
    private int restartCode = 0;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRunExternal.class);

    /**
     *
     */
    protected final SingleEvent finished = new SingleEvent("Process finished");

    /**
     *
     */
    protected final SingleEvent started = new SingleEvent("Process started");

    /**
     *
     */
    protected boolean joinedOutAndErr = false;// whether to have stdout and stderr in one file

    /**
     * Constructor.
     */
    public AbstractRunExternal() {
        super();
    }

    /**
     * Set the command to execute.
     *
     * @param cmd The command to execute
     */
    public void setExecCmd(List<String> cmd) {
        execCmd = new ArrayList<>(cmd.size());
        execCmd.addAll(cmd);
    }

    public void setForkRetries(int retries) {
        forkRetries = Math.min(retries, MAX_FORK_RETRY);
    }

    /**
     * Returns true is stdout and stderr are directed to the same file.
     *
     * @return boolean
     */
    public boolean isJoinedOutAndErr() {
        return joinedOutAndErr;
    }

    /**
     * Returns true if this process is running.
     *
     * @return true if this process is running
     */
    public Boolean getRunning() {
        return started.getEventFlag() && !finished.getEventFlag();
    }

    /**
     * Returns true if this process is started.
     *
     * @return true if this process is started
     */
    public Boolean getStarted() {
        return started.getEventFlag();
    }

    /**
     * Returns the exit code of this process.
     *
     * @return exit code of this precess
     * @throws IOException in case of any errors
     */
    public Integer getExitCode() throws IOException {
        if (exitCode == -1) {
            if (myProc == null) {
                /* On very slow machines (i.e. windows rigs) it is possible that
                exitcode can be requested before the value is available so try to
                sleep and then check again before throwing an exception */
                LOG.warning("exitCode is -1 and myProc is null when getExitCode was called. "
                        + "Will try to sleep before checking exitCode again");
                Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
                if (exitCode != -1) {
                    return exitCode;
                } else {
                    throw new IOException("exitCode is -1 and myProc is null, find out reason why!");
                }
            } else {
                return myProc.exitValue();
            }
        } else {
            return exitCode;
        }
    }

    /**
     * Get the exception message from this run.
     *
     * @return exception message
     */
    public String getExceptionMsg() {
        return this.exceptionMsg;
    }

    /**
     * Get the duration of this run.
     *
     * @return duration (ms)
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * Destroy the process.
     */
    public void destroy() {
        if (myProc != null) {
            myProc.destroy();
        }
    }

    /**
     * Stop this process.
     *
     * @return true of the process has been stopped
     */
    abstract public Boolean stopRun();

    /**
     * Start the process
     */
    public void startRun() {
        Thread rexec = new Thread(this);
        rexec.start();
    }

    /**
     *
     */
    @Override
    @SuppressWarnings("PMD.NcssMethodCount")
    public void run() {
        boolean restart;
        do {
            Duration partialDuration = Duration.ZERO;
            Instant starttime = Instant.now();
            started.reset();
            finished.reset();
            exitCode = -1;

            ProcessBuilder processBuilder = new ProcessBuilder(execCmd);
            processBuilder.directory(execPath);
            processBuilder.redirectErrorStream(joinedOutAndErr);
            try {
                configureProcessBuilder(processBuilder);
            } catch (RunnerException e) {
                LOG.log(Level.SEVERE, "Failed to configure ProcessBuilder: {0}", e.getMessage());
                return;
            }
            Map<String, String> env = processBuilder.environment();
            env.putAll(addEnvs);
            configureShellEnvironment();
            int attempts = 1;
            LOG.log(Level.FINE, "Starting process: {0}", execCmd);
            LOG.log(Level.FINER, "Directory: {0}", processBuilder.directory());
            LOG.log(Level.FINER, "Environment: {0}", env);
            try {
                IOException lastEx = null;
                while (!started.getEventFlag() && attempts <= forkRetries) {
                    try {
                        // Start measuring duration again, since we managed to start normally
                        starttime = Instant.now();
                        myProc = processBuilder.start();
                        configureStreams();
                        started.cause();
                    } catch (IOException ex) {
                        //catch and try again
                        lastEx = ex;
                        LOG.log(Level.SEVERE, "Could not fork process, retry #" + attempts + "...", ex);
                        attempts++;
                    }
                }
                if (attempts > forkRetries) {
                    finished.cause();
                    LOG.severe("Could not fork process, ran out of retries.");
                    exceptionMsg = "Failed to fork process after retries";
                    gotException = new IOException("Failed to fork process, last exception: ", lastEx);
                } else {
                    LOG.finer("process started");
                    exitCode = myProc.waitFor();
                    partialDuration = Duration.between(starttime, Instant.now());
                    LOG.log(Level.FINER, "Process returned exit value {0}", exitCode);
                    waitForStreams();
                    LOG.finer("Streams joined now closing");
                }
            } catch (InterruptedException ex) {
                LOG.log(Level.SEVERE, "ERR {0}", ex.getMessage());
                exceptionMsg = "Exception when creating process: " + ex.getMessage();
                gotException = ex;
                LOG.log(Level.SEVERE, "Stack trace: ", ex);
            } finally {
                closeStreams();
                if (partialDuration.isZero()) {
                    // In case of abnormal exit
                    partialDuration = Duration.between(starttime, Instant.now());
                }
                finished.cause();
                LOG.log(Level.FINE, "Finished executing: {0} exitcode {1}", new Object[]{execCmd, exitCode});
            }
            restart = restartCode != 0 && exitCode == restartCode;
            if (restart) {
                LOG.log(Level.FINE, "Restarting {0}, restartCode={1} after {2} ms",
                        new Object[]{execCmd.get(0), restartCode, duration.toMillis()});
            }
            duration = duration.plus(partialDuration);
        } while (restart);
    }

    /**
     *
     * @param pb
     * @throws RunnerException
     */
    protected abstract void configureProcessBuilder(ProcessBuilder pb) throws RunnerException;

    /**
     *
     * @throws IOException
     */
    protected void configureStreams() throws IOException {
    }

    /**
     *
     * @throws InterruptedException
     */
    protected void waitForStreams() throws InterruptedException {
    }

    /**
     *
     */
    protected void closeStreams() {
    }

    /**
     * Returns the name up to the first '.' of the command to be executed.
     * Returns "RunExternal" if execCmd is null or empty.
     *
     * @return String with the name of the executable
     * @throws RunnerException if the command is not yet set
     */
    protected String getExecName() throws RunnerException {
        if (execCmd != null && !execCmd.isEmpty()) {
            String exec = execCmd.get(0);
            int pos = exec.indexOf(".");
            if (pos > 0) {
                return exec.substring(pos);  // Return up until first "."
            }
            return exec;
        }
        throw new RunnerException("Exec command is null or empty.");
    }

    /**
     * Check that all usage of endings for this filename are unused.
     *
     * @param path The path to place the file
     * @param fileName The heading of the filename
     * @param extensions The file extensions to check for
     * @return True if no file exists in the given path with the given name and extension
     */
    boolean canUseFilename(File path, String fileName, String... extensions) {
        boolean canUse = true;
        for (String extension : extensions) {
            File f = new File(path + File.separator + fileName + "." + extension);
            if (f.exists()) {
                canUse = false;
            }
        }
        return canUse;
    }

    /**
     * Set additional environment variables for the process.
     *
     * @param envs Additional environment variables (key, value pairs)
     */
    public void setAdditionalEnvVars(Map<String, String> envs) {
        addEnvs.putAll(envs);
    }

    /**
     * Set the path where this process is executed.
     *
     * @param path path where this process is executed (cwd)
     */
    public void setExecPath(File path) {
        this.execPath = path;
    }

    /**
     * Get the exception from this process.
     *
     * @return exception from this process
     */
    public Exception getException() {
        return this.gotException;
    }

    /**
     * Returns true if this process is finished.
     *
     * @return true if this process is finished
     */
    public boolean isFinished() {
        return finished.getEventFlag();
    }

    /**
     * Wait for process to finish, with no timeout.
     *
     * @return exit code.
     * @throws IOException
     */
    public int waitFor() throws IOException {
        return waitFor(Duration.ZERO);
    }

    /**
     * Wait for process to finish, timeout after a
     * given interval.
     *
     * @param timeout
     * @return exit code.
     * @throws IOException
     */
    public int waitFor(Duration timeout) throws IOException {
        if (timeout.isZero()) {
            finished.await();
        } else {
            finished.await(timeout);
        }
        if (!started.getEventFlag()) {
            String cmd = execCmd != null ? execCmd.get(0) : "null";
            throw new IOException("Was not able to start process successfully: " + cmd);
        }
        return this.getExitCode();
    }

    /**
     * Wait for process to start, with no timeout.
     *
     * @throws IOException
     */
    public void waitForStart() throws IOException {
        waitForStart(Duration.ZERO);
    }

    /**
     * Wait for process to start, timeout after a
     * given interval.
     *
     * @param timeout
     * @throws IOException
     */
    public void waitForStart(Duration timeout) throws IOException {
        if (timeout.isZero()) {
            started.await();
        } else {
            started.await(timeout);
        }
    }

    /**
     * This method can be used to get RunExternal to execute the command in a
     * shell environment, by using "/bin/sh -c" on Unix or "cmd.exe" on Windows.
     *
     * @param doShellEnv whether or not to run the command in a shell environment
     */
    public void doShellEnvironment(boolean doShellEnv) {
        shellEnvironment = doShellEnv;
    }

    /**
     * If configured to run the command in a shell environment, this method
     * manipulates the commandline string to do so depending on OS.
     */
    private void configureShellEnvironment() {
        if (!shellEnvironment) {
            return;
        }
        StringBuilder oldCommandline = new StringBuilder();
        for (String argument : execCmd) {
            oldCommandline.append(argument);
            oldCommandline.append(" ");
        }
        execCmd.clear(); // Making space for new commandline
        if (Environment.isUnix()) {
            execCmd.add("/bin/sh");
            execCmd.add("-c");
        } else if (Environment.isWindows()) {
            execCmd.add("CMD.exe");
            execCmd.add("/C");
        }
        execCmd.add(oldCommandline.toString());
    }

    /**
     * Set to true to join STDERR and STDOUT.
     *
     * @param doJoin whether or not to join the output from STDERR and STDOUT
     */
    public void doJoinedOutAndErr(boolean doJoin) {
        this.joinedOutAndErr = doJoin;
    }

    public void setRestartCode(int restartCode) {
        this.restartCode = restartCode;
    }

}
