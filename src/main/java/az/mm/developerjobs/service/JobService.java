
package az.mm.developerjobs.service;

import az.mm.developerjobs.constant.ImageSource;
import az.mm.developerjobs.entity.JobInfo;
import az.mm.developerjobs.model.Pagination;
import az.mm.developerjobs.repository.JobRepository;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author MM <mushfiqazeri@gmail.com>
 */
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
        Bson filter = Filters.eq("countryCode", countryCode);
        Bson sort = Sorts.descending("id");
        MongoCursor<Document> cursor = collection.find(filter).sort(sort).skip(start).limit(10).iterator();
        try {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                JobInfo job = documentToJobInfo(doc);
                job.setImageSrc(createImageSource(job.getWebsite(), job.getJobTitle()));
                jobs.add(job);
            }
        } finally {
            cursor.close();
        }
        return jobs;
    }

    public List<JobInfo> getJobsWithPageRequest(String countryCode, int pageIndex) {
        int pageSize = 10;
        int start = pageIndex * pageSize;
        return getJobsWithLimit(countryCode, start);
    }

    public JobInfo getJob(int id, String title) {
        MongoCollection<Document> collection = mongoDatabase.getCollection("jobs");
        Bson filter = Filters.and(Filters.eq("id", id), Filters.eq("title", title));
        Document doc = collection.find(filter).first();
        if (doc != null) {
            JobInfo job = documentToJobInfo(doc);
            job.setImageSrc(createImageSource(job.getWebsite(), job.getJobTitle()));
            return job;
        }
        return null;
    }

    public int countOfVacancy(String countryCode) {
        MongoCollection<Document> collection = mongoDatabase.getCollection("jobs");
        Bson filter = Filters.eq("countryCode", countryCode);
        return Math.toIntExact(collection.countDocuments(filter));
    }

    public List<JobInfo> caseInsensitiveSearchResult(String searchText) {
        List<JobInfo> jobs = new ArrayList<>();
        MongoCollection<Document> collection = mongoDatabase.getCollection("jobs");
        Bson filter = Filters.regex("content", searchText, "i");
        MongoCursor<Document> cursor = collection.find(filter).iterator();
        try {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                JobInfo job = documentToJobInfo(doc);
                job.setImageSrc(createImageSource(job.getWebsite(), job.getJobTitle()));
                jobs.add(job);
            }
        } finally {
            cursor.close();
        }
        return jobs;
    }

    public List<JobInfo> caseSensitiveSearchResult(String searchText) {
        List<JobInfo> jobs = new ArrayList<>();
        logger.info("Starting search... [{}]", searchText);
        MongoCollection<Document> collection = mongoDatabase.getCollection("jobs");
        MongoCursor<Document> cursor = collection.find().iterator();
        try {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                JobInfo job = documentToJobInfo(doc);
                if (job.getJobTitle().contains(searchText) || job.getCompany().contains(searchText) || job.getContent().contains(searchText)) {
                    job.setImageSrc(createImageSource(job.getWebsite(), job.getJobTitle()));
                    jobs.add(job);
                }
            }
        } finally {
            cursor.close();
        }
        logger.info("Ending search..., result: {}", jobs.size());
        jobs.sort((j1, j2) -> j2.getId() - j1.getId());
        return jobs;
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

    private JobInfo documentToJobInfo(Document doc) {
        JobInfo jobInfo = new JobInfo();
        jobInfo.setId(doc.getInteger("id"));
        jobInfo.setJobTitle(doc.getString("title"));
        jobInfo.setCompany(doc.getString("company"));
        jobInfo.setCountryCode(doc.getString("countryCode"));
        jobInfo.setContent(doc.getString("content"));
        jobInfo.setWebsite(doc.getString("website"));
        // Add other fields as needed
        return jobInfo;
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
