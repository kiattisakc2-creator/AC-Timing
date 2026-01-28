package racetimingms.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.validation.ValidationException;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;
import racetimingms.model.DatabaseData;
import racetimingms.model.PagingData;
import racetimingms.model.PermissionData;
import racetimingms.model.RoleData;

@Slf4j
@Component
public class RoleService extends DatabaseService {
    // #region get
    public DatabaseData getAllRole(PagingData paging) throws SQLException {

        String sql = """
                SELECT
                        a.id, a.uuid, COUNT(*) OVER() as hits, a.name, a.active, case a.active when 1 then 'ใช้งาน' else 'ไม่ใช้งาน' END AS activeText
                FROM
                    :database.role a
                WHERE
                    1 = 1
                        """;
        List<Object> params = new ArrayList<>();

        try {
            if (paging != null && !Strings.isNullOrEmpty(paging.getSearchField())
                    && !Strings.isNullOrEmpty(paging.getSearchText())) {
                switch (paging.getSearchField()) {
                    case "name":
                        sql += "and a.name like ? ";
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
                params.clear();
                sql = """
                        SELECT name, menuId, uuid, roleId, canAccess FROM (
                            SELECT a.name, a.id AS menuId, b.uuid, b.roleId, b.canAccess
                            FROM :database.menu a
                            INNER JOIN :database.permission b ON b.menuId = a.id
                            WHERE a.role = 'admin' AND a.active = true AND b.roleId = ?
                            UNION
                            SELECT a.name, a.id AS menuId, null AS uuid, null AS roleId, 0 AS canAccess
                            FROM :database.menu a
                            WHERE a.role = 'admin' AND a.active = true
                            ) subquery
                        GROUP BY menuId
                                """;
                params.add(row.get("id"));
                List<Map<String, Object>> dataAgeGroup = jdbcTemplate.queryForList(replaceConstants(sql),
                        params.toArray());
                List<PermissionData> menuPermission = mapper.convertValue(dataAgeGroup,
                        new TypeReference<List<PermissionData>>() {
                        });
                row.put("menuPermission", menuPermission);
            }

            List<RoleData> data = mapper.convertValue(results,
                    new TypeReference<List<RoleData>>() {
                    });
            Long hits = getHits(data);
            return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Map<String, Object> getRoleById(String id) throws SQLException {

        String sql = """
                SELECT
                        a.uuid, a.name, a.active
                FROM
                    :database.role a
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
    // #endregion

    // #region insert
    @Transactional(rollbackFor = Exception.class)
    public void createRole(RoleData data) throws SQLException {

        try {
            String sql = """
                    INSERT INTO :database.role ( name, active, createdBy ) VALUES (?, ?, ?)
                        """;
            List<Object> params = new ArrayList<>();
            params.add(data.getName());
            params.add(true);
            params.add(httpSession.getAttribute("userId"));
            Integer lastId = transactionalUpdate(sql, params);

            params.clear();
            sql = """
                    SELECT id FROM :database.menu WHERE role = 'admin' AND active = true
                        """;
            List<Integer> menuIds = jdbcTemplate.queryForList(replaceConstants(sql), Integer.class, params.toArray());
            for (Integer menuId : menuIds) {
                params.clear();
                sql = """
                        INSERT INTO :database.permission ( menuId, roleId, canAccess, active, createdBy ) VALUES (?, ?, ?, ?, ?)
                            """;
                params.add(menuId);
                params.add(lastId);
                params.add(false);
                params.add(true);
                params.add(httpSession.getAttribute("userId"));
                jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
            }
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region update
    public void updateRole(RoleData data) throws SQLException {

        String sql = """
                UPDATE :database.role SET name = ?, active = ?, updatedBy = ? WHERE uuid = ?
                    """;
        List<Object> params = new ArrayList<>();
        params.add(data.getName());
        params.add(data.getActive());
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
    public void updatePermission(RoleData data) throws SQLException {
        String sql = "";

        try {
            List<Object> params = new ArrayList<>();
            Integer roleId = getTransacionalIdByUuid("role", data.getUuid());
            List<PermissionData> dataItems = Optional.ofNullable(data.getMenuPermission()).orElse(new ArrayList<>());
            StringBuilder valuesLabel = new StringBuilder("");
            for (PermissionData item : dataItems) {
                params.add(getTransacionalIdByUuid("permission", item.getUuid()));
                params.add(item.getMenuId());
                params.add(roleId);
                params.add(item.getCanAccess());
                params.add(true);
                params.add(httpSession.getAttribute("userId"));
                valuesLabel.append("(?, ?, ?, ?, ?, ?),");
            }
            if (!Strings.isNullOrEmpty(valuesLabel.toString())) {
                valuesLabel.delete(valuesLabel.length() - 1, valuesLabel.length());
                sql = "INSERT INTO :database.permission (id, menuId, roleId, canAccess, active, createdBy) VALUES "
                        + valuesLabel
                        + " AS new ON DUPLICATE KEY UPDATE canAccess = new.canAccess, updatedBy = new.createdBy ";
                jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());
            }

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region delete
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(String id) throws SQLException {
        Integer roleId = getTransacionalIdByUuid("role", id);

        try {
            String sql = "DELETE FROM :database.role WHERE uuid = ? ";
            List<Object> params = new ArrayList<>();
            params.add(id);
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());

            params.clear();
            sql = "DELETE FROM :database.permission WHERE roleId = ? ";
            params.add(roleId);
            jdbcTemplateTrans.update(replaceConstants(sql), params.toArray());

        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion
}
