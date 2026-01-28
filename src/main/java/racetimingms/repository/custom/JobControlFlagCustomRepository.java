package racetimingms.repository.custom;

import racetimingms.model.JobControlFlag;

public interface JobControlFlagCustomRepository {
    JobControlFlag findByJobName(String jobName);
    void saveJobControlFlag(JobControlFlag flag);
}

