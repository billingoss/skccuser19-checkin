package nosmokeaaa;

public class CheckIned extends AbstractEvent {

    private Long id;
    private Long smokingAreaId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getAreaId() {
        return smokingAreaId;
    }

    public void setAreaId(Long smokingAreaId) {
        this.smokingAreaId = smokingAreaId;
    }
}