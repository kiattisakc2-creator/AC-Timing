package racetimingms.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;
import racetimingms.model.DatabaseData;
import racetimingms.model.JobControlFlag;
import racetimingms.model.LatestParticipantByCheckpoint;
import racetimingms.model.PagingData;
import racetimingms.model.ParticipantByEventData;
import racetimingms.model.ParticipantData;
import racetimingms.model.RunnerRank;
import racetimingms.model.request.ParticipantLiveRequest;
import racetimingms.model.request.RunnerRequest;
import racetimingms.model.request.UploadParticipantRequest;
import racetimingms.repository.JobControlFlagRepository;
import racetimingms.utils.IdNoGenerator;

@Slf4j
@Component
public class RunnerService extends DatabaseService {

    @Value("${app.sync-summaryResult-enabled}")
    private boolean syncSummaryResultEnabled;

    @Value("${app.sync-summaryLive-enabled}")
    private boolean syncSummaryLiveEnabled;

    @Autowired
    private JobControlFlagRepository jobControlFlagRepository;

    @Scheduled(cron = "${app.sync-summaryResult}")
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public void syncSummaryResult() {

        JobControlFlag flag = jobControlFlagRepository.findByJobName("sync_summary_result");

        if (flag == null || Boolean.FALSE.equals(flag.getIsEnabled())) {
            logger.info("SummaryResult synchronization is disabled via DB flag.");
            return;
        }

        String deleteSql = "DELETE FROM :database.v_summaryResult WHERE isFinished = FALSE";
        String insertSql = """
                INSERT INTO :database.v_summaryResult (ageGroup, ageGroupBygenderPos, ageGroupPos, bibNo, chipTime, distance, eventTypeName, eventUuid, gender, genderPos, gunTime, id, lastCP, name, nationality, pictureUrl, pos, status, uuid, eventName, isFinished)
                        SELECT ageGroup, ageGroupBygenderPos, ageGroupPos, bibNo, chipTime, distance, eventTypeName, eventUuid, gender, genderPos, gunTime, id, lastCP, name, nationality, pictureUrl, pos, status, uuid, eventName, isFinished
                        FROM :database.allParticipantByEvent WHERE isFinished = FALSE
                        """;
        try {
            jdbcTemplateTrans.update(replaceConstants(deleteSql));

            jdbcTemplateTrans.update(replaceConstants(insertSql));
        } catch (DataAccessException dae) {
            logger.error("Database error while syncing v_summaryResult: {}", dae.getMessage(), dae);
            throw dae; // ฐานข้อมูลล้มเหลว
        } catch (Exception e) {
            logger.error("Unexpected error while syncing v_summaryResult: {}", e.getMessage(), e);
            throw e; // ข้อผิดพลาดทั่วไป
        }
    }

    @Scheduled(cron = "${app.sync-summaryLive}")
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public void syncSummaryLive() {

        JobControlFlag flag = jobControlFlagRepository.findByJobName("sync_summary_result");

        if (flag == null || Boolean.FALSE.equals(flag.getIsEnabled())) {
            logger.info("SummaryLive synchronization is disabled via DB flag.");
            return;
        }

        String deleteSql = "TRUNCATE TABLE :database.v_summary_live";
        String deleteCpSql = "TRUNCATE TABLE :database.v_summary_cp_live";
        String insertSql = """
                INSERT INTO :database.v_summary_live (id, uuid, eventId, eventUuid, bibNo, name, gender, nationality, lastCP, lastCPTime, distance, gunTime, chipTime, status, ageGroup, pos, ageGroupBygenderPos, ageGroupPos, genderPos, isFinished)
                        SELECT id, uuid, eventId, eventUuid, bibNo, name, gender, nationality, lastCP, lastCPTime, distance, gunTime, chipTime, status, ageGroup, pos, ageGroupBygenderPos, ageGroupPos, genderPos, isFinished
                        FROM :database.allParticipantLive WHERE isFinished = FALSE
                        """;
        String insertCpSql = """
                INSERT INTO :database.v_summary_cp_live (eventId, eventUuid, participantId, stationName, stationTime, isFinished)
                        SELECT eventId, eventUuid, participantId, stationName, stationTime, isFinished
                        FROM :database.allCheckpointLive WHERE isFinished = FALSE
                        """;
        try {
            jdbcTemplateTrans.execute(replaceConstants(deleteSql));

            jdbcTemplateTrans.execute(replaceConstants(deleteCpSql));

            jdbcTemplateTrans.update(replaceConstants(insertSql));

            jdbcTemplateTrans.update(replaceConstants(insertCpSql));
        } catch (DataAccessException dae) {
            logger.error("Database error while syncing Live: {}", dae.getMessage(), dae);
            throw dae; // ฐานข้อมูลล้มเหลว
        } catch (Exception e) {
            logger.error("Unexpected error while syncing Live: {}", e.getMessage(), e);
            throw e; // ข้อผิดพลาดทั่วไป
        }
    }

    // #region get
    public DatabaseData getAllParticipant(String id, String user, String role, PagingData paging) throws SQLException {
        List<Object> params = new ArrayList<>();
        params.add(id);
        String condition = "";
        if ("organizer".equals(role)) {
            Integer userId = getIdByUuid("user", user);
            condition = " AND d.organizerId = ? ";
            params.add(userId);
        }
        String sql = """
                SELECT
                    a.uuid, COUNT(*) OVER() as hits, a.firstName, a.lastName,
                    a.idNo, DATE_FORMAT(a.birthDate, '%Y-%m-%d %T') AS birthDate, a.nationality,
                    CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) AS name, a.gender,
                    ag.ageGroup, a.bibNo,
                    DATE_FORMAT(a.registerDate, '%Y-%m-%d') AS registerDate, a.teamName, a.shirtSize, a.chipCode,
                    IFNULL(a.status, CASE WHEN a.isStarted = 1 THEN 'Started' ELSE null END) AS status,
                    a.isStarted, a.active, d.allowRFIDSync
                FROM
                    :database.participant a
                INNER JOIN
                    :database.event c ON c.id = a.eventId
                LEFT JOIN
                    :database.ageGroupByParticipant ag ON ag.eventId = c.id AND ag.participantId = a.id
                LEFT JOIN
                    :database.campaign d ON d.id = c.campaignId
                WHERE
                    a.active = true AND c.uuid = ?
                    """
                + condition;

        try {
            if (paging != null && !Strings.isNullOrEmpty(paging.getSearchField())
                    && !Strings.isNullOrEmpty(paging.getSearchText())) {
                switch (paging.getSearchField()) {
                    case "name":
                        sql += "and CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "gender":
                        sql += "and a.gender like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "bibNo":
                        sql += "and a.bibNo like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "teamName":
                        sql += "and a.teamName like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "shirtSize":
                        sql += "and a.shirtSize like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "chipCode":
                        sql += "and a.chipCode like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "status":
                        sql += "and a.status like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "active":
                        sql += "and case a.active when 1 then 'ใช้งาน' else 'ไม่ใช้งาน' end like ? ";
                        params.add(paging.getSearchText());
                        break;
                    default:
                        break;
                }
            }

            String orderByClause = "";
            String limitClause = "";

            if (paging != null) {
                if (!Strings.isNullOrEmpty(paging.getField()) && !Strings.isNullOrEmpty(paging.getSort())) {
                    orderByClause = " ORDER BY " + paging.getField() + " "
                            + paging.getSort();
                } else {
                    orderByClause = " ORDER BY a.updatedTime DESC";
                }

                if (paging.getStart() != null && paging.getLimit() != null) {
                    limitClause = " LIMIT " + paging.getStart() + ", " + paging.getLimit();
                }
            } else {
                orderByClause = " ORDER BY a.updatedTime DESC";
                limitClause = " LIMIT 10";
            }

            sql += orderByClause + limitClause;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            List<ParticipantData> data = mapper.convertValue(results,
                    new TypeReference<List<ParticipantData>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public DatabaseData getRunnerById(String id) throws SQLException {

        String sql = """
                SELECT
                        a.uuid, COUNT(*) OVER() as hits, CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) AS name, a.bibNo,
                        et.category, a.gender, DATE_FORMAT(a.timeOut, '%T') AS raceTime,
                        orun.runnerRank AS overallRank, grun.runnerRank AS genderRank, agrun.runnerRank AS ageGroupRank,
                        a.status
                FROM :database.participant a
                INNER JOIN  :database.event et ON et.id = a.eventId
                INNER JOIN :database.campaign b ON b.id = et.campaignId
                LEFT JOIN :database.overallRank orun ON orun.participantUuid = a.uuid
                LEFT JOIN :database.genderRank grun ON grun.participantUuid = a.uuid
                LEFT JOIN :database.ageGroupRank agrun ON agrun.participantUuid = a.uuid
                WHERE a.uuid = ?
                            """; // remove a.pictureUrl
        List<Object> params = new ArrayList<>();
        params.add(id);

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            List<RunnerRank> data = mapper.convertValue(results,
                    new TypeReference<List<RunnerRank>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public DatabaseData getAllParticipantByEvent(String id, PagingData paging, String eventName, String gender,
            String ageGroup, String favorites, String type) throws SQLException {

        String sql = """
                SELECT
                        COUNT(*) OVER() as hits, a.id, a.uuid, a.eventUuid, a.eventTypeName, a.name, a.bibNo, a.gender, a.nationality,
                        a.lastCP, a.distance, a.gunTime, a.chipTime, a.status, a.pictureUrl, a.ageGroup,
                        CASE WHEN a.status IN ('Finish', 'Started', 'Running')
                                THEN TIME_FORMAT(CAST(TIMEDIFF(a.gunTime, MIN(CASE WHEN a.status IN ('Finish', 'Started', 'Running') THEN a.gunTime END) OVER(PARTITION BY a.distance)) AS TIME), '%H:%i:%s')
                            ELSE NULL
                        END AS raceTimeDiff,
                        CASE
                            WHEN ? = 'gender' THEN a.genderPos
                            WHEN ? = 'agegroup' THEN a.ageGroupPos
                            WHEN ? = 'agegroupBygender' THEN a.ageGroupBygenderPos
                            ELSE a.pos
                        END AS pos, a.genderPos, a.ageGroupBygenderPos AS ageGroupPos
                FROM :database.v_summaryResult a
                WHERE a.eventUuid = ?
                """;
        List<Object> params = new ArrayList<>();
        params.add(type);
        params.add(type);
        params.add(type);
        params.add(id);
        try {
            if (paging != null && !Strings.isNullOrEmpty(paging.getSearchField())
                    && !Strings.isNullOrEmpty(paging.getSearchText())) {
                switch (paging.getSearchField()) {
                    case "name":
                        sql += "and a.name like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "bibNo":
                        sql += "and a.bibNo like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "gender":
                        sql += "and a.gender like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "nationality":
                        sql += "and a.nationality like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "lastCP":
                        sql += "and a.stationName like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "distance":
                        sql += "and a.distance like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "bibOrName":
                        sql += "AND ( a.bibNo like ? OR a.name like ? ) ";
                        params.add(paging.getSearchText());
                        params.add(paging.getSearchText());
                        break;
                    default:
                        break;
                }
            }
            if (eventName != null && !eventName.equals("")) {
                sql += " AND a.eventTypeName like ? ";
                params.add(eventName);
            }
            if (gender != null && !gender.equals("")) {
                sql += " AND a.gender like ? ";
                params.add(gender);
            }
            if (ageGroup != null && !ageGroup.equals("")) {
                sql += " AND a.ageGroup like ? ";
                params.add(ageGroup);
            }
            if (favorites != null && !favorites.equals("")) {
                sql += " AND a.uuid in ( " + favorites + " ) ";
            }
            String groupBy = "GROUP BY a.id ";
            String orderByClause = "";
            String limitClause = "";
            String standardSort = """
                                CASE
                                    WHEN a.status = 'Finish' THEN 1
                                    WHEN a.status = 'Running' THEN 2
                                    WHEN a.status = 'Started' THEN 3
                                    ELSE 4
                                END, distance DESC, raceTimeDiff
                    """;

            if (paging != null) {
                if (!Strings.isNullOrEmpty(paging.getField()) && !Strings.isNullOrEmpty(paging.getSort())
                        && !paging.getField().equals("favorite")) {

                    boolean isPosSorting = "pos".equals(paging.getField());
                    boolean isAsc = "asc".equalsIgnoreCase(paging.getSort());

                    if (isPosSorting) {
                        orderByClause = " ORDER BY pos IS NULL " + (isAsc ? "ASC" : "DESC") + ", "
                                + paging.getField() + " " + paging.getSort() + ", " + standardSort;
                    } else {
                        orderByClause = " ORDER BY " + paging.getField() + " " + paging.getSort() + ", " + standardSort;
                    }
                } else {
                    orderByClause = " ORDER BY " + standardSort;
                }

                if (paging.getStart() != null && paging.getLimit() != null) {
                    limitClause = " LIMIT " + paging.getStart() + ", " + paging.getLimit();
                }
            } else {
                orderByClause = " ORDER BY " + standardSort;
                limitClause = " LIMIT 15";
            }

            sql += groupBy + orderByClause + limitClause;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            List<ParticipantByEventData> data = mapper.convertValue(results,
                    new TypeReference<List<ParticipantByEventData>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> getAllParticipantByEventMoreDetail(String id, PagingData paging, String eventName,
            String gender,
            String ageGroup, String favorites, String type) throws SQLException {

        String sql = """
                    SELECT
                        GROUP_CONCAT(DISTINCT s.name ORDER BY s.orderNum, s.id ) AS stationName
                    FROM :database.checkpointMapping c
                    INNER JOIN :database.station s ON s.id = c.stationId
                    INNER JOIN :database.event et ON et.id = c.eventId
                    WHERE et.uuid = ?
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            List<Map<String, Object>> stationData = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            sql = """
                    SELECT
                            COUNT(*) OVER() as hits, a.id, a.uuid, a.eventUuid, a.eventTypeName, a.name, a.bibNo, a.gender, a.nationality,
                            a.lastCP, a.distance, a.gunTime, a.chipTime, a.status, a.pictureUrl, a.ageGroup,
                            CASE WHEN a.status IN ('Finish', 'Started', 'Running')
                                    THEN TIME_FORMAT(CAST(TIMEDIFF(a.gunTime, MIN(CASE WHEN a.status IN ('Finish', 'Started', 'Running') THEN a.gunTime END) OVER(PARTITION BY a.distance)) AS TIME), '%H:%i:%s')
                                ELSE NULL
                            END AS raceTimeDiff,
                            CASE
                                WHEN ? = 'gender' THEN a.genderPos
                                WHEN ? = 'agegroup' THEN a.ageGroupPos
                                WHEN ? = 'agegroupBygender' THEN a.ageGroupBygenderPos
                                ELSE a.pos
                            END AS pos, a.genderPos, a.ageGroupBygenderPos AS ageGroupPos
                    FROM :database.v_summaryResult a
                    WHERE a.eventUuid = ?
                    """;
            params.clear();
            params.add(type);
            params.add(type);
            params.add(type);
            params.add(id);

            if (paging != null && !Strings.isNullOrEmpty(paging.getSearchField())
                    && !Strings.isNullOrEmpty(paging.getSearchText())) {
                switch (paging.getSearchField()) {
                    case "name":
                        sql += "and a.name like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "bibNo":
                        sql += "and a.bibNo like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "gender":
                        sql += "and a.gender like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "nationality":
                        sql += "and a.nationality like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "lastCP":
                        sql += "and a.stationName like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "distance":
                        sql += "and a.distance like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "bibOrName":
                        sql += "AND ( a.bibNo like ? OR a.name like ? ) ";
                        params.add(paging.getSearchText());
                        params.add(paging.getSearchText());
                        break;
                    default:
                        break;
                }
            }
            if (eventName != null && !eventName.equals("")) {
                sql += " AND a.eventTypeName like ? ";
                params.add(eventName);
            }
            if (gender != null && !gender.equals("")) {
                sql += " AND a.gender like ? ";
                params.add(gender);
            }
            if (ageGroup != null && !ageGroup.equals("")) {
                sql += " AND a.ageGroup like ? ";
                params.add(ageGroup);
            }
            if (favorites != null && !favorites.equals("")) {
                sql += " AND a.uuid in ( " + favorites + " ) ";
            }
            String groupBy = "GROUP BY a.id ";
            String orderByClause = "";
            String limitClause = "";
            String standardSort = """
                                CASE
                                    WHEN a.status = 'Finish' THEN 1
                                    WHEN a.status = 'Running' THEN 2
                                    WHEN a.status = 'Started' THEN 3
                                    ELSE 4
                                END, distance DESC, raceTimeDiff
                    """;

            if (paging != null) {
                if (!Strings.isNullOrEmpty(paging.getField()) && !Strings.isNullOrEmpty(paging.getSort())
                        && !paging.getField().equals("favorite")) {

                    boolean isPosSorting = "pos".equals(paging.getField());
                    boolean isAsc = "asc".equalsIgnoreCase(paging.getSort());

                    if (isPosSorting) {
                        orderByClause = " ORDER BY pos IS NULL " + (isAsc ? "ASC" : "DESC") + ", "
                                + paging.getField() + " " + paging.getSort() + ", " + standardSort;
                    } else {
                        orderByClause = " ORDER BY " + paging.getField() + " " + paging.getSort() + ", " + standardSort;
                    }
                } else {
                    orderByClause = " ORDER BY " + standardSort;
                }

                if (paging.getStart() != null && paging.getLimit() != null) {
                    limitClause = " LIMIT " + paging.getStart() + ", " + paging.getLimit();
                }
            } else {
                orderByClause = " ORDER BY " + standardSort;
                limitClause = " LIMIT 15";
            }

            sql += groupBy + orderByClause + limitClause;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            // ดึงข้อมูล Checkpoints
            String cpQuery = """
                    SELECT eventId, participantId, stationName, stationTime
                    FROM :database.v_final_cp_live
                    WHERE eventUuid = ?
                    """;
            List<Map<String, Object>> cpData = jdbcTemplate.queryForList(replaceConstants(cpQuery), id);

            // สร้าง Checkpoint Map
            Map<String, Map<String, String>> checkpointMap = cpData.stream()
                    .filter(cp -> cp.get("stationName") != null && cp.get("participantId") != null)
                    .collect(Collectors.groupingBy(
                            cp -> String.valueOf(cp.get("participantId")),
                            Collectors.toMap(
                                    cp -> cp.get("stationName").toString(),
                                    cp -> cp.get("stationTime") != null ? cp.get("stationTime").toString() : null,
                                    (oldValue, newValue) -> newValue)));

            // ใช้ `mapper.convertValue()` แปลงผลลัพธ์
            List<ParticipantByEventData> data = mapper.convertValue(results, new TypeReference<>() {
            });

            // เติม Checkpoints ลงใน Data
            for (ParticipantByEventData participant : data) {
                String key = String.valueOf(participant.getId());
                participant.setCheckpoints(checkpointMap.getOrDefault(key, new HashMap<>()));
            }

            // คำนวณ Hits
            Long hits = data.isEmpty() ? 0L : (Long) results.get(0).get("hits");

            // คืนค่าในรูปแบบ JSON
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("hits", hits);
            responseData.put("records", data);
            responseData.put("stationName", stationData.get(0).get("stationName"));

            return responseData;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> getAllParticipantWithStationByEvent(String id, PagingData paging, String gender,
            String ageGroup, String type) throws SQLException {

        String sql = """
                SELECT
                    GROUP_CONCAT(DISTINCT s.name ORDER BY s.orderNum, s.id ) AS stationName
                FROM :database.checkpointMapping c
                INNER JOIN :database.station s ON s.id = c.stationId
                INNER JOIN :database.event et ON et.id = c.eventId
                WHERE et.uuid = ?
                """;

        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            List<Map<String, Object>> stationData = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            Map<String, Object> finalData = null;

            int retryCount = 0;
            while (retryCount < 5) {
                // Reset parameters for each try
                params.clear();
                params.add(type);
                params.add(type);
                params.add(type);
                params.add(id);

                sql = """
                        SELECT
                            COUNT(*) OVER() as hits, a.id, a.uuid, a.eventId, a.bibNo, a.name, a.gender, a.nationality,
                            a.lastCP, a.lastCPTime, a.distance, a.gunTime, a.chipTime, a.status, a.ageGroup,
                            CASE
                                WHEN ? = 'gender' THEN a.genderPos
                                WHEN ? = 'agegroup' THEN a.ageGroupPos
                                WHEN ? = 'agegroupBygender' THEN a.ageGroupBygenderPos
                                ELSE a.pos
                            END AS pos, a.genderPos, a.ageGroupBygenderPos AS ageGroupPos
                        FROM :database.v_summary_live a
                        WHERE a.eventUuid = ?
                        """;

                if (paging != null && !Strings.isNullOrEmpty(paging.getSearchField())
                        && !Strings.isNullOrEmpty(paging.getSearchText())) {
                    switch (paging.getSearchField()) {
                        case "name":
                            sql += "and a.name like ? ";
                            params.add(paging.getSearchText());
                            break;
                        case "bibNo":
                            sql += "and a.bibNo like ? ";
                            params.add(paging.getSearchText());
                            break;
                        case "gender":
                            sql += "and a.gender like ? ";
                            params.add(paging.getSearchText());
                            break;
                        case "nationality":
                            sql += "and a.nationality like ? ";
                            params.add(paging.getSearchText());
                            break;
                        case "lastCP":
                            sql += "and a.lastCP like ? ";
                            params.add(paging.getSearchText());
                            break;
                        case "distance":
                            sql += "and a.distance like ? ";
                            params.add(paging.getSearchText());
                            break;
                        case "bibOrName":
                            sql += "AND ( a.bibNo like ? OR a.name like ? ) ";
                            params.add(paging.getSearchText());
                            params.add(paging.getSearchText());
                            break;
                        default:
                            break;
                    }
                }

                if (gender != null && !gender.isEmpty()) {
                    sql += " AND a.gender like ? ";
                    params.add(gender);
                }

                if (ageGroup != null && !ageGroup.isEmpty()) {
                    sql += " AND a.ageGroup like ? ";
                    params.add(ageGroup);
                }

                String groupBy = "GROUP BY a.id";
                String orderByClause = "";
                String limitClause = "";

                if (paging != null) {
                    if (!Strings.isNullOrEmpty(paging.getField()) && !Strings.isNullOrEmpty(paging.getSort())) {
                        orderByClause = " ORDER BY " + paging.getField() + " " + paging.getSort();
                    } else {
                        orderByClause = " ORDER BY pos IS NULL, pos";
                    }

                    if (paging.getStart() != null && paging.getLimit() != null) {
                        limitClause = " LIMIT " + paging.getStart() + ", " + paging.getLimit();
                    }
                } else {
                    orderByClause = " ORDER BY pos IS NULL, pos";
                    limitClause = " LIMIT 15";
                }

                sql += groupBy + orderByClause + limitClause;

                List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

                if (!results.isEmpty()) {
                    // Get checkpoints
                    String cpQuery = """
                            SELECT eventId, participantId, stationName, stationTime
                            FROM :database.v_summary_cp_live
                            WHERE eventUuid = ?
                            """;
                    List<Map<String, Object>> cpData = jdbcTemplate.queryForList(replaceConstants(cpQuery), id);

                    // Build checkpoint map
                    Map<String, Map<String, String>> checkpointMap = cpData.stream()
                            .filter(cp -> cp.get("stationName") != null)
                            .collect(Collectors.groupingBy(
                                    cp -> cp.get("eventId") + "-" + cp.get("participantId"),
                                    Collectors.toMap(
                                            cp -> (String) cp.get("stationName"),
                                            cp -> cp.get("stationTime") != null ? cp.get("stationTime").toString()
                                                    : null,
                                            (oldValue, newValue) -> newValue)));

                    ObjectMapper objectMapper = new ObjectMapper();
                    List<ParticipantLiveRequest> mappedData = results.stream().map(participant -> {
                        String key = participant.get("eventId") + "-" + participant.get("id");
                        ParticipantLiveRequest participantLive = objectMapper.convertValue(participant,
                                ParticipantLiveRequest.class);
                        participantLive.setCheckpoints(checkpointMap.getOrDefault(key, new HashMap<>()));
                        return participantLive;
                    }).collect(Collectors.toList());

                    finalData = new HashMap<>();
                    finalData.put("data", mappedData);
                    if (stationData.get(0).get("stationName") != null) {
                        finalData.put("stationName", stationData.get(0).get("stationName"));
                    }

                    break; // Success, exit retry loop
                }

                retryCount++;
                try {
                    Thread.sleep(2000); // Wait 2 seconds before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // Restore interrupt
                    throw new SQLException("Thread interrupted during retry sleep", ie);
                }
            }

            // Return result or empty data
            if (finalData != null) {
                return finalData;
            } else {
                Map<String, Object> emptyData = new HashMap<>();
                emptyData.put("data", Collections.emptyList());
                if (stationData.get(0).get("stationName") != null) {
                    emptyData.put("stationName", stationData.get(0).get("stationName"));
                }
                return emptyData;
            }

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public DatabaseData getLatestParticipantByCheckpoint(String id, String eventUuid, PagingData paging,
            String checkpointName, String gender, String ageGroup) throws SQLException {

        String sql = """
                SELECT
                        COUNT(*) OVER() as hits, e.uuid,
                        p.bibNo, CONCAT(p.firstName, IFNULL(CONCAT(' ', p.lastName), '')) AS name,
                        p.gender, p.nationality, DATE_FORMAT(t.raceTimingIn, '%T') AS passTime,
                        t.raceTimingIn, s.name AS checkpointName, agp.ageGroup
                FROM :database.timeRecordsManualFirst t
                INNER JOIN :database.participant p ON (p.id = t.participantId)
                INNER JOIN :database.event e ON (e.id = p.eventId)
                INNER JOIN :database.campaign c ON (c.id = e.campaignId)
                INNER JOIN :database.checkpointMapping cm ON (cm.eventId = p.eventId AND cm.id = t.checkpointMappingId)
                INNER JOIN :database.station s ON (s.id = cm.stationId)
                LEFT JOIN :database.ageGroupByParticipant agp ON (agp.participantId = p.id AND agp.eventId = e.id)
                WHERE c.uuid = ? AND s.name = ?
                        """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        params.add(checkpointName);
        try {
            if (eventUuid != null && !eventUuid.equals("")) {
                sql += " AND e.uuid = ? ";
                params.add(eventUuid);
            }
            if (gender != null && !gender.equals("")) {
                sql += " AND p.gender = ? ";
                params.add(gender);
            }
            if (ageGroup != null && !ageGroup.equals("")) {
                sql += " AND agp.ageGroup = ? ";
                params.add(ageGroup);
            }
            String orderByClause = "";
            String limitClause = "";

            if (paging != null) {
                if (!Strings.isNullOrEmpty(paging.getField()) && !Strings.isNullOrEmpty(paging.getSort())) {
                    orderByClause = " ORDER BY " + paging.getField() + " "
                            + paging.getSort();
                } else {
                    orderByClause = " ORDER BY t.raceTimingIn DESC";
                }

                if (paging.getStart() != null && paging.getLimit() != null) {
                    limitClause = " LIMIT " + paging.getStart() + ", " + paging.getLimit();
                }
            } else {
                orderByClause = " ORDER BY t.raceTimingIn DESC";
                limitClause = " LIMIT 10";
            }

            sql += orderByClause + limitClause;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            List<LatestParticipantByCheckpoint> data = mapper.convertValue(results,
                    new TypeReference<List<LatestParticipantByCheckpoint>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getParticipantByChipCode(String id, String chipCode, String bibNo)
            throws SQLException {

        String sql = """
                SELECT
                        p.bibNo, e.name AS eventName,
                        e.distance, DATE_FORMAT(e.eventDate, '%Y-%m-%d %T') AS eventDate, e.timeLimit,
                        CONCAT(p.firstName, IFNULL(CONCAT(' ', p.lastName), '')) AS name,
                        p.gender, agp.ageGroup
                FROM :database.participant p
                INNER JOIN :database.event e ON (e.id = p.eventId)
                INNER JOIN :database.campaign c ON (c.id = e.campaignId)
                LEFT JOIN :database.ageGroupByParticipant agp ON (agp.participantId = p.id AND agp.eventId = e.id)
                WHERE c.uuid = ? AND (p.chipCode = ? OR p.bibNo = ?)
                LIMIT 1
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        params.add(chipCode);
        params.add(bibNo);
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            return result;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getAllStatusByEvent(String id) throws SQLException {

        String sql = """
                SELECT IFNULL(p.status, CASE WHEN p.isStarted = 1 THEN 'Started' ELSE null END) AS status, COUNT(p.id) AS total
                FROM :database.participant p
                INNER JOIN  :database.event e ON e.id = p.eventId
                WHERE e.uuid = ?
                GROUP BY IFNULL(p.status, CASE WHEN p.isStarted = 1 THEN 'Started' ELSE null END)
                    """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            return results;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getStartersByAge(String id) throws SQLException {

        String sql = """
                SELECT ag.ageGroup, ag.gender, COUNT(p.id) AS total
                FROM :database.participant p
                INNER JOIN :database.event e ON e.id = p.eventId
                LEFT JOIN :database.ageGroupByParticipant ag ON ag.eventId = e.id AND ag.participantId = p.id
                WHERE IFNULL(p.status, '') = 'Finish' AND e.uuid = ?
                GROUP BY ag.ageGroup, ag.gender
                ORDER BY
                CASE
                    WHEN ag.ageGroup LIKE 'ต่ำกว่า%' THEN 0
                    WHEN ag.ageGroup LIKE '%ขึ้นไป' THEN 2
                    WHEN ag.ageGroup LIKE '%ไม่ระบุ กลุ่มอายุ%' THEN 3
                    ELSE 1
                END, ag.ageGroup
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            return results;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getWithdrawalByAge(String id) throws SQLException {

        String sql = """
                SELECT ag.ageGroup, ag.gender, COUNT(p.id) AS total
                FROM :database.participant p
                INNER JOIN :database.event e ON e.id = p.eventId
                LEFT JOIN :database.ageGroupByParticipant ag ON ag.eventId = e.id AND ag.participantId = p.id
                WHERE IFNULL(p.status, '') <> 'Finish' AND e.uuid = ?
                GROUP BY ag.ageGroup, ag.gender
                ORDER BY
                CASE
                    WHEN ag.ageGroup LIKE 'ต่ำกว่า%' THEN 0
                    WHEN ag.ageGroup LIKE '%ขึ้นไป' THEN 2
                    WHEN ag.ageGroup LIKE '%ไม่ระบุ กลุ่มอายุ%' THEN 3
                    ELSE 1
                END, ag.ageGroup
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            return results;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getWithdrawalByCheckpoint(String id) throws SQLException {

        String sql = """
                WITH
                event_info AS (
                    SELECT e.id AS event_id
                    FROM :database.event e
                    WHERE e.uuid = ?
                ),

                event_cps AS (
                    SELECT
                        cp.eventId,
                        cp.id              AS cp_id,
                        s.orderNum         AS order_key,
                        s.name             AS station_name
                    FROM :database.checkpointMapping cp
                    JOIN :database.station s ON s.id = cp.stationId
                    WHERE cp.eventId = (SELECT event_id FROM event_info)
                ),

                event_bounds AS (
                    SELECT
                        MIN(order_key) AS start_order,
                        MAX(order_key) AS finish_order
                    FROM event_cps
                ),

                evt_participants AS (
                    SELECT
                        p.id,
                        p.eventId,
                        COALESCE(p.isStarted,0) AS isStarted,
                        COALESCE(p.status,'')   AS status
                    FROM :database.participant p
                    WHERE p.eventId = (SELECT event_id FROM event_info)
                        AND COALESCE(p.active,1) = 1
                ),

                last_cp AS (
                    SELECT
                        tr.participantId,
                        MAX(ec.order_key) AS last_order
                    FROM :database.timeRecord tr
                    JOIN event_cps ec ON ec.cp_id = tr.checkpointMappingId
                    GROUP BY tr.participantId
                ),

                participant_bucket AS (
                    SELECT
                        ep.id AS participantId,
                        CASE
                            WHEN ep.isStarted = 0 OR ep.status = 'DNS' THEN NULL
                            WHEN lc.last_order IS NOT NULL THEN lc.last_order
                            WHEN ep.status = 'Finish' AND lc.last_order IS NULL
                                THEN (SELECT finish_order FROM event_bounds)
                            WHEN ep.isStarted = 1 AND lc.last_order IS NULL
                                THEN (SELECT start_order FROM event_bounds)
                            ELSE NULL
                        END AS bucket_order
                    FROM evt_participants ep
                    LEFT JOIN last_cp lc ON lc.participantId = ep.id
                ),

                current_counts AS (
                    SELECT pb.bucket_order AS order_key, COUNT(*) AS current
                    FROM participant_bucket pb
                    WHERE pb.bucket_order IS NOT NULL
                    GROUP BY pb.bucket_order
                ),

                passed_from_tr AS (
                    SELECT DISTINCT
                        ec.order_key,
                        tr.participantId
                    FROM :database.timeRecord tr
                    JOIN event_cps ec ON ec.cp_id = tr.checkpointMappingId
                ),

                passed_extras AS (
                    SELECT
                        (SELECT start_order FROM event_bounds) AS order_key,
                        ep.id AS participantId
                    FROM evt_participants ep
                    WHERE ep.isStarted = 1

                    UNION DISTINCT

                    SELECT
                        (SELECT finish_order FROM event_bounds) AS order_key,
                        ep.id AS participantId
                    FROM evt_participants ep
                    LEFT JOIN (
                        SELECT DISTINCT tr.participantId
                        FROM :database.timeRecord tr
                        JOIN event_cps ecf ON ecf.cp_id = tr.checkpointMappingId
                        WHERE ecf.order_key = (SELECT finish_order FROM event_bounds)
                    ) has_finish_tr ON has_finish_tr.participantId = ep.id
                    WHERE ep.status = 'Finish' AND has_finish_tr.participantId IS NULL
                ),

                passed_all AS (
                    SELECT * FROM passed_from_tr
                    UNION DISTINCT
                    SELECT * FROM passed_extras
                ),

                passed_counts AS (
                    SELECT order_key, COUNT(DISTINCT participantId) AS passed
                    FROM passed_all
                    GROUP BY order_key
                )

                SELECT
                    ec.station_name AS name,
                    COALESCE(cc.current, 0) AS current,
                    COALESCE(pc.passed, 0)  AS passed
                FROM event_cps ec
                LEFT JOIN current_counts cc ON cc.order_key = ec.order_key
                LEFT JOIN passed_counts  pc ON pc.order_key = ec.order_key
                ORDER BY ec.order_key
                """;

        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            return jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getFinishByTime(String id) throws SQLException {

        String sql = """
                SELECT
                    TIME(DATE_ADD(DATE(ct.gunTime), INTERVAL FLOOR(HOUR(ct.gunTime) / 2) * 2 HOUR)) AS twoHr,
                    CONCAT(
                        LPAD(FLOOR(HOUR(ct.gunTime) / 2) * 2, 2, '00'), ':00:00 - ',
                        LPAD(FLOOR(HOUR(ct.gunTime) / 2) * 2 + 1, 2, '00'), ':59:59'
                    ) AS rateTime,
                    COUNT(p.id) AS total
                FROM :database.participant p
                INNER JOIN :database.event e ON e.id = p.eventId
                LEFT JOIN :database.calculateGunTime ct ON ct.eventId = e.id AND ct.participantId = p.id
                WHERE IFNULL(p.status, '') = 'Finish' AND e.uuid = ?
                GROUP BY twoHr
                ORDER BY twoHr
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            return results;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public String getTitleParticipant(String id) throws SQLException {
        String sql = "SELECT name AS title FROM :database.campaign WHERE uuid = ?";
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            Map<String, Object> results = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            return results.get("title").toString();
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (DataAccessException e) {
            log.error("Error occurred:", e);
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> getTitleParticipantByEvent(String id) throws SQLException {
        String sql = "SELECT c.name, e.name AS eventType FROM :database.event e INNER JOIN :database.campaign c ON c.id = e.campaignId WHERE e.uuid = ?";
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            Map<String, Object> results = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            return results;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (DataAccessException e) {
            log.error("Error occurred:", e);
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getMockListParticipantTemplate(String id) throws SQLException {
        Integer campaignId = getIdByUuid("campaign", id);
        String sql = "SELECT name AS unitName FROM :database.event WHERE campaignId = ? AND active = true";
        List<Object> params = new ArrayList<>();
        params.add(campaignId);

        String[] columns = { "ชื่อ", "นามสกุล", "บัตรประชาชน", "เพศ", "วันเกิด", "สัญชาติ",
                "อีเมล", "เบอร์โทรศัพท์", "ที่อยู่", "จังหวัด", "อำเภอ", "ตำบล", "ไปรษณีย์",
                "หมู่เลือด", "ปัญหาสุขภาพ", "ผู้ติดต่อฉุกเฉิน", "เบอร์โทรศัพท์ผู้ติดต่อฉุกเฉิน",
                "ไซส์เสื้อ", "สมัครวันที่", "ชื่อทีม", "bib", "chipCode" };

        try {
            List<String> results = jdbcTemplate.queryForList(replaceConstants(sql), String.class, params.toArray());

            List<Map<String, Object>> dataList = new ArrayList<>();
            for (String unitName : results) {
                Map<String, Object> quizUnit = new HashMap<>();
                quizUnit.put("sheetName", unitName);
                quizUnit.put("columns", columns);
                quizUnit.put("preHeader", List.of("ชื่ออีเว้นท์", unitName));

                List<String> example = new ArrayList<>();
                example.add("พลพล");
                example.add("ลุยสวน");
                example.add("1234567890123");
                example.add("Male ( Male / Female )");
                example.add("2011-05-22");
                example.add("THA");
                example.add("Palapol.ls@gmail.com");
                example.add("0999999999");
                example.add("11/22");
                example.add("ชุมพร");
                example.add("สวี");
                example.add("ครน");
                example.add("86130");
                example.add("A ( A / B / AB / O )");
                example.add("ไม่มี");
                example.add("ปรเมท");
                example.add("0876500000");
                example.add("S");
                example.add("2024-02-02");
                example.add("ACTION");
                example.add("M22-11");
                example.add("010A111A");

                quizUnit.put("datas", new ArrayList<>(Collections.singletonList(example)));

                dataList.add(quizUnit);
            }

            return dataList;
        } catch (DataAccessException e) {
            log.error("Error occurred:", e);
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getCheckpointMappingByEvent(String id) throws SQLException {

        String sql = """
                        SELECT
                            cp.uuid AS checkpointMappingUuid, s.name
                        FROM :database.station s
                        INNER JOIN :database.checkpointMapping cp ON cp.stationId = s.id
                        INNER JOIN :database.event e ON e.id = cp.eventId
                        WHERE e.uuid = ?
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            return results;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public String optString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return ((LocalDateTime) value).format(formatter);
        }
        return value.toString();
    }

    public List<Map<String, Object>> getAllParticipantDownload(String id) throws SQLException {
        String[] columns = { "uuid", "ชื่อ", "นามสกุล", "บัตรประชาชน", "เพศ", "วันเกิด", "สัญชาติ",
                "อีเมล", "เบอร์โทรศัพท์", "ที่อยู่", "จังหวัด", "อำเภอ", "ตำบล", "ไปรษณีย์",
                "หมู่เลือด", "ปัญหาสุขภาพ", "ผู้ติดต่อฉุกเฉิน", "เบอร์โทรศัพท์ผู้ติดต่อฉุกเฉิน",
                "ไซส์เสื้อ", "สมัครวันที่", "ชื่อทีม", "bib", "chipCode" };

        String sql = "SELECT id AS eventId, name AS unitName FROM :database.event WHERE uuid = ?";

        List<Object> params = new ArrayList<>();
        params.add(id);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
        List<Map<String, Object>> formatData = new ArrayList<>();
        sql = """
                        SELECT
                            p.uuid AS uuid, p.firstName, p.lastName, p.idNo, p.gender,
                            p.birthDate, p.nationality, p.email, p.tel, p.address, p.province, p.amphoe, p.district,
                            p.zipcode, p.bloodGroup, p.healthProblems, p.emergencyContact, p.emergencyContactTel,
                            p.shirtSize, p.registerDate, p.teamName, p.bibNo, p.chipCode
                        FROM :database.participant p
                        WHERE p.eventId = ?
                    ORDER BY p.id
                """;
        for (Map<String, Object> result : results) {
            List<Object> participantParams = new ArrayList<>();
            participantParams.add(result.get("eventId"));
            List<Map<String, Object>> participants = jdbcTemplate.queryForList(replaceConstants(sql),
                    participantParams.toArray());
            Map<String, Object> allParticipant = new HashMap<>();
            allParticipant.put("sheetName", result.get("unitName"));
            allParticipant.put("columns", columns);
            allParticipant.put("preHeader", List.of("ชื่ออีเว้นท์", result.get("unitName")));
            List<List<String>> sheetData = new ArrayList<>();
            for (Map<String, Object> participant : participants) {
                List<String> row = new ArrayList<>();
                row.add(optString(participant.get("uuid")));
                row.add(optString(participant.get("firstName")));
                row.add(optString(participant.get("lastName")));
                row.add(optString(participant.get("idNo")));
                row.add(optString(participant.get("gender")));
                row.add(optString(participant.get("birthDate")));
                row.add(optString(participant.get("nationality")));
                row.add(optString(participant.get("email")));
                row.add(optString(participant.get("tel")));
                row.add(optString(participant.get("address")));
                row.add(optString(participant.get("province")));
                row.add(optString(participant.get("amphoe")));
                row.add(optString(participant.get("district")));
                row.add(optString(participant.get("zipcode")));
                row.add(optString(participant.get("bloodGroup")));
                row.add(optString(participant.get("healthProblems")));
                row.add(optString(participant.get("emergencyContact")));
                row.add(optString(participant.get("emergencyContactTel")));
                row.add(optString(participant.get("shirtSize")));
                row.add(optString(participant.get("registerDate")));
                row.add(optString(participant.get("teamName")));
                row.add(optString(participant.get("bibNo")));
                row.add(optString(participant.get("chipCode")));
                sheetData.add(row);
            }

            allParticipant.put("datas", sheetData);

            formatData.add(allParticipant);
        }
        return formatData;
    }

    public List<Map<String, Object>> getAllParticipantAndTimeDownload(String id) throws SQLException {
        String[] columns = { "uuid", "participantUuid", "recordType", "ชื่อ", "นามสกุล", "bib", "เวลา In", "เวลา Out" };

        Integer eventId = getIdByUuid("event", id);

        String sql = """
                SELECT c.id AS checkpointMappingId, s.name
                FROM :database.checkpointMapping c
                INNER JOIN :database.station s ON s.id = c.stationId
                INNER JOIN :database.event et ON et.id = c.eventId
                WHERE et.uuid = ?
                ORDER BY s.orderNum
                """;

        List<Object> params = new ArrayList<>();
        params.add(id);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
        List<Map<String, Object>> formatData = new ArrayList<>();
        sql = """
                    SELECT
                        uuid, participantUuid, recordType, firstName, lastName, bibNo, raceTimingIn, raceTimingOut FROM (
                        SELECT
                            p.id, t.uuid, p.uuid AS participantUuid, t.recordType, p.firstName, p.lastName, p.bibNo, t.raceTimingIn, t.raceTimingOut
                        FROM :database.participant p
                        INNER JOIN :database.timeRecordsManualFirst t ON t.participantId = p.id
                        WHERE t.checkpointMappingId = ?
                        UNION
                        SELECT
                            p.id, null AS uuid, p.uuid AS participantUuid, null AS recordType, p.firstName, p.lastName, p.bibNo, null AS raceTimingIn, null AS raceTimingOut
                        FROM :database.participant p
                        WHERE p.eventId = ?
                    ) subquery
                    GROUP BY participantUuid
                    ORDER BY id
                """;
        for (Map<String, Object> result : results) {
            List<Object> checkpointParams = new ArrayList<>();
            checkpointParams.add(result.get("checkpointMappingId"));
            checkpointParams.add(eventId);
            List<Map<String, Object>> participants = jdbcTemplate.queryForList(replaceConstants(sql),
                    checkpointParams.toArray());
            Map<String, Object> allParticipant = new HashMap<>();
            allParticipant.put("sheetName", result.get("name"));
            allParticipant.put("columns", columns);
            allParticipant.put("preHeader", List.of("ชื่อจุด checkpoint", result.get("name")));
            List<List<String>> sheetData = new ArrayList<>();
            for (Map<String, Object> participant : participants) {
                List<String> row = new ArrayList<>();
                row.add(optString(participant.get("uuid")));
                row.add(optString(participant.get("participantUuid")));
                row.add(optString(participant.get("recordType")));
                row.add(optString(participant.get("firstName")));
                row.add(optString(participant.get("lastName")));
                row.add(optString(participant.get("bibNo")));
                row.add(optString(participant.get("raceTimingIn")));
                row.add(optString(participant.get("raceTimingOut")));
                sheetData.add(row);
            }

            allParticipant.put("datas", sheetData);

            formatData.add(allParticipant);
        }
        return formatData;
    }

    public List<Map<String, Object>> getParticipantTimeCPById(String id, String eventUuid)
            throws SQLException {
        String sql = """
                    SELECT
                        uuid, checkpointMappingUuid, participantUuid, name, type, scanInOut, raceTimingIn, raceTimingOut, recordType FROM (
                        SELECT
                            s.orderNum, s.id, t.uuid, s.uuid AS stationUuid, cp.uuid AS checkpointMappingUuid, p.uuid AS participantUuid,
                            s.name, s.type, cp.scanInOut, t.raceTimingIn, t.raceTimingOut, t.recordType
                        FROM :database.participant p
                        INNER JOIN :database.timeRecordsManualFirst t ON t.participantId = p.id
                        INNER JOIN :database.checkpointMapping cp ON cp.id = t.checkpointMappingId AND cp.eventId = p.eventId
                        INNER JOIN :database.station s ON s.id = cp.stationId
                        WHERE p.uuid = ?
                        UNION
                        SELECT
                            s.orderNum, s.id, null AS uuid, s.uuid AS stationUuid, cp.uuid AS checkpointMappingUuid, null AS participantUuid,
                            s.name, s.type, cp.scanInOut, null AS raceTimingIn, null AS raceTimingOut, null AS recordType
                        FROM :database.station s
                        INNER JOIN :database.checkpointMapping cp ON cp.stationId = s.id
                        INNER JOIN :database.event e ON e.id = cp.eventId
                        WHERE e.uuid = ?
                ) subquery
                GROUP BY stationUuid
                ORDER BY orderNum, id
                            """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        params.add(eventUuid);

        try {
            List<Map<String, Object>> data = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            return data;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> getParticipantById(String id) throws SQLException {

        String sql = """
                SELECT
                    a.uuid, a.firstName, a.lastName, a.idNo, DATE_FORMAT(a.birthDate, '%Y-%m-%d %T') AS birthDate, a.nationality,
                    a.gender, a.bibNo, DATE_FORMAT(a.registerDate, '%Y-%m-%d') AS registerDate, a.teamName, a.shirtSize, a.chipCode,
                    IFNULL(a.status, CASE WHEN a.isStarted = 1 THEN 'Started' ELSE null END) AS status,
                    CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) AS name, a.isStarted, a.active
                FROM
                    :database.participant a
                INNER JOIN
                    :database.event c ON c.id = a.eventId
                WHERE
                    a.uuid = ?
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());

            return result;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> checkUserDupIdNo(String uuid, String idNo) throws SQLException {
        String sql = """
                SELECT a.id FROM :database.participant a WHERE a.uuid <> ? AND a.idNo = ?
                    """;
        List<Object> params = new ArrayList<>();
        params.add(uuid);
        params.add(idNo);
        try {
            Map<String, Object> data = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            return data;
        } catch (EmptyResultDataAccessException e) {
            return new HashMap<>();
        } catch (DataAccessException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Boolean checkCheckpointMappingByEvent(String id) throws SQLException {
        String sql = """
                SELECT COUNT(a.id) AS countCheckpointMapping
                FROM :database.checkpointMapping a
                INNER JOIN :database.event b ON b.id = a.eventId
                WHERE b.uuid = ?
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            Integer data = jdbcTemplate.queryForObject(replaceConstants(sql), Integer.class, params.toArray());
            return data > 0;
        } catch (DataAccessException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getResultExcel(String id, String gender, String ageGroup, String topRank,
            String type) throws SQLException {
        String[] columns = { "No.", "ลำดับเพศ", "ลำดับกลุ่มอายุ", "Bib", "ชื่อ นามสกุล", "เพศ", "สัญชาติ", "กลุ่มอายุ",
                "สถานะ", "จุดล่าสุด", "ระยะทาง", "Gun time", "Chip time", "เวลา" };
        String sql = "";
        List<Object> params = new ArrayList<>();
        String genderValue = "".equals(gender) ? null : gender;
        String ageGroupValue = "".equals(ageGroup) ? null : ageGroup;
        String topRankValue = "".equals(topRank) ? null : topRank;

        if ("agegroupBygender".equals(type)) {
            sql = """
                    SELECT eventName, eventTypeName, gender, ageGroup
                    FROM :database.v_summaryResult
                    WHERE eventUuid = ?
                    AND (gender = ? OR ? IS NULL)
                    AND (ageGroup = ? OR ? IS NULL)
                    GROUP BY gender, ageGroup, eventUuid
                    ORDER BY ageGroup, gender
                    """;
            params.add(id);
            params.add(genderValue);
            params.add(genderValue);
            params.add(ageGroupValue);
            params.add(ageGroupValue);
        } else if ("gender".equals(type)) {
            sql = """
                    SELECT eventName, eventTypeName, gender
                    FROM :database.v_summaryResult
                    WHERE eventUuid = ?
                    AND (gender = ? OR ? IS NULL)
                    GROUP BY gender, eventUuid
                    ORDER BY gender
                    """;
            params.add(id);
            params.add(genderValue);
            params.add(genderValue);
        } else if ("agegroup".equals(type)) {
            sql = """
                    SELECT eventName, eventTypeName, ageGroup
                    FROM :database.v_summaryResult
                    WHERE eventUuid = ?
                    AND (ageGroup = ? OR ? IS NULL)
                    GROUP BY ageGroup, eventUuid
                    ORDER BY ageGroup
                    """;
            params.add(id);
            params.add(ageGroupValue);
            params.add(ageGroupValue);
        } else {
            sql = """
                    SELECT c.name AS eventName, e.name AS eventTypeName
                    FROM :database.event e
                    INNER JOIN :database.campaign c ON c.id = e.campaignId
                    WHERE e.uuid = ?
                    """;
            params.add(id);
        }

        List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
        List<Map<String, Object>> formatData = new ArrayList<>();
        sql = """
                SELECT * FROM (
                    SELECT a.name, a.bibNo, a.gender, a.nationality, a.lastCP, a.distance, a.gunTime, a.chipTime, a.status, a.ageGroup,
                        CASE WHEN a.status IN ('Finish', 'Started', 'Running')
                            THEN TIME_FORMAT(CAST(TIMEDIFF(a.gunTime, MIN(CASE WHEN a.status IN ('Finish', 'Started', 'Running') THEN a.gunTime END) OVER(PARTITION BY a.distance)) AS TIME), '%H:%i:%s')
                            ELSE NULL
                        END AS raceTimeDiff,
                        CASE
                            WHEN ? = 'gender' THEN a.genderPos
                            WHEN ? = 'agegroup' THEN a.ageGroupPos
                            WHEN ? = 'agegroupBygender' THEN a.ageGroupBygenderPos
                            ELSE a.pos
                        END AS rankingPos, a.genderPos, a.ageGroupBygenderPos AS ageGroupPos
                    FROM :database.v_summaryResult a
                    WHERE a.eventUuid = ?
                        AND (a.gender = ? OR ? IS NULL)
                        AND (a.ageGroup = ? OR ? IS NULL)
                    ORDER BY
                        CASE
                            WHEN a.status = 'Finish' THEN 1
                            WHEN a.status = 'Running' THEN 2
                            WHEN a.status = 'Started' THEN 3
                            ELSE 4
                        END, distance DESC, raceTimeDiff
                ) ranked
                WHERE (rankingPos <= ? OR ? IS NULL)
                            """;
        for (Map<String, Object> result : results) {

            List<Object> checkpointParams = new ArrayList<>();
            checkpointParams.add(type);
            checkpointParams.add(type);
            checkpointParams.add(type);
            checkpointParams.add(id);
            checkpointParams.add(result.get("gender"));
            checkpointParams.add(result.get("gender"));
            checkpointParams.add(result.get("ageGroup"));
            checkpointParams.add(result.get("ageGroup"));
            checkpointParams.add(topRankValue);
            checkpointParams.add(topRankValue);
            List<Map<String, Object>> participants = jdbcTemplate.queryForList(replaceConstants(sql),
                    checkpointParams.toArray());
            Map<String, Object> allParticipant = new HashMap<>();

            String typeName = "ทั้งหมด";
            if ("agegroupBygender".equals(type)) {
                typeName = result.get("gender") + " " + result.get("ageGroup");
            } else if ("gender".equals(type)) {
                typeName = result.get("gender").toString();
            } else if ("agegroup".equals(type)) {
                typeName = result.get("ageGroup").toString();
            }

            allParticipant.put("sheetName", typeName);
            allParticipant.put("columns", columns);
            allParticipant.put("preHeader",
                    List.of("ชื่ออีเว้นท์", result.get("eventName"), result.get("eventTypeName")));

            List<List<String>> sheetData = new ArrayList<>();
            for (Map<String, Object> participant : participants) {
                Object raceTimeDiff = participant.get("raceTimeDiff");
                List<String> row = new ArrayList<>();
                row.add(optString(participant.get("rankingPos")));
                row.add(optString(participant.get("genderPos")));
                row.add(optString(participant.get("ageGroupPos")));
                row.add(optString(participant.get("bibNo")));
                row.add(optString(participant.get("name")));
                row.add(optString(participant.get("gender")));
                row.add(optString(participant.get("nationality")));
                row.add(optString(participant.get("ageGroup")));
                row.add(optString(participant.get("status")));
                row.add(optString(participant.get("lastCP")));
                row.add(optString(participant.get("distance")));
                row.add(optString(participant.get("gunTime")));
                row.add(optString(participant.get("chipTime")));
                row.add(optString(raceTimeDiff != null ? raceTimeDiff : "-"));
                sheetData.add(row);
            }

            allParticipant.put("datas", sheetData);

            formatData.add(allParticipant);
        }
        return formatData;
    }

    public List<Map<String, Object>> getResultLiveExcel(String id, String gender, String ageGroup, String type)
            throws SQLException {
        String[] columns = { "Pos", "ลำดับเพศ", "ลำดับกลุ่มอายุ", "Bib", "Name", "Gender", "Age Group", "Nationality",
                "Last CP", "Last CP Time",
                "Distance", "Gun time", "Chip time", "Status" };
        String sql = """
                SELECT e.id, e.name
                FROM :database.event e
                INNER JOIN :database.campaign c ON c.id = e.campaignId
                WHERE c.uuid = ? AND e.active = true
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

        List<Map<String, Object>> formatData = new ArrayList<>();

        for (Map<String, Object> result : results) {
            sql = """
                    SELECT
                        GROUP_CONCAT(DISTINCT s.name ORDER BY s.orderNum, s.id ) AS stationName
                    FROM :database.checkpointMapping c
                    INNER JOIN :database.station s ON s.id = c.stationId
                    INNER JOIN :database.event et ON et.id = c.eventId
                    WHERE et.id = ?
                    """;
            params.clear();
            params.add(result.get("id"));
            List<Map<String, Object>> stationData = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            String stationNames = null;
            List<String> allColumnsList = new ArrayList<>(Arrays.asList(columns));
            if (!stationData.isEmpty()) {
                stationNames = (String) stationData.get(0).get("stationName");
                if (stationNames != null && !stationNames.isEmpty()) {
                    allColumnsList.addAll(Arrays.asList(stationNames.split(",")));
                }
            }

            String[] allColumns = allColumnsList.toArray(new String[0]);

            sql = """
                    SELECT
                        a.id, a.uuid, a.eventId, a.bibNo, a.name, a.gender, a.nationality,
                        a.lastCP, a.lastCPTime, a.distance, a.gunTime, a.chipTime, a.status, a.ageGroup,
                        CASE
                            WHEN ? = 'gender' THEN a.genderPos
                            WHEN ? = 'agegroup' THEN a.ageGroupPos
                            WHEN ? = 'agegroupBygender' THEN a.ageGroupBygenderPos
                            ELSE a.pos
                        END AS pos, a.genderPos, a.ageGroupBygenderPos AS ageGroupPos
                    FROM :database.v_summary_live a
                    WHERE a.eventId = ?
                    """;

            params.clear();
            params.add(type);
            params.add(type);
            params.add(type);
            params.add(result.get("id"));
            if (gender != null && !gender.equals("")) {
                sql += " AND a.gender like ? ";
                params.add(gender);
            }
            if (ageGroup != null && !ageGroup.equals("")) {
                sql += " AND a.ageGroup like ? ";
                params.add(ageGroup);
            }
            String groupBy = "GROUP BY a.id";
            String orderByClause = " ORDER BY pos IS NULL, pos";

            sql += groupBy + orderByClause;
            List<Map<String, Object>> participants = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            // ดึงข้อมูล Checkpoints
            String cpQuery = """
                    SELECT eventId, participantId, stationName, stationTime
                    FROM :database.v_summary_cp_live
                    WHERE eventId = ?
                    """;
            List<Map<String, Object>> cpData = jdbcTemplate.queryForList(replaceConstants(cpQuery), result.get("id"));

            // สร้าง checkpointMap โดยให้ stationTime เป็น null ได้
            Map<String, Map<String, String>> checkpointMap = cpData.stream()
                    .filter(cp -> cp.get("stationName") != null) // ต้อง filter stationName ไม่ให้เป็น null เพราะใช้เป็น
                                                                 // key
                    .collect(Collectors.groupingBy(
                            cp -> cp.get("eventId") + "-" + cp.get("participantId"),
                            Collectors.toMap(
                                    cp -> (String) cp.get("stationName"),
                                    cp -> cp.get("stationTime") != null
                                            ? cp.get("stationTime").toString()
                                            : null, // คืน null ตรงๆ ถ้า stationTime เป็น null
                                    (oldValue, newValue) -> newValue)));

            Map<String, Object> allParticipant = new HashMap<>();
            allParticipant.put("sheetName", result.get("name"));
            allParticipant.put("columns", allColumns);
            allParticipant.put("preHeader", List.of("ชื่ออีเว้นท์", result.get("name")));
            List<List<String>> sheetData = new ArrayList<>();
            for (Map<String, Object> participant : participants) {
                String key = participant.get("eventId") + "-" + participant.get("id");
                Map<String, String> checkpoints = checkpointMap.getOrDefault(key, new HashMap<>());

                List<String> row = new ArrayList<>();
                row.add(optString(participant.get("pos")));
                row.add(optString(participant.get("genderPos")));
                row.add(optString(participant.get("ageGroupPos")));
                row.add(optString(participant.get("bibNo")));
                row.add(optString(participant.get("name")));
                row.add(optString(participant.get("gender")));
                row.add(optString(participant.get("ageGroup")));
                row.add(optString(participant.get("nationality")));
                row.add(optString(participant.get("lastCP")));
                row.add(optString(participant.get("lastCPTime")));
                row.add(optString(participant.get("distance")));
                row.add(optString(participant.get("gunTime")));
                row.add(optString(participant.get("chipTime")));
                row.add(optString(participant.get("status")));
                // ใช้ checkpoints ที่เตรียมไว้แล้วตรงนี้
                if (stationNames != null && !stationNames.isEmpty()) {
                    String[] stations = stationNames.split(",");
                    for (String station : stations) {
                        row.add(optString(checkpoints.get(station)));
                    }
                }
                sheetData.add(row);
            }

            allParticipant.put("datas", sheetData);

            formatData.add(allParticipant);
        }
        return formatData;
    }

    public List<Map<String, Object>> getResultMoreDetailExcel(String id, String gender, String ageGroup, String topRank,
            String type) throws SQLException {
        String[] columns = { "No.", "ลำดับเพศ", "ลำดับกลุ่มอายุ", "Bib", "ชื่อ นามสกุล", "เพศ", "สัญชาติ", "กลุ่มอายุ",
                "สถานะ", "จุดล่าสุด", "ระยะทาง", "Gun time", "Chip time", "เวลา" };
        String sql = "";
        List<Object> params = new ArrayList<>();
        String genderValue = "".equals(gender) ? null : gender;
        String ageGroupValue = "".equals(ageGroup) ? null : ageGroup;
        String topRankValue = "".equals(topRank) ? null : topRank;

        if ("agegroupBygender".equals(type)) {
            sql = """
                    SELECT eventName, eventTypeName, gender, ageGroup
                    FROM :database.v_summaryResult
                    WHERE eventUuid = ?
                    AND (gender = ? OR ? IS NULL)
                    AND (ageGroup = ? OR ? IS NULL)
                    GROUP BY gender, ageGroup, eventUuid
                    ORDER BY ageGroup, gender
                    """;
            params.add(id);
            params.add(genderValue);
            params.add(genderValue);
            params.add(ageGroupValue);
            params.add(ageGroupValue);
        } else if ("gender".equals(type)) {
            sql = """
                    SELECT eventName, eventTypeName, gender
                    FROM :database.v_summaryResult
                    WHERE eventUuid = ?
                    AND (gender = ? OR ? IS NULL)
                    GROUP BY gender, eventUuid
                    ORDER BY gender
                    """;
            params.add(id);
            params.add(genderValue);
            params.add(genderValue);
        } else if ("agegroup".equals(type)) {
            sql = """
                    SELECT eventName, eventTypeName, ageGroup
                    FROM :database.v_summaryResult
                    WHERE eventUuid = ?
                    AND (ageGroup = ? OR ? IS NULL)
                    GROUP BY ageGroup, eventUuid
                    ORDER BY ageGroup
                    """;
            params.add(id);
            params.add(ageGroupValue);
            params.add(ageGroupValue);
        } else {
            sql = """
                    SELECT c.name AS eventName, e.name AS eventTypeName
                    FROM :database.event e
                    INNER JOIN :database.campaign c ON c.id = e.campaignId
                    WHERE e.uuid = ?
                    """;
            params.add(id);
        }

        List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
        List<Map<String, Object>> formatData = new ArrayList<>();

        sql = """
                SELECT
                    GROUP_CONCAT(DISTINCT s.name ORDER BY s.orderNum, s.id ) AS stationName
                FROM :database.checkpointMapping c
                INNER JOIN :database.station s ON s.id = c.stationId
                INNER JOIN :database.event et ON et.id = c.eventId
                WHERE et.uuid = ?
                """;
        params.clear();
        params.add(id);
        List<Map<String, Object>> stationData = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

        String stationNames = null;
        List<String> allColumnsList = new ArrayList<>(Arrays.asList(columns));
        if (!stationData.isEmpty()) {
            stationNames = (String) stationData.get(0).get("stationName");
            if (stationNames != null && !stationNames.isEmpty()) {
                allColumnsList.addAll(Arrays.asList(stationNames.split(",")));
            }
        }
        String[] allColumns = allColumnsList.toArray(new String[0]);

        sql = """
                SELECT * FROM (
                    SELECT a.id, a.name, a.bibNo, a.gender, a.nationality, a.lastCP, a.distance, a.gunTime, a.chipTime, a.status, a.ageGroup,
                        CASE WHEN a.status IN ('Finish', 'Started', 'Running')
                            THEN TIME_FORMAT(CAST(TIMEDIFF(a.gunTime, MIN(CASE WHEN a.status IN ('Finish', 'Started', 'Running') THEN a.gunTime END) OVER(PARTITION BY a.distance)) AS TIME), '%H:%i:%s')
                            ELSE NULL
                        END AS raceTimeDiff,
                        CASE
                            WHEN ? = 'gender' THEN a.genderPos
                            WHEN ? = 'agegroup' THEN a.ageGroupPos
                            WHEN ? = 'agegroupBygender' THEN a.ageGroupBygenderPos
                            ELSE a.pos
                        END AS rankingPos, a.genderPos, a.ageGroupBygenderPos AS ageGroupPos
                    FROM :database.v_summaryResult a
                    WHERE a.eventUuid = ?
                        AND (a.gender = ? OR ? IS NULL)
                        AND (a.ageGroup = ? OR ? IS NULL)
                    ORDER BY
                        CASE
                            WHEN a.status = 'Finish' THEN 1
                            WHEN a.status = 'Running' THEN 2
                            WHEN a.status = 'Started' THEN 3
                            ELSE 4
                        END, distance DESC, raceTimeDiff
                ) ranked
                WHERE (rankingPos <= ? OR ? IS NULL)
                            """;
        for (Map<String, Object> result : results) {

            List<Object> checkpointParams = new ArrayList<>();
            checkpointParams.add(type);
            checkpointParams.add(type);
            checkpointParams.add(type);
            checkpointParams.add(id);
            checkpointParams.add(result.get("gender"));
            checkpointParams.add(result.get("gender"));
            checkpointParams.add(result.get("ageGroup"));
            checkpointParams.add(result.get("ageGroup"));
            checkpointParams.add(topRankValue);
            checkpointParams.add(topRankValue);
            List<Map<String, Object>> participants = jdbcTemplate.queryForList(replaceConstants(sql),
                    checkpointParams.toArray());

            // ดึงข้อมูล Checkpoints
            String cpQuery = """
                    SELECT eventId, participantId, stationName, stationTime
                    FROM :database.v_final_cp_live
                    WHERE eventUuid = ?
                    """;
            List<Map<String, Object>> cpData = jdbcTemplate.queryForList(replaceConstants(cpQuery), id);

            // สร้าง checkpointMap โดยให้ stationTime เป็น null ได้
            Map<String, Map<String, String>> checkpointMap = cpData.stream()
                    .filter(cp -> cp.get("stationName") != null && cp.get("participantId") != null)
                    .collect(Collectors.groupingBy(
                            cp -> String.valueOf(cp.get("participantId")),
                            Collectors.toMap(
                                    cp -> cp.get("stationName").toString(),
                                    cp -> cp.get("stationTime") != null ? cp.get("stationTime").toString() : null,
                                    (oldValue, newValue) -> newValue)));

            Map<String, Object> allParticipant = new HashMap<>();

            String typeName = "ทั้งหมด";
            if ("agegroupBygender".equals(type)) {
                typeName = result.get("gender") + " " + result.get("ageGroup");
            } else if ("gender".equals(type)) {
                typeName = result.get("gender").toString();
            } else if ("agegroup".equals(type)) {
                typeName = result.get("ageGroup").toString();
            }

            allParticipant.put("sheetName", typeName);
            allParticipant.put("columns", allColumns);
            allParticipant.put("preHeader",
                    List.of("ชื่ออีเว้นท์", result.get("eventName"), result.get("eventTypeName")));

            List<List<String>> sheetData = new ArrayList<>();
            for (Map<String, Object> participant : participants) {
                Object raceTimeDiff = participant.get("raceTimeDiff");
                String key = String.valueOf(participant.get("id"));
                Map<String, String> checkpoints = checkpointMap.getOrDefault(key, new HashMap<>());

                List<String> row = new ArrayList<>();
                row.add(optString(participant.get("rankingPos")));
                row.add(optString(participant.get("genderPos")));
                row.add(optString(participant.get("ageGroupPos")));
                row.add(optString(participant.get("bibNo")));
                row.add(optString(participant.get("name")));
                row.add(optString(participant.get("gender")));
                row.add(optString(participant.get("nationality")));
                row.add(optString(participant.get("ageGroup")));
                row.add(optString(participant.get("status")));
                row.add(optString(participant.get("lastCP")));
                row.add(optString(participant.get("distance")));
                row.add(optString(participant.get("gunTime")));
                row.add(optString(participant.get("chipTime")));
                row.add(optString(raceTimeDiff != null ? raceTimeDiff : "-"));
                // ใช้ checkpoints ที่เตรียมไว้แล้วตรงนี้
                if (stationNames != null && !stationNames.isEmpty()) {
                    String[] stations = stationNames.split(",");
                    for (String station : stations) {
                        row.add(optString(checkpoints.get(station)));
                    }
                }
                sheetData.add(row);
            }

            allParticipant.put("datas", sheetData);

            formatData.add(allParticipant);
        }
        return formatData;
    }

    // #endregion

    // #region insert
    @Transactional(rollbackFor = Exception.class)
    public void createRunner(List<UploadParticipantRequest> dataList) throws SQLException {

        String insertParticipantSql = """
                INSERT INTO :database.participant (eventId, bibNo, firstName, lastName, idNo, gender, birthDate, email, tel, address, province, amphoe, district, zipcode, nationality, bloodGroup, healthProblems, emergencyContact, emergencyContactTel, registerDate, teamName, shirtSize, chipCode, isStarted, active, createdBy) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

        try {
            List<Object> params = new ArrayList<>();

            for (UploadParticipantRequest dataUpload : dataList) {
                Integer eventId = getTransacionalIdByUuid("event", dataUpload.getId());
                List<RunnerRequest> datas = Optional.ofNullable(dataUpload.getData()).orElse(new ArrayList<>());

                for (RunnerRequest data : datas) {
                    params.clear();
                    params.add(data.getBibNo());
                    params.add(eventId);
                    String sql = """
                            SELECT COUNT(p.id) AS dupBibNo, p.id
                            FROM :database.participant p
                            INNER JOIN :database.event e ON e.id = p.eventId
                            WHERE p.bibNo = ? AND p.eventId = ?
                            """;
                    List<RunnerRequest> duplicateBibNo = transactionalQuery(sql, params, RunnerRequest.class);

                    if (data.getIdNo() == null || data.getIdNo().isEmpty()) {
                        String draftIdNo = IdNoGenerator.generateUniqueIdNo();
                        data.setIdNo(draftIdNo);
                    }

                    if (duplicateBibNo.get(0).getDupBibNo() > 0) {
                        params.clear();
                        sql = """
                                UPDATE :database.participant SET firstName = ?, lastName = ?, idNo = ?, gender = ?, birthDate = ?, email = ?, tel = ?, address = ?, province = ?, amphoe = ?, district = ?, zipcode = ?, nationality = ?, bloodGroup = ?, healthProblems = ?, emergencyContact = ?, emergencyContactTel = ?, registerDate = ?, teamName = ?, shirtSize = ?, chipCode = ?, updatedBy = ? WHERE id = ?
                                """;
                        params.add(data.getFirstName());
                        params.add(data.getLastName());
                        params.add(data.getIdNo());
                        params.add(data.getGender());
                        params.add(data.getBirthDate());
                        params.add(data.getEmail());
                        params.add(data.getTel());
                        params.add(data.getAddress());
                        params.add(data.getProvince());
                        params.add(data.getAmphoe());
                        params.add(data.getDistrict());
                        params.add(data.getZipcode());
                        params.add(data.getNationality());
                        params.add(data.getBloodGroup());
                        params.add(data.getHealthProblems());
                        params.add(data.getEmergencyContact());
                        params.add(data.getEmergencyContactTel());
                        params.add(data.getRegisterDate());
                        params.add(data.getTeamName());
                        params.add(data.getShirtSize());
                        params.add(data.getChipCode());
                        params.add(httpSession.getAttribute("userId"));
                        params.add(duplicateBibNo.get(0).getId());
                        jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
                    } else {
                        params.clear();
                        params.add(eventId);
                        params.add(data.getBibNo());
                        params.add(data.getFirstName());
                        params.add(data.getLastName());
                        params.add(data.getIdNo());
                        params.add(data.getGender());
                        params.add(data.getBirthDate());
                        params.add(data.getEmail());
                        params.add(data.getTel());
                        params.add(data.getAddress());
                        params.add(data.getProvince());
                        params.add(data.getAmphoe());
                        params.add(data.getDistrict());
                        params.add(data.getZipcode());
                        params.add(data.getNationality());
                        params.add(data.getBloodGroup());
                        params.add(data.getHealthProblems());
                        params.add(data.getEmergencyContact());
                        params.add(data.getEmergencyContactTel());
                        params.add(data.getRegisterDate());
                        params.add(data.getTeamName());
                        params.add(data.getShirtSize());
                        params.add(data.getChipCode());
                        params.add(false);
                        params.add(true);
                        params.add(httpSession.getAttribute("userId"));
                        jdbcTemplateTrans.update(replaceConstants(insertParticipantSql), params.toArray());
                    }
                }
            }
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRunnerUpload(List<UploadParticipantRequest> dataList) throws SQLException {
        try {
            List<Object> params = new ArrayList<>();

            for (UploadParticipantRequest dataUpload : dataList) {
                Integer eventId = getTransacionalIdByUuid("event", dataUpload.getId());
                List<RunnerRequest> datas = Optional.ofNullable(dataUpload.getData()).orElse(new ArrayList<>());
                StringBuilder valuesLabel = new StringBuilder("");

                for (RunnerRequest data : datas) {
                    params.add(getTransacionalIdByUuid("participant", data.getUuid()));
                    params.add(eventId);
                    params.add(data.getBibNo());
                    params.add(data.getFirstName());
                    params.add(data.getLastName());
                    params.add(data.getIdNo());
                    params.add(data.getGender());
                    params.add(data.getBirthDate());
                    params.add(data.getEmail());
                    params.add(data.getTel());
                    params.add(data.getAddress());
                    params.add(data.getProvince());
                    params.add(data.getAmphoe());
                    params.add(data.getDistrict());
                    params.add(data.getZipcode());
                    params.add(data.getNationality());
                    params.add(data.getBloodGroup());
                    params.add(data.getHealthProblems());
                    params.add(data.getEmergencyContact());
                    params.add(data.getEmergencyContactTel());
                    params.add(data.getRegisterDate());
                    params.add(data.getTeamName());
                    params.add(data.getShirtSize());
                    params.add(data.getChipCode());
                    params.add(false);
                    params.add(true);
                    params.add(httpSession.getAttribute("userId"));

                    valuesLabel
                            .append("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?),");

                }
                if (!Strings.isNullOrEmpty(valuesLabel.toString())) {
                    valuesLabel.delete(valuesLabel.length() - 1, valuesLabel.length());
                    String sql = """
                            INSERT INTO :database.participant (id, eventId, bibNo, firstName, lastName, idNo, gender, birthDate, email, tel, address, province, amphoe, district, zipcode, nationality, bloodGroup, healthProblems, emergencyContact, emergencyContactTel, registerDate, teamName, shirtSize, chipCode, isStarted, active, createdBy) VALUES
                            """
                            + valuesLabel
                            + """
                                    AS new ON DUPLICATE KEY UPDATE firstName = new.firstName, lastName = new.lastName, idNo = new.idNo, gender = new.gender, birthDate = new.birthDate, email = new.email, tel = new.tel, address = new.address, province = new.province, amphoe = new.amphoe, district = new.district, zipcode = new.zipcode, nationality = new.nationality, bloodGroup = new.bloodGroup, healthProblems = new.healthProblems, emergencyContact = new.emergencyContact, emergencyContactTel = new.emergencyContactTel, registerDate = new.registerDate, teamName = new.teamName, shirtSize = new.shirtSize, bibNo = new.bibNo, chipCode = new.chipCode, updatedBy = new.createdBy
                                    """;
                    jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
                }
            }
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region update
    @Transactional(rollbackFor = Exception.class)
    public void updateParticipant(ParticipantData data) throws SQLException {
        try {
            List<Object> params = new ArrayList<>();
            String fetchOldSql = """
                        SELECT status, isManualStatus FROM :database.participant WHERE uuid = ?
                    """;
            Map<String, Object> oldData = jdbcTemplateTrans.queryForMap(replaceConstants(fetchOldSql), data.getUuid());

            String oldStatus = (String) oldData.get("status");
            Integer oldIsManual = (Integer) oldData.get("isManualStatus");

            boolean statusChanged = (oldStatus == null && data.getStatus() != null)
                    || (oldStatus != null && !oldStatus.equals(data.getStatus()));

            int isManualToSet = (oldIsManual != null && oldIsManual == 1) || statusChanged ? 1 : 0;

            String sql = """
                        UPDATE :database.participant
                        SET bibNo = ?, firstName = ?, lastName = ?, idNo = ?, gender = ?, birthDate = ?, nationality = ?, registerDate = ?, teamName = ?, shirtSize = ?, chipCode = ?, status = ?, isStarted = ?, isManualStatus = ?, updatedBy = ?
                        WHERE uuid = ?
                    """;
            params.add(data.getBibNo());
            params.add(data.getFirstName());
            params.add(data.getLastName());
            params.add(data.getIdNo());
            params.add(data.getGender());
            params.add(data.getBirthDate());
            params.add(data.getNationality());
            params.add(data.getRegisterDate());
            params.add(data.getTeamName());
            params.add(data.getShirtSize());
            params.add(data.getChipCode());
            params.add(data.getStatus());
            params.add(data.getIsStarted());
            params.add(isManualToSet);
            params.add(httpSession.getAttribute("userId"));
            params.add(data.getUuid());
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());

        } catch (DataAccessException e) {
            log.error("Error updating participant", e);
            throw new SQLException("Failed to update participant: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatusParticipant(Integer id) throws SQLException {
        log.info("Updating participant status for checkpoint Id: {}", id);
        try {
            // เปลี่ยน Started เป็น DNS หากเกินเวลาจบงานและไม่ได้มาวิ่ง
            updateToDNS(id);

            // เปลี่ยน Running เป็น DNF หากเกินเวลา checkpoint
            updateToDNF(id);

        } catch (DataAccessException e) {
            log.error("Failed to update participant status: {}", e.getMessage(), e);
            throw new SQLException("Unable to update participant status", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void reverseStatusParticipant(Integer id) throws SQLException {
        log.info("Reverse participant status for event Id: {}", id);
        try {
            revertDNSToRunning(id);

            revertDNFToRunning(id);

        } catch (DataAccessException e) {
            log.error("Failed to reverse participant status: {}", e.getMessage(), e);
            throw new SQLException("Unable to reverse participant status", e);
        }
    }

    private void updateToDNS(Integer checkpointMappingId) {
        String sql = """
                UPDATE :database.participant p
                JOIN :database.checkpointMapping c ON c.eventId = p.eventId
                LEFT JOIN (
                    SELECT t1.*
                    FROM :database.timeRecord t1
                    JOIN (
                        SELECT participantId, checkpointMappingId,
                                MIN(CASE WHEN recordType = 'manual' THEN 1 ELSE 2 END) AS priority
                        FROM :database.timeRecord
                        GROUP BY participantId, checkpointMappingId
                    ) pref ON pref.participantId = t1.participantId
                        AND pref.checkpointMappingId = t1.checkpointMappingId
                        AND ((pref.priority = 1 AND t1.recordType = 'manual') OR (pref.priority = 2 AND t1.recordType != 'manual'))
                ) t ON t.participantId = p.id AND t.checkpointMappingId = c.id
                SET p.status = ?, p.updatedBy = ?
                WHERE (p.status IS NULL OR p.status NOT IN ('DNS'))
                    AND t.id IS NULL
                    AND c.distance = 0
                    AND c.id = ?
                    AND CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok') >= c.cutOffTime
                """;

        jdbcTemplateTrans.update(replaceConstants(sql), "DNS", 1, checkpointMappingId);
    }

    private void updateToDNF(Integer checkpointMappingId) {
        String sql = """
                UPDATE :database.participant p
                JOIN :database.checkpointMapping cm ON cm.id = ?
                LEFT JOIN (
                    SELECT t1.*
                    FROM :database.timeRecord t1
                    JOIN (
                        SELECT participantId, checkpointMappingId,
                                MIN(CASE WHEN recordType = 'manual' THEN 1 ELSE 2 END) AS priority
                        FROM :database.timeRecord
                        GROUP BY participantId, checkpointMappingId
                    ) pref ON pref.participantId = t1.participantId
                        AND pref.checkpointMappingId = t1.checkpointMappingId
                        AND ((pref.priority = 1 AND t1.recordType = 'manual') OR (pref.priority = 2 AND t1.recordType != 'manual'))
                ) t ON t.participantId = p.id AND t.checkpointMappingId = cm.id
                    AND t.raceTimingOut <= cm.cutOffTime
                SET p.status = ?, p.updatedBy = ?
                WHERE p.eventId = cm.eventId
                    AND p.status IN ('Running', 'Finish')
                    AND t.id IS NULL
                    AND cm.distance != 0
                    AND CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok') >= cm.cutOffTime
                """;

        jdbcTemplateTrans.update(replaceConstants(sql), checkpointMappingId, "DNF", 1);
    }

    private void revertDNSToRunning(Integer eventId) {
        if (eventId == null)
            return;

        String sql = """
                UPDATE :database.participant p
                JOIN :database.checkpointMapping cm ON cm.eventId = p.eventId
                JOIN :database.event e ON cm.eventId = e.id
                LEFT JOIN (
                    SELECT t1.*
                    FROM :database.timeRecord t1
                    JOIN (
                        SELECT participantId, checkpointMappingId,
                            MIN(CASE WHEN recordType = 'manual' THEN 1 ELSE 2 END) AS priority
                        FROM :database.timeRecord
                        GROUP BY participantId, checkpointMappingId
                    ) pref ON pref.participantId = t1.participantId
                        AND pref.checkpointMappingId = t1.checkpointMappingId
                        AND ((pref.priority = 1 AND t1.recordType = 'manual') OR (pref.priority = 2 AND t1.recordType != 'manual'))
                ) t ON t.participantId = p.id AND t.checkpointMappingId = cm.id
                SET p.status = CASE
                    WHEN CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok') > e.eventDate THEN 'Running'
                    ELSE NULL
                END, p.updatedBy = ?
                WHERE p.status = 'DNS'
                    AND p.eventId = ?
                    AND cm.distance = 0
                    AND cm.eventId = p.eventId
                    AND (
                        (t.raceTimingOut IS NOT NULL AND t.raceTimingOut <= cm.cutOffTime)
                        OR (t.id IS NULL AND CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok') < cm.cutOffTime)
                    )
                """;

        jdbcTemplateTrans.update(replaceConstants(sql), 1, eventId);
    }

    private void revertDNFToRunning(Integer eventId) {
        if (eventId == null)
            return;

        String sql = """
                UPDATE :database.participant p
                JOIN (
                    SELECT
                        sub.participantId,
                        MAX(CASE
                            WHEN sub.distance = max_distance.maxDistance THEN 1
                            ELSE 0
                        END) AS reachedFinish
                    FROM (
                        SELECT
                            t1.participantId,
                            cm.id AS checkpointMappingId,
                            t1.raceTimingOut,
                            cm.cutOffTime,
                            cm.distance,
                            cm.eventId
                        FROM :database.timeRecord t1
                        JOIN (
                            SELECT
                                participantId,
                                checkpointMappingId,
                                MIN(CASE WHEN recordType = 'manual' THEN 1 ELSE 2 END) AS priority
                            FROM :database.timeRecord
                            WHERE raceTimingOut IS NOT NULL
                            GROUP BY participantId, checkpointMappingId
                        ) pref ON pref.participantId = t1.participantId
                            AND pref.checkpointMappingId = t1.checkpointMappingId
                            AND (
                                (pref.priority = 1 AND t1.recordType = 'manual') OR
                                (pref.priority = 2 AND t1.recordType != 'manual')
                            )
                        JOIN :database.checkpointMapping cm ON cm.id = t1.checkpointMappingId
                    ) sub
                    JOIN (
                        SELECT eventId, MAX(distance) AS maxDistance
                        FROM :database.checkpointMapping
                        GROUP BY eventId
                    ) max_distance ON sub.eventId = max_distance.eventId
                    GROUP BY sub.participantId
                    HAVING MAX(CASE WHEN sub.raceTimingOut > sub.cutOffTime THEN 1 ELSE 0 END) = 0
                ) valid_participant ON p.id = valid_participant.participantId
                SET p.status = CASE
                        WHEN valid_participant.reachedFinish = 1 THEN 'Finish'
                        ELSE 'Running'
                    END,
                    p.updatedBy = ?
                WHERE p.status = 'DNF'
                AND p.eventId = ?
                AND p.isManualStatus = 0;
                                """;

        jdbcTemplateTrans.update(replaceConstants(sql), 1, eventId);
    }

    public void revertToStarted(Integer eventId) {
        if (eventId == null)
            return;

        String sql = """
                UPDATE :database.participant
                SET status = null, isStarted = FALSE, updatedBy = ?
                WHERE status is not null
                    AND eventId = ?
                """;
        jdbcTemplateTrans.update(replaceConstants(sql), 1, eventId);
    }
    // #endregion

    // #region delete
    @Transactional(rollbackFor = Exception.class)
    public void deleteRunner(String id) throws SQLException {

        try {
            String sql = "DELETE FROM :database.participant WHERE uuid = ? ";
            List<Object> params = new ArrayList<>();
            params.add(id);
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteParticipant(String id) throws SQLException {

        try {
            String sql = "DELETE FROM :database.participant WHERE uuid = ? ";
            List<Object> params = new ArrayList<>();
            params.add(id);
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAllParticipants(String id) throws SQLException {
        Integer eventId = getTransacionalIdByUuid("event", id);

        try {
            String sql = "DELETE FROM :database.participant WHERE eventId = ? ";
            List<Object> params = new ArrayList<>();
            params.add(eventId);
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion
}