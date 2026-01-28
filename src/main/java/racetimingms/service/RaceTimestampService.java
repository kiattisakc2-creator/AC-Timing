package racetimingms.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.validation.ValidationException;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;
import racetimingms.model.DatabaseData;
import racetimingms.model.PagingData;
import racetimingms.model.RaceTimestampData;
import racetimingms.model.ResultData;
import racetimingms.model.TimeRecordData;
import racetimingms.model.request.RaceTimestampRequest;
import racetimingms.model.request.RaceTimestampRequest.ParticipantItems;
import racetimingms.model.request.RaceTimestampRequest.RaceTimingItems;
import racetimingms.model.request.RunnerAndTimeRequest;
import racetimingms.model.request.UploadParticipantAndTimeRequest;

@Slf4j
@Component
public class RaceTimestampService extends DatabaseService {
    // #region get
    public DatabaseData getRaceTimestampByStation(String id, PagingData paging)
            throws SQLException {

        String sql = """
                SELECT
                        c.bibNo, COUNT(*) OVER() as hits,
                        CONCAT(c.firstName, IFNULL(CONCAT(' ', c.lastName), '')) AS name,
                        DATE_FORMAT(a.raceTimingIn, '%Y-%m-%d %T') AS raceTimingIn, b.uuid AS stationUuid, cm.scanInOut
                FROM :database.timeRecord a
                INNER JOIN :database.checkpointMapping cm ON cm.id = a.checkpointMappingId
                INNER JOIN :database.station b ON b.id = cm.stationId
                INNER JOIN :database.participant c ON c.id = a.participantId
                WHERE b.uuid = ? AND a.recordType = 'qrcode'
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);

        try {
            if (paging != null && !Strings.isNullOrEmpty(paging.getSearchField())
                    && !Strings.isNullOrEmpty(paging.getSearchText())) {
                switch (paging.getSearchField()) {
                    case "bibName":
                        sql += "and (c.bibNo like ? or CONCAT(c.firstName, IFNULL(CONCAT(' ', c.lastName), '')) like ? ) ";
                        params.add(paging.getSearchText());
                        params.add(paging.getSearchText());
                        break;
                    default:
                        break;
                }
            }

            String orderByClause = " ORDER BY a.raceTimingIn DESC, a.createdTime DESC ";
            String limitClause = "";

            if (paging != null) {

                if (paging.getStart() != null && paging.getLimit() != null) {
                    limitClause = " LIMIT " + paging.getStart() + ", " + paging.getLimit();
                }
            } else {
                limitClause = " LIMIT 15";
            }

            sql += orderByClause + limitClause;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            List<RaceTimestampData> data = mapper.convertValue(results,
                    new TypeReference<List<RaceTimestampData>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getParticipantBycampaign(String campaignUuid)
            throws SQLException {

        String sql = """
                SELECT
                        DISTINCT p.bibNo, CONCAT(p.firstName, IFNULL(CONCAT(' ', p.lastName), '')) AS name
                FROM :database.participant p
                INNER JOIN :database.event e ON e.id = p.eventId
                INNER JOIN :database.campaign c ON c.id = e.campaignId
                WHERE c.uuid = ?
                """;
        List<Object> params = new ArrayList<>();
        params.add(campaignUuid);

        try {
            List<Map<String, Object>> data = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            return data;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region insert
    @Transactional(rollbackFor = Exception.class)
    public void createRaceTimestampWithQRCode(RaceTimestampRequest data) throws SQLException {
        Integer stationId = getTransacionalIdByUuid("station", data.getStationUuid());
        Integer campaignId = getTransacionalIdByUuid("campaign", data.getCampaignUuid());
        String sql = "";
        List<Object> params = new ArrayList<>();
        try {
            List<RaceTimingItems> dataItems = Optional.ofNullable(data.getRaceTimingItems()).orElse(new ArrayList<>());
            for (RaceTimingItems item : dataItems) {
                sql = """
                        SELECT
                            a.id AS participantId, cp.id AS checkpointMappingId, a.eventId,
                            e.eventDate, IFNULL(cp.distance, 0) AS distance, d.lastResultId,
                            COUNT(c.participantId) AS dupResult
                        FROM :database.participant a
                        INNER JOIN :database.event e ON e.id = a.eventId
                        INNER JOIN :database.checkpointMapping cp ON cp.eventId = a.eventId
                        INNER JOIN :database.station b ON b.id = cp.stationId
                        LEFT JOIN :database.timeRecord c ON c.participantId = a.id AND c.checkpointMappingId = cp.id
                        LEFT JOIN :database.latestStationRaceTiming d ON d.participantId = a.id
                        WHERE a.bibNo = ? AND b.campaignId = ? AND cp.stationId = ? AND (c.recordType IS NULL OR c.recordType != 'rfid')
                        GROUP BY a.id, a.eventId
                            """;
                params.clear();
                params.add(item.getBibNo());
                params.add(campaignId);
                params.add(stationId);
                List<ParticipantItems> result = transactionalQuery(sql, params, ParticipantItems.class);

                if (!result.isEmpty() && result.get(0).getDupResult() < 1) {
                    params.clear();
                    if ("Out".equals(item.getType())) {
                        sql = """
                                UPDATE :database.timeRecord SET raceTimingOut = ?, updatedBy = ?
                                WHERE id = ?
                                """;
                        params.add(item.getRaceTimingIn());
                        params.add(httpSession.getAttribute("userId"));
                        params.add(result.get(0).getLastResultId());
                        jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
                    } else {
                        sql = """
                                INSERT INTO :database.timeRecord ( checkpointMappingId, participantId, raceTimingIn, raceTimingOut, checkInTime, recordType, active, createdBy ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                """;
                        params.add(result.get(0).getCheckpointMappingId());
                        params.add(result.get(0).getParticipantId());
                        params.add(result.get(0).getDistance() == 0 ? result.get(0).getEventDate() : item.getRaceTimingIn());
                        params.add(result.get(0).getDistance() == 0 ? result.get(0).getEventDate() : item.getRaceTimingIn());
                        params.add(item.getRaceTimingIn());
                        params.add("qrcode");
                        params.add(true);
                        params.add(httpSession.getAttribute("userId"));
                        jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
                    }
                } else if (!result.isEmpty() && item.getUpdateDupTime() != null) {
                    params.clear();
                    sql = """
                            UPDATE :database.timeRecord SET raceTimingIn = ?, raceTimingOut = ?, updatedBy = ?
                            WHERE id = ?
                            """;
                    params.add(result.get(0).getDistance() == 0 ? result.get(0).getEventDate() : item.getUpdateDupTime());
                    params.add(result.get(0).getDistance() == 0 ? result.get(0).getEventDate() : item.getUpdateDupTime());
                    params.add(httpSession.getAttribute("userId"));
                    params.add(result.get(0).getLastResultId());
                    jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
                }

                Integer haveStarted = 0;
                sql = """
                        SELECT
                            COUNT(f.eventId) AS haveStarted
                        FROM racetiming.participant p
                        INNER JOIN racetiming.timeRecord t ON t.participantId = p.id
                        INNER JOIN (
                            SELECT cp.eventId, cp.id AS checkpointMappingId
                            FROM racetiming.checkpointMapping cp
                            INNER JOIN racetiming.station s ON s.id = cp.stationId
                            WHERE cp.eventId = ? ORDER BY s.orderNum ASC LIMIT 1)
                        f ON f.eventId = p.eventId  AND f.checkpointMappingId = t.checkpointMappingId
                        WHERE p.id = ?
                        """;
                params.clear();
                params.add(result.get(0).getEventId());
                params.add(result.get(0).getParticipantId());
                try {
                    haveStarted = jdbcTemplateTrans.queryForObject(sql, Integer.class, params.toArray());
                } catch (EmptyResultDataAccessException e) {
                    haveStarted = 0;
                }

                sql = """
                        UPDATE :database.participant SET isStarted = ?, updatedBy = ?
                        WHERE id = ?
                        """;
                params.clear();
                params.add(haveStarted > 0); // true หรือ false
                params.add(httpSession.getAttribute("userId"));
                params.add(result.get(0).getParticipantId());
                jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
            }
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region update
    public void updateRaceTimestamp(ResultData data) throws SQLException {

        String sql = """
                UPDATE :database.timeRecord SET raceTimingIn = ?, raceTimingOut = ?, updatedBy = ?
                WHERE uuid = ?
                    """;
        List<Object> params = new ArrayList<>();
        params.add(data.getRaceTimingIn());
        params.add(data.getRaceTimingOut());
        params.add(httpSession.getAttribute("userId"));
        params.add(data.getUuid());

        try {
            int result = jdbcTemplate.update(replaceConstants(sql), params.toArray());

            if (result < 1) {
                throw new ValidationException(NO_ROW_ERR);
            }
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateParticipantTimeCP(List<TimeRecordData> data) throws SQLException {

        String sql = "";

        try {
            List<Object> paramsInsert = new ArrayList<>();
            List<Object> paramsUpdate = new ArrayList<>();

            if (!data.isEmpty()) {
                Integer participantId = getTransacionalIdByUuid("participant", data.get(0).getParticipantUuid());

                List<String> itemUuids = new ArrayList<>();
                StringBuilder valuesLabelInsert = new StringBuilder("");
                StringBuilder valuesLabelUpdatable = new StringBuilder("");
                for (TimeRecordData item : data) {
                    // แยกเงื่อนไขสำหรับ recordType
                    if ("rfid".equalsIgnoreCase(item.getRecordType())) {
                        paramsInsert.add(getTransacionalIdByUuid("checkpointMapping", item.getCheckpointMappingUuid()));
                        paramsInsert.add(participantId);
                        paramsInsert.add(item.getRaceTimingIn());
                        paramsInsert.add(item.getRaceTimingOut());
                        paramsInsert.add("manual");
                        paramsInsert.add(true);
                        paramsInsert.add(httpSession.getAttribute("userId"));
                        valuesLabelInsert.append("(?, ?, ?, ?, ?, ?, ?),");
                    } else {
                        paramsUpdate.add(getTransacionalIdByUuid("timeRecord", item.getUuid()));
                        paramsUpdate.add(getTransacionalIdByUuid("checkpointMapping", item.getCheckpointMappingUuid()));
                        paramsUpdate.add(participantId);
                        paramsUpdate.add(item.getRaceTimingIn());
                        paramsUpdate.add(item.getRaceTimingOut());
                        paramsUpdate.add("manual");
                        paramsUpdate.add(true);
                        paramsUpdate.add(httpSession.getAttribute("userId"));
                        valuesLabelUpdatable.append("(?, ?, ?, ?, ?, ?, ?, ?),");
                        if (item.getUuid() != null) {
                            itemUuids.add(item.getUuid());
                        }
                    }
                }

                if (!itemUuids.isEmpty()) {
                    sql = "DELETE FROM :database.timeRecord WHERE uuid NOT IN ("
                            + String.join(",",
                                    itemUuids.stream().map(uuid -> "'" + uuid.toString() +
                                            "'").toArray(String[]::new))
                            + ") AND participantId = ? AND recordType != 'rfid'";
                    List<Object> paramsItem = new ArrayList<>();
                    paramsItem.add(participantId);
                    jdbcTemplateTrans.update(replaceConstants(sql), paramsItem.toArray());
                } else {
                    sql = "DELETE FROM :database.timeRecord WHERE participantId = ? AND recordType != 'rfid'";
                    List<Object> paramsItem = new ArrayList<>();
                    paramsItem.add(participantId);
                    jdbcTemplateTrans.update(replaceConstants(sql), paramsItem.toArray());
                }

                // INSERT สำหรับ recordType = rfid เท่านั้น
                if (!Strings.isNullOrEmpty(valuesLabelInsert.toString())) {
                    valuesLabelInsert.delete(valuesLabelInsert.length() - 1, valuesLabelInsert.length());
                    sql = "INSERT INTO :database.timeRecord (checkpointMappingId, participantId, raceTimingIn, raceTimingOut, recordType, active, createdBy) VALUES "
                            + valuesLabelInsert;
                    jdbcTemplateTrans.update(replaceConstants(sql), paramsInsert.toArray());
                }

                // INSERT ON DUPLICATE KEY UPDATE สำหรับ recordType != rfid
                if (!Strings.isNullOrEmpty(valuesLabelUpdatable.toString())) {
                    valuesLabelUpdatable.delete(valuesLabelUpdatable.length() - 1, valuesLabelUpdatable.length());
                    sql = "INSERT INTO :database.timeRecord (id, checkpointMappingId, participantId, raceTimingIn, raceTimingOut, recordType, active, createdBy) VALUES "
                            + valuesLabelUpdatable
                            + " AS new ON DUPLICATE KEY UPDATE raceTimingIn = new.raceTimingIn, raceTimingOut = new.raceTimingOut, updatedBy = new.createdBy";
                    jdbcTemplateTrans.update(replaceConstants(sql), paramsUpdate.toArray());
                }

                List<Object> params = new ArrayList<>();
                Integer eventId = getTransacionalIdByUuid("event", data.get(0).getEventUuid());
                Integer haveStarted = 0;
                sql = """
                        SELECT
                            COUNT(f.eventId) AS haveStarted
                        FROM racetiming.participant p
                        INNER JOIN racetiming.timeRecord t ON t.participantId = p.id
                        INNER JOIN (
                            SELECT cp.eventId, cp.id AS checkpointMappingId
                            FROM racetiming.checkpointMapping cp
                            INNER JOIN racetiming.station s ON s.id = cp.stationId
                            WHERE cp.eventId = ? ORDER BY s.orderNum ASC LIMIT 1)
                        f ON f.eventId = p.eventId  AND f.checkpointMappingId = t.checkpointMappingId
                        WHERE p.id = ?
                        """;
                params.clear();
                params.add(eventId);
                params.add(participantId);
                try {
                    haveStarted = jdbcTemplateTrans.queryForObject(sql, Integer.class, params.toArray());
                } catch (EmptyResultDataAccessException e) {
                    haveStarted = 0;
                }

                sql = """
                        UPDATE :database.participant SET isStarted = ?, updatedBy = ?
                        WHERE id = ?
                        """;
                params.clear();
                params.add(haveStarted > 0); // true หรือ false
                params.add(httpSession.getAttribute("userId"));
                params.add(participantId);
                jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
            }

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateParticipantAndTime(List<UploadParticipantAndTimeRequest> dataList) throws SQLException {
        try {
            if (!dataList.isEmpty()) {
                String sql = "";
                List<Object> paramsInsert = new ArrayList<>();
                List<Object> paramsUpdate = new ArrayList<>();
                StringBuilder valuesLabelInsert = new StringBuilder("");
                StringBuilder valuesLabelUpdatable = new StringBuilder("");
                for (UploadParticipantAndTimeRequest dataUpload : dataList) {
                    Integer checkpointMappingId = getTransacionalIdByUuid("checkpointMapping", dataUpload.getId());
                    List<RunnerAndTimeRequest> datas = Optional.ofNullable(dataUpload.getData())
                            .orElse(new ArrayList<>());
                    for (RunnerAndTimeRequest data : datas) {
                        Integer participantId = getTransacionalIdByUuid("participant", data.getParticipantUuid());
                        // ทำงานเฉพาะเมื่อ RaceTimingIn และ RaceTimingOut มีค่า
                        if (data.getRaceTimingIn() != null && data.getRaceTimingOut() != null) {
                            // แยกเงื่อนไขสำหรับ recordType
                            if ("rfid".equalsIgnoreCase(data.getRecordType())) {
                                paramsInsert.add(checkpointMappingId);
                                paramsInsert.add(participantId);
                                paramsInsert.add(data.getRaceTimingIn());
                                paramsInsert.add(data.getRaceTimingOut());
                                paramsInsert.add("manual");
                                paramsInsert.add(true);
                                paramsInsert.add(httpSession.getAttribute("userId"));
                                valuesLabelInsert.append("(?, ?, ?, ?, ?, ?, ?),");
                            } else {
                                paramsUpdate.add(getTransacionalIdByUuid("timeRecord", data.getUuid()));
                                paramsUpdate.add(checkpointMappingId);
                                paramsUpdate.add(participantId);
                                paramsUpdate.add(data.getRaceTimingIn());
                                paramsUpdate.add(data.getRaceTimingOut());
                                paramsUpdate.add("manual");
                                paramsUpdate.add(true);
                                paramsUpdate.add(httpSession.getAttribute("userId"));
                                valuesLabelUpdatable.append("(?, ?, ?, ?, ?, ?, ?, ?),");
                            }
                        }
                    }
                }

                // INSERT สำหรับ recordType = rfid เท่านั้น
                if (!Strings.isNullOrEmpty(valuesLabelInsert.toString())) {
                    valuesLabelInsert.delete(valuesLabelInsert.length() - 1, valuesLabelInsert.length());
                    sql = "INSERT INTO :database.timeRecord (checkpointMappingId, participantId, raceTimingIn, raceTimingOut, recordType, active, createdBy) VALUES "
                            + valuesLabelInsert;
                    jdbcTemplateTrans.update(replaceConstants(sql), paramsInsert.toArray());
                }

                // INSERT ON DUPLICATE KEY UPDATE สำหรับ recordType != rfid
                if (!Strings.isNullOrEmpty(valuesLabelUpdatable.toString())) {
                    valuesLabelUpdatable.delete(valuesLabelUpdatable.length() - 1, valuesLabelUpdatable.length());
                    sql = "INSERT INTO :database.timeRecord (id, checkpointMappingId, participantId, raceTimingIn, raceTimingOut, recordType, active, createdBy) VALUES "
                            + valuesLabelUpdatable
                            + " AS new ON DUPLICATE KEY UPDATE raceTimingIn = new.raceTimingIn, raceTimingOut = new.raceTimingOut, updatedBy = new.createdBy";
                    jdbcTemplateTrans.update(replaceConstants(sql), paramsUpdate.toArray());
                }
            }

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region delete
    @Transactional(rollbackFor = Exception.class)
    public void deleteRaceTimestamp(String id) throws SQLException {

        try {
            String sql = "DELETE FROM :database.timeRecord WHERE uuid = ? ";
            List<Object> params = new ArrayList<>();
            params.add(id);
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion
}
