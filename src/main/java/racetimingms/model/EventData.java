package racetimingms.model;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;

@Data
@Generated
@NoArgsConstructor
@AllArgsConstructor
public class EventData extends StandardField {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("campaignId")
    private Integer campaignId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("eventDate")
    private String eventDate;

    @JsonProperty("category")
    private String category;

    @JsonProperty("otherText")
    private String otherText;

    @JsonProperty("distance")
    private Double distance;

    @JsonProperty("elevationGain")
    private Double elevationGain;

    @JsonProperty("location")
    private String location;

    @JsonProperty("timeLimit")
    private Double timeLimit;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("description")
    private String description;

    @JsonProperty("prefixPath")
    private String prefixPath;

    @JsonProperty("pictureUrl")
    private String pictureUrl;

    @JsonProperty("awardUrl")
    private String awardUrl;

    @JsonProperty("souvenirUrl")
    private String souvenirUrl;

    @JsonProperty("mapUrl")
    private String mapUrl;

    @JsonProperty("scheduleUrl")
    private String scheduleUrl;

    @JsonProperty("awardDetail")
    private String awardDetail;

    @JsonProperty("souvenirDetail")
    private String souvenirDetail;

    @JsonProperty("dropOff")
    private String dropOff;

    @JsonProperty("scheduleDetail")
    private String scheduleDetail;

    @JsonProperty("contactName")
    private String contactName;

    @JsonProperty("contactTel")
    private String contactTel;

    @JsonProperty("contactOwner")
    private String contactOwner;

    @JsonProperty("rfidEventId")
    private Integer rfidEventId;

    @JsonProperty("isFinished")
    private Boolean isFinished;

    @JsonProperty("isAutoFix")
    private Boolean isAutoFix;

    @JsonProperty("isPicture")
    private Boolean isPicture;

    @JsonProperty("isAward")
    private Boolean isAward;

    @JsonProperty("isSouvenir")
    private Boolean isSouvenir;

    @JsonProperty("isMap")
    private Boolean isMap;

    @JsonProperty("isSchedule")
    private Boolean isSchedule;

    @JsonProperty("campaignUuid")
    private String campaignUuid;

    @JsonProperty("thumbPictureUrl")
    private String thumbPictureUrl;

    @JsonProperty("thumbAwardUrl")
    private String thumbAwardUrl;

    @JsonProperty("thumbSouvenirUrl")
    private String thumbSouvenirUrl;

    @JsonProperty("thumbMapUrl")
    private String thumbMapUrl;

    @JsonProperty("thumbScheduleUrl")
    private String thumbScheduleUrl;

    @JsonProperty("categoryText")
    private String categoryText;

    @JsonProperty("ageGroups")
    private List<AgeGroupData> ageGroups;

    @JsonProperty("checkpointMappingItems")
    private List<CheckpointMappingData> checkpointMappingItems;

    @JsonProperty("stationUuid")
    private String stationUuid;

    @JsonProperty("finishTime")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;
}
