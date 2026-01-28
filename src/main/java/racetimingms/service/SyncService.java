package racetimingms.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.time.temporal.ChronoUnit;
import java.time.Duration;

@Service
@Slf4j
@Component
public class SyncService extends DatabaseService {

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private int previousRecordCount = 0;

    @Value("${app.sync-cron}")
    private String syncCron;

    @Value("${app.sync-rfid-enabled}")
    private boolean syncRfidEnabled;

    public boolean wasLastSyncError(int campaignId) {
        String masterSql = """
                SELECT status
                FROM :database.rfid_sync_log
                WHERE details LIKE '%Master%'
                AND campaign_id = ?
                ORDER BY createdTime DESC
                LIMIT 1
                """;

        String splitSql = """
                SELECT status
                FROM :database.rfid_sync_log
                WHERE details LIKE '%Split%'
                AND campaign_id = ?
                ORDER BY createdTime DESC
                LIMIT 1
                """;

        try {
            Map<String, Object> masterResult = jdbcTemplate.queryForMap(replaceConstants(masterSql), campaignId);
            String masterStatus = (String) masterResult.get("status");

            Map<String, Object> splitResult = jdbcTemplate.queryForMap(replaceConstants(splitSql), campaignId);
            String splitStatus = (String) splitResult.get("status");

            return "Failed".equalsIgnoreCase(masterStatus) || "Failed".equalsIgnoreCase(splitStatus);
        } catch (DataAccessException e) {
            log.error("Error checking last sync status: {}", e.getMessage());
            return true; // Assume error if we cannot query the log
        }
    }

    public List<Map<String, Object>> getAllCampaignSyncErrors() throws SQLException {
        log.info("Starting getAllCampaignSyncErrors - checking sync status for all campaigns");
        
        String latestMasterSql = """
                SELECT campaign_id, MAX(updatedTime) AS maxTime
                FROM :database.rfid_sync_log
                WHERE details LIKE '%Master%'
                GROUP BY campaign_id
                """;

        String latestSplitSql = """
                SELECT campaign_id, MAX(updatedTime) AS maxTime
                FROM :database.rfid_sync_log
                WHERE details LIKE '%Split%'
                GROUP BY campaign_id
                """;

        Map<Integer, Boolean> campaignErrorStatus = new HashMap<>();

        try {
            List<Map<String, Object>> latestMasterResults = jdbcTemplate
                    .queryForList(replaceConstants(latestMasterSql));
            log.info("Found {} campaigns with Master sync logs", latestMasterResults.size());
            for (Map<String, Object> result : latestMasterResults) {
                Integer campaignId = (Integer) result.get("campaign_id");
                Object maxTime = result.get("maxTime");
                log.info("Processing Master sync for campaignId={}, maxTime={}", campaignId, maxTime);
                
                String masterStatusSql = """
                        SELECT status
                        FROM :database.rfid_sync_log
                        WHERE campaign_id = ? AND updatedTime = ?
                        LIMIT 1
                        """;
                String status = jdbcTemplate.queryForObject(replaceConstants(masterStatusSql),
                        new Object[] { campaignId, maxTime }, String.class);
                log.info("Master sync status for campaignId={}: {}", campaignId, status);
                
                if ("Failed".equalsIgnoreCase(status)) {
                    log.warn("Master sync FAILED for campaignId={}", campaignId);
                    campaignErrorStatus.put(campaignId, true);
                } else {
                    campaignErrorStatus.putIfAbsent(campaignId, false);
                }
            }

            List<Map<String, Object>> latestSplitResults = jdbcTemplate.queryForList(replaceConstants(latestSplitSql));
            log.info("Found {} campaigns with Split sync logs", latestSplitResults.size());
            
            for (Map<String, Object> result : latestSplitResults) {
                Integer campaignId = (Integer) result.get("campaign_id");
                Object maxTime = result.get("maxTime");
                log.info("Processing Split sync for campaignId={}, maxTime={}", campaignId, maxTime);
                
                String splitStatusSql = """
                        SELECT status
                        FROM :database.rfid_sync_log
                        WHERE campaign_id = ? AND updatedTime = ?
                        LIMIT 1
                        """;
                String status = jdbcTemplate.queryForObject(replaceConstants(splitStatusSql),
                        new Object[] { campaignId, maxTime }, String.class);
                log.info("Split sync status for campaignId={}: {}", campaignId, status);
                
                if ("Failed".equalsIgnoreCase(status)) {
                    log.warn("Split sync FAILED for campaignId={}", campaignId);
                    campaignErrorStatus.put(campaignId, true);
                } else {
                    // Only set to false if it does not exist yet
                    campaignErrorStatus.putIfAbsent(campaignId, false);
                }
            }

        } catch (DataAccessException e) {
            log.error("Error checking sync status - Exception type: {}, Message: {}", 
                e.getClass().getSimpleName(), e.getMessage(), e);
            log.error("SQL State: {}", e.getCause() != null ? e.getCause().getMessage() : "N/A");
            // Handle the error according to your requirements, e.g., set all campaigns to
            // error
        }

        // Convert the map to a list of maps with "id" and "status" keys
        log.info("Campaign error status map: {}", campaignErrorStatus);
        
        List<Map<String, Object>> responseList = new ArrayList<>();
        for (Map.Entry<Integer, Boolean> entry : campaignErrorStatus.entrySet()) {
            Map<String, Object> map = new HashMap<>();
            String campaignUuid = getUuidById("campaign", entry.getKey());
            log.info("Mapping campaignId={} to uuid={}, hasError={}", 
                entry.getKey(), campaignUuid, entry.getValue());
            
            map.put("uuid", campaignUuid);
            map.put("status", entry.getValue());
            responseList.add(map);
        }
        
        log.info("getAllCampaignSyncErrors completed - returning {} campaigns, {} with errors", 
            responseList.size(), 
            responseList.stream().filter(m -> Boolean.TRUE.equals(m.get("status"))).count());
        
        return responseList;

    }

    private int getCronIntervalInMinutes(String cronExpression) {
        // Extract the interval from the cron expression
        String[] parts = cronExpression.split(" ");
        if (parts.length > 1 && parts[1].startsWith("*/")) {
            return Integer.parseInt(parts[1].substring(2));
        }
        // Default to 5 minutes if the cron expression is not in the expected format
        return 5;
    }

    public Map<String, Object> getSyncData(String id) throws SQLException {
        Map<String, Object> syncData = new HashMap<>();

        String sql = """
                SELECT id, allowRFIDSync FROM :database.campaign WHERE uuid = ?
                """;
        Map<String, Object> result = jdbcTemplate.queryForMap(replaceConstants(sql), new Object[] { id });

        Integer campaignId = (Integer) result.get("id");
        Integer allowRFIDSyncInt = (Integer) result.get("allowRFIDSync");
        Boolean allowRFIDSync = allowRFIDSyncInt != null && allowRFIDSyncInt == 1;

        // ✅ Total raw data size
        String dataSizeSql = "SELECT SUM(OCTET_LENGTH(rawData)) FROM :database.rfid_raw_split_data WHERE campaignId = ?";
        Long totalDataSize = jdbcTemplate.queryForObject(replaceConstants(dataSizeSql), new Object[] { campaignId },
                Long.class);
        syncData.put("totalDataSize", totalDataSize);

        // ✅ Last error detail
        String errorListSql = """
                SELECT DISTINCT details
                FROM :database.rfid_sync_log
                WHERE campaign_id = ? AND status = 'Failed'
                ORDER BY updatedTime DESC
                LIMIT 10
                """;

        List<String> errorDetails = jdbcTemplate.queryForList(
                replaceConstants(errorListSql), new Object[] { campaignId }, String.class);

        // ✅ Last error time
        String lastErrorSql = """
                SELECT MAX(updatedTime) FROM :database.rfid_sync_log
                WHERE campaign_id = ? AND status = 'Failed'
                """;
        Timestamp lastErrorTime = jdbcTemplate.queryForObject(replaceConstants(lastErrorSql), Timestamp.class,
                campaignId);

        // ✅ Last completed time
        String lastCompletedSql = """
                SELECT MAX(updatedTime) FROM :database.rfid_sync_log
                WHERE campaign_id = ? AND status = 'Completed'
                """;
        Timestamp lastCompletedTime = jdbcTemplate.queryForObject(replaceConstants(lastCompletedSql), Timestamp.class,
                campaignId);

        // ✅ Status
        String runningStatus = (allowRFIDSync && syncRfidEnabled) ? "running" : "stopped";

        syncData.put("status", runningStatus);
        syncData.put("errorDetails", errorDetails);
        syncData.put("lastErrorTime", lastErrorTime);
        syncData.put("lastCompletedTime", lastCompletedTime);

        return syncData;
    }
}
