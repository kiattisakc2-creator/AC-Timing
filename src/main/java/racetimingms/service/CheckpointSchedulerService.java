package racetimingms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
public class CheckpointSchedulerService extends DatabaseService {

    @Autowired
    protected RunnerService runnerService;

    @Value("${app.local-timezone}")
    private String localTimezone;

    private final TaskScheduler taskScheduler;
    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>(); // เก็บ Task ทั้งหมดของวันนั้น

    public CheckpointSchedulerService(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public void scheduleDailyTasks(List<Map<String, Object>> cutOffTimes) {
        // ยกเลิก Task เดิมทั้งหมดของวันก่อน
        cancelAllTasks();
        for (Map<String, Object> cutOffData : cutOffTimes) {
            // ดึงข้อมูลจาก Map
            String taskCutOffTime = (String) cutOffData.get("taskCutOffTime");
            Integer eventId = (Integer) cutOffData.get("eventId");
            Integer id = (Integer) cutOffData.get("id");

            // ตั้ง Task ด้วย TaskScheduler
            Trigger trigger = createTrigger(taskCutOffTime);
            ScheduledFuture<?> future = taskScheduler.schedule(() -> executeTask(eventId, id),
                    trigger);

            if (future != null) {
                scheduledTasks.add(future);
            } else {
                log.error("Failed to schedule task for eventId: {}, at {}", eventId, taskCutOffTime);
            }
        }
    }

    // การสร้าง Trigger สำหรับเวลา
    private Trigger createTrigger(String time) {
        try {
            String[] timeParts = time.split(":");
            // แปลงเวลาจาก Asia/Bangkok (UTC+7) เป็น UTC
            LocalTime localTime = LocalTime.of(Integer.parseInt(timeParts[0]), Integer.parseInt(timeParts[1]));
            ZonedDateTime utcTime = localTime.atDate(LocalDate.now()).atZone(ZoneId.of(localTimezone))
                    .withZoneSameInstant(ZoneId.systemDefault());

            String cronExpression = String.format("0 %d %d * * ?", utcTime.getMinute(), utcTime.getHour());

            return new CronTrigger(cronExpression);
        } catch (Exception e) {
            log.error("Invalid time format for taskCutOffTime: {}", time, e);
            throw new IllegalArgumentException("Invalid time format for CronTrigger", e);
        }
    }

    private void executeTask(Integer eventId, Integer id) {
        log.info("Executing task for eventId: {}", eventId);
        try {
            runnerService.updateStatusParticipant(id);
        } catch (SQLException e) {
            log.error("Error updating participant status for eventId: {}: {}", eventId, e.getMessage(), e);
        }
    }

    public void cancelAllTasks() {
        for (ScheduledFuture<?> task : scheduledTasks) {
            if (task != null) {
                task.cancel(false); // ยกเลิก Task
            }
        }
        scheduledTasks.clear(); // ล้างรายการ Task
    }

}
