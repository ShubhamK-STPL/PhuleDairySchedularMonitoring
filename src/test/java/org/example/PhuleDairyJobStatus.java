package org.example;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.testng.annotations.Test;
import java.util.*;

public class PhuleDairyJobStatus {
@Test
public void sendHangfireJobStatusToTeams() throws Exception {
    // 🔹 Load secrets from config.properties
    String TEAMS_WEBHOOK_URL = ConfigLoader.get("TEAMS_WEBHOOK_URL");
    String HANGFIRE_URL = ConfigLoader.get("HANGFIRE_URL");

        System.out.println("🚀 Starting job status extraction from: " + HANGFIRE_URL);

        // ✅ Setup WebDriver (Headless)
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--window-size=1920,1080");
        WebDriver driver = new ChromeDriver(options);
        driver.get(HANGFIRE_URL);

        // ✅ Extract Job Data
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

        // ✅ Predefined Job IDs
        String[] jobIds = {
                "Call-NextOccurrence",
                "Call-NotificationBeforeStart",
                "Call-NotificationBeforeComplete"
        };

        // ✅ Build Markdown Table for Teams
        StringBuilder message = new StringBuilder();
        message.append("### ✅ Hangfire Scheduler Job Status\n");
        message.append("Below is the latest job status report:\n\n");
        message.append("| Job ID | Cron | Next Execution | Last Execution | Status |\n");
        message.append("|--------|------|----------------|----------------|--------|\n");

        for (String jobId : jobIds) {
            String cron = "N/A", nextExec = "N/A", lastExec = "N/A", statusIcon = "⚪", status = "Unknown";

            if (jobData.containsKey(jobId)) {
                String[] details = jobData.get(jobId);
                cron = details[0];
                nextExec = details[1];
                lastExec = details[2];

                // ✅ Determine Status
                if (nextExec.equalsIgnoreCase("N/A")) {
                    statusIcon = "⚪"; status = "No Schedule";
                } else if (nextExec.toLowerCase().contains("in") || nextExec.toLowerCase().contains("minutes")) {
                    statusIcon = "🟢"; status = "Scheduled";
                } else if (nextExec.toLowerCase().contains("ago")) {
                    statusIcon = "🟡"; status = "Recently Executed";
                } else {
                    statusIcon = "🔴"; status = "Issue";
                }
            }

            message.append("| `").append(jobId).append("` | `").append(cron).append("` | ")
                    .append(nextExec).append(" | ").append(lastExec).append(" | ")
                    .append(statusIcon).append(" ").append(status).append(" |\n");
        }

        // ✅ Send to Teams Webhook
        sendToTeams(TEAMS_WEBHOOK_URL, message.toString());
    }

    // ---------------- SEND TO TEAMS ----------------
    public static void sendToTeams(String webhookUrl, String message) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(webhookUrl);
            post.setHeader("Content-Type", "application/json");
            String payload = "{ \"text\": \"" + message.replace("\"", "\\\"").replace("\n", "\\n") + "\" }";
            post.setEntity(new StringEntity(payload));
            client.execute(post);
            System.out.println("✅ Message sent to Teams successfully!");
        } catch (Exception e) {
            System.out.println("❌ Failed to send message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
