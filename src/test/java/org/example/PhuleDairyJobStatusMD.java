package org.example;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;
import org.testng.annotations.Test;

import java.util.*;

public class PhuleDairyJobStatusMD {

    @Test
    public void sendHangfireJobStatusToTeams() throws Exception {
        // ✅ Read secrets from environment variables (works in GitHub Actions)
        String TEAMS_WEBHOOK_URL = System.getenv("TEAMS_WEBHOOK_URL");
        String HANGFIRE_URL = System.getenv("HANGFIRE_URL");

        if (TEAMS_WEBHOOK_URL == null || HANGFIRE_URL == null) {
            throw new RuntimeException("❌ Missing environment variables: TEAMS_WEBHOOK_URL or HANGFIRE_URL");
        }

        System.out.println("🚀 Starting job status extraction from: " + HANGFIRE_URL);

        // ✅ Setup WebDriver (Headless Mode for CI)
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--window-size=1920,1080");
        WebDriver driver = new ChromeDriver(options);
        driver.get(HANGFIRE_URL);

        // ✅ Extract job data from Hangfire table
        Map<String, String[]> jobData = new HashMap<>();
        List<WebElement> rows = driver.findElements(By.cssSelector("table tbody tr"));

        for (WebElement row : rows) {
            List<WebElement> cols = row.findElements(By.tagName("td"));
            if (cols.size() >= 8) {
                String jobId = cols.get(1).getText().trim();
                String cron = cols.get(2).getText().trim();
                String nextExec = cols.get(5).getText().trim();
                String lastExec = cols.get(6).getText().trim();
                jobData.put(jobId, new String[]{cron, nextExec, lastExec});
            }
        }
        driver.quit();
        System.out.println("✅ Job data extracted successfully!");

        // ✅ List of predefined jobs to monitor
        String[] jobIds = {
                "Call-NextOccurrence",
                "Call-NotificationBeforeStart",
                "Call-NotificationBeforeComplete"
        };

        // ✅ Build Markdown Table
        StringBuilder markdownTable = new StringBuilder();
        markdownTable.append("🚀 **Hangfire Scheduler Job Status**\n\n");
        markdownTable.append("| Job ID | Cron | Next Execution | Last Execution | Status |\n");
        markdownTable.append("|--------|------|----------------|----------------|--------|\n");

        boolean hasIssue = false;

        for (String jobId : jobIds) {
            String cron = "N/A", nextExec = "N/A", lastExec = "N/A", statusIcon = "⚪", status = "Unknown";

            if (jobData.containsKey(jobId)) {
                String[] details = jobData.get(jobId);
                cron = details[0];
                nextExec = details[1];
                lastExec = details[2];

                if (nextExec.equalsIgnoreCase("N/A")) {
                        statusIcon = "🔴"; status = "No Schedule"; hasIssue = true;
                    } else if (lastExec.equalsIgnoreCase("N/A")) {
                        statusIcon = "⚪"; status = "Scheduled (Never Executed)";
                    } else if (nextExec.toLowerCase().contains("in") || nextExec.toLowerCase().contains("minutes")) {
                        statusIcon = "🟢"; status = "Scheduled";
                    } else if (nextExec.toLowerCase().contains("ago")) {
                        statusIcon = "🟡"; status = "Recently Executed";
                    } else {
                        statusIcon = "🔴"; status = "Issue"; hasIssue = true;
                    }

            } else {
                hasIssue = true;
            }

            markdownTable.append("| ")
                    .append(statusIcon).append(" ").append(jobId).append(" | `")
                    .append(cron).append("` | ")
                    .append(nextExec).append(" | ")
                    .append(lastExec).append(" | ")
                    .append(status).append(" |\n");
        }

        // ✅ Select Teams Card Theme Color
        String themeColor = hasIssue ? "FF0000" : "00CC00";

        // ✅ Build MessageCard payload with Markdown table
        String payload = "{"
                + "\"@type\": \"MessageCard\","
                + "\"@context\": \"https://schema.org/extensions\","
                + "\"themeColor\": \"" + themeColor + "\","
                + "\"summary\": \"Hangfire Job Status\","
                + "\"sections\": [{"
                + "\"activityTitle\": \"Hangfire Job Report\","
                + "\"text\": \"" + markdownTable.toString().replace("\"", "\\\"").replace("\n", "\\n") + "\""
                + "}]"
                + "}";

        // ✅ Send to Microsoft Teams
        sendToTeams(TEAMS_WEBHOOK_URL, payload);
    }

    // ✅ Helper to send JSON payload to Teams Webhook
    public static void sendToTeams(String webhookUrl, String payload) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(webhookUrl);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));
            client.execute(post);
            System.out.println("✅ Teams notification sent successfully!");
        } catch (Exception e) {
            System.out.println("❌ Failed to send Teams notification: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
