
package az.mm.developerjobs.service;

import az.mm.developerjobs.constant.ImageSource;
import az.mm.developerjobs.entity.JobInfo;
import az.mm.developerjobs.model.Pagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final Logger logger = LoggerFactory.getLogger(JobService.class);

    @Autowired
    private MongoDatabase mongoDatabase;

    @Autowired
    private JavaMailSender javaMailSender;

    public List<JobInfo> getJobsWithLimit(String countryCode, int start) {
        List<JobInfo> jobs = new ArrayList<>();
        MongoCollection<Document> collection = mongoDatabase.getCollection("jobs");
        collection.find(Filters.eq("countryCode", countryCode))
                .skip(start)
                .limit(10)
                .forEach((doc) -> {
                    JobInfo job = documentToJobInfo(doc);
                    job.setImageSrc(createImageSource(job.getWebsite(), job.getJobTitle()));
                    jobs.add(job);
                });

        return jobs;
    }

    public List<JobInfo> getJobsWithPageRequest(String countryCode, int pageIndex) {
        List<JobInfo> jobs = new ArrayList<>();
        MongoCollection<Document> collection = mongoDatabase.getCollection("jobs");
        collection.find(Filters.eq("countryCode", countryCode))
                .sort(Sorts.descending("id"))
                .skip(pageIndex * 10)
                .limit(10)
                .forEach((doc) -> {
                    JobInfo job = documentToJobInfo(doc);
                    job.setImageSrc(createImageSource(job.getWebsite(), job.getJobTitle()));
                    jobs.add(job);
                });

        return jobs;
    }

    public JobInfo getJob(int id, String title) {
        MongoCollection<Document> collection = mongoDatabase.getCollection("jobs");
        Document doc = collection.find(Filters.and(Filters.eq("id", id), Filters.eq("urlSuffix", title))).first();
        if (doc != null) {
            JobInfo job = documentToJobInfo(doc);
            job.setImageSrc(createImageSource(job.getWebsite(), job.getJobTitle()));
            return job;
        }
        return null;
    }

    public int countOfVacancy(String countryCode) {
        MongoCollection<Document> collection = mongoDatabase.getCollection("jobs");
        long count = collection.countDocuments(Filters.eq("countryCode", countryCode));
        return (int) count;
    }

    public List<JobInfo> caseInsensitiveSearchResult(String searchText) {
        List<JobInfo> jobs = new ArrayList<>();
        MongoCollection<Document> collection = mongoDatabase.getCollection("jobs");
        collection.find(Filters.regex("jobTitle", searchText, "i")).forEach((doc) -> {
            JobInfo job = documentToJobInfo(doc);
            job.setImageSrc(createImageSource(job.getWebsite(), job.getJobTitle()));
            jobs.add(job);
        });

        return jobs;
    }

    public List<JobInfo> caseSensitiveSearchResult(String searchText) {
        List<JobInfo> jobs = new ArrayList<>();
        logger.info("Starting search... [{}]", searchText);
        MongoCollection<Document> collection = mongoDatabase.getCollection("jobs");
        collection.find().forEach((doc) -> {
            JobInfo job = documentToJobInfo(doc);
            if (job.getJobTitle().contains(searchText) || job.getCompany().contains(searchText) || job.getContent().contains(searchText)) {
                job.setImageSrc(createImageSource(job.getWebsite(), job.getJobTitle()));
                jobs.add(job);
            }
        });
        logger.info("Ending search..., result: {}", jobs.size());

        jobs.sort((j1, j2) -> j2.getId() - j1.getId());

        return jobs;
    }

    private JobInfo documentToJobInfo(Document doc) {
        JobInfo job = new JobInfo();
        job.setId(doc.getInteger("id"));
        job.setUrlSuffix(doc.getString("urlSuffix"));
        job.setCountryCode(doc.getString("countryCode"));
        job.setJobTitle(doc.getString("jobTitle"));
        job.setCompany(doc.getString("company"));
        job.setContent(doc.getString("content"));
        job.setCreatedAt(doc.getDate("createdAt"));
        job.setUpdatedAt(doc.getDate("updatedAt"));
        return job;
    }

    private String createImageSource(String website, String jobTitle) {
        ImageSource imgSource;
        switch (website) {
            case "boss.az":
                imgSource = ImageSource.BOSS_AZ;
                break;
            case "jobsearch.az":
                imgSource = ImageSource.JOBSEARCH_AZ;
                break;
            case "rabota.az":
                imgSource = ImageSource.RABOTA_AZ;
                break;
            case "banco.az":
                imgSource = ImageSource.BANCO_AZ;
                break;
            case "careerbuilder.com":
            case "monster.de":
                imgSource = getDeveloperImageUrl(jobTitle);
                break;
            default:
                imgSource = ImageSource.JOBSEARCH_AZ;
        }

        return imgSource.toString();
    }

    private ImageSource getDeveloperImageUrl(String jobTitle) {
        ImageSource imgSource = ImageSource.DEVELOPER;
        if (jobTitle != null) {
            jobTitle = jobTitle.toLowerCase();

            if (jobTitle.contains("java")) {
                imgSource = ImageSource.JAVA;
            } else if (jobTitle.contains("net")) {
                imgSource = ImageSource.NET;
            } else if (jobTitle.contains("android")) {
                imgSource = ImageSource.ANDROID;
            } else if (jobTitle.contains("sql")) {
                imgSource = ImageSource.SQL;
            } else if (jobTitle.contains("python")) {
                imgSource = ImageSource.PYTHON;
            } else if (jobTitle.contains("php")) {
                imgSource = ImageSource.PHP;
            }
        }

        return imgSource;
    }

    public Pagination createPagination(String countryCode, int page) {
        int vacancyCount = countOfVacancy(countryCode);
        int count = (int) Math.ceil(vacancyCount / 10.0);
        int prev = (page != 1) ? (page - 1) : 1;
        int next = (page != count) ? (page + 1) : count;

        int begin = 1, end = 10;
        if (page > 6) {
            begin = page - 5;
            end = page + 4;
        }
        if (end > count) {
            end = count;
            begin = count - 9;
            if (begin < 1) begin = 1;
        }

        Pagination pagination = new Pagination(count, begin, end, prev, next);

        return pagination;
    }

    public void sendMail(String from, String subject, String message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo("contact@developerjobs.info"); //which email you want to send
        mailMessage.setFrom(from);
        mailMessage.setSubject(subject);
        mailMessage.setText(message);
        javaMailSender.send(mailMessage);
        logger.info("Mail sent");
    }

}
