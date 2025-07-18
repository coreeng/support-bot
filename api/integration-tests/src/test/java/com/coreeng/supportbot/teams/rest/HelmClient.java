package com.coreeng.supportbot.teams.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class HelmClient {
    private static final Logger log = LoggerFactory.getLogger(HelmClient.class);

    public void install(String releaseName, String chartPath, String namespace) throws IOException, InterruptedException {
        log.info("Attempting to deploy Helm chart...");
        ProcessBuilder helmProcessBuilder = new ProcessBuilder("helm", "install", releaseName, chartPath, "--namespace", namespace, "--atomic", "--wait");
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
