package racetimingms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import racetimingms.model.JobControlFlag;
import racetimingms.repository.custom.JobControlFlagCustomRepository;

@Repository
public interface JobControlFlagRepository
        extends JpaRepository<JobControlFlag, String>, JobControlFlagCustomRepository {
}