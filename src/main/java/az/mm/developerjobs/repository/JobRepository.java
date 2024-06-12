
package az.mm.developerjobs.repository;

import az.mm.developerjobs.entity.JobInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 *
 * @author MM <mushfiqazeri@gmail.com>
 */

@Repository
public interface JobRepository extends MongoRepository<JobInfo, Integer> {
    
    JobInfo findById(int id);
    
    JobInfo findByIdAndUrlSuffix(int id, String urlSuffix);
    
    List<JobInfo> findAllByCountryCode(String countryCode);
    
    Page<JobInfo> findAllByCountryCode(String countryCode, Pageable page);

    long countByCountryCode(String countryCode);
    
    @Query(value = "{ 'countryCode': ?0 }", sort = "{ 'id': -1 }")
    List<JobInfo> getJobsWithLimit(String countryCode, Pageable pageable);
    
    @Query(value = "{ $or: [ { 'jobTitle': { $regex: ?0, $options: 'i' } }, { 'company': { $regex: ?0, $options: 'i' } }, { 'content': { $regex: ?0, $options: 'i' } } ] }", sort = "{ 'id': -1 }")
    List<JobInfo> searchResult(String searchText);

}
