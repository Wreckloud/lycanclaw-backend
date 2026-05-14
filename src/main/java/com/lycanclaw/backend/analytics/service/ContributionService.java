package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.DailyContributionItem;
import com.lycanclaw.backend.analytics.dto.DailyContributionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContributionService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Value("${lycan.analytics.repo-path:D:/Portfolio/Website/LycanClaw}")
    private String repoPath;

    @Value("${lycan.analytics.zone-id:Asia/Shanghai}")
    private String zoneId;

    @Value("${lycan.analytics.days:365}")
    private int defaultDays;

    @Value("${lycan.analytics.scope:docs/thoughts,docs/knowledge}")
    private String scopeRaw;

    public DailyContributionResponse getDailyContributions(Integer daysParam) {
        int days = normalizeDays(daysParam);
        ZoneId zone = ZoneId.of(zoneId);
        LocalDate today = LocalDate.now(zone);
        LocalDate startDate = today.minusDays(days - 1L);

        List<String> scope = parseScope(scopeRaw);
        Map<LocalDate, ContributionCounter> dailyMap = initDateBuckets(startDate, today);
        fillFromGitLog(dailyMap, startDate, scope);

        List<DailyContributionItem> items = new ArrayList<>(dailyMap.size());
        for (Map.Entry<LocalDate, ContributionCounter> entry : dailyMap.entrySet()) {
            ContributionCounter counter = entry.getValue();
            items.add(new DailyContributionItem(
                    entry.getKey().format(DATE_FORMATTER),
                    counter.additions,
                    counter.deletions,
                    counter.additions + counter.deletions
            ));
        }

        return new DailyContributionResponse(
                Instant.now().toString(),
                zone.getId(),
                "新增+删除",
                days,
                scope,
                items
        );
    }

    private int normalizeDays(Integer daysParam) {
        int raw = daysParam == null ? defaultDays : daysParam;
        if (raw < 1) return 1;
        return Math.min(raw, 3660);
    }

    private List<String> parseScope(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private Map<LocalDate, ContributionCounter> initDateBuckets(LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, ContributionCounter> map = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            map.put(date, new ContributionCounter());
        }
        return map;
    }

    private void fillFromGitLog(
            Map<LocalDate, ContributionCounter> dailyMap,
            LocalDate startDate,
            List<String> scope
    ) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repoPath);
        command.add("log");
        command.add("--since=" + startDate.format(DATE_FORMATTER));
        command.add("--numstat");
        command.add("--pretty=format:%ad");
        command.add("--date=short");
        command.add("--");
        command.addAll(scope);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            parseGitLogOutput(process, dailyMap);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("读取 Git 日志失败，退出码: " + exitCode);
            }
        } catch (IOException e) {
            throw new IllegalStateException("执行 Git 命令失败，请确认服务器已安装 Git", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读取 Git 日志时线程被中断", e);
        }
    }

    private void parseGitLogOutput(Process process, Map<LocalDate, ContributionCounter> dailyMap) throws IOException {
        LocalDate currentDate = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    currentDate = LocalDate.parse(trimmed, DATE_FORMATTER);
                    continue;
                }

                if (currentDate == null || !dailyMap.containsKey(currentDate)) {
                    continue;
                }

                String[] parts = trimmed.split("\\s+");
                if (parts.length < 3) {
                    continue;
                }

                int additions = parseNumStatValue(parts[0]);
                int deletions = parseNumStatValue(parts[1]);
                ContributionCounter counter = dailyMap.get(currentDate);
                counter.additions += additions;
                counter.deletions += deletions;
            }
        }
    }

    private int parseNumStatValue(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static class ContributionCounter {
        int additions;
        int deletions;
    }
}
