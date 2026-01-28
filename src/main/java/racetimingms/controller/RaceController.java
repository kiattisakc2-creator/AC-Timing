package racetimingms.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import racetimingms.constants.Constants;
import racetimingms.service.RaceService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/race")
public class RaceController {

    @Autowired
    private RaceService raceService;

    @GetMapping("/info")
    public String getRaceInfo() {
        return raceService.pullRaceInfo(101901,"8f157107111642afb42b65bb250c14be");
    }

    @GetMapping("/bio")
    public String getBio(@RequestParam(defaultValue = "1") int page) {
        return raceService.pullBio(101901,"8f157107111642afb42b65bb250c14be",page);
    }

    @GetMapping("/result")
    public String getResult(@RequestParam(defaultValue = "1") int page) {
        return raceService.pullResult(101901,"8f157107111642afb42b65bb250c14be",page);
    }

    @GetMapping("/split")
    public String getSplit(@RequestParam(defaultValue = "1") int page) {
        return raceService.pullSplit(101901,"8f157107111642afb42b65bb250c14be",page);
    }
}
