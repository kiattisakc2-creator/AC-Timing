
package racetimingms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;

import racetimingms.constants.Constants;
import racetimingms.model.UserInfoData;
import racetimingms.model.response.UserDataResponse;
import racetimingms.model.MenuInfoData;
import racetimingms.model.StandardField;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpSession;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@Component
@Primary
public class DatabaseService {

    @Autowired
    @Qualifier("mainJdbcTemplate")
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("transactdbJdbcTemplate")
    protected JdbcTemplate jdbcTemplateTrans;

    @Autowired
    @Qualifier("mainNamedJdbcTemplate")
    protected NamedParameterJdbcTemplate namedJdbcTemplate;

    @Autowired
    protected HttpSession httpSession;

    @Autowired
    protected AWSService awsService;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    public static final String NO_ROW_ERR = "No rows updated";
    private static final String QUERY_ERROR_MESSAGE = "There error in query method";

    Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    public UserInfoData getUserInfo(String id) throws SQLException {

        String sql = """
                SELECT
                    id, uuid, CONCAT(firstName, IFNULL(CONCAT(' ', lastName), '')) AS username, role, email, pictureUrl
                FROM
                    :database.user
                where
                    id = ?
                    and active = true
                        """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            UserDataResponse userData = jdbcTemplate.queryForObject(replaceConstants(sql),
                    new BeanPropertyRowMapper<>(UserDataResponse.class), params.toArray());
            String userDataJson = mapper.writeValueAsString(userData);
            String encryptedUserData = encryptionService.encryptPayload(userDataJson);
            return UserInfoData.builder().userData(encryptedUserData).build();
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<MenuInfoData> getMenuInfo(String id) throws SQLException {

        String sql = """
                SELECT role FROM :database.user where id = ? and active = true
                        """;
        List<Object> params = new ArrayList<>();
        params.add(id);
        try {
            String roleData = jdbcTemplate.queryForObject(replaceConstants(sql), String.class, params.toArray());

            params.clear();
            sql = """
                    SELECT a.uuid AS id, a.role, a.title, e.uuid AS main, a.name, a.desc, a.icon, a.path, a.isDisabled, a.isDisplay
                    FROM :database.menu a
                    LEFT JOIN :database.menu e ON e.id = a.main
                    WHERE a.role = ? AND a.active = true
                    ORDER BY a.id
                        """;
            params.add(roleData);
            List<MenuInfoData> menuData = jdbcTemplate.query(replaceConstants(sql),
                    new BeanPropertyRowMapper<>(MenuInfoData.class), params.toArray());
            return menuData;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    protected Integer getIdByUuid(String table, String uuid) throws SQLException {
        String sql = "SELECT id FROM :database." + table + " WHERE uuid = ? ";
        List<Object> params = new ArrayList<>();
        params.add(uuid);

        try {
            Map<String, Object> results = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            return (Integer) results.get("id");
        } catch (EmptyResultDataAccessException e) {
            e.printStackTrace();
            log.error("Can't get data from table: {} with uuid: {}", table, uuid);
            return null;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
   
    protected Integer getTransacionalIdByUuid(String table, String uuid) throws SQLException {
        String sql = "SELECT id FROM :database." + table + " WHERE uuid = ? ";
        List<Object> params = new ArrayList<>();
        params.add(uuid);

        try {
            Map<String, Object> results = jdbcTemplateTrans.queryForMap(replaceConstants(sql), params.toArray());
            return (Integer) results.get("id");
        } catch (EmptyResultDataAccessException e) {
            e.printStackTrace();
            log.error("Can't get data from table: {} with uuid: {}", table, uuid);
            return null;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    protected void syncUpdate(Connection connection, String sql, List<Object> params) {
        jdbcTemplate.update(con -> {
            PreparedStatement ps = connection.prepareStatement(replaceConstants(sql), Statement.NO_GENERATED_KEYS);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps;
        });
    }

    public <T extends StandardField> Long getHits(List<T> datas) {
        return datas != null && !datas.isEmpty() ? datas.get(0).getHits() : 0;
    }

    public String replaceConstants(String sql) {
        sql = sql.replaceAll(":database", Constants.DATABASE);
        sql = sql.replaceAll(":companyName", Constants.COMPANY_NAME);
        return sql;
    }

    public String getUuidById(String table, Integer id) throws SQLException {
        String sql = "SELECT uuid FROM :database." + table + " WHERE id = ? ";
        List<Object> params = new ArrayList<>();
        params.add(id);

        try {
            Map<String, Object> results = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            String uuid = (String) results.get("uuid");
            return uuid;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public Integer updateAndReturnId(Connection connection, String sql, List<Object> params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        int updatedRows = jdbcTemplate.update(con -> {
            PreparedStatement ps = connection.prepareStatement(replaceConstants(sql), Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps;
        }, keyHolder);

        if (updatedRows > 0) {
            Number number = Optional.ofNullable(keyHolder.getKey()).orElse(null);
            return number != null ? number.intValue() : -1;
        } else {
            return -1;
        }
    }

    public ResultSet query(Connection connection, String sql, List<Object> values) throws SQLException {

        ResultSet rs = null;
        PreparedStatement ps = null;

        try {
            ps = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
            int i = 1;
            for (Object value : values) {
                if (JSONObject.NULL.equals(value)) {
                    ps.setNull(i++, Types.NULL);
                } else {
                    ps.setObject(i++, value);
                }
            }
            rs = ps.executeQuery();
        } catch (Exception ex) {
            logger.error(QUERY_ERROR_MESSAGE, ex);
            throw new SQLException(QUERY_ERROR_MESSAGE);
        } finally {
            if (ps != null) {
                // ps.close();
            }
        }

        return rs;
    }

    public <T> List<T> transactionalQuery(String sql, List<Object> params, Class<T> classes) {
        List<T> result = jdbcTemplateTrans.query(replaceConstants(sql), new BeanPropertyRowMapper<>(classes),
                params.toArray());
        return Optional.ofNullable(result).orElse(new ArrayList<>());
    }

    public <T> T transactionalQueryForObject(String sql, List<Object> params, Class<T> classes) {
        return jdbcTemplateTrans.queryForObject(replaceConstants(sql), new BeanPropertyRowMapper<>(classes),
                params.toArray());
    }

    protected Integer transactionalUpdate(String sql, List<Object> params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        int updatedRows = jdbcTemplateTrans.update(con -> {
            PreparedStatement ps = con.prepareStatement(replaceConstants(sql), Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps;
        }, keyHolder);

        if (updatedRows > 0) {
            Number number = Optional.ofNullable(keyHolder.getKey()).orElse(null);
            return number != null ? number.intValue() : -1;
        } else {
            return -1;
        }
    }

    public String getPublicUrl(String prefix, String key) throws SQLException {
        try {
            awsService.setPrefix(prefix);
            String url = awsService.getSharedUrl(key);

            return url;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }

    }
}
