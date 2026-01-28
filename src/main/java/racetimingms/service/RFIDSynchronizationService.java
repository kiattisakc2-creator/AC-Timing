package racetimingms.service;

import lombok.extern.slf4j.Slf4j;
import racetimingms.exception.SyncException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;

@Service
@Slf4j
public class RFIDSynchronizationService extends DatabaseService {

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("namedJdbcTemplate")
    private NamedParameterJdbcTemplate namedJdbcTemplate;

    @Autowired
    private RaceService raceService;

    @Value("${app.sync-rfid-enabled}")
    private boolean syncRfidEnabled;

    @Value("${app.sync-cron}")
    private String syncCron;

    @Value("${app.sync-eror-mail-enabled}")
    private boolean syncErorMailEnabled;

    @Value("${app.sync-eror-mail-target}")
    private String syncErorMailTarget;

    @Value("${app.server-timezone}")
    private String serverTimezone;

    @Value("${app.local-timezone}")
    private String localTimezone;

    @Autowired
    private EmailService emailService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<Integer, String> lastFailedMessageMap = new ConcurrentHashMap<>();

    @Scheduled(cron = "${app.sync-cron}")
    public void synchronizeRFIDTags() {
        if (!syncRfidEnabled) {
            log.info("RFID synchronization is disabled.");
            return;
        }

        log.info("Start Master and Time Record Synchronization");
        synchronizeMasterData();
        syncTimeRecord();
        log.info("Completed Master and Time Record Synchronization");
    }

    private void syncTimeRecord() {
        log.info("Start sync time record");
        String sql = "SELECT id, rfid_token, raceid FROM :database.campaign WHERE allowRFIDSync = 1 and active = 1 and status='active'";
        List<Map<String, Object>> campaigns = jdbcTemplate.queryForList(replaceConstants(sql));

        for (Map<String, Object> campaign : campaigns) {
            int raceId = (int) campaign.get("raceid");
            String token = (String) campaign.get("rfid_token");
            int campaignId = (int) campaign.get("id");
            LocalDateTime startTime = LocalDateTime.now();
            try {
                int totalRecords = synchronizeCampaignRFIDTags(campaignId, raceId, token);
                LocalDateTime endTime = LocalDateTime.now();
                logSync(campaignId, "Completed", "Split Synchronization completed successfully.", 1, startTime, endTime,
                        totalRecords);
            } catch (SyncException e) {
                handleSyncException(e, campaignId, startTime, 0);
            } catch (Exception e) {
                handleGeneralException(e, campaignId, startTime, 0);
            }
        }
        log.info("Completed sync time record");
    }

    private void clearTimeRecordsForCampaign(int campaignId) {
        String fetchSql = """
                SELECT cm.id
                FROM :database.checkpointMapping cm
                JOIN :database.station s ON cm.stationId = s.id
                WHERE s.campaignId = ?
                """;
        List<Integer> checkpointMappingIds = jdbcTemplate.queryForList(replaceConstants(fetchSql),
                new Object[] { campaignId }, Integer.class);

        if (!checkpointMappingIds.isEmpty()) {
            String deleteSql = """
                    DELETE FROM :database.timeRecord
                    WHERE checkpointMappingId IN (:checkpointMappingIds) AND recordType = 'rfid'
                    """;
            SqlParameterSource params = new MapSqlParameterSource("checkpointMappingIds", checkpointMappingIds);
            namedJdbcTemplate.update(replaceConstants(deleteSql), params);
        }
    }

    private String compressString(String data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data.getBytes("UTF-8"));
            gzipOut.close();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Error compressing data", e);
        }
    }

    private void updateLastUpdatedTime(Integer id) {
        String sql = """
                UPDATE :database.rfid_raw_split_data
                SET lastUpdatedTime = CURRENT_TIMESTAMP,
                    count = count + 1
                WHERE id = ?
                """;
        jdbcTemplate.update(replaceConstants(sql), id);
    }

    private Integer getExistingEntryId(Integer campaignId, int raceId, int page, String compressedData) {
        String sql = """
                SELECT id FROM :database.rfid_raw_split_data
                WHERE campaignId = ? AND raceId = ? AND page = ? AND rawData = ?
                """;
        try {
            return jdbcTemplate.queryForObject(replaceConstants(sql), Integer.class, campaignId, raceId, page,
                    compressedData);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void storeRawSplitData(Integer campaignId, int raceId, int page, String rawData) {
        String sql = """
                INSERT INTO :database.rfid_raw_split_data (campaignId, raceId, page, rawData, createdBy, updatedBy)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(replaceConstants(sql), campaignId, raceId, page, rawData, 1, 1);
    }

    private int synchronizeCampaignRFIDTags(Integer campaignId, int raceId, String token) {
        int page = 1;
        int totalRecords = 0;
        while (true) {
            String response = raceService.pullSplit(raceId, token, page);

            // String compressedData = compressString(rawData);

            Integer existingId = getExistingEntryId(campaignId, raceId, page, response);
            if (existingId != null) {
                updateLastUpdatedTime(existingId);
            } else {
                storeRawSplitData(campaignId, raceId, page, response);
            }

            List<Map<String, Object>> rfidDataList = parseResponse(response);

            if (rfidDataList.isEmpty()) {
                break;
            }

            for (Map<String, Object> rfidData : rfidDataList) {
                saveRFIDData(rfidData, campaignId);
                totalRecords++;
            }
            page++;
        }
        return totalRecords;
    }

    private List<Map<String, Object>> parseResponse(String response) {
        JSONObject jsonResponse = new JSONObject(response);
        JSONArray data = jsonResponse.getJSONArray("data");

        List<Map<String, Object>> rfidDataList = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.getJSONObject(i);
            Map<String, Object> rfidData = new HashMap<>();
            rfidData.put("EventId", entry.getInt("EventId"));
            rfidData.put("AthleteId", entry.getInt("AthleteId"));
            rfidData.put("TpName", entry.getString("TpName"));
            rfidData.put("PassTime", entry.getString("PassTime"));
            rfidData.put("SortOrder", entry.getInt("SortOrder"));
            rfidDataList.add(rfidData);
        }
        return rfidDataList;
    }

    private void saveRFIDData(Map<String, Object> rfidData, Integer campaignId) {
        Integer eventId = (Integer) rfidData.get("EventId");
        Integer athleteId = (Integer) rfidData.get("AthleteId");
        String tpName = (String) rfidData.get("TpName");

        // Convert PassTime from server timezone to local timezone
        String passTimeString = (String) rfidData.get("PassTime");
        LocalDateTime localDateTime;

        if (passTimeString.length() > 8) {
            localDateTime = LocalDateTime.parse(passTimeString, DATE_TIME_FORMATTER);
        } else {
            LocalTime passTime = LocalTime.parse(passTimeString, TIME_FORMATTER);
            LocalDate currentDate = LocalDate.now();
            localDateTime = LocalDateTime.of(currentDate, passTime);
        }

        ZonedDateTime serverZonedPassTime = localDateTime.atZone(ZoneId.of(serverTimezone));
        ZonedDateTime localZonedPassTime = serverZonedPassTime.withZoneSameInstant(ZoneId.of(localTimezone));
        LocalDateTime localPassTime = localZonedPassTime.toLocalDateTime();

        Integer checkpointMappingId = mapTpNameToCheckpointMappingId(tpName, campaignId, eventId);
        Integer participantId = mapAthleteIdToParticipantId(athleteId, campaignId, eventId);
        Integer id = findTimeRecordId(checkpointMappingId, participantId);

        if (checkpointMappingId == null || participantId == null) {
            throw new SyncException("Null checkpointMappingId or participantId for campaignId: " + campaignId
                    + ", tpName: " + tpName + ", eventId: " + eventId + ", athleteId: " + athleteId);
        }

        String sql = """
                INSERT INTO :database.timeRecord (id, checkpointMappingId, participantId, raceTimingIn, raceTimingOut, active, recordType, createdBy)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    raceTimingIn = VALUES(raceTimingIn),
                    raceTimingOut = VALUES(raceTimingOut),
                    updatedBy = VALUES(createdBy)
                """;
        jdbcTemplate.update(replaceConstants(sql), id, checkpointMappingId, participantId, localPassTime, localPassTime,
                true, "rfid", 1);
    }

    private Integer findTimeRecordId(Integer checkpointMappingId, Integer participantId) {
        String sql = """
                SELECT t.id FROM :database.timeRecord t WHERE t.checkpointMappingId = ? AND t.participantId = ? AND t.recordType = 'rfid' LIMIT 1
                """;
        List<Object> params = new ArrayList<>();
        params.add(checkpointMappingId);
        params.add(participantId);
        try {
            return jdbcTemplate.queryForObject(replaceConstants(sql), Integer.class, params.toArray());
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (DataAccessException e) {
            log.error("Error findTimeRecordId", e);
            throw new RuntimeException("Error findTimeRecordId", e);
        }
    }

    private Integer mapTpNameToCheckpointMappingId(String tpName, Integer campaignId, Integer eventId) {
        String sql = """
                SELECT cm.id
                FROM :database.checkpointMapping cm
                JOIN :database.station s ON cm.stationId = s.id
                JOIN :database.event e ON e.id = cm.eventId
                WHERE s.campaignId = ? AND s.name = ? AND e.rfidEventId = ?
                """;
        try {
            return jdbcTemplate.queryForObject(replaceConstants(sql), new Object[] { campaignId, tpName, eventId },
                    Integer.class);
        } catch (EmptyResultDataAccessException e) {
            log.error("No checkpoint mapping id found for campaignId: {}, tpName: {}, eventId: {}", campaignId, tpName,
                    eventId, e);
            return null;
        } catch (DataAccessException e) {
            log.error("Error mapping tpName to checkpointMappingId", e);
            throw new RuntimeException("Error mapping tpName to checkpointMappingId", e);
        }
    }

    private Integer mapAthleteIdToParticipantId(Integer athleteId, Integer campaignId, Integer eventId) {
        String sql = """
                SELECT DISTINCT p.id
                FROM :database.participant p
                JOIN :database.rfid_bio b ON p.bibNo = b.bib
                JOIN :database.campaign c ON c.id = b.campaignId
                JOIN :database.event e ON c.id = e.campaignId and e.id = p.eventId and b.eventId = e.rfidEventId
                WHERE b.athleteId = ? AND b.campaignId = ? AND e.rfidEventId = ?
                """;
        try {
            return jdbcTemplate.queryForObject(replaceConstants(sql), new Object[] { athleteId, campaignId, eventId },
                    Integer.class);
        } catch (EmptyResultDataAccessException e) {
            log.error("No participant found for athleteId: {} and campaignId {} and eventId {}: {}", athleteId,
                    campaignId, eventId, e);
            return null;
        } catch (DataAccessException e) {
            log.error("Error mapping athleteId to participantId athleteId: {} and campaignId {} and eventId {} : {}",
                    athleteId, campaignId, eventId, e);
            throw new RuntimeException("Error mapping athleteId to participantId", e);
        }
    }

    private void logSync(Integer campaignId, String status, String details, Integer userId,
            LocalDateTime startTime, LocalDateTime endTime, int recordCount) {

        long duration = Duration.between(startTime, endTime).toMillis();

        if ("Failed".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status)) {
            if (details != null && details.length() > 255) {
                details = details.substring(0, 252) + "...";
            }

            log.info("Sync {} for campaignId: {}, duration={} ms, records={}, details={}",
                    status, campaignId, duration, recordCount, details);

            // Check if a record already exists
            String checkSql = """
                    SELECT COUNT(*) FROM :database.rfid_sync_log
                    WHERE campaign_id = ? AND status = ? AND details = ?
                    """;
            Integer count = jdbcTemplate.queryForObject(
                    replaceConstants(checkSql), Integer.class, campaignId, status, details);

            if (count != null && count > 0) {
                // Update timestamp only
                String updateSql = """
                        UPDATE :database.rfid_sync_log
                        SET updatedTime = CURRENT_TIMESTAMP
                        WHERE campaign_id = ? AND status = ? AND details = ?
                        ORDER BY updatedTime DESC
                        LIMIT 1
                        """;
                jdbcTemplate.update(replaceConstants(updateSql), campaignId, status, details);
            } else {
                // Insert new log
                String insertSql = """
                        INSERT INTO :database.rfid_sync_log
                        (campaign_id, status, details, createdBy, updatedBy, duration, record_count)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;
                jdbcTemplate.update(replaceConstants(insertSql),
                        campaignId, status, details, userId, userId, duration, recordCount);
            }
        }

    }

    public void synchronizeMasterData() {
        log.info("Start master synchronization");

        String sql = "SELECT id, rfid_token, raceid FROM :database.campaign WHERE allowRFIDSync = 1 and status='active'";
        List<Map<String, Object>> campaigns = jdbcTemplate.queryForList(replaceConstants(sql));
        log.info("Number of campaigns: {}", campaigns.size());
        for (Map<String, Object> campaign : campaigns) {
            int raceId = (int) campaign.get("raceid");
            String token = (String) campaign.get("rfid_token");
            int campaignId = (int) campaign.get("id");
            LocalDateTime startTime = LocalDateTime.now();
            String jsonResponse;
            int totalRecords = 0;
            log.info("Getting master data for campaign {}", campaignId);
            try {
                jsonResponse = raceService.pullRaceInfo(raceId, token);
                totalRecords = storeRawMasterRaceData(jsonResponse, campaignId);

                int page = 1;

                while (true) {
                    String response = raceService.pullBio(raceId, token, page);
                    List<Map<String, Object>> bioDataList = parseBioResponse(response);

                    if (bioDataList.isEmpty()) {
                        break;
                    }

                    for (Map<String, Object> bioData : bioDataList) {
                        totalRecords += saveBioData(bioData, campaignId);
                    }
                    page++;
                }
                logSync(campaignId, "Completed", "Master Synchronization completed successfully.", 1, startTime,
                        LocalDateTime.now(), totalRecords);
            } catch (Exception e) {
                logSync(campaignId, "Failed", "Master Synchronization failed: " + e.getMessage(), 1, startTime,
                        LocalDateTime.now(), totalRecords);
            }
        }
        log.info("Completed master synchronization");
    }

    private List<Map<String, Object>> parseBioResponse(String response) {
        JSONObject jsonResponse = new JSONObject(response);
        JSONArray data = jsonResponse.getJSONArray("data");

        List<Map<String, Object>> bioDataList = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.getJSONObject(i);
            Map<String, Object> bioData = new HashMap<>();
            bioData.put("EventId", entry.getInt("EventId"));
            bioData.put("AthleteId", entry.getInt("AthleteId"));
            bioData.put("Name", entry.getString("Name"));
            bioData.put("EnName", entry.getString("EnName"));
            bioData.put("Gender", entry.getString("Gender"));
            bioData.put("Birthday", entry.getString("Birthday"));
            bioData.put("CountryRegion", entry.getString("CountryRegion"));
            bioData.put("Province", entry.getString("Province"));
            bioData.put("City", entry.getString("City"));
            bioData.put("Category", entry.getString("Category"));
            bioData.put("Category2", entry.getString("Category2"));
            bioData.put("TeamName", entry.getString("TeamName"));
            bioData.put("TeamId", entry.getInt("TeamId"));
            bioData.put("BIB", entry.getString("BIB"));
            bioData.put("ChipCode", entry.getString("ChipCode"));
            bioData.put("Phone", entry.getString("Phone"));
            bioData.put("WaveName", entry.getString("WaveName"));
            bioData.put("Age", entry.getInt("Age"));
            bioDataList.add(bioData);
        }
        return bioDataList;
    }

    public int storeRawMasterRaceData(String jsonResponse, int campaignId) {
        // Clear the existing data in the tables
        clearExistingData(campaignId);
        int totalRecords = 0;
        JSONObject jsonObject = new JSONObject(jsonResponse);
        JSONObject raceData = jsonObject.getJSONObject("data");

        int raceId = raceData.getInt("RaceId");
        String raceName = raceData.getString("RaceName");
        String raceType = raceData.getString("RaceType");
        String raceTime = raceData.getString("RaceTime");
        String address = raceData.getString("Address");

        log.info("Inserting race data: raceId={}, raceName={}", raceId, raceName);

        // Insert race data
        String raceSql = """
                INSERT INTO :database.rfid_race (id, name, type, time, address, createdBy, updatedBy, campaignId)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    type = VALUES(type),
                    time = VALUES(time),
                    address = VALUES(address),
                    updatedBy = VALUES(updatedBy),
                    campaignId = VALUES(campaignId)
                """;
        totalRecords = jdbcTemplate.update(replaceConstants(raceSql), raceId, raceName, raceType, raceTime, address, 1,
                1, campaignId);

        JSONArray events = raceData.getJSONArray("Events");

        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            int eventId = event.getInt("EventId");
            String eventName = event.getString("EventName");
            String distance = event.getString("Distance");
            int sortOrder = event.getInt("SortOrder");

            // Insert event data
            String eventSql = """
                    INSERT INTO :database.rfid_event (id, raceId, name, distance, sortOrder, createdBy, updatedBy, campaignId)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        raceId = VALUES(raceId),
                        name = VALUES(name),
                        distance = VALUES(distance),
                        sortOrder = VALUES(sortOrder),
                        updatedBy = VALUES(updatedBy),
                        campaignId = VALUES(campaignId)
                    """;
            totalRecords += jdbcTemplate.update(replaceConstants(eventSql), eventId, raceId, eventName, distance,
                    sortOrder, 1, 1, campaignId);

            JSONArray timingPoints = event.getJSONArray("TimingPoints");

            for (int j = 0; j < timingPoints.length(); j++) {
                JSONObject timingPoint = timingPoints.getJSONObject(j);
                int tpId = timingPoint.getInt("TpId");
                String tpName = timingPoint.getString("TpName");
                String tpDistance = timingPoint.getString("Distance");
                int tpSortOrder = timingPoint.getInt("SortOrder");

                // Insert timing point data
                String tpSql = """
                        INSERT INTO :database.rfid_timing_point (id, eventId, name, distance, sortOrder, createdBy, updatedBy, campaignId)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            eventId = VALUES(eventId),
                            name = VALUES(name),
                            distance = VALUES(distance),
                            sortOrder = VALUES(sortOrder),
                            updatedBy = VALUES(updatedBy),
                            campaignId = VALUES(campaignId)
                        """;
                totalRecords += jdbcTemplate.update(replaceConstants(tpSql), tpId, eventId, tpName, tpDistance,
                        tpSortOrder, 1, 1, campaignId);
            }
        }
        log.info("Race data stored successfully");
        return totalRecords;
    }

    private int saveBioData(Map<String, Object> bioData, int campaignId) {
        String uuid = java.util.UUID.randomUUID().toString();
        Integer eventId = (Integer) bioData.get("EventId");
        Integer athleteId = (Integer) bioData.get("AthleteId");
        int totalRecords = 0;

        String sql = """
                INSERT INTO :database.rfid_bio (uuid, eventId, athleteId, name, enName, gender, birthday, countryRegion, province, city, category, category2, teamName, teamId, bib, chipCode, phone, waveName, age, createdBy, updatedBy, campaignId)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    enName = VALUES(enName),
                    gender = VALUES(gender),
                    birthday = VALUES(birthday),
                    countryRegion = VALUES(countryRegion),
                    province = VALUES(province),
                    city = VALUES(city),
                    category = VALUES(category),
                    category2 = VALUES(category2),
                    teamName = VALUES(teamName),
                    teamId = VALUES(teamId),
                    bib = VALUES(bib),
                    chipCode = VALUES(chipCode),
                    phone = VALUES(phone),
                    waveName = VALUES(waveName),
                    age = VALUES(age),
                    updatedBy = VALUES(updatedBy),
                    campaignId = VALUES(campaignId)
                """;

        totalRecords = jdbcTemplate.update(replaceConstants(sql), uuid, eventId, athleteId, bioData.get("Name"),
                bioData.get("EnName"), bioData.get("Gender"), bioData.get("Birthday"), bioData.get("CountryRegion"),
                bioData.get("Province"), bioData.get("City"), bioData.get("Category"), bioData.get("Category2"),
                bioData.get("TeamName"), bioData.get("TeamId"),
                bioData.get("BIB"), bioData.get("ChipCode"), bioData.get("Phone"), bioData.get("WaveName"),
                bioData.get("Age"), 1, 1, campaignId);
        return totalRecords;
    }

    private void clearExistingData(int campaignId) {
        log.info("Start clear raw data");
        String clearTimingPointSql = """
                DELETE FROM :database.rfid_timing_point
                WHERE campaignId = ?
                """;
        String clearEventSql = """
                DELETE FROM :database.rfid_event
                WHERE campaignId = ?
                """;
        String clearRaceSql = """
                DELETE FROM :database.rfid_race
                WHERE campaignId = ?
                """;
        String clearBioSql = """
                DELETE FROM :database.rfid_bio
                WHERE campaignId = ?
                """;
        jdbcTemplate.update(replaceConstants(clearTimingPointSql), campaignId);
        jdbcTemplate.update(replaceConstants(clearEventSql), campaignId);
        jdbcTemplate.update(replaceConstants(clearRaceSql), campaignId);
        jdbcTemplate.update(replaceConstants(clearBioSql), campaignId);
        log.info("Completed clear raw data");
    }

    private void handleSyncException(SyncException e, int campaignId, LocalDateTime startTime, int totalRecords) {
        LocalDateTime endTime = LocalDateTime.now();
        logSync(campaignId, "Failed", "Split Synchronization failed: " + e.getMessage(), 1, startTime, endTime,
                totalRecords);
        log.error("Synchronization failed for campaignId: {} with error: {}", campaignId, e.getMessage(), e);
        if (syncErorMailEnabled) {
            emailService.sendSyncErrorEmail(syncErorMailTarget, e.getMessage());
        }
    }

    private void handleGeneralException(Exception e, int campaignId, LocalDateTime startTime, int totalRecords) {
        LocalDateTime endTime = LocalDateTime.now();
        logSync(campaignId, "Failed", "Split Synchronization failed: " + e.getMessage(), 1, startTime, endTime,
                totalRecords);
        log.error("Synchronization failed for campaignId: {} with error: {}", campaignId, e.getMessage(), e);
        if (syncErorMailEnabled) {
            emailService.sendSyncErrorEmail(syncErorMailTarget, e.getMessage());
        }
    }

}
