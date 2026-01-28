package racetimingms.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import racetimingms.constants.Constants;


@Service
public class ApplicationUserService implements UserDetailsService {

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String sql = "SELECT id, password FROM :database.user WHERE email = ? and active = true limit 1";
        return jdbcTemplate.queryForObject(replaceConstants(sql), new Object[] { email },
                (rs, rowNum) -> new ApplicationUser(
                    rs.getString("id"),
                    rs.getString("password"),
                    AuthorityUtils.createAuthorityList("ROLE_USER"),
                    true,
                    true,
                    true,
                    true));
    }

    private String replaceConstants(String sql) {
        sql = sql.replaceAll(":database", Constants.DATABASE);
        return sql;
    }
}
