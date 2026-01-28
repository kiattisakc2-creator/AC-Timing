package racetimingms.service;

import cn.hutool.http.HttpUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class RaceService {

    private static final String raceUrl ="https://rqs.racetigertiming.com/Dif/info";
    private static final String bioUrl ="https://rqs.racetigertiming.com/Dif/bio";
    private static final String resultUrl ="https://rqs.racetigertiming.com/Dif/score";
    private static final String splitUrl ="https://rqs.racetigertiming.com/Dif/splitScore";

    private static final String partnerCode = "000001";

    public String pullRaceInfo(int raceId, String token) {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("pc", partnerCode);
        paramMap.put("rid", raceId);
        paramMap.put("token", token);
        return HttpUtil.post(raceUrl, paramMap);
    }

    public String pullBio(int raceId, String token, int page) {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("pc", partnerCode);
        paramMap.put("rid", raceId);
        paramMap.put("token", token);
        paramMap.put("page", page);
        return HttpUtil.post(bioUrl, paramMap);
    }

    public String pullResult(int raceId, String token, int page) {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("pc", partnerCode);
        paramMap.put("rid", raceId);
        paramMap.put("token", token);
        paramMap.put("page", page);
        return HttpUtil.post(resultUrl, paramMap);
    }

    public String pullSplit(int raceId, String token, int page) {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("pc", partnerCode);
        paramMap.put("rid", raceId);
        paramMap.put("token", token);
        paramMap.put("page", page);
        return HttpUtil.post(splitUrl, paramMap);
    }
}
