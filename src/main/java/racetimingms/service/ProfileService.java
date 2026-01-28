package racetimingms.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.validation.ValidationException;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import racetimingms.model.ProfileData;

@Slf4j
@Component
public class ProfileService extends DatabaseService {

    // #region get
    public Map<String, Object> getProfileById(String id) throws SQLException {

        String sql = """
                SELECT
                    a.uuid, a.firstName, a.lastName, a.idNo, a.gender,
                    CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) AS username,
                    DATE_FORMAT(a.birthDate, '%Y-%m-%d %T') AS birthDate, a.email, a.tel,
                    a.address, a.province, a.amphoe, a.district, a.zipcode, a.nationality, a.bloodGroup,
                    a.healthProblems, a.emergencyContact, a.emergencyContactTel, a.prefixPath, a.pictureUrl, a.active
                FROM
                    :database.user a
                WHERE a.uuid = ?
                            """;
        List<Object> params = new ArrayList<>();
        params.add(id);

        try {
            // List<Map<String, Object>> data =
            // jdbcTemplate.queryForList(replaceConstants(sql), params.toArray());
            Map<String, Object> data = jdbcTemplate.queryForMap(replaceConstants(sql), params.toArray());

            String prefixPath = (String) data.get("prefixPath");
            String thumbPictureUrl = (String) data.get("pictureUrl");
            if (prefixPath != "" && prefixPath != null) {
                String publicUrl = getPublicUrl(prefixPath, thumbPictureUrl);
                data.put("thumbPictureUrl", publicUrl);
            }

            return data;

            // Long hits = getHits();
            // return DatabaseData.builder().hits(hits).records(data).build();
        } catch (DataAccessException e) {
            log.error(e.getMessage());
            throw new SQLException(e.getMessage());
        }
    }
    // #endregion

    // #region update
    public void updateProfile(ProfileData data) throws SQLException {

        String sql = """
                UPDATE :database.user SET   firstName = ?, lastName = ?, idNo = ?, gender = ?, birthDate = ?, email = ?,
                                            tel = ?, address = ?, province = ?, amphoe = ?, district = ?, zipcode = ?,
                                            nationality = ?, bloodGroup = ?, healthProblems = ?, emergencyContact = ?, emergencyContactTel = ?,
                                            active = ?, updatedBy = ?
                                    WHERE uuid = ?
                    """;
        List<Object> params = new ArrayList<>();
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
        if (data.getPrefixPath() != null) {
            sql = """
                    UPDATE :database.user SET   firstName = ?, lastName = ?, idNo = ?, gender = ?, birthDate = ?, email = ?,
                                                tel = ?, address = ?, province = ?, amphoe = ?, district = ?, zipcode = ?,
                                                nationality = ?, bloodGroup = ?, healthProblems = ?, emergencyContact = ?, emergencyContactTel = ?,
                                                prefixPath = ?, pictureUrl = ?, active = ?, updatedBy = ?
                                        WHERE uuid = ?
                        """;
            params.add(data.getPrefixPath());
            params.add(data.getPictureUrl());
        }
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
    // #endregion
}
