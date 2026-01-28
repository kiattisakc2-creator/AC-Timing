package racetimingms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

@Slf4j
@Service
public class CheckpointService extends DatabaseService {

    @Autowired
    private CheckpointSchedulerService checkpointSchedulerService;

    @Autowired
    @Qualifier("transactdbJdbcTemplate")
    protected JdbcTemplate jdbcTemplateTrans;

    @Value("${app.sync-checkpointStatus-enabled}")
    private boolean syncCheckpointStatusEnabled;

    @PostConstruct
    public void onServerStart() {
        log.info("Restoring daily scheduled tasks after server restart...");
        initializeDailyScheduler();
    }

    @Scheduled(cron = "${app.sync-checkpointStatus}")
    @Transactional(readOnly = true)
    public void initializeDailyScheduler() {
        if (!syncCheckpointStatusEnabled) {
            log.info("CheckpointStatus synchronization is disabled.");
            return;
        }

        log.info("Starting daily CheckpointStatus synchronization...");
        // ดึงข้อมูล cutOffTime สำหรับวันนี้
        String sql = """
                    SELECT cp.eventId, cp.id, DATE_FORMAT(cp.cutOffTime, '%H:%i') AS taskCutOffTime
                    FROM :database.checkpointMapping cp
                    INNER JOIN :database.event e ON e.id = cp.eventId
                    INNER JOIN :database.campaign c ON c.id = e.campaignId
                    WHERE cp.active = 1
                        AND cp.cutOffTime IS NOT NULL
                        AND cp.cutOffTime >= CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok')
                        AND cp.cutOffTime < DATE(CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok')) + INTERVAL 1 DAY
                        AND e.active = 1 AND COALESCE(e.isFinished, 0) = 0 AND c.active = 1
                """;
        List<Map<String, Object>> cutOffData = jdbcTemplateTrans.queryForList(replaceConstants(sql));

        // ตั้งค่า Task สำหรับ cutOffTime ที่ดึงมา
        checkpointSchedulerService.scheduleDailyTasks(cutOffData);
        log.info("Daily CheckpointStatus synchronization tasks initialized.");
    }
}
