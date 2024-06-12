
package az.mm.developerjobs.repository;

import az.mm.developerjobs.entity.JobInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Repository interface for JobInfo entity with MongoDB operations.
 * Author: MM <mushfiqazeri@gmail.com>
 */
public interface JobRepository extends MongoRepository<JobInfo, Integer> {
    
    JobInfo findById(int id);
    
    JobInfo findByIdAndUrlSuffix(int id, String urlSuffix);
    
    List<JobInfo> findAllByCountryCode(String countryCode);
    
    Page<JobInfo> findAllByCountryCode(String countryCode, Pageable page);

    long countByCountryCode(String countryCode);

    @Query("{ 'countryCode': ?0 }")
    List<JobInfo> getJobsWithLimit(String countryCode, int start, int limit);
    
    @Query("{ '$or': [ { 'title': { '$regex': ?0, '$options': 'i' } }, { 'company': { '$regex': ?0, '$options': 'i' } }, { 'content': { '$regex': ?0, '$options': 'i' } } ] }")
    List<JobInfo> searchResult(String searchText);
}
