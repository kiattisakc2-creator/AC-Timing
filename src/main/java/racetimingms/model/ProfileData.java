package racetimingms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;

@Data
@Generated
@NoArgsConstructor
@AllArgsConstructor
public class ProfileData extends StandardField {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("idNo")
    private String idNo;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("birthDate")
    private String birthDate;

    @JsonProperty("email")
    private String email;

    @JsonProperty("tel")
    private String tel;

    @JsonProperty("address")
    private String address;

    @JsonProperty("province")
    private String province;

    @JsonProperty("amphoe")
    private String amphoe;

    @JsonProperty("district")
    private String district;

    @JsonProperty("zipcode")
    private String zipcode;
    
    @JsonProperty("nationality")
    private String nationality;

    @JsonProperty("bloodGroup")
    private String bloodGroup;

    @JsonProperty("healthProblems")
    private String healthProblems;

    @JsonProperty("emergencyContact")
    private String emergencyContact;

    @JsonProperty("emergencyContactTel")
    private String emergencyContactTel;

    @JsonProperty("prefixPath")
    private String prefixPath;

    @JsonProperty("pictureUrl")
    private String pictureUrl;
}
