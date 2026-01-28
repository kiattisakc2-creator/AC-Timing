package racetimingms.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import racetimingms.constants.Constants;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/help")
public class HelpController {

    @Autowired
    @Qualifier("reportCacheManager")
    private CacheManager reportCacheManager;

    @DeleteMapping("/clearReports")
    @CacheEvict(value = { "getEslipReport", "getCertificateReport" }, cacheManager = "reportCacheManager", allEntries = true)
    public ResponseEntity<String> clearReportCache() {
    return ResponseEntity.ok("Report caches cleared successfully.");
        }

}
