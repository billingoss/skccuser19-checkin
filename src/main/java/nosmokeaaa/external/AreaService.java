
package nosmokeaaa.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

//@FeignClient(name="area", url="http://area:8080")
@FeignClient(name="area", url="${api.url.area}")
public interface AreaService {

    @RequestMapping(method= RequestMethod.POST, path="/areas")
    public void occupy(@RequestBody Area area);

}