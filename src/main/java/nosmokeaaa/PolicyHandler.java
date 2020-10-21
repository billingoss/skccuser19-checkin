package nosmokeaaa;

import nosmokeaaa.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PolicyHandler
{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverEarned_UpdatePoint(@Payload Earned earned)
    {

        if(earned.isMe()){
            System.out.println("##### listener UpdatePoint : " + earned.toJson());
        }
    }


    @Autowired
    CheckInRepository checkInRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverEmptied_UpdateStatus(@Payload Emptied emptied)
    {

        if(emptied.isMe())
        {
            System.out.println("##### LLLLL88 UpdatePoint : " + emptied.toJson());
            Optional<CheckIn> checkInOptional = checkInRepository.findById(emptied.getId());
            CheckIn checkIn = checkInOptional.get();
            checkIn.setStatus("SUCCESS");
            checkInRepository.save(checkIn);
        }
    }




}
