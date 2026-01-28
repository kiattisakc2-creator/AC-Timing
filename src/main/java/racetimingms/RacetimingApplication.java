package racetimingms;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;


@EntityScan(basePackages = "racetimingms.model")
@SpringBootApplication
@EnableScheduling
public class RacetimingApplication{

    public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(RacetimingApplication.class, args);
	}

}