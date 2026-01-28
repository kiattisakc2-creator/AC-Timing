package racetimingms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;

@Service
public class RFIDSyncDisallowService {

    @Autowired
    @Qualifier("transactdbJdbcTemplate")
    protected JdbcTemplate jdbcTemplateTrans;

    // Automatically runs every 1 minute to disable RFID Sync when the campaign is
    // over
    @Transactional
    public void updateRFIDSyncStatusAutomatically() {
        updateRFIDSyncStatus(); // Call the same logic for manual and automatic
    }

    // Disable RFID Sync after campaign ends
    @Transactional
    public void updateRFIDSyncStatus() {
        String query = """
                    UPDATE campaign c
                    SET c.allowRFIDSync = 0
                    WHERE c.manualOverride = 0
                      AND c.id IN (
                        SELECT e.campaignId
                        FROM event e
                        WHERE CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok') > DATE_ADD(e.eventDate, INTERVAL IFNULL(e.timeLimit, 3) HOUR)
                        GROUP BY e.campaignId
                        HAVING COUNT(*) = SUM(
                          CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok') > DATE_ADD(e.eventDate, INTERVAL IFNULL(e.timeLimit, 3) HOUR)
                        )
                      )
                      AND c.allowRFIDSync = 1
                """;

        jdbcTemplateTrans.update(query);
    }

    // Manually enable RFID Sync
    @Transactional
    public void enableRFIDSyncManually(Integer campaignId) {
        boolean afterCampaign = isAfterCampaign(campaignId);

        String query = """
                UPDATE campaign c
                SET c.allowRFIDSync = 1,
                    c.manualEnableTime = ?""" +
                (afterCampaign ? ", c.manualOverride = 1" : "") +
                """
                            WHERE c.id = ?
                        """;

        jdbcTemplateTrans.update(query, LocalDateTime.now(), campaignId);
    }

    // Determine if campaign is over
    private boolean isAfterCampaign(Integer campaignId) {
        String query = """
                    SELECT COUNT(*) FROM event e
                    WHERE e.campaignId = ?
                      AND CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok') > DATE_ADD(e.eventDate, INTERVAL IFNULL(e.timeLimit, 3) HOUR)
                """;

        Integer eventCount = jdbcTemplateTrans.queryForObject(query, Integer.class, campaignId);
        return eventCount != null && eventCount > 0;
    }
}
