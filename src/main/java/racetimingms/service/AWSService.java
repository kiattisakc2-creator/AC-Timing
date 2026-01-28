package racetimingms.service;

import java.io.File;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

import lombok.Setter;

@Service
@Setter
public class AWSService {

    @Value("${aws.s3.accessKey}")
    private String accessKey;
    @Value("${aws.s3.secretKey}")
    private String secretKey;
    @Value("${aws.s3.bucketName}")
    private String bucketName;

    private String prefix = "";
    Logger logger = LoggerFactory.getLogger(AWSService.class);

    private AmazonS3 authClient() {
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
        return AmazonS3ClientBuilder.standard().withRegion(Regions.AP_SOUTHEAST_1)
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .build();
    }

    public List<Bucket> listBucket() {
        AmazonS3 s3 = authClient();
        List<Bucket> buckets = s3.listBuckets();
        s3.shutdown();

        return buckets;
    }

    public String uploadFile(File file) {
        String dest = bucketName + prefix;
        String fileName = file.getName();
        AmazonS3 s3 = authClient();

        try {
            s3.putObject(dest, fileName, file);
        } catch (AmazonServiceException e) {
            logger.error(e.getErrorMessage());
        } finally {
            s3.shutdown();
        }

        return fileName;
    }

    @Cacheable(value = "publicUrlCache", key = "#bucketName ?: '' + '-' + #objectKey ?: ''", cacheManager = "caffeineCacheManager")
    public String getSharedUrl(String objectKey) {
        String dest = bucketName + prefix;
        Date expiration = new Date();
        long expTimeMillis = Instant.now().toEpochMilli();
        expTimeMillis += 1000 * 60 * 60 * 24 * 2;
        expiration.setTime(expTimeMillis);
        URL url = null;
        AmazonS3 s3 = authClient();
        try {
            if (objectKey == null) {
                return null;
            }
            GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(dest, objectKey)
                    .withMethod(HttpMethod.GET)
                    .withExpiration(expiration);
            url = s3.generatePresignedUrl(generatePresignedUrlRequest);
        } catch (AmazonServiceException e) {
            logger.error(e.getErrorMessage());
        } finally {
            s3.shutdown();
        }

        return url != null ? url.toString() : null;
    }

    public void deleteFile(String prefix, String objectKey) {
        String dest = bucketName + prefix;
        AmazonS3 s3 = authClient();
        try {
            s3.deleteObject(dest, objectKey);
        } catch (AmazonServiceException e) {
            logger.error(e.getErrorMessage());
        } finally {
            s3.shutdown();
        }
    }
}
