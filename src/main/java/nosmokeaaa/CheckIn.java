package nosmokeaaa;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="CheckIn_table")
public class CheckIn {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long smokingAreaId;
    private Long point;
    private String status;

    @PostPersist
    public void onPostPersist(){
        CheckIned checkIned = new CheckIned();
        BeanUtils.copyProperties(this, checkIned);
        checkIned.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        nosmokeaaa.external.Area area = new nosmokeaaa.external.Area();
        // mappings goes here
        System.out.println("##### LLLL1:"+this.getSmokingAreaId());

        area.setAreaId(this.getSmokingAreaId());
        //area.setId(this.getSmokingAreaId());
        area.setStatus("USE");

        CheckInApplication.applicationContext.getBean(nosmokeaaa.external.AreaService.class)
            .occupy(area);
        System.out.println("##### LLLL2");


    }

    @PostUpdate
    public void onPostUpdate(){

        System.out.println("##### LLLL3");

        CheckOuted checkOuted = new CheckOuted();
        BeanUtils.copyProperties(this, checkOuted);
        checkOuted.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getSmokingAreaId() {
        return smokingAreaId;
    }

    public void setSmokingAreaId(Long smokingAreaId) {
        this.smokingAreaId = smokingAreaId;
    }
    public Long getPoint() {
        return point;
    }

    public void setPoint(Long point) {
        this.point = point;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }




}
