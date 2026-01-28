package racetimingms.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import javax.validation.ValidationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;
import racetimingms.model.DatabaseData;
import racetimingms.model.EventData;
import racetimingms.model.AgeGroupData;
import racetimingms.model.CampaignData;
import racetimingms.model.CheckpointMappingData;
import racetimingms.model.PagingData;
import racetimingms.model.StationData;
import racetimingms.model.request.UserStationRequest;

@Slf4j
@Component
public class EventService extends DatabaseService {

    @Value("${app.sync-summaryResult-enabled}")
    private boolean syncSummaryResultEnabled;

    @Autowired
    private CheckpointService checkpointService;

    @Autowired
    private RunnerService runnerService;

    // #region get
    public DatabaseData getAllCampaign(String id, String user, String role, PagingData paging) throws SQLException {
        List<Object> params = new ArrayList<>();
        String condition = "";
        if ("organizer".equals(role)) {
            Integer userId = getIdByUuid("user", user);
            condition = " AND a.organizerId = ? ";
            params.add(userId);
        }
        String sql = """
                SELECT * FROM (
                    SELECT
                        a.uuid, COUNT(*) OVER() as hits, a.organizerName, a.name, a.shortName, a.description,
                        DATE_FORMAT(a.eventDate, '%Y-%m-%d %T') AS eventDate, a.location, a.prefixPath,
                        a.logoUrl, a.pictureUrl, a.bgUrl, a.email, a.website, a.facebook, a.contactName, a.contactTel,
                        a.isDraft, GROUP_CONCAT(DISTINCT b.category SEPARATOR ', ') AS category, a.active,
                        a.allowRFIDSync, a.rfid_token, a.raceid, a.chipBgUrl, a.chipBanner, a.chipPrimaryColor, a.chipSecondaryColor, a.chipModeColor,
                        a.certTextColor, GROUP_CONCAT(DISTINCT b.name SEPARATOR ', ') AS eventNameType,
                        c.uuid AS organizerUuid, COUNT(b.id) AS eventTotal, a.status, a.isApproveCertificate
                    FROM
                        :database.campaign a
                    LEFT JOIN
                        :database.event b ON b.campaignId = a.id AND b.active = true
                    LEFT JOIN
                        :database.user c ON c.id = a.organizerId
                    WHERE
                        a.active = true
                    """
                + condition;

        try {
            if (paging != null && !Strings.isNullOrEmpty(paging.getSearchField())
                    && !Strings.isNullOrEmpty(paging.getSearchText())) {
                switch (paging.getSearchField()) {
                    case "category":
                        sql += "and b.category like ? ";
                        params.add(paging.getSearchText());
                        break;
                    default:
                        break;
                }
            }

            String groupBy = " GROUP BY a.id ) AS subquery ";
            String orderByClause = "";
            String limitClause = "";

            if (paging != null) {
                if (!Strings.isNullOrEmpty(paging.getField()) && !Strings.isNullOrEmpty(paging.getSort())) {
                    orderByClause = " ORDER BY " + paging.getField() + " "
                            + paging.getSort();
                } else {
                    orderByClause = " ORDER BY eventDate DESC";
                }

                if (paging.getStart() != null && paging.getLimit() != null) {
                    limitClause = " LIMIT " + paging.getStart() + ", " + paging.getLimit();
                }
            } else {
                orderByClause = " ORDER BY eventDate DESC";
                limitClause = " LIMIT 15";
            }

            sql += groupBy + orderByClause + limitClause;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            for (Map<String, Object> row : results) {
                String prefixPath = (String) row.get("prefixPath");
                String thumbLogoUrl = (String) row.get("logoUrl");
                String thumbPictureUrl = (String) row.get("pictureUrl");
                String thumbBgUrl = (String) row.get("bgUrl");
                String thumbChipImgUrl = (String) row.get("chipBgUrl");
                if (prefixPath != null && !prefixPath.isEmpty()) {
                    if (thumbLogoUrl != null && !thumbLogoUrl.isEmpty()) {
                        String publicUrlLogo = getPublicUrl(prefixPath, thumbLogoUrl);
                        row.put("thumbLogoUrl", publicUrlLogo);
                    }
                    if (thumbPictureUrl != null && !thumbPictureUrl.isEmpty()) {
                        String publicUrlPicture = getPublicUrl(prefixPath, thumbPictureUrl);
                        row.put("thumbPictureUrl", publicUrlPicture);
                    }
                    if (thumbBgUrl != null && !thumbBgUrl.isEmpty()) {
                        String publicUrlBg = getPublicUrl(prefixPath, thumbBgUrl);
                        row.put("thumbBgUrl", publicUrlBg);
                    }
                    if (thumbChipImgUrl != null && !thumbChipImgUrl.isEmpty()) {
                        String publicChipUrlBg = getPublicUrl(prefixPath, thumbChipImgUrl);
                        row.put("thumbChipImgUrl", publicChipUrlBg);
                    }
                }
            }

            List<CampaignData> data = mapper.convertValue(results,
                    new TypeReference<List<CampaignData>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public DatabaseData getEventByCampaign(String id, String user, String role, PagingData paging) throws SQLException {
        List<Object> params = new ArrayList<>();
        params.add(id);
        String condition = "";
        if ("organizer".equals(role)) {
            Integer userId = getIdByUuid("user", user);
            condition = " AND a.organizerId = ? ";
            params.add(userId);
        }

        String sql = """
                SELECT
                    b.uuid, COUNT(*) OVER() as hits, a.uuid AS campaignUuid, b.campaignId, b.name, DATE_FORMAT(b.eventDate, '%Y-%m-%d %T') AS eventDate,
                    b.category, b.otherText, b.distance, b.elevationGain, b.location, b.timeLimit, b.price, b.description,
                    b.prefixPath, b.pictureUrl, b.awardUrl, b.souvenirUrl, b.mapUrl, b.scheduleUrl, b.awardDetail, b.souvenirDetail,
                    b.dropOff, b.scheduleDetail, b.contactName, b.contactTel, b.contactOwner, b.rfidEventId, b.isFinished, b.isAutoFix, b.active,
                    CASE b.category WHEN 'Other' THEN b.otherText ELSE b.category END AS categoryText
                FROM
                    :database.campaign a
                INNER JOIN
                    :database.event b ON b.campaignId = a.id
                WHERE a.uuid = ? AND b.active = true
                    """
                + condition;

        try {
            if (paging != null && !Strings.isNullOrEmpty(paging.getSearchField())
                    && !Strings.isNullOrEmpty(paging.getSearchText())) {
                switch (paging.getSearchField()) {
                    case "category":
                        sql += "and b.category like ? ";
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
                    orderByClause = " ORDER BY b.id DESC";
                }

                if (paging.getStart() != null && paging.getLimit() != null) {
                    limitClause = " LIMIT " + paging.getStart() + ", " + paging.getLimit();
                }
            } else {
                orderByClause = " ORDER BY b.id DESC";
                limitClause = " LIMIT 15";
            }

            sql += orderByClause + limitClause;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            params.clear();
            for (Map<String, Object> row : results) {
                // String prefixPath = (String) row.get("prefixPath");
                // String thumbPictureUrl = (String) row.get("pictureUrl");
                // String thumbAwardUrl = (String) row.get("awardUrl");
                // String thumbSouvenirUrl = (String) row.get("souvenirUrl");
                // String thumbMapUrl = (String) row.get("mapUrl");
                // String thumbScheduleUrl = (String) row.get("scheduleUrl");
                // if (prefixPath != null && !prefixPath.isEmpty()) {
                // if (thumbPictureUrl != null && !thumbPictureUrl.isEmpty()) {
                // String publicUrlPicture = getPublicUrl(prefixPath, thumbPictureUrl);
                // row.put("thumbPictureUrl", publicUrlPicture);
                // }
                // if (thumbAwardUrl != null && !thumbAwardUrl.isEmpty()) {
                // String publicUrlAward = getPublicUrl(prefixPath, thumbAwardUrl);
                // row.put("thumbAwardUrl", publicUrlAward);
                // }
                // if (thumbSouvenirUrl != null && !thumbSouvenirUrl.isEmpty()) {
                // String publicUrlSouvenir = getPublicUrl(prefixPath, thumbSouvenirUrl);
                // row.put("thumbSouvenirUrl", publicUrlSouvenir);
                // }
                // if (thumbMapUrl != null && !thumbMapUrl.isEmpty()) {
                // String publicUrlMap = getPublicUrl(prefixPath, thumbMapUrl);
                // row.put("thumbMapUrl", publicUrlMap);
                // }
                // if (thumbScheduleUrl != null && !thumbScheduleUrl.isEmpty()) {
                // String publicUrlSchedule = getPublicUrl(prefixPath, thumbScheduleUrl);
                // row.put("thumbScheduleUrl", publicUrlSchedule);
                // }
                // }
                sql = "SELECT a.uuid, a.gender, a.minAge, a.maxAge, a.active FROM :database.ageGroup a INNER JOIN :database.event b ON b.id = a.eventId WHERE b.uuid = '"
                        + row.get("uuid") + "'";
                List<Map<String, Object>> dataAgeGroup = jdbcTemplate.queryForList(replaceConstants(sql),
                        params.toArray());
                List<AgeGroupData> ageGroups = mapper.convertValue(dataAgeGroup,
                        new TypeReference<List<AgeGroupData>>() {
                        });
                row.put("ageGroups", ageGroups);

                sql = """
                        SELECT c.name, c.type, a.distance, DATE_FORMAT(a.cutOffTime, '%d/%m/%Y %H:%i') AS cutOffTime, a.scanInOut
                        FROM :database.checkpointMapping a
                        INNER JOIN :database.event b ON b.id = a.eventId
                        INNER JOIN :database.station c ON c.id = a.stationId
                        WHERE b.uuid = '"""
                        + row.get("uuid") + "' ORDER BY c.orderNum ";
                List<Map<String, Object>> dataCheckpointMapping = jdbcTemplate.queryForList(replaceConstants(sql),
                        params.toArray());
                List<CheckpointMappingData> checkpointMappingItems = mapper.convertValue(dataCheckpointMapping,
                        new TypeReference<List<CheckpointMappingData>>() {
                        });
                row.put("checkpointMappingItems", checkpointMappingItems);
            }

            List<EventData> data = mapper.convertValue(results,
                    new TypeReference<List<EventData>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> getEventById(String id) throws SQLException {

        String sql = """
                SELECT
                        a.uuid, a.name, a.shortName
                FROM
                    :database.campaign a
                WHERE a.uuid = ?
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

    public Map<String, Object> getEventTypeById(String id) throws SQLException {

        String sql = """
                SELECT
                        a.uuid, a.name
                FROM
                    :database.event a
                WHERE a.uuid = ?
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

    public DatabaseData getCampaignByDate(String type, String user, String role, PagingData paging)
            throws SQLException {
        List<Object> params = new ArrayList<>();
        String condition = "";
        String conditionRole = "";

        if (type != null && !type.isEmpty()) {
            condition = " AND c.name LIKE ? ";
            params.add(type);
        }

        if ("organizer".equals(role)) {
            Integer userId = getIdByUuid("user", user);
            conditionRole = " AND c.organizerId = ? ";
            params.add(userId);
        } else if ("admin".equals(role)) {
            conditionRole = " AND 1 = 1 ";
        } else {
            conditionRole = " AND 1 = 0 ";
        }
        String sql = """
                SELECT uuid, hits, name, eventDate, prefixPath, pictureUrl, isDraft, statusDate FROM (
                        SELECT
                            c.uuid, COUNT(*) OVER() as hits, c.name, DATE_FORMAT(c.eventDate, '%Y-%m-%d') AS eventDate, c.prefixPath, c.pictureUrl, c.isDraft,
                            CASE WHEN c.eventDate < DATE(CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok')) THEN "passed" WHEN c.eventDate > DATE(CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok')) THEN "soon" ELSE NULL END AS statusDate
                        FROM
                            :database.campaign c
                        WHERE c.active = true AND c.status = 'active' AND c.isDraft IS NOT TRUE
                """
                + condition +
                """
                        UNION
                        SELECT
                            c.uuid, COUNT(*) OVER() as hits, c.name, DATE_FORMAT(c.eventDate, '%Y-%m-%d') AS eventDate, c.prefixPath, c.pictureUrl, c.isDraft,
                            CASE WHEN c.eventDate < DATE(CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok')) THEN "passed" WHEN c.eventDate > DATE(CONVERT_TZ(NOW(), @@session.time_zone, 'Asia/Bangkok')) THEN "soon" ELSE NULL END AS statusDate
                        FROM
                            :database.campaign c
                        WHERE c.active = true AND c.status = 'active' AND c.isDraft IS TRUE
                        """
                + conditionRole +
                " ) AS subquery ORDER BY eventDate DESC ";

        try {

            String limitClause = "";

            if (paging != null) {
                if (paging.getStart() != null && paging.getLimit() != null) {
                    limitClause = " LIMIT " + paging.getStart() + ", " + paging.getLimit();
                }
            } else {
                limitClause = " LIMIT 15";
            }

            sql += limitClause;
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            for (Map<String, Object> row : results) {
                String prefixPath = (String) row.get("prefixPath");
                String logoUrl = (String) row.get("pictureUrl");
                if (prefixPath != null && !prefixPath.isEmpty()) {
                    String publicUrl = getPublicUrl(prefixPath, logoUrl);
                    row.put("logoUrl", publicUrl);
                }
            }

            List<CampaignData> data = mapper.convertValue(results,
                    new TypeReference<List<CampaignData>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> getCampaignDetailById(String id) throws SQLException {
        Integer campaignId = getIdByUuid("campaign", id);

        String sql = """
                SELECT
                    c.uuid, c.name, c.organizerName, DATE_FORMAT(c.eventDate, '%Y-%m-%d') AS eventDate, c.location,
                    c.prefixPath, c.pictureUrl, c.description, c.email, c.website, c.facebook, c.contactName, c.contactTel,
                    c.isApproveCertificate
                FROM
                    :database.campaign c
                WHERE
                    c.uuid = ? AND c.active = true
                        """;
        List<Object> params = new ArrayList<>();
        params.add(id);

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            String thumbprefixPath = (String) result.get("prefixPath");
            String thumbPictureUrl = (String) result.get("pictureUrl");
            if (thumbprefixPath != "" && thumbprefixPath != null) {
                String publicUrl = getPublicUrl(thumbprefixPath, thumbPictureUrl);
                result.put("pictureUrl", publicUrl);
            }
            params.clear();
            params.add(campaignId);

            sql = """
                        SELECT
                            e.uuid, e.category, e.name, e.distance, COALESCE(e.isFinished, 0) AS isFinished,
                            CASE WHEN IFNULL(e.pictureUrl, '') = '' THEN c.prefixPath ELSE e.prefixPath END AS prefixPath,
                            IFNULL(e.pictureUrl, c.pictureUrl) AS pictureUrl, COUNT(p.id) AS participants
                        FROM
                            :database.event e
                        INNER JOIN
                            :database.campaign c ON c.id = e.campaignId
                        LEFT JOIN :database.participant p ON p.eventId = e.id
                        WHERE e.campaignId = ? AND e.active = true
                        GROUP BY e.id
                    """;
            List<Map<String, Object>> data = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            List<Map<String, Object>> eventTypeItems = mapper.convertValue(data,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            for (Map<String, Object> row : eventTypeItems) {
                String prefixPath = (String) row.get("prefixPath");
                String pictureUrl = (String) row.get("pictureUrl");
                if (prefixPath != null && !prefixPath.isEmpty()) {
                    String publicUrl = getPublicUrl(prefixPath, pictureUrl);
                    row.put("pictureUrl", publicUrl);
                }
            }
            result.put("eventData", eventTypeItems);

            sql = """
                    SELECT
                        CONCAT(IFNULL(ag.minAge, 'ต่ำกว่า'), ' - ', IFNULL(ag.maxAge, 'ขึ้นไป')) AS name,
                        e.name AS eventName
                    FROM
                        :database.ageGroup ag
                    INNER JOIN
                        :database.event e ON e.id = ag.eventId
                    WHERE e.campaignId = ? AND e.active = true
                    GROUP BY e.id, IFNULL(ag.minAge, 0), IFNULL(ag.maxAge, 200)
                    ORDER BY IFNULL(ag.minAge, 0)
                        """;
            List<Map<String, Object>> agData = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            List<Map<String, Object>> agItems = mapper.convertValue(agData,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            result.put("ageGroupData", agItems);

            return result;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> getEventDetailById(String id) throws SQLException {
        String sql = """
                SELECT
                    e.uuid, e.name, CASE WHEN IFNULL(e.pictureUrl, '') = '' THEN c.prefixPath ELSE e.prefixPath END AS prefixPath,
                    IFNULL(e.pictureUrl, c.pictureUrl) AS pictureUrl, e.category, c.organizerName, DATE_FORMAT(e.eventDate, '%d/%m/%Y %T') AS eventDate,
                    e.location, e.distance, e.price, e.description, e.awardUrl, e.souvenirUrl, e.mapUrl, e.scheduleUrl,
                    e.awardDetail, e.souvenirDetail, e.dropOff, e.scheduleDetail, e.contactName, e.contactTel
                FROM :database.event e
                INNER JOIN :database.campaign c ON c.id = e.campaignId AND c.active = true
                WHERE e.uuid = ? AND e.active = true
                    """;
        List<Object> params = new ArrayList<>();
        params.add(id);

        try {
            Map<String, Object> data = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            String prefixPath = (String) data.get("prefixPath");
            String pictureUrl = (String) data.get("pictureUrl");
            if (prefixPath != "" && prefixPath != null) {
                String publicUrl = getPublicUrl(prefixPath, pictureUrl);
                data.put("pictureUrl", publicUrl);
            }

            return data;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getCheckpointById(String id) throws SQLException {
        String sql = """
                SELECT
                    s.uuid, s.orderNum, s.name, s.type
                FROM :database.station s
                INNER JOIN :database.campaign e ON e.id = s.campaignId
                WHERE e.uuid = ?
                ORDER BY s.orderNum, s.id
                    """;
        List<Object> params = new ArrayList<>();
        params.add(id);

        try {
            List<Map<String, Object>> data = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            return data;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getCheckpointMappingById(String campaignUuid, String eventUuid)
            throws SQLException {
        String sql = """
                    SELECT
                        orderNum, stationUuid, uuid, name, type, distance, cutOffTime, scanInOut FROM (
                        SELECT
                            s.orderNum, s.id, s.uuid AS stationUuid, cp.uuid, s.name, s.type, cp.distance, cp.cutOffTime, cp.scanInOut
                        FROM :database.station s
                        INNER JOIN :database.checkpointMapping cp ON cp.stationId = s.id
                        INNER JOIN :database.event e ON e.id = cp.eventId
                        WHERE e.uuid = ?
                        UNION
                        SELECT
                            s.orderNum, s.id, s.uuid AS stationUuid, null AS uuid, s.name, s.type, null AS distance, null AS cutOffTime, null AS scanInOut
                        FROM :database.station s
                        INNER JOIN :database.campaign c ON c.id = s.campaignId
                        WHERE c.uuid = ?
                ) subquery
                GROUP BY stationUuid
                ORDER BY orderNum, id
                            """;
        List<Object> params = new ArrayList<>();
        params.add(eventUuid);
        params.add(campaignUuid);

        try {
            List<Map<String, Object>> data = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            return data;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getUserStationById(String id) throws SQLException {
        String sql = """
                SELECT s.name, u.username, u.password
                FROM :database.checkpointMapping c
                INNER JOIN :database.event e ON e.id = c.eventId
                INNER JOIN :database.station s ON s.id = c.stationId
                INNER JOIN :database.userStation u ON u.stationId = s.id
                WHERE e.uuid = ?
                ORDER BY s.id
                        """;
        List<Object> params = new ArrayList<>();
        params.add(id);

        try {
            List<Map<String, Object>> data = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            return data;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> getCampaignById(String id) throws SQLException {

        String sql = """
                SELECT c.name, c.prefixPath, c.chipBgUrl, c.chipBanner, c.chipPrimaryColor, c.chipSecondaryColor, c.chipModeColor
                        ,DATE_FORMAT(c.eventDate, '%W %d.%m.%Y') AS eventDate
                FROM :database.campaign c
                WHERE c.uuid = ?
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            String prefixPath = (String) result.get("prefixPath");
            String thumbPictureUrl = (String) result.get("chipBgUrl");
            if (prefixPath != "" && prefixPath != null) {
                String publicUrl = getPublicUrl(prefixPath, thumbPictureUrl);
                result.put("bgUrl", publicUrl);
            }
            return result;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region insert
    @Transactional(rollbackFor = Exception.class)
    public void createCampaign(CampaignData data) throws SQLException {
        Integer organizerId = getTransacionalIdByUuid("user", data.getOrganizerUuid());
        String sql = """
                INSERT INTO :database.campaign (organizerId, organizerName, name, shortName, description, eventDate, location, prefixPath, logoUrl, pictureUrl,
                                                email, website, facebook, contactName, contactTel, isDraft, active, status, isApproveCertificate, allowRFIDSync, rfid_token, raceid,
                                                chipBgUrl, chipBanner, chipPrimaryColor, chipSecondaryColor, chipModeColor, createdBy )
                                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        try {
            List<Object> params = new ArrayList<>();

            params.add(data.getRole().equals("admin") ? organizerId : httpSession.getAttribute("userId"));
            params.add(data.getOrganizerName());
            params.add(data.getName());
            params.add(data.getShortName());
            params.add(data.getDescription());
            params.add(data.getEventDate());
            params.add(data.getLocation());
            params.add(data.getPrefixPath());
            params.add(data.getLogoUrl());
            params.add(data.getPictureUrl());
            params.add(data.getEmail());
            params.add(data.getWebsite());
            params.add(data.getFacebook());
            params.add(data.getContactName());
            params.add(data.getContactTel());
            params.add(false);
            params.add(true);
            params.add("deactive");
            params.add(false);
            params.add(data.getAllowRFIDSync());
            params.add(data.getRfid_token());
            params.add(data.getRaceid());
            params.add(data.getChipBgUrl());
            params.add(data.getChipBanner());
            params.add(data.getChipPrimaryColor());
            params.add(data.getChipSecondaryColor());
            params.add(data.getChipModeColor());
            params.add(httpSession.getAttribute("userId"));

            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void createEvent(EventData data) throws SQLException {
        Integer campaignId = getTransacionalIdByUuid("campaign", data.getCampaignUuid());
        String sql = """
                INSERT INTO :database.event (campaignId, name, eventDate, category, otherText, distance, elevationGain, location,
                                            timeLimit, price, description, prefixPath, pictureUrl, awardUrl, souvenirUrl, mapUrl,
                                            scheduleUrl, awardDetail, souvenirDetail, dropOff, scheduleDetail, contactName,
                                            contactTel, contactOwner, rfidEventId, isFinished, active, createdBy )
                                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        try {
            List<Object> params = new ArrayList<>();

            params.add(campaignId);
            params.add(data.getName());
            params.add(data.getEventDate());
            params.add(data.getCategory());
            params.add(data.getOtherText());
            params.add(data.getDistance());
            params.add(data.getElevationGain());
            params.add(data.getLocation());
            params.add(data.getTimeLimit());
            params.add(data.getPrice());
            params.add(data.getDescription());
            params.add(data.getPrefixPath());
            params.add(data.getPictureUrl());
            params.add(data.getAwardUrl());
            params.add(data.getSouvenirUrl());
            params.add(data.getMapUrl());
            params.add(data.getScheduleUrl());
            params.add(data.getAwardDetail());
            params.add(data.getSouvenirDetail());
            params.add(data.getDropOff());
            params.add(data.getScheduleDetail());
            params.add(data.getContactName());
            params.add(data.getContactTel());
            params.add(data.getContactOwner());
            params.add(data.getRfidEventId());
            params.add(false);
            params.add(true);
            params.add(httpSession.getAttribute("userId"));

            Integer lastId = transactionalUpdate(sql, params);

            StringBuilder valuesLabel = new StringBuilder("");
            List<AgeGroupData> dataItems = Optional.ofNullable(data.getAgeGroups()).orElse(new ArrayList<>());
            params.clear();
            for (AgeGroupData item : dataItems) {
                params.add(lastId);
                params.add(item.getGender());
                params.add(item.getMinAge());
                params.add(item.getMaxAge());
                params.add(true);
                params.add(httpSession.getAttribute("userId"));
                valuesLabel.append("(?, ?, ?, ?, ?, ?),");
            }

            if (!Strings.isNullOrEmpty(valuesLabel.toString())) {
                valuesLabel.delete(valuesLabel.length() - 1, valuesLabel.length());
                sql = "INSERT INTO :database.ageGroup (eventId, gender, minAge, maxAge, active, createdBy) VALUES "
                        + valuesLabel;
                jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
            }

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region update
    @Transactional(rollbackFor = Exception.class)
    public void updateCampaign(CampaignData data) throws SQLException {
        Integer organizerId = getTransacionalIdByUuid("user", data.getOrganizerUuid());
        String sql = """
                UPDATE :database.campaign SET   organizerId = ?, organizerName = ?, name = ?, shortName = ?, description = ?, eventDate = ?,
                                                location = ?, prefixPath = ?, logoUrl = ?, pictureUrl = ?, email = ?, website = ?, facebook = ?,
                                                contactName = ?, contactTel = ?, active = ?, allowRFIDSync = ?, rfid_token = ?, raceid = ?,
                                                chipBgUrl = ?, chipBanner = ?, chipPrimaryColor = ?, chipSecondaryColor = ?, chipModeColor = ?,
                                                updatedBy = ? WHERE uuid = ?
                    """;

        try {
            List<Object> params = new ArrayList<>();

            params.add(organizerId);
            params.add(data.getOrganizerName());
            params.add(data.getName());
            params.add(data.getShortName());
            params.add(data.getDescription());
            params.add(data.getEventDate());
            params.add(data.getLocation());
            params.add(data.getPrefixPath());
            params.add(data.getLogoUrl());
            params.add(data.getPictureUrl());
            params.add(data.getEmail());
            params.add(data.getWebsite());
            params.add(data.getFacebook());
            params.add(data.getContactName());
            params.add(data.getContactTel());
            params.add(data.getActive());
            params.add(data.getAllowRFIDSync());
            params.add(data.getRfid_token());
            params.add(data.getRaceid());
            params.add(data.getChipBgUrl());
            params.add(data.getChipBanner());
            params.add(data.getChipPrimaryColor());
            params.add(data.getChipSecondaryColor());
            params.add(data.getChipModeColor());
            params.add(httpSession.getAttribute("userId"));
            params.add(data.getUuid());
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateEvent(EventData data) throws SQLException {
        Integer eventId = getTransacionalIdByUuid("event", data.getUuid());
        String sql = """
                UPDATE :database.event SET  name = ?, eventDate = ?, category = ?, otherText = ?, distance = ?, elevationGain = ?,
                                            location = ?, timeLimit = ?, price = ?, description = ?, prefixPath = ?, pictureUrl = ?,
                                            awardUrl = ?, souvenirUrl = ?, mapUrl = ?, scheduleUrl = ?, awardDetail = ?,
                                            souvenirDetail = ?, dropOff = ?, scheduleDetail = ?, contactName = ?, contactTel = ?,
                                            contactOwner = ?, rfidEventId = ?, active = ?, updatedBy = ? WHERE uuid = ?
                    """;

        try {
            List<Object> params = new ArrayList<>();

            params.add(data.getName());
            params.add(data.getEventDate());
            params.add(data.getCategory());
            params.add(data.getOtherText());
            params.add(data.getDistance());
            params.add(data.getElevationGain());
            params.add(data.getLocation());
            params.add(data.getTimeLimit());
            params.add(data.getPrice());
            params.add(data.getDescription());
            params.add(data.getPrefixPath());
            params.add(data.getPictureUrl());
            params.add(data.getAwardUrl());
            params.add(data.getSouvenirUrl());
            params.add(data.getMapUrl());
            params.add(data.getScheduleUrl());
            params.add(data.getAwardDetail());
            params.add(data.getSouvenirDetail());
            params.add(data.getDropOff());
            params.add(data.getScheduleDetail());
            params.add(data.getContactName());
            params.add(data.getContactTel());
            params.add(data.getContactOwner());
            params.add(data.getRfidEventId());
            params.add(data.getActive());
            params.add(httpSession.getAttribute("userId"));
            params.add(data.getUuid());
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());

            params.clear();
            List<String> itemUuids = new ArrayList<>();
            StringBuilder valuesLabel = new StringBuilder("");
            List<AgeGroupData> dataItems = Optional.ofNullable(data.getAgeGroups()).orElse(new ArrayList<>());
            if (!dataItems.isEmpty()) {
                for (AgeGroupData item : dataItems) {
                    params.add(getTransacionalIdByUuid("ageGroup", item.getUuid()));
                    params.add(eventId);
                    params.add(item.getGender());
                    params.add(item.getMinAge());
                    params.add(item.getMaxAge());
                    params.add(true);
                    params.add(httpSession.getAttribute("userId"));
                    valuesLabel.append("(?, ?, ?, ?, ?, ?, ?),");
                    if (item.getUuid() != null) {
                        itemUuids.add(item.getUuid());
                    }
                }
            }

            if (!itemUuids.isEmpty()) {
                sql = "DELETE FROM :database.ageGroup WHERE uuid NOT IN ("
                        + String.join(",",
                                itemUuids.stream().map(uuid -> "'" + uuid.toString() +
                                        "'").toArray(String[]::new))
                        + ") AND eventId = ?";
                List<Object> paramsItem = new ArrayList<>();
                paramsItem.add(eventId);
                jdbcTemplateTrans.update(replaceConstants(sql), paramsItem.toArray());
            } else {
                sql = "DELETE FROM :database.ageGroup WHERE eventId = ?";
                List<Object> paramsItem = new ArrayList<>();
                paramsItem.add(eventId);
                jdbcTemplateTrans.update(replaceConstants(sql), paramsItem.toArray());
            }

            if (!Strings.isNullOrEmpty(valuesLabel.toString())) {
                valuesLabel.delete(valuesLabel.length() - 1, valuesLabel.length());
                sql = "INSERT INTO :database.ageGroup (id, eventId, gender, minAge, maxAge, active, createdBy) VALUES "
                        + valuesLabel
                        + " AS new ON DUPLICATE KEY UPDATE gender = new.gender, minAge = new.minAge, maxAge = new.maxAge, updatedBy = new.createdBy ";
                jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
            }

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public void updateCampaignTemplate(CampaignData data) throws SQLException {

        String sql = """
                UPDATE :database.campaign SET prefixPath = ?, bgUrl = ?, certTextColor = ?, updatedBy = ?
                WHERE uuid = ?
                    """;
        List<Object> params = new ArrayList<>();
        params.add(data.getPrefixPath());
        params.add(data.getBgUrl());
        params.add(data.getCertTextColor());
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
    public void updateApproveCertificate(CampaignData data) throws SQLException {
        String sql = """
                UPDATE :database.campaign SET isApproveCertificate = ?, updatedBy = ?
                WHERE uuid = ?
                    """;
        List<Object> params = new ArrayList<>();
        params.add(data.getIsApproveCertificate());
        params.add(httpSession.getAttribute("userId"));
        params.add(data.getUuid());
        try {
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCampaignStatus(CampaignData data) throws SQLException {
        String sql = """
                UPDATE :database.campaign SET status = ?, updatedBy = ?
                WHERE uuid = ?
                    """;
        List<Object> params = new ArrayList<>();
        params.add(data.getStatus());
        params.add(httpSession.getAttribute("userId"));
        params.add(data.getUuid());
        try {
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCheckpoint(List<StationData> data) throws SQLException {

        String sql = "";

        try {
            List<Object> params = new ArrayList<>();

            if (!data.isEmpty()) {
                Integer campaignId = getTransacionalIdByUuid("campaign", data.get(0).getCampaignUuid());

                List<String> itemUuids = new ArrayList<>();
                StringBuilder valuesLabel = new StringBuilder("");
                if (data.get(0).getName() != null) {
                    for (StationData item : data) {
                        params.add(getTransacionalIdByUuid("station", item.getUuid()));
                        params.add(campaignId);
                        params.add(item.getOrderNum());
                        params.add(item.getName());
                        params.add(item.getType());
                        params.add(true);
                        params.add(httpSession.getAttribute("userId"));
                        valuesLabel.append("(?, ?, ?, ?, ?, ?, ?),");
                        if (item.getUuid() != null) {
                            itemUuids.add(item.getUuid());
                        }
                    }
                }

                if (!itemUuids.isEmpty()) {
                    sql = "DELETE FROM :database.station WHERE uuid NOT IN ("
                            + String.join(",",
                                    itemUuids.stream().map(uuid -> "'" + uuid.toString() +
                                            "'").toArray(String[]::new))
                            + ") AND campaignId = ?";
                    List<Object> paramsItem = new ArrayList<>();
                    paramsItem.add(campaignId);
                    jdbcTemplateTrans.update(replaceConstants(sql), paramsItem.toArray());
                } else {
                    sql = "DELETE FROM :database.station WHERE campaignId = ?";
                    List<Object> paramsItem = new ArrayList<>();
                    paramsItem.add(campaignId);
                    jdbcTemplateTrans.update(replaceConstants(sql), paramsItem.toArray());
                }

                if (!Strings.isNullOrEmpty(valuesLabel.toString())) {
                    valuesLabel.delete(valuesLabel.length() - 1, valuesLabel.length());
                    sql = "INSERT INTO :database.station (id, campaignId, orderNum, name, type, active, createdBy) VALUES "
                            + valuesLabel
                            + " AS new ON DUPLICATE KEY UPDATE orderNum = new.orderNum, name = new.name, type = new.type, updatedBy = new.createdBy ";
                    jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
                }

                params.clear();
                sql = """
                        SELECT u.id, s.uuid, s.id AS stationId
                        FROM :database.campaign c
                        LEFT JOIN :database.station s ON s.campaignId = c.id
                        LEFT JOIN :database.userStation u ON u.stationId = s.id
                        WHERE c.id = ?
                            """;
                params.add(campaignId);
                List<UserStationRequest> users = transactionalQuery(sql, params, UserStationRequest.class);

                if (!users.isEmpty()) {
                    params.clear();
                    List<String> itemIds = new ArrayList<>();
                    valuesLabel = new StringBuilder("");
                    for (UserStationRequest item : users) {
                        String uuid = item.getUuid();
                        if (uuid != null) {
                            params.add(item.getId());
                            params.add("ST" + uuid.substring(3, 7));
                            params.add(item.getUuid().substring(4, 8));
                            params.add(item.getStationId());
                            params.add(true);
                            params.add(httpSession.getAttribute("userId"));
                            valuesLabel.append("(?, ?, ?, ?, ?, ?),");
                            if (item.getUuid() != null) {
                                itemIds.add(item.getUuid());
                            }
                        }
                    }

                    if (!itemIds.isEmpty()) {
                        sql = "DELETE FROM :database.userStation WHERE uuid NOT IN ("
                                + String.join(",",
                                        itemIds.stream().map(uuid -> "'" + uuid.toString() +
                                                "'").toArray(String[]::new))
                                + ") AND stationId = ?";
                        List<Object> paramsItem = new ArrayList<>();
                        paramsItem.add(users.get(0).getStationId());
                        jdbcTemplateTrans.update(replaceConstants(sql), paramsItem.toArray());
                    } else {
                        sql = "DELETE FROM :database.userStation WHERE stationId = ?";
                        List<Object> paramsItem = new ArrayList<>();
                        paramsItem.add(users.get(0).getStationId());
                        jdbcTemplateTrans.update(replaceConstants(sql), paramsItem.toArray());
                    }

                    if (!Strings.isNullOrEmpty(valuesLabel.toString())) {
                        valuesLabel.delete(valuesLabel.length() - 1, valuesLabel.length());
                        sql = "INSERT INTO :database.userStation (id, username, password, stationId, active, createdBy) VALUES "
                                + valuesLabel
                                + " AS new ON DUPLICATE KEY UPDATE updatedBy = new.createdBy ";
                        jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
                    }
                }
            }

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCheckpointMapping(List<CheckpointMappingData> data) throws SQLException {

        try {
            if (!data.isEmpty()) {
                String sql = """
                        SELECT
                            id, eventDate
                        FROM :database.event
                        WHERE uuid = ? AND active = true
                            """;
                List<Object> params = new ArrayList<>();
                params.add(data.get(0).getEventUuid());

                Map<String, Object> eventData = jdbcTemplateTrans.queryForMap(replaceConstants(sql), params.toArray());
                Integer eventId = Integer.parseInt(eventData.get("id").toString());
                LocalDateTime eventDateTime = (LocalDateTime) eventData.get("eventDate");
                List<String> itemUuids = new ArrayList<>();
                StringBuilder valuesLabel = new StringBuilder("");
                params.clear();
                for (CheckpointMappingData item : data) {
                    params.add(getTransacionalIdByUuid("checkpointMapping", item.getUuid()));
                    params.add(eventId);
                    params.add(getTransacionalIdByUuid("station", item.getStationUuid()));
                    params.add(item.getDistance());
                    params.add(item.getCutOffTime());
                    params.add(item.getScanInOut());
                    params.add(true);
                    params.add(httpSession.getAttribute("userId"));
                    valuesLabel.append("(?, ?, ?, ?, ?, ?, ?, ?),");
                    if (item.getUuid() != null) {
                        itemUuids.add(item.getUuid());
                    }
                }

                if (!itemUuids.isEmpty()) {
                    sql = "DELETE FROM :database.checkpointMapping WHERE uuid NOT IN ("
                            + String.join(",",
                                    itemUuids.stream().map(uuid -> "'" + uuid.toString() +
                                            "'").toArray(String[]::new))
                            + ") AND eventId = ?";
                    List<Object> paramsItem = new ArrayList<>();
                    paramsItem.add(eventId);
                    jdbcTemplateTrans.update(replaceConstants(sql), paramsItem.toArray());
                } else {
                    sql = "DELETE FROM :database.checkpointMapping WHERE eventId = ?";
                    List<Object> paramsItem = new ArrayList<>();
                    paramsItem.add(eventId);
                    jdbcTemplateTrans.update(replaceConstants(sql), paramsItem.toArray());
                }

                if (!Strings.isNullOrEmpty(valuesLabel.toString())) {
                    valuesLabel.delete(valuesLabel.length() - 1, valuesLabel.length());
                    sql = "INSERT INTO :database.checkpointMapping (id, eventId, stationId, distance, cutOffTime, scanInOut, active, createdBy) VALUES "
                            + valuesLabel
                            + " AS new ON DUPLICATE KEY UPDATE distance = new.distance, cutOffTime = new.cutOffTime, scanInOut = new.scanInOut, updatedBy = new.createdBy ";
                    jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
                }

                // Correct status after checkpoint changed
                ZoneId bangkokZone = ZoneId.of("Asia/Bangkok");
                ZoneId utcZone = ZoneOffset.UTC;

                ZonedDateTime bangkokZoned = eventDateTime.atZone(bangkokZone);
                LocalDateTime utcDateTime = bangkokZoned.withZoneSameInstant(utcZone).toLocalDateTime();
                if (!utcDateTime.isAfter(LocalDateTime.now())) {
                    for (CheckpointMappingData item : data) {
                        Integer checkpointMappingId = getTransacionalIdByUuid("checkpointMapping", item.getUuid());
                        runnerService.updateStatusParticipant(checkpointMappingId);
                    }
                    runnerService.reverseStatusParticipant(eventId);
                } else {
                    runnerService.revertToStarted(eventId);
                }

                // Reinitialize scheduler
                checkpointService.initializeDailyScheduler();
            }

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCampaignActive(CampaignData data) throws SQLException {
        String sql = """
                UPDATE :database.campaign SET active = ?, updatedBy = ?, deletedTime = CURRENT_TIMESTAMP
                WHERE uuid = ?
                    """;
        List<Object> params = new ArrayList<>();
        params.add(false);
        params.add(httpSession.getAttribute("userId"));
        params.add(data.getUuid());
        try {
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateEventActive(EventData data) throws SQLException {
        String sql = """
                UPDATE :database.event SET active = ?, updatedBy = ?, deletedTime = CURRENT_TIMESTAMP
                WHERE uuid = ?
                    """;
        List<Object> params = new ArrayList<>();
        params.add(false);
        params.add(httpSession.getAttribute("userId"));
        params.add(data.getUuid());
        try {
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateIsAutoFix(EventData data) throws SQLException {

        String sql = "";
        List<Object> params = new ArrayList<>();
        try {
            sql = """
                    UPDATE :database.event SET isAutoFix = ?, updatedBy = ?
                    WHERE uuid = ?
                        """;
            params.add(data.getIsAutoFix());
            params.add(httpSession.getAttribute("userId"));
            params.add(data.getUuid());
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());

            // autofix
            if (data.getFinishTime() != null && data.getIsAutoFix()) {
                sql = """
                        SELECT * FROM :database.event WHERE uuid = ?
                            """;
                params.clear();
                params.add(data.getUuid());
                EventData event = jdbcTemplateTrans.queryForObject(replaceConstants(sql),
                        new BeanPropertyRowMapper<>(EventData.class), params.toArray());

                sql = """
                            WITH RankedStations AS (
                                SELECT
                                    campaignId,
                                    name,
                                    orderNum,
                                    ROW_NUMBER() OVER (PARTITION BY campaignId ORDER BY orderNum ASC) AS rn_asc,
                                    ROW_NUMBER() OVER (PARTITION BY campaignId ORDER BY orderNum DESC) AS rn_desc
                                FROM :database.station
                            )
                            SELECT
                                campaignId,
                                MAX(CASE WHEN rn_asc = 1 THEN name END) AS firstStation,
                                MAX(CASE WHEN rn_asc = 2 THEN name END) AS nextStation,
                                MAX(CASE WHEN rn_desc = 1 THEN name END) AS lastStation
                            FROM RankedStations
                            WHERE campaignId = ?
                            GROUP BY campaignId
                        """;
                params.clear();
                params.add(event.getCampaignId());
                Map<String, Object> station = jdbcTemplateTrans.queryForMap(replaceConstants(sql), params.toArray());

                String firstStation = (String) station.get("firstStation");
                String nextStation = (String) station.get("nextStation");
                String lastStation = (String) station.get("lastStation");

                String pivotedSql = """
                            WITH checkpoint_times AS (
                                SELECT
                                    a.id AS timeRecordId,
                                    a.participantId,
                                    s.name AS checkpointName,
                                    a.raceTimingIn
                                FROM :database.timeRecord a
                                INNER JOIN :database.checkpointMapping c ON a.checkpointMappingId = c.id
                                INNER JOIN :database.station s ON c.stationId = s.id
                                INNER JOIN :database.event e ON c.eventId = e.id
                                WHERE s.name IN (:firstStation, :nextStation, :lastStation) AND e.uuid = :eventId
                            ),
                            pivoted_times AS (
                                SELECT
                                    participantId,
                                    MAX(CASE WHEN checkpointName = :firstStation THEN raceTimingIn END) AS startTime,
                                    MAX(CASE WHEN checkpointName = :nextStation THEN raceTimingIn END) AS cp1Time,
                                    MAX(CASE WHEN checkpointName = :lastStation THEN raceTimingIn END) AS finishTime
                                FROM checkpoint_times
                                GROUP BY participantId
                            )
                        """;
                sql = pivotedSql + """
                        SELECT
                                a.id
                        FROM
                            :database.timeRecord a
                            JOIN :database.checkpointMapping b ON a.checkpointMappingId = b.id
                            JOIN :database.station c ON b.stationId = c.id
                            JOIN :database.participant d ON a.participantId = d.id
                        WHERE
                            participantId in (
                                SELECT
                                    participantId
                                FROM
                                    pivoted_times
                                WHERE
                                    finishTime > cp1Time
                                    AND startTime > cp1Time
                                    AND cp1Time is not null
                                ORDER BY
                                    participantId
                            )
                            AND c.name = :firstStation
                            """;
                Map<String, Object> namedParams = new HashMap<>();
                namedParams.put("eventId", data.getUuid());
                namedParams.put("firstStation", firstStation);
                namedParams.put("nextStation", nextStation);
                namedParams.put("lastStation", lastStation);
                List<Integer> dataToFixCase1 = namedJdbcTemplate.queryForList(replaceConstants(sql), namedParams,
                        Integer.class);

                if (!dataToFixCase1.isEmpty()) {
                    sql = """
                            UPDATE :database.timeRecord SET revertRaceTimingIn = raceTimingIn, revertRaceTimingOut = raceTimingOut WHERE id in (:timeRecordIds)
                            """;
                    namedParams.clear();
                    namedParams.put("timeRecordIds", dataToFixCase1);
                    namedJdbcTemplate.update(replaceConstants(sql), namedParams);
                    sql = """
                                UPDATE :database.timeRecord SET raceTimingIn = :startTime, raceTimingOut = :startTime WHERE id in (:timeRecordIds)
                            """;
                    namedParams.clear();
                    namedParams.put("startTime", event.getEventDate());
                    namedParams.put("timeRecordIds", dataToFixCase1);
                    namedJdbcTemplate.update(replaceConstants(sql), namedParams);
                }

                sql = pivotedSql + """
                        SELECT
                                a.id
                        FROM
                            :database.timeRecord a
                            JOIN :database.checkpointMapping b ON a.checkpointMappingId = b.id
                            JOIN :database.station c ON b.stationId = c.id
                            JOIN :database.participant d ON a.participantId = d.id
                        WHERE
                            participantId in (
                                SELECT
                                    participantId
                                FROM
                                    pivoted_times
                                WHERE
                                    finishTime < cp1Time
                                    AND startTime < cp1Time
                                    AND cp1Time is not null
                                ORDER BY
                                    participantId
                            )
                            AND c.name = :lastStation
                            """;
                namedParams.clear();
                namedParams.put("eventId", data.getUuid());
                namedParams.put("firstStation", firstStation);
                namedParams.put("nextStation", nextStation);
                namedParams.put("lastStation", lastStation);
                List<Integer> dataToFixCase2 = namedJdbcTemplate.queryForList(replaceConstants(sql), namedParams,
                        Integer.class);

                if (!dataToFixCase2.isEmpty()) {
                    sql = """
                                UPDATE :database.timeRecord SET revertRaceTimingIn = raceTimingIn, revertRaceTimingOut = raceTimingOut WHERE id in (:timeRecordIds)
                            """;
                    namedParams.clear();
                    namedParams.put("timeRecordIds", dataToFixCase2);
                    namedJdbcTemplate.update(replaceConstants(sql), namedParams);
                    sql = """
                                UPDATE :database.timeRecord SET raceTimingIn = :finishTime, raceTimingOut = :finishTime WHERE id in (:timeRecordIds)
                            """;
                    namedParams.clear();
                    namedParams.put("finishTime", data.getFinishTime());
                    namedParams.put("timeRecordIds", dataToFixCase2);
                    namedJdbcTemplate.update(replaceConstants(sql), namedParams);
                }

                sql = pivotedSql + """
                        SELECT
                                a.id
                        FROM
                            :database.timeRecord a
                            JOIN :database.checkpointMapping b ON a.checkpointMappingId = b.id
                            JOIN :database.station c ON b.stationId = c.id
                            JOIN :database.participant d ON a.participantId = d.id
                        WHERE
                            participantId in (
                                SELECT
                                    participantId
                                FROM
                                    pivoted_times
                                WHERE
                                    TIMESTAMPDIFF(SECOND, startTime, finishTime) <= 120
                                    AND cp1Time is null
                                ORDER BY
                                    participantId
                            )
                            AND c.name = :lastStation
                            """;
                namedParams.clear();
                namedParams.put("eventId", data.getUuid());
                namedParams.put("firstStation", firstStation);
                namedParams.put("nextStation", nextStation);
                namedParams.put("lastStation", lastStation);
                List<Integer> dataToFixCase3 = namedJdbcTemplate.queryForList(replaceConstants(sql), namedParams,
                        Integer.class);

                if (!dataToFixCase3.isEmpty()) {
                    sql = """
                                UPDATE :database.timeRecord SET revertRaceTimingIn = raceTimingIn, revertRaceTimingOut = raceTimingOut WHERE id in (:timeRecordIds)
                            """;
                    namedParams.clear();
                    namedParams.put("timeRecordIds", dataToFixCase3);
                    namedJdbcTemplate.update(replaceConstants(sql), namedParams);

                    sql = """
                                UPDATE :database.timeRecord SET raceTimingIn = :finishTime, raceTimingOut = :finishTime WHERE id in (:timeRecordIds)
                            """;
                    namedParams.clear();
                    namedParams.put("finishTime", data.getFinishTime());
                    namedParams.put("timeRecordIds", dataToFixCase3);
                    namedJdbcTemplate.update(replaceConstants(sql), namedParams);
                }
            } else if (!data.getIsAutoFix()) {
                List<Object> paramsRevert = new ArrayList<>();
                sql = """
                        WITH TempTimeRecordIds AS (
                        SELECT cp.id
                        FROM :database.checkpointMapping cp
                        INNER JOIN :database.event e ON e.id = cp.eventId
                        WHERE e.uuid = ?
                        )
                        UPDATE :database.timeRecord SET raceTimingIn = revertRaceTimingIn, raceTimingOut = revertRaceTimingOut, updatedBy = ?
                        WHERE revertRaceTimingIn IS NOT NULL AND revertRaceTimingOut IS NOT NULL AND checkpointMappingId IN (SELECT id FROM TempTimeRecordIds)
                        """;
                paramsRevert.add(data.getUuid());
                paramsRevert.add(httpSession.getAttribute("userId"));
                jdbcTemplateTrans.update(replaceConstants(sql), paramsRevert.toArray());
            }

        } catch (EmptyResultDataAccessException e) {
            System.out.println("No event found for UUID: " + data.getUuid());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateIsFinished(EventData data) throws SQLException {
        boolean originalSyncSummaryResultEnabled = syncSummaryResultEnabled;

        syncSummaryResultEnabled = false;
        logger.info("Temporarily disabled SummaryResult synchronization");

        String sql = "";
        List<Object> params = new ArrayList<>();
        try {
            sql = """
                    UPDATE :database.event SET isFinished = ?, updatedBy = ?
                    WHERE uuid = ?
                        """;
            params.add(data.getIsFinished());
            params.add(httpSession.getAttribute("userId"));
            params.add(data.getUuid());
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());

            String deleteSql = "DELETE FROM :database.v_summaryResult WHERE eventUuid = ?";
            params.clear();
            params.add(data.getUuid());
            jdbcTemplateTrans.update(replaceConstants(deleteSql), params.toArray());

            String insertSql = """
                    INSERT INTO :database.v_summaryResult (ageGroup, ageGroupBygenderPos, ageGroupPos, bibNo, chipTime, distance, eventTypeName, eventUuid, gender, genderPos, gunTime, id, lastCP, name, nationality, pictureUrl, pos, status, uuid, eventName, isFinished)
                            SELECT ageGroup, ageGroupBygenderPos, ageGroupPos, bibNo, chipTime, distance, eventTypeName, eventUuid, gender, genderPos, gunTime, id, lastCP, name, nationality, pictureUrl, pos, status, uuid, eventName, isFinished
                            FROM :database.allParticipantByEvent WHERE eventUuid = ?
                            """;
            params.clear();
            params.add(data.getUuid());
            jdbcTemplateTrans.update(replaceConstants(insertSql), params.toArray());

            if (data.getIsFinished()) {
                String insertFinalSql = """
                        INSERT INTO :database.v_final_live (id, uuid, eventId, eventUuid, bibNo, name, gender, nationality, lastCP, lastCPTime, distance, gunTime, chipTime, status, ageGroup, pos, ageGroupBygenderPos, ageGroupPos, genderPos, isFinished)
                                SELECT id, uuid, eventId, eventUuid, bibNo, name, gender, nationality, lastCP, lastCPTime, distance, gunTime, chipTime, status, ageGroup, pos, ageGroupBygenderPos, ageGroupPos, genderPos, isFinished
                                FROM :database.allParticipantLive WHERE eventUuid = ?
                                """;
                jdbcTemplateTrans.update(replaceConstants(insertFinalSql), data.getUuid());

                String insertFinalCpSql = """
                        INSERT INTO :database.v_final_cp_live (eventId, eventUuid, participantId, stationName, stationTime, isFinished)
                                SELECT eventId, eventUuid, participantId, stationName, stationTime, isFinished
                                FROM :database.allCheckpointLive WHERE eventUuid = ?
                                """;
                jdbcTemplateTrans.update(replaceConstants(insertFinalCpSql), data.getUuid());
            } else {
                String deleteFinalSql = "DELETE FROM :database.v_final_live WHERE eventUuid = ?";
                jdbcTemplateTrans.update(replaceConstants(deleteFinalSql), data.getUuid());
                String deleteFinalCpSql = "DELETE FROM :database.v_final_cp_live WHERE eventUuid = ?";
                jdbcTemplateTrans.update(replaceConstants(deleteFinalCpSql), data.getUuid());
            }

        } catch (EmptyResultDataAccessException e) {
            System.out.println("No event found for UUID: " + data.getUuid());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        } finally {
            syncSummaryResultEnabled = originalSyncSummaryResultEnabled;
            logger.info("Re-enabled SummaryResult synchronization (restored to: {})", originalSyncSummaryResultEnabled);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateIsDraft(CampaignData data) throws SQLException {
        String sql = "";
        try {
            if (Boolean.FALSE.equals(data.getIsDraft())) {
                sql = """
                        WITH EventIds AS (
                        SELECT DISTINCT e.id
                        FROM :database.event e
                        INNER JOIN :database.campaign c ON c.id = e.campaignId
                        WHERE c.uuid = ?
                        )
                        UPDATE :database.participant SET status = NULL, isStarted = FALSE, updatedBy = ? WHERE status IS NOT NULL AND eventId IN (SELECT id FROM EventIds)
                        """;

                List<Object> paramsEvent = new ArrayList<>();
                paramsEvent.add(data.getUuid());
                paramsEvent.add(httpSession.getAttribute("userId"));
                jdbcTemplateTrans.update(replaceConstants(sql), paramsEvent.toArray());

                sql = """
                        WITH ParticipantIds AS (
                        SELECT DISTINCT t.participantId
                        FROM :database.timeRecord t
                        INNER JOIN :database.checkpointMapping cp ON cp.id = t.checkpointMappingId
                        INNER JOIN :database.station s ON s.id = cp.stationId
                        INNER JOIN :database.campaign c ON c.id = s.campaignId
                        WHERE c.uuid = ?
                        )
                        DELETE FROM :database.participant WHERE id IN (SELECT participantId FROM ParticipantIds)
                        """;

                List<Object> paramsParticipant = new ArrayList<>();
                paramsParticipant.add(data.getUuid());
                jdbcTemplateTrans.update(replaceConstants(sql), paramsParticipant.toArray());

                sql = """
                        WITH TempTimeRecordIds AS (
                        SELECT cp.id
                        FROM :database.checkpointMapping cp
                        INNER JOIN :database.station s ON s.id = cp.stationId
                        INNER JOIN :database.campaign c ON c.id = s.campaignId
                        WHERE c.uuid = ?
                        )
                        DELETE FROM :database.timeRecord WHERE checkpointMappingId IN (SELECT id FROM TempTimeRecordIds)
                        """;

                List<Object> paramsTempTimeRecord = new ArrayList<>();
                paramsTempTimeRecord.add(data.getUuid());
                jdbcTemplateTrans.update(replaceConstants(sql), paramsTempTimeRecord.toArray());
            }

            List<Object> params = new ArrayList<>();
            sql = """
                    UPDATE :database.campaign SET isDraft = ?, updatedBy = ?
                    WHERE uuid = ?
                        """;
            params.add(data.getIsDraft());
            params.add(httpSession.getAttribute("userId"));
            params.add(data.getUuid());
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region delete
    @Transactional(rollbackFor = Exception.class)
    public void deleteCampaign(String id) throws SQLException {

        try {
            String sql = "DELETE FROM :database.campaign WHERE uuid = ? ";
            List<Object> params = new ArrayList<>();
            params.add(id);
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteEvent(String id) throws SQLException {

        try {
            String sql = "DELETE FROM :database.event WHERE uuid = ? ";
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
