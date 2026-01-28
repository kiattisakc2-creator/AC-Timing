package racetimingms.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import racetimingms.constants.Constants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Primary
public class CoreService {
    @Autowired
    @Qualifier("mainJdbcTemplate")
    JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("mainNamedJdbcTemplate")
    NamedParameterJdbcTemplate namedJdbcTemplate;

    @Autowired
    private AWSService awsService;

    @Autowired
    protected HttpSession httpSession;

    public static final String NO_ROW_ERR = "No rows updated";

    // #region get
    public List<Map<String, Object>> getAllProvince() throws SQLException {

        String sql = "SELECT id, province, amphoe, district, zipcode, active FROM master.province WHERE active = true";
        List<Object> params = new ArrayList<>();

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            return results;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getNationality() throws SQLException {

        String sql = "SELECT alpha_3_code, nationality FROM master.countries ";
        List<Object> params = new ArrayList<>();

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params.toArray());

            return results;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getOrganizer() throws SQLException {

        String sql = "SELECT uuid, IFNULL(CONCAT(firstName, IFNULL(CONCAT(' ', lastName), '')), email) AS name FROM :database.user WHERE role = 'organizer' AND active = true ";
        List<Object> params = new ArrayList<>();

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            return results;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getOtionData(String vData, String tData) throws SQLException {
        String sql = "SELECT DISTINCT " + vData + " AS value FROM :database." + tData + " WHERE active = true ";

        List<Object> params = new ArrayList<>();

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            return results;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getOtionIdOtions(String vData, String tData) throws SQLException {
        String sql = "SELECT DISTINCT id, " + vData + " AS value FROM :database." + tData + " WHERE active = true ";

        List<Object> params = new ArrayList<>();

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());

            return results;
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public <T> List<T> query(String sql, List<Object> params, Class<T> classes) throws SQLException {
        try {
            return jdbcTemplate.query(replaceConstants(sql), new BeanPropertyRowMapper<>(classes), params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    public <T> T queryForObject(String sql, List<Object> params, Class<T> classes) throws SQLException {
        try {
            return jdbcTemplate.queryForObject(replaceConstants(sql), new BeanPropertyRowMapper<>(classes),
                    params.toArray());
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }

    // #endregion

    public String replaceConstants(String sql) {
        sql = sql.replaceAll(":database", Constants.DATABASE);
        sql = sql.replaceAll(":companyName", Constants.COMPANY_NAME);
        return sql;
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

    public String checkSameFile(String newFileName, String uuid, String table, String prefixPath, String fileUrl)
            throws SQLException {
        Map<String, Object> dataFileOld = getOldFile(uuid, table, prefixPath, fileUrl);
        String prefix = (String) dataFileOld.get(prefixPath);
        String oldFileName = (String) dataFileOld.get(fileUrl);
        if (newFileName != null && !newFileName.contains("https://s3")) {
            if (oldFileName != null && !"".equals(oldFileName)) {
                awsService.deleteFile(prefix, oldFileName);
                return newFileName;
            } else {
                return oldFileName;
            }
        }
        return oldFileName;
    }

    public Map<String, Object> getOldFile(String uuid, String table, String prefixPath, String fileUrl)
            throws SQLException {
        String sql = "SELECT " + prefixPath + ", " + fileUrl + " FROM :database." + table + " WHERE uuid = ? ";
        List<Object> params = new ArrayList<>();
        params.add(uuid);
        try {
            Map<String, Object> data = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());
            return data;
        } catch (EmptyResultDataAccessException e) {
            return new HashMap<>();
        } catch (DataAccessException e) {
            log.error("Error occurred:", e);
            throw new SQLException(e.getMessage());
        }
    }
}
