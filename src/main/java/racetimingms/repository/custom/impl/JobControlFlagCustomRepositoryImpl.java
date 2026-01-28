package racetimingms.repository.custom.impl;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import org.springframework.stereotype.Repository;
import racetimingms.model.JobControlFlag;
import racetimingms.repository.custom.JobControlFlagCustomRepository;

import java.time.LocalDateTime;

@Repository
public class JobControlFlagCustomRepositoryImpl implements JobControlFlagCustomRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String FIXED_SCHEMA = "racetiming"; // ✅ schema is locked

    @Override
    public JobControlFlag findByJobName(String jobName) {
        String sql = "SELECT * FROM " + FIXED_SCHEMA + ".job_control_flags WHERE job_name = :jobName";
        return (JobControlFlag) entityManager.createNativeQuery(sql, JobControlFlag.class)
                .setParameter("jobName", jobName)
                .getSingleResult();
    }

    @Override
    @Transactional
    public void saveJobControlFlag(JobControlFlag flag) {
        String sql = "REPLACE INTO " + FIXED_SCHEMA + ".job_control_flags (job_name, is_enabled, updated_at) " +
                "VALUES (:jobName, :isEnabled, :updatedAt)";
        entityManager.createNativeQuery(sql)
                .setParameter("jobName", flag.getJobName())
                .setParameter("isEnabled", flag.getIsEnabled())
                .setParameter("updatedAt", flag.getUpdatedAt() != null ? flag.getUpdatedAt() : LocalDateTime.now())
                .executeUpdate();
    }
}
