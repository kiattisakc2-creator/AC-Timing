package racetimingms.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.ValidationException;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;
import racetimingms.model.DatabaseData;
import racetimingms.model.MenuInfoData;
import racetimingms.model.PagingData;
import racetimingms.model.UserData;
import racetimingms.model.UserTokenData;
import racetimingms.model.request.UserStationRequest;

@Slf4j
@Component
public class UserService extends DatabaseService {

    // #region get
    public DatabaseData getAllUser(PagingData paging) throws SQLException {

        String sql = """
                SELECT
                        a.uuid, COUNT(*) OVER() as hits, CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) AS username, a.email,
                        a.role, a.prefixPath, a.pictureUrl, a.active, case a.active when 1 then 'ใช้งาน' else 'ไม่ใช้งาน' END AS activeText,
                        IFNULL(COUNT(c.organizerId), 0) AS totalEvent
                FROM
                    :database.user a
                LEFT JOIN :database.campaign c ON c.organizerId = a.id AND c.active = true
                WHERE
                    a.role IN ('admin', 'organizer')
                        """;
        List<Object> params = new ArrayList<>();

        try {
            if (paging != null && !Strings.isNullOrEmpty(paging.getSearchField())
                    && !Strings.isNullOrEmpty(paging.getSearchText())) {
                switch (paging.getSearchField()) {
                    case "username":
                        sql += "and CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "role":
                        sql += "and a.role like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "email":
                        sql += "and a.email like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "activeText":
                        sql += "and case a.active when 1 then 'ใช้งาน' else 'ไม่ใช้งาน' end like ? ";
                        params.add(paging.getSearchText());
                        break;
                    default:
                        break;
                }
            }
            String groupByClause = "GROUP BY a.id ";
            String orderByClause = "";
            String limitClause = "";

            if (paging != null) {
                if (!Strings.isNullOrEmpty(paging.getField()) && !Strings.isNullOrEmpty(paging.getSort())) {
                    orderByClause = " ORDER BY " + paging.getField() + " "
                            + paging.getSort();
                } else {
                    orderByClause = " ORDER BY a.createdTime DESC";
                }

                if (paging.getStart() != null && paging.getLimit() != null) {
                    limitClause = " LIMIT " + paging.getStart() + ", " + paging.getLimit();
                }
            } else {
                orderByClause = " ORDER BY a.createdTime DESC";
                limitClause = " LIMIT 15";
            }

            sql += groupByClause + orderByClause + limitClause;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            for (Map<String, Object> row : results) {
                String prefixPath = (String) row.get("prefixPath");
                String thumbPictureUrl = (String) row.get("pictureUrl");
                if (prefixPath != null && !prefixPath.isEmpty()) {
                    String publicUrl = getPublicUrl(prefixPath, thumbPictureUrl);
                    row.put("thumbPictureUrl", publicUrl);
                }
            }
            List<UserData> data = mapper.convertValue(results,
                    new TypeReference<List<UserData>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public DatabaseData getAllUserRole(PagingData paging) throws SQLException {

        String sql = """
                SELECT
                    a.uuid, COUNT(*) OVER() as hits, CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) AS username, a.email,
                    a.role, a.roleId, a.prefixPath, a.pictureUrl, a.active, case a.active when 1 then 'ใช้งาน' else 'ไม่ใช้งาน' END AS activeText,
                    b.name AS roleText
                FROM
                    :database.user a
                LEFT JOIN
                    :database.role b ON b.id = a.roleId
                WHERE
                    a.role IN ('admin')
                        """;
        List<Object> params = new ArrayList<>();

        try {
            if (paging != null && !Strings.isNullOrEmpty(paging.getSearchField())
                    && !Strings.isNullOrEmpty(paging.getSearchText())) {
                switch (paging.getSearchField()) {
                    case "username":
                        sql += "and CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "role":
                        sql += "and a.role like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "roleText":
                        sql += "and b.name like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "email":
                        sql += "and a.email like ? ";
                        params.add(paging.getSearchText());
                        break;
                    case "activeText":
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
                limitClause = " LIMIT 15";
            }

            sql += orderByClause + limitClause;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            for (Map<String, Object> row : results) {
                String prefixPath = (String) row.get("prefixPath");
                String thumbPictureUrl = (String) row.get("pictureUrl");
                if (prefixPath != null && !prefixPath.isEmpty()) {
                    String publicUrl = getPublicUrl(prefixPath, thumbPictureUrl);
                    row.put("thumbPictureUrl", publicUrl);
                }
            }
            List<UserData> data = mapper.convertValue(results,
                    new TypeReference<List<UserData>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> getUserById(String id) throws SQLException {

        String sql = """
                SELECT
                        a.uuid, a.role, a.email
                FROM
                    :database.user a
                WHERE a.uuid = ?
                """;
        List<Object> params = new ArrayList<>();
        params.add(id);

        try {
            Map<String, Object> data = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            return data;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> userStation(UserStationRequest body) throws SQLException {
        String sql = """
                SELECT a.uuid, s.name AS station, s.uuid AS stationUuid, MAX(cp.scanInOut) AS scanInOut
                FROM :database.userStation a
                INNER JOIN :database.checkpointMapping cp ON cp.stationId = a.stationId
                INNER JOIN :database.station s ON s.id = cp.stationId
                INNER JOIN :database.event e ON e.id = cp.eventId AND e.active = true
                INNER JOIN :database.campaign c ON c.id = e.campaignId AND c.active = true
                WHERE a.username = ? AND a.password = ? AND c.uuid = ?
                GROUP BY a.id, s.id
                        """;
        List<Object> params = new ArrayList<>();
        params.add(body.getUsername());
        params.add(body.getPassword());
        params.add(body.getCampaignUuid());

        try {
            Map<String, Object> data = new HashMap<>();

            Map<String, Object> result = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            data.put("data", result);

            params.clear();
            sql = """
                    SELECT a.uuid AS id, a.role, a.title, e.uuid AS main, a.name, a.desc, a.icon, a.path, a.isDisabled, a.isDisplay
                    FROM :database.menu a
                    LEFT JOIN :database.menu e ON e.id = a.main
                    WHERE a.role = 'staff'
                    ORDER BY a.id
                        """;
            List<MenuInfoData> menuData = jdbcTemplate.query(replaceConstants(sql),
                    new BeanPropertyRowMapper<>(MenuInfoData.class), params.toArray());

            data.put("menuData", menuData);
            return data;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> findUserPasswordById(String userId) throws SQLException {
        String sql = """
                    SELECT a.password
                FROM
                    :database.user a
                WHERE
                    a.uuid = ?
                        """;
        List<Object> params = new ArrayList<>();
        params.add(userId);
        try {
            Map<String, Object> data = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            return data;
        } catch (DataAccessException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> checkUserEmail(String email) throws SQLException {
        String sql = """
                SELECT
                    a.id, a.uuid, CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) AS username
                FROM
                    :database.user a
                WHERE
                    a.email = ?
                        """;
        try {
            Map<String, Object> data = jdbcTemplate.queryForMap(replaceConstants(sql), email);
            return data;
        } catch (EmptyResultDataAccessException e) {
            return new HashMap<>();
        } catch (DataAccessException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Boolean checkUserToken(String id) throws SQLException {
        String sql = """
                SELECT
                    COUNT(*)
                FROM
                    :database.token a
                WHERE
                    a.uuid = ? AND a.active = true AND a.createdTime >= DATE_SUB(NOW(), INTERVAL 1 DAY)
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
    // #endregion

    // #region insert
    public void createUser(UserData data) throws SQLException {

        String sql = """
                INSERT INTO :database.user ( firstName, lastName, password, role, email, active, createdBy ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
        try {
            List<Object> params = new ArrayList<>();
            params.add(data.getFirstName());
            params.add(data.getLastName());
            params.add(data.getPassword());
            params.add(data.getRole());
            params.add(data.getEmail());
            params.add(true);
            params.add(httpSession.getAttribute("userId"));

            Integer lastId = jdbcTemplate.update(replaceConstants(sql), params.toArray());
            if (lastId < 1) {
                throw new ValidationException(NO_ROW_ERR);
            }
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public void createUserByAdmin(UserData data) throws SQLException {

        String sql = """
                INSERT INTO :database.user ( password, role, email, active, createdBy ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
        try {
            List<Object> params = new ArrayList<>();
            params.add(data.getPassword());
            params.add(data.getRole());
            params.add(data.getEmail());
            params.add(true);
            params.add(httpSession.getAttribute("userId"));

            Integer lastId = jdbcTemplate.update(replaceConstants(sql), params.toArray());
            if (lastId < 1) {
                throw new ValidationException(NO_ROW_ERR);
            }
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer createUserToken(Integer id) throws SQLException {

        try {
            List<Object> params = new ArrayList<>();
            String sql = """
                    UPDATE :database.token SET active = ?, updatedBy = ?
                    WHERE userId = ? AND active = true AND createdTime >= DATE_SUB(NOW(), INTERVAL 1 DAY)
                        """;
            params.add(false);
            params.add(httpSession.getAttribute("userId"));
            params.add(id);
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());

            params.clear();
            sql = """
                    INSERT INTO :database.token ( userId, active, createdBy ) VALUES (?, ?, ?)
                        """;
            params.add(id);
            params.add(true);
            params.add(httpSession.getAttribute("userId"));

            Integer lastId = transactionalUpdate(sql, params);
            if (lastId < 1) {
                throw new ValidationException(NO_ROW_ERR);
            }
            return lastId;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region update
    public void updateUser(UserData data) throws SQLException {

        String sql = """
                UPDATE :database.user SET password = ?, updatedBy = ? WHERE uuid = ?
                    """;
        List<Object> params = new ArrayList<>();
        params.add(data.getNpw());
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
    public void updateUserToken(UserTokenData data) throws SQLException {
        try {
            String sql = "SELECT userId FROM :database.token WHERE uuid = ? ";
            List<Object> params = new ArrayList<>();
            params.add(data.getUuid());
            List<UserTokenData> result = transactionalQuery(sql, params, UserTokenData.class);

            if (!result.isEmpty()) {
                params.clear();
                sql = """
                        UPDATE :database.user SET password = ?, updatedBy = ? WHERE id = ?
                            """;
                params.add(data.getNpw());
                params.add(httpSession.getAttribute("userId"));
                params.add(result.get(0).getUserId());
                jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
            }

            params.clear();
            sql = """
                    UPDATE :database.token SET active = ?, updatedBy = ? WHERE uuid = ?
                        """;
            params.add(false);
            params.add(httpSession.getAttribute("userId"));
            params.add(data.getUuid());
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public void updateUserRole(UserData data) throws SQLException {

        String sql = """
                UPDATE :database.user SET roleId = ?, updatedBy = ? WHERE uuid = ?
                    """;
        List<Object> params = new ArrayList<>();
        params.add(data.getRoleId());
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
    // #endregion

    // #region delete
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(String id) throws SQLException {

        try {
            String sql = "DELETE FROM :database.user WHERE uuid = ? ";
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