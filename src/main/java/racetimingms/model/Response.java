package racetimingms.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Response {

	private Boolean isSuccess;
	private Object data;
}
