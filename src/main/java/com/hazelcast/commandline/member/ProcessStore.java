/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.commandline.member;

import com.hazelcast.commandline.member.names.MobyNames;
import com.hazelcast.core.HazelcastException;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.hazelcast.commandline.HazelcastCommandLine.SEPARATOR;

class ProcessStore {
    private static final String LOGS_DIR_STRING = "logs";
    private static final String LOGS_FILE_NAME_STRING = "hazelcast.log";
    private final String hazelcastHome;
    private final String instancesFilePath;

    ProcessStore(String hazelcastHome) {
        this.hazelcastHome = hazelcastHome;
        this.instancesFilePath = hazelcastHome + SEPARATOR + "instances.dat";
        createHazelcastHomeDirectory();
    }

    private void createHazelcastHomeDirectory() {
        try {
            new File(hazelcastHome).mkdirs();
        } catch (Exception e) {
            throw new HazelcastException("Process directories couldn't created. This might be related to user "
                    + "permissions, please check your write permissions at: " + hazelcastHome, e);
        }
    }

    void save(HazelcastProcess process)
            throws IOException {
        Map<String, HazelcastProcess> processMap = findAll();
        processMap.put(process.getName(), process);
        updateFile(processMap);
    }

    Map<String, HazelcastProcess> findAll() {
        Map<String, HazelcastProcess> processes = new HashMap<>();
        try {
            Path path = FileSystems.getDefault().getPath(instancesFilePath);
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
            FileInputStream fileInputStream = new FileInputStream(instancesFilePath);
            if (Files.size(path) == 0) {
                return processes;
            }
            ObjectInputStream input = new ObjectInputStream(fileInputStream);
            processes = (Map<String, HazelcastProcess>) input.readObject();
            input.close();
        } catch (IOException e) {
            throw new HazelcastException("Error when reading from file.", e);
        } catch (ClassNotFoundException cnfe) {
            throw new HazelcastException(cnfe.getMessage(), cnfe);
        }
        return processes;
    }

    void updateFile(Map<String, HazelcastProcess> processMap)
            throws IOException {
        FileOutputStream fileOut = new FileOutputStream(instancesFilePath);
        ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
        objectOut.writeObject(processMap);
        objectOut.close();
    }

    HazelcastProcess find(String name) {
        return findAll().get(name);
    }

    void remove(String name)
            throws IOException {
        Map<String, HazelcastProcess> processMap = findAll();
        if (processMap == null || !processMap.containsKey(name)) {
            throw new HazelcastException("No process found with pid: " + name);
        }
        processMap.remove(name);
        updateFile(processMap);
    }

    boolean exists(String name) {
        return findAll().containsKey(name);
    }

    HazelcastProcess create()
            throws FileNotFoundException {
        String name = MobyNames.getRandomName(0);
        String processDir = createProcessDirs(name);
        String logFilePath = processDir + SEPARATOR + LOGS_DIR_STRING + SEPARATOR + LOGS_FILE_NAME_STRING;
        String loggingPropertiesPath = createLoggingPropertiesFile(processDir, logFilePath);
        return new HazelcastProcess(name, loggingPropertiesPath, logFilePath);
    }

    private String createProcessDirs(String name) {
        String processPath = hazelcastHome + SEPARATOR + name;
        String logPath = processPath + SEPARATOR + LOGS_DIR_STRING;
        try {
            new File(logPath).mkdirs();
        } catch (Exception e) {
            throw new HazelcastException("Process directories couldn't be created.");
        }
        return processPath;
    }

    private String createLoggingPropertiesFile(String processDir, String logFilePath)
            throws FileNotFoundException {
        String loggingPropertiesPath = processDir + SEPARATOR + "logging.properties";
        PrintWriter printWriter = new PrintWriter(loggingPropertiesPath);
        String fileContent = "handlers= java.util.logging.FileHandler, java.util.logging.ConsoleHandler\n" + ".level= INFO\n"
                + "java.util.logging.FileHandler.pattern = " + logFilePath + "\n"
                + "java.util.logging.FileHandler.limit = 50000\n" + "java.util.logging.FileHandler.count = 1\n"
                + "java.util.logging.FileHandler.maxLocks = 100\n"
                + "java.util.logging.FileHandler.formatter = java.util.logging.SimpleFormatter\n"
                + "java.util.logging.FileHandler.append=true\n"
                + "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter";
        printWriter.println(fileContent);
        printWriter.close();
        return loggingPropertiesPath;
    }
}
