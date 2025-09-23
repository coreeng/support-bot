package com.coreeng.supportbot.teams.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelmClient {
    private static final Logger log = LoggerFactory.getLogger(HelmClient.class);

    public void install(String releaseName, String chartPath, String namespace, Map<String, String> values) throws IOException, InterruptedException {
        log.info("Attempting to deploy Helm chart...");
        List<String> command = new ArrayList<>();
        command.add("helm");
        command.add("install");
        command.add(releaseName);
        command.add(chartPath);
        command.add("--namespace");
        command.add(namespace);
        if (values != null && !values.isEmpty()) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                command.add("--set");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        command.add("--atomic");
        command.add("--wait");

        ProcessBuilder helmProcessBuilder = new ProcessBuilder(command);
        helmProcessBuilder.inheritIO();
        Process helmInstallProcess = helmProcessBuilder.start();
        int helmExitCode = helmInstallProcess.waitFor();
        if (helmExitCode != 0) {
            throw new RuntimeException("Helm install failed with exit code " + helmExitCode);
        }
        log.info("Helm chart deployed successfully.");
    }

    public void uninstall(String releaseName, String namespace) throws IOException, InterruptedException {
        log.info("Uninstalling Helm release...");
        Process helmUninstallProcess = new ProcessBuilder("helm", "uninstall", releaseName, "--namespace", namespace).inheritIO().start();
        helmUninstallProcess.waitFor();
        log.info("Helm release uninstalled.");
    }

    public boolean isReleaseDeployed(String releaseName, String namespace) throws IOException, InterruptedException {
        log.info("Checking for existing Helm release...");
        Process helmListProcess = new ProcessBuilder("helm", "list", "-n", namespace, "-f", releaseName).start();
        String helmListOutput = new BufferedReader(new InputStreamReader(helmListProcess.getInputStream()))
                .lines().collect(java.util.stream.Collectors.joining("\n"));
        int helmListExitCode = helmListProcess.waitFor();
        log.info("Helm list output:\n{}", helmListOutput);
        return helmListExitCode == 0 && helmListOutput.contains(releaseName);
    }
}
