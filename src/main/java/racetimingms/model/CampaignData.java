package racetimingms.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;

@Data
@Generated
@NoArgsConstructor
@AllArgsConstructor
public class CampaignData extends StandardField {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("organizerId")
    private Integer organizerId;

    @JsonProperty("organizerName")
    private String organizerName;

    @JsonProperty("name")
    private String name;

    @JsonProperty("shortName")
    private String shortName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("eventDate")
    private String eventDate;

    @JsonProperty("location")
    private String location;

    @JsonProperty("prefixPath")
    private String prefixPath;

    @JsonProperty("logoUrl")
    private String logoUrl;

    @JsonProperty("pictureUrl")
    private String pictureUrl;

    @JsonProperty("bgUrl")
    private String bgUrl;

    @JsonProperty("email")
    private String email;

    @JsonProperty("website")
    private String website;

    @JsonProperty("facebook")
    private String facebook;

    @JsonProperty("contactName")
    private String contactName;

    @JsonProperty("contactTel")
    private String contactTel;

    @JsonProperty("isDraft")
    private Boolean isDraft;

    @JsonProperty("status")
    private String status;

    @JsonProperty("isApproveCertificate")
    private Boolean isApproveCertificate;

    @JsonProperty("allowRFIDSync")
    private Boolean allowRFIDSync;

    @JsonProperty("rfid_token")
    private String rfid_token;

    @JsonProperty("raceid")
    private Integer raceid;

    @JsonProperty("chipBgUrl")
    private String chipBgUrl;

    @JsonProperty("chipBanner")
    private String chipBanner;

    @JsonProperty("chipPrimaryColor")
    private String chipPrimaryColor;

    @JsonProperty("chipSecondaryColor")
    private String chipSecondaryColor;

    @JsonProperty("chipModeColor")
    private String chipModeColor;

    @JsonProperty("certTextColor")
    private String certTextColor;

    @JsonProperty("stationItems")
    private List<StationData> stationItems;

    @JsonProperty("eventTypeItems")
    private List<EventData> eventTypeItems;

    @JsonProperty("isLogo")
    private Boolean isLogo;

    @JsonProperty("isPicture")
    private Boolean isPicture;

    @JsonProperty("thumbLogoUrl")
    private String thumbLogoUrl;

    @JsonProperty("thumbPictureUrl")
    private String thumbPictureUrl;

    @JsonProperty("thumbBgUrl")
    private String thumbBgUrl;

    @JsonProperty("thumbChipImgUrl")
    private String thumbChipImgUrl;

    @JsonProperty("category")
    private String category;

    @JsonProperty("role")
    private String role;

    @JsonProperty("organizerUuid")
    private String organizerUuid;

    @JsonProperty("eventTotal")
    private Integer eventTotal;

    @JsonProperty("statusDate")
    private String statusDate;

    @JsonProperty("eventNameType")
    private String eventNameType;
}
