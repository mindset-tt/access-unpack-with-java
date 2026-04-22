package com.access.unpack.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class EnvironmentProbe {
    public Map<String, Object> inspect(ExtractionRequest request) {
        Map<String, Object> environment = new LinkedHashMap<>();
        String osName = System.getProperty("os.name", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");
        environment.put("capturedAt", Instant.now());
        environment.put("osName", osName);
        environment.put("osArch", osArch);
        environment.put("javaVersion", System.getProperty("java.version", "unknown"));
        environment.put("javaVendor", System.getProperty("java.vendor", "unknown"));
        environment.put("inputPath", request.input().toAbsolutePath().toString());
        environment.put("outputPath", request.output().toAbsolutePath().toString());
        environment.put("runningOnWindows", osName.toLowerCase(Locale.ROOT).contains("win"));
        environment.put("windowsAccessAvailable", detectAccessAutomation(osName));
        environment.put("workingDirectory", request.output().toAbsolutePath().getParent() != null
                ? request.output().toAbsolutePath().getParent().toString()
                : request.output().toAbsolutePath().toString());
        return environment;
    }

    private boolean detectAccessAutomation(String osName) {
        if (!osName.toLowerCase(Locale.ROOT).contains("win")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "$ErrorActionPreference='Stop'; try { $a = New-Object -ComObject Access.Application; $a.Quit(); "
                            + "[System.Runtime.InteropServices.Marshal]::ReleaseComObject($a) | Out-Null; "
                            + "Write-Output 'true' } catch { Write-Output 'false' }")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String output = reader.lines().collect(Collectors.joining("\n")).trim();
                process.waitFor();
                return "true".equalsIgnoreCase(output);
            }
        } catch (Exception ignored) {
            return false;
        }
    }
}
