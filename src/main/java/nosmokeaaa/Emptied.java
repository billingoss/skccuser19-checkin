package nosmokeaaa;

public class Emptied extends AbstractEvent {

    private Long id;
    private String status;
    private Long areaId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getCount() {
        return status;
    }

    public void setCount(String status) {
        this.status = status;
    }
    public Long getAreaId() {
        return areaId;
    }

    public void setAreaId(Long areaId) {
        this.areaId = areaId;
    }
}