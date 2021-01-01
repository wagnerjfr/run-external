/* Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved. */
package com.oracle.mysql.runexternal;

import exception.RunnerException;
import util.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class RunExternal extends AbstractRunExternal {

    private static final int STREAM_BUFFER_SIZE = 10000;

    private static final Logger LOG = LoggerFactory.getLogger(RunExternal.class);
    private boolean logToFile = true;
    private File logPath = null;
    private String logPrefix = "";

    private File stdoutFile = null;
    private File stderrFile = null;

    /**
     * Returns the file which contains the redirected stdout stream.
     *
     * @return File
     */
    public File getRecordedOutFile() {
        return stdoutFile;
    }

    /**
     * Returns the file which contains the redirected stderr stream. If stdout
     * and stderr are joined (joinedOutAndErr == true) it will return the same
     * as getRecordedOutFile().
     *
     * @return File
     */
    public File getRecordedErrFile() {
        return (joinedOutAndErr ? stdoutFile : stderrFile);
    }

    /**
     * Returns the recorded output stream.
     *
     * @return the recorded output stream
     */
    public String getRecordedOutstream() {
        return getFileString(stdoutFile);
    }

    /**
     * Get a stream from the ProcessBuilder's redirected stdout file. Throws
     * exception if file doesn't exist of if process has not started.
     *
     * @return BufferedReader
     * @throws FileNotFoundException
     * @throws RunnerException
     */
    public BufferedReader getRecordedStdOutFileReader() throws FileNotFoundException, RunnerException {
        verifyRecordedFileStatus(stdoutFile);
        return new BufferedReader(new FileReader(stdoutFile));
    }

    /**
     * Get a stream from the ProcessBuilder's redirected stderr file. Throws
     * exception if file doesn't exist of if process has not started.
     *
     * @return BufferedReader
     * @throws FileNotFoundException
     * @throws RunnerException
     */
    public BufferedReader getRecordedStdErrFileReader() throws FileNotFoundException, RunnerException {
        verifyRecordedFileStatus(stderrFile);
        return new BufferedReader(new FileReader(stderrFile));
    }

    /**
     * Make sure file is only read if the process builder has started and the
     * file exists.
     *
     * @throws RunnerException
     */
    private void verifyRecordedFileStatus(File file) throws RunnerException {
        if (!started.getEventFlag()) {
            throw new RunnerException("Attempting to read from file " + file + " before process builder started.");
        }
        if (!file.exists()) {
            throw new RunnerException("File " + file + " doesn't exist.");
        }
        if (!finished.getEventFlag()) {
            LOG.log(Level.WARNING, "Reading from file {0} before process is finished.", new Object[]{file});
        }
    }

    /**
     * Return the exact contents of a file.
     *
     * @param file
     * @return String
     */
    public String getFileString(File file) {
        if (file != null) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int ch;
                do {
                    ch = reader.read();
                    if (ch != -1) {
                        buffer.write(ch);
                    }
                } while (ch != -1);
                return buffer.toString();
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Reading recorded output file '"
                        + stdoutFile.toString()
                        + "' caused exception {0}",
                        ex.getMessage());
            }
        }
        return null;
    }

    /**
     * Returns the recorded error stream.
     *
     * @return the recorded error stream
     */
    public String getRecordedErrstream() {
        if (joinedOutAndErr) {
            return getFileString(stdoutFile);
        }
        return getFileString(stderrFile);
    }

    /**
     * Initialize ProcessBuilder process to redirect stdout/stderr to log files
     * that can be read using getRecordedOutstream() / getRecordedErrstream().
     * Set logPath/logPrefix to identify log files. If joinedOutAndErr is true
     * both streams are sent to outstream.
     *
     * @param processBuilder
     * @throws RunnerException if the required settings are missing
     */
    @Override
    protected void configureProcessBuilder(ProcessBuilder processBuilder) throws RunnerException {
        if (isLogToFile()) {
            if (logPath == null) {
                throw new RunnerException("RunExternal requires a log path.");
            }
            if (processBuilder == null) {
                throw new RunnerException("RunExternal processBuilder was null");
            }
            if (processBuilder.command() != null && !processBuilder.command().isEmpty()) {
                LOG.log(Level.FINER, "ProcessBuilder [{0}] log path: {1} ",
                        new Object[]{processBuilder.command().get(0), logPath});
            }
            if (logPrefix == null || logPrefix.isEmpty()) {
                logPrefix = getExecName();
            }

            String fileName = findNextFilename(logPath, logPrefix, "out", "err");

            stdoutFile = new File(logPath, fileName + ".out");
            LOG.log(Level.FINER, "ProcessBuilder redirecting stdout to file: {0}", stdoutFile);
            processBuilder.redirectOutput(stdoutFile);

            if (!joinedOutAndErr) {
                stderrFile = new File(logPath, fileName + ".err");
                LOG.log(Level.FINER, "ProcessBuilder redirecting stderr to {0}", stderrFile);
                processBuilder.redirectError(stderrFile);
            } else {
                processBuilder.redirectErrorStream(true);
                LOG.log(Level.FINER, "ProcessBuilder joining stderr with stdout.");
            }
        } else {
            LOG.log(Level.FINE, "ProcessBuilder logfile disabled, no recorded stdout/stderr will be available.");
        }
    }

    public boolean deleteLogs() throws RunnerException {

        boolean ret = true;
        if (!isFinished()) {
            throw new RunnerException("Cannot delete logs while process is still running");
        }
        if (stderrFile != null) {
            if (!stderrFile.delete()) {
                LOG.log(Level.WARNING, "Failed to delete {0}", stderrFile.getPath());
                ret = false;
            }
        }
        if (stdoutFile != null) {
            if (!stdoutFile.delete()) {
                LOG.log(Level.WARNING, "Failed to delete {0}", stdoutFile.getPath());
                ret = false;
            }
        }
        return ret;
    }

    /**
     * Set whether we should log command out/err streams to file
     *
     * @param write false if we should not log to file
     */
    public void setLogToFile(boolean write) {
        logToFile = write;
    }

    /**
     * Set the log path.
     *
     * @param path log path
     */
    public void setLogPath(File path) {
        logPath = path.getAbsoluteFile();
        if (!logPath.exists()) {
            logPath.mkdirs();
        }
    }

    /**
     * Find the next available filename heading with the common heading.
     *
     * Example:
     *
     * If these files exist in path ".":
     *
     * foo.out, foo-1.err. foo-2.out, foo-2.err, foo-3.out
     *
     * And we call: findNextFilename(".", "foo", "out", "err");
     *
     * It will return "foo-4"
     *
     * @param path The path to place the file
     * @param heading The heading of the filename
     * @param extensions The file extensions to check for
     * @return Heading for filename that can be used, null if not found.
     */
    private String findNextFilename(File path, String heading, String... extensions) {
        // NOPMD - PMD does not work with varargs
        String next = heading;
        // Try with file name directly
        if (!canUseFilename(path, heading, extensions)) {
            // Try to increase counter
            long counter = 1;
            String head = "";
            boolean found = false;
            while ((counter < AbstractRunExternal.MAX_UNIQUE_FILENAME) && (!found)) {
                head = heading + "-" + counter;
                counter++;
                found = canUseFilename(path, head, extensions);
            }
            if (found) {
                next = head;
            }
        }
        // Give up - return the default name
        return next;
    }

    /**
     * Returns the output stream that can write to stdin for this process
     *
     * @param timeout The max time to wait for process to start
     * @return output stream for this process
     * @throws IOException in case of any errors
     */
    public OutputStream getOutputStream(Duration timeout) throws IOException {
        started.await(timeout);
        if (finished.getEventFlag()) {
            throw new IOException("Process already finished");
        }
        if (myProc == null) {
            throw new IOException("Process object is null");
        } else {
            return myProc.getOutputStream();
        }
    }

    /**
     * Returns the input stream that can read from stdout for this process. This
     * method will throw an exception if the process is configured to write
     * directly to file and does not expose an input stream for stdout.
     *
     *
     * @param timeout The max time to wait for process to start
     * @return output stream for this process
     * @throws RunnerException if configured to write directly to file.
     * @throws IOException in case of any errors
     */
    public InputStream getInputStream(Duration timeout) throws IOException, RunnerException {
        if (isLogToFile()) {
            throw new RunnerException("Process streams are redirected to file, cannot get stream for error stdout");
        }
        started.await(timeout);
        if (finished.getEventFlag()) {
            throw new IOException("Process already finished");
        }
        if (myProc == null) {
            throw new IOException("Process object is null");
        } else {
            return new BufferedInputStream(myProc.getInputStream(), STREAM_BUFFER_SIZE);
        }
    }

    /**
     * Returns the input stream that can read from stderr for this process. This
     * method will throw an exception if the process is configured to write
     * directly to file and does not expose an input stream for stdout.
     *
     *
     * @param timeout The max time to wait for process to start
     * @return output stream for this process
     * @throws RunnerException if configured to write directly to file.
     * @throws IOException in case of any errors
     */
    public InputStream getErrorStream(Duration timeout) throws IOException, RunnerException {
        if (isLogToFile()) {
            throw new RunnerException("Process streams are redirected to file, cannot get stream for error stderr");
        }
        started.await(timeout);
        if (finished.getEventFlag()) {
            throw new IOException("Process already finished");
        }
        if (myProc == null) {
            throw new IOException("Process object is null");
        } else {
            return new BufferedInputStream(myProc.getErrorStream(), STREAM_BUFFER_SIZE);
        }
    }

    /**
     * Set the prefix to the log name.
     *
     * @param prefix Prefix to the log name
     */
    public void setLogNamePrefix(String prefix) {
        logPrefix = prefix;
    }

    /**
     * Stop this process.
     *
     * @return true of the process has been stopped
     */
    @Override
    public Boolean stopRun() {
        destroy();
        if (myProc != null) {
            LOG.fine("Stopped, now waiting");
            try {
                myProc.waitFor();
                finished.cause();
            } catch (InterruptedException ex) {
                LOG.log(Level.SEVERE, "Exception waiting for process exit: {0}", ex.getMessage());
            }
        }
        return Boolean.TRUE;
    }

    /**
     *
     * @param timeout
     * @return BufferedReader
     * @throws IOException in case of any errors
     * @throws RunnerException if configured to write directly to file.
     */
    public BufferedReader getBufferedReader(Duration timeout) throws IOException, RunnerException {
        return new BufferedReader(new InputStreamReader(getInputStream(timeout)));
    }

    /**
     * @return the logToFile
     */
    public boolean isLogToFile() {
        return logToFile;
    }

    /**
     * Set the command to execute.
     *
     * @param cmd The command to execute
     */
    public void setExecCmd(String cmd) {
        List<String> execCmd = new ArrayList<>();
        execCmd.addAll(Arrays.asList(cmd.split(" ")));
        super.setExecCmd(execCmd);
    }

    /**
     * Gets the command output formatted in a list of strings.
     *
     * @return List<String>
     * @throws RunnerException if configured to write directly to file.
     */
    public List<String> getCommandOutput() throws RunnerException {
        List<String> response;
        try {
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(myProc.getInputStream()))) {
                response = new ArrayList<>();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    response.add(line);
                }
            }
        } catch (IOException e) {
            throw new RunnerException(e);
        }

        return response;
    }
}
