
![image](https://user-images.githubusercontent.com/70302884/96567670-0504e000-1302-11eb-877c-375e23c5f20f.png)

# 흡연구역 포인트 서비스

본 서비스는 흡연구역에서 흡연한 경우, Point 가 적립되며 적립된 Point 을 사용하는 기능입니다. 
CNA 개발에 요구되는 체크포인트를 만족하기 위하여 분석/설계/구현/운영 전단계를 포함하도록 구성하였습니다. 


# Table of contents

- [흡연구역 포인트 서비스](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현:](#구현-)
    - [DDD 의 적용](#ddd-의-적용)
    - [Saga](#Saga)
    - [CQRS](#CQRS)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출-과-Eventual-Consistency)
  - [운영](#운영)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [Istio 적용](#Istio 적용)
    - [Kiali](#Kiali)
    - [Jaeger](#Jaeger)
    - [Circuit Breaker](#Destination Rule)

# 서비스 시나리오

기능적 요구사항
1. 고객이 흡연구역에서 체크인 한다.
1. (추가) 체크인 하면 흡연구역 상태를 사용중으로 변경(USE)한다.
1. 고객이 흡연구역에서 체크아웃 한다.
1. (추가) 체크아웃 되면 포인트를 적립하고 흡연구역 상태를 비었음(VACANT)로 변경한다.
1. 고객이 포인트를 사용한다.
1. 포인트가 사용되면 적립된 포인트에서 차감한다.
1. 포인트가 차감되면 포인트 내역을 업데이트 한다.
1. 고객이 포인트 내역을 확인한다. 

비기능적 요구사항
1. 트랜잭션
    1. 포인트 차감이 이루어지지 않은 포인트 결제는 성립되지 않는다. Sync 호출
2. 장애격리
    1. 포인트 결제, 포인트 적립 기능이 수행되지 않더라도 흡연장소 체크인/아웃은 작동되어야 한다. Async (event-driven), Eventual Consistency
    1. (기능 추가시 내용 업데이트 필요)  Circuit breaker, fallback
3. 성능
    1. 고객이 흡연장소에 체크인/아웃한 내역과 포인트 적립 내역을 mypage에서 확인이 가능해야 한다. CQRS
    1. 고객이 포인트 결제하여 차감된 내역을 mypage에서 확인이 가능해야 한다. CQRS
    1. 체크아웃후 포인트 적립이 완료되면 적립상태가 체크인 내역에 업데이트 되어야 한다. SAGA
    1. (추가) 체크아웃후 흡연구역 사용여부가 VACANT 로 변경되면 체크인 내역에 상태를 성공(SUCCESS) 로 업데이트 한다. SAGA
    1. (추가) 체크인/아웃에 따라 흡연구역 사용여부(USE/VACANT)를 areaList 에서 확인 가능하다. CQRS
    

# 분석/설계


## AS-IS 조직 (Horizontally-Aligned)
  ![image](https://user-images.githubusercontent.com/487999/79684144-2a893200-826a-11ea-9a01-79927d3a0107.png)

## TO-BE 조직 (Vertically-Aligned)
  ![image](https://user-images.githubusercontent.com/70302884/96571555-ad1ca800-1306-11eb-8951-0621a1469146.png)


## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과:  http://www.msaez.io/#/storming/9ClIIvT5hZUzaMQIL3q96ps98iJ2/every/a85337ed8fc34a9f060b32f77e7d16cc/-MKAD1p39nqu1DmG16y9


### 이벤트 도출
![image](https://user-images.githubusercontent.com/70302884/96573041-911a0600-1308-11eb-8506-1efc69f8a481.png)

### 부적격 이벤트 탈락
![image](https://user-images.githubusercontent.com/70302884/96574180-1651ea80-130a-11eb-9337-f8e08478713e.png)

    - 과정중 도출된 잘못된 도메인 이벤트들을 걸러내는 작업을 수행함
        - 장소 체크인시>흡연 구역변경/등록됨, 포인트 적립/차감시>등록됨, 사용자 정보>사용자 정보 등록/변경/삭제 :  별도 이벤트이지, 메인 업무적인 의미의 이벤트가 아니라서 제외

### 액터, 커맨드 부착하여 읽기 좋게
![image](https://user-images.githubusercontent.com/70302884/96574093-f9b5b280-1309-11eb-9a8b-34eb81f718ec.png)

### 어그리게잇으로 묶기
![image](https://user-images.githubusercontent.com/70302884/96574602-a1cb7b80-130a-11eb-8bf7-c15dee072f02.png)

    - pay의 지불, point 의 차감은 그와 연결된 command 와 event 들에 의하여 트랜잭션이 유지되어야 하는 단위로 그들 끼리 묶어줌

### 바운디드 컨텍스트로 묶기

![image](https://user-images.githubusercontent.com/70302884/96575104-4bab0800-130b-11eb-9d9c-dde7958dd0db.png)

    - 도메인 서열 분리
        - Core Domain:  checkIn, Area : 없어서는 안될 핵심 서비스이며, 연견 Up-time SLA 수준을 99.999% 목표, 배포주기는 app 의 경우 1주일 1회 미만, store 의 경우 1개월 1회 미만
        - Supporting Domain:   point, pay : 경쟁력을 내기위한 서비스이며, SLA 수준은 연간 60% 이상 uptime 목표, 배포주기는 각 팀의 자율이나 표준 스프린트 주기가 1주일 이므로 1주일 1회 이상을 기준으로 함.
        - General Domain:   지도서비스 : Google Maps 등 3rd Party 외부 서비스를 사용하는 것이 경쟁력이 높음 (핑크색으로 이후 전환할 예정)

### 폴리시 부착 (괄호는 수행주체, 폴리시 부착을 둘째단계에서 해놔도 상관 없음. 전체 연계가 초기에 드러남)

![image](https://user-images.githubusercontent.com/70302884/96575590-e73c7880-130b-11eb-87d5-ab72fdaaa809.png)

### 폴리시의 이동과 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Resp)

![image](https://user-images.githubusercontent.com/70302884/96575916-5ade8580-130c-11eb-8b5c-62c791ef3c67.png)

### 완성된 1차 모형

![image](https://user-images.githubusercontent.com/61965188/96833654-c72bc700-147b-11eb-98c8-69bea84b740d.jpg)

    - Area, View Model 추가

### 1차 완성본에 대한 기능적/비기능적 요구사항을 커버하는지 검증

![image](https://user-images.githubusercontent.com/61965188/96833674-cc891180-147b-11eb-9a85-a13a112ad178.jpg)

    - 고객이 흡연구역에서 체크인 한다. (ok)
    - 체크인하면 흡연구역이 사용중으로 변경된다. (ok)
    - 고객이 흡연구역에서 체크아웃 한다. (ok)
    - 체크아웃 되면 흡연구역이 비었음으로 변경하고, 체크인 정보의 상태를 성공으로 변경다. (ok)



### 비기능 요구사항에 대한 검증

![image](https://user-images.githubusercontent.com/61965188/96833674-cc891180-147b-11eb-9a85-a13a112ad178.jpg)

    - 마이크로 서비스를 넘나드는 시나리오에 대한 트랜잭션 처리
    - 포인트 결제 처리: 포인트 차감이 완료되지 않으면 결제가 이루어지지 않아야 함.(ACID 트랜잭션 적용) 포인트 결제는 Request-Response 방식 처리
    - 체크아웃 및 포인트 적립 처리:  checkIn 에서 point 마이크로서비스로 포인트 적립 요청이 전달되는 과정에 있어서 point 마이크로 서비스가 별도의 배포주기를 가지기 때문에 Eventual Consistency 방식으로 트랜잭션 처리함.
    - 나머지 모든 inter-microservice 트랜잭션: 포인트 적립 상태 등 모든 이벤트에 대해 데이터 일관성의 시점이 크리티컬하지 않은 모든 경우가 대부분이라 판단, Eventual Consistency 를 기본으로 채택함.


## 헥사고날 아키텍처 다이어그램 도출
    
![image](https://user-images.githubusercontent.com/70302884/96579381-b9f2c900-1311-11eb-8094-72967631ee76.png)


    - Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
    - 호출관계에서 PubSub 과 Req/Resp 를 구분함
    - 서브 도메인과 바운디드 컨텍스트의 분리:  각 팀의 KPI 별로 아래와 같이 관심 구현 스토리를 나눠가짐


# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트와 JPA으로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n 이다)

```
cd skccuser19-checkIn
mvn spring-boot:run

cd skccuser19-point
mvn spring-boot:run 

cd skccuser19-pay
mvn spring-boot:run  

cd skccuser19-area
mvn spring-boot:run  

```

## DDD 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 point 마이크로 서비스). 이때 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용하려고 노력했다. 하지만, 일부 구현에 있어서 영문이 아닌 경우는 실행이 불가능한 경우가 있기 때문에 계속 사용할 방법은 아닌것 같다. (Maven pom.xml, Kafka의 topic id, FeignClient 의 서비스 id 등은 한글로 식별자를 사용하는 경우 오류가 발생하는 것을 확인하였다)

```
package nosmokeaaa;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Earn_table")
public class Earn {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long checkInId;
    private Long point;
    private String status;

    @PostPersist
    public void onPostPersist(){
        Earned earned = new Earned();
        BeanUtils.copyProperties(this, earned);
        earned.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getCheckInId() {
        return checkInId;
    }

    public void setCheckInId(Long checkInId) {
        this.checkInId = checkInId;
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

```
- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```
package nosmoke;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface EarnRepository extends PagingAndSortingRepository<Earn, Long>{

}
```
- 적용 후 REST API 의 테스트
```
# checkIn 서비스의 체크인 처리
http http://localhost:8081/checkIns smokingAreaId="453!#FEQ"

# checkIn 서비스의 체크아웃 후 point 서비스의 적립 처리
http PUT http://localhost:8081/checkIns/1 point=100

# 적립 상태 확인
http http://localhost:8081/checkIns/1
http http://localhost:8082/earns/1

```


## Saga

checkIn 서비스에서 체크아웃 후 area 서비스에서 흡연구역 상태 변경을 Eventual Consistency 방식으로 처리했기 때문에 area 서비스에서 상태변경 처리가 완료되면 checkIn 서비스의 상태를 "SUCCESS"로 업데이트 시켜주는 SAGA 패턴을 적용하였다. 이 기능 역시 비동기 방식으로 checkIn의 PolicyHandler에 처리되도록 구현하였다.

```
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

```


## 폴리글랏
 micro serviced 별로 적합한 DB를 적용하였습니다.

![image](https://user-images.githubusercontent.com/61965188/96833506-8469ef00-147b-11eb-8975-f250309b113c.jpg)
![image](https://user-images.githubusercontent.com/61965188/96833512-892ea300-147b-11eb-9fdb-1c54bec85f44.jpg)


## 유비쿼터스 랭기지
 일반적으로 이해 가능한 영어단어를 사용하였습니다. ( checkin, area, point 등) 

## CQRS

고객관리 서비스(customercenter)의 시나리오인 체크인/아웃에 따른 흡연구역상태(USE/VACANT) 내역 확인을 CQRS로 구현하었고 코드는 다음과 같다:
```
package nosmokeaaa;

import nosmokeaaa.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class AreaListViewHandler {


    @Autowired
    private AreaListRepository areaListRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOccupied_then_CREATE_1 (@Payload Occupied occupied) {
        try {
            if (occupied.isMe()) {
                // view 객체 생성
                AreaList areaList = new AreaList();
                // view 객체에 이벤트의 Value 를 set 함
                areaList.setId(occupied.getAreaId());
                areaList.setStatus(occupied.getStatus());
                // view 레파지 토리에 save
                areaListRepository.save(areaList);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenEmptied_then_UPDATE_1(@Payload Emptied emptied) {
        try {
            if (emptied.isMe())
            {
                // view 객체 조회
                Optional<AreaList> areaListOptional = areaListRepository.findById(emptied.getId());
                if( areaListOptional.isPresent())
                {
                    AreaList areaList = areaListOptional.get();
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    areaList.setStatus(emptied.getStatus());
                    // view 레파지 토리에 save
                    areaListRepository.save(areaList);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
```

## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 포인트결제(pay)->포인트차감(point) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현

```
@FeignClient(name="point", url="${api.point.url}")
public interface DeductService {

    @RequestMapping(method= RequestMethod.GET, path="/deducts")
    public void pay(@RequestBody Deduct deduct);

}
```

- 주문을 받은 직후(@PostPersist) 결제를 요청하도록 처리

```
    @PostPersist
    public void onPostPersist(){
        Paid paid = new Paid();
        BeanUtils.copyProperties(this, paid);
        paid.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        nosmoke.external.Deduct deduct = new nosmoke.external.Deduct();
        // mappings goes here
        deduct.setPoint(this.getPoint());
        deduct.setPayId(this.getId());
        PayApplication.applicationContext.getBean(nosmoke.external.DeductService.class)
            .pay(deduct);
    }
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, point 시스템이 장애가 나면 주문도 못받는다는 것을 확인:


```
# point 서비스를 잠시 내려놓음 (spring-boot:stop)

#포인트결제 처리
http http://localhost:8083/pays point=100    #Fail

#point 서비스 재기동
cd point
mvn spring-boot:run

#주문처리
http http://localhost:8083/pays point=100   #Success
```

- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, 폴백 처리는 운영단계에서 설명한다.)




## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트


체크아웃이 이루어진 후에 point 서비스로 이를 알려주는 행위는 동기식이 아니라 비 동기식으로 처리하여 point 서비스의 처리를 위하여 체크인/아웃이 블로킹 되지 않아도록 처리한다.

 
```
    @PostPersist
    public void onPostPersist(){
        CheckIned checkIned = new CheckIned();
        BeanUtils.copyProperties(this, checkIned);
        checkIned.publishAfterCommit();


    }

    @PostUpdate
    public void onPostUpdate(){
        if(this.getStatus()==null){
            CheckOuted checkOuted = new CheckOuted();
            BeanUtils.copyProperties(this, checkOuted);
            checkOuted.publishAfterCommit();
        }


    }
```

- point 서비스에서는 체크아웃 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:

```
    @Autowired
    EarnRepository EarnRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCheckOuted_CheckOut(@Payload CheckOuted checkOuted){

        if(checkOuted.isMe()){

            Earn earn = new Earn();
            earn.setCheckInId(checkOuted.getId());
            earn.setPoint(checkOuted.getPoint());
            //earn.setStatus(checkOuted.getStatus());

            EarnRepository.save(earn);
        }
    }

```

checkIn 시스템은 포인트적립/사용과 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, point 서비스가 유지보수로 인해 잠시 내려간 상태라도 주문을 받는데 문제가 없다.

```
# point 서비스 를 잠시 내려놓음 (ctrl+c)

#체크인/아웃 처리
http localhost:8081/checkIns smokingAreaId=1   #Success
http PUT localhost:8081/checkIns point=200     #Success

#체크인 포인트 적립 상태 확인
http localhost:8081/checkIns/1     # 포인트 적립 상태 안바뀜 확인

#point 서비스 기동
cd point
mvn spring-boot:run

#체크인 포인트 적립 상태 확인
http localhost:8081/checkIns/1     # 모든 체크인 상태가 "EARNED"로 확인
```

## Gateway를 통한 진입점 통일

gateway를 통해 checkIn, point, pay, customercenter 등 모든 서비스에 진입할 수 있도록 yaml 파일에 적용

```
spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: checkIn
          uri: http://checkIn:8080
          predicates:
            - Path=/checkIns/** 
        - id: point
          uri: http://point:8080
          predicates:
            - Path=/deducts/**,/earns/**
        - id: pay
          uri: http://pay:8080
          predicates:
            - Path=/pays/** 
        - id: customercenter
          uri: http://customercenter:8080
          predicates:
            - Path= /mypages/**,/areaLists/**
        - id: area
          uri: http://area:8080
          predicates:
            - Path=/areas/** 
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080
```

gateway를 통한 진입점 통일 테스트

```
http POST http://checkIn:8080/checkIns smokingAreaId=5001  #checkIn 서비스에 직접 진입
http POST http://gateway:8080/checkIns smokingAreaId=5001  #checkIn 서비스에 gateway를 통해 진입(결과값 같음)
```

# 클라우드 환경에서의 http 명령어를 통한 동작 확인
1. checkin (동기방식)
![image](https://user-images.githubusercontent.com/61965188/96836877-8e422100-1480-11eb-8d78-121af611b07e.jpg)

2. checkin 에 따른 area 생성확인 (상태 :USE)
![image](https://user-images.githubusercontent.com/61965188/96836893-939f6b80-1480-11eb-8ea1-75513f3c0b5d.jpg)

3. CQRS 에 arealist 내역 확인
![image](https://user-images.githubusercontent.com/61965188/96836902-97cb8900-1480-11eb-9def-861db454d8e4.jpg)

4. checkouted 수행(비동기 방식, SAGA) 
![image](https://user-images.githubusercontent.com/61965188/96836921-9c903d00-1480-11eb-9a1e-6fae80c0fa56.jpg)

5. checkout 에 따른 area 변경확인 (상태 :VACANT)
![image](https://user-images.githubusercontent.com/61965188/96836952-a619a500-1480-11eb-9e73-d58998e73574.jpg)

6. checkout 에 따른 checkin 상태 변경확인(SAGA) (상태 :SUCCESS) 
![image](https://user-images.githubusercontent.com/61965188/96836977-ab76ef80-1480-11eb-98c3-12213f5d8521.jpg)




# 운영

### 오토스케일 아웃

* 오토스케일 설정을 완료하여 아래에서 설정된 것을 확인

![image](https://user-images.githubusercontent.com/61965188/96833527-921f7480-147b-11eb-8378-14eaf7c46d92.jpg)


## Istio 적용

* Istio 모니터링 툴을 설치하고 istio를 enable 설정한 다음 deploy를 배포하여 각 pod들이side-car pattern으로 생성되어 있는 것을 확인

![image](https://user-images.githubusercontent.com/61965188/96833449-6b613e00-147b-11eb-8b0b-e445aa03324b.jpg)


## Kiali

* Monitoring Server - Kiali를 적용하였다.  Graph 형식으로 보여지는 모니터링 결과를 확인

![image](https://user-images.githubusercontent.com/61965188/96833462-71571f00-147b-11eb-8568-ac6b955fe646.jpg)


## Jaeger

* Tracing Server - Jaeger를 적용하였다.  gateway로 동기 호출된 결과에 대해 Trace 결과를 보여주고 있음을 확인 

![image](https://user-images.githubusercontent.com/61965188/96833476-76b46980-147b-11eb-8d9f-c96a62502bca.jpg)


## Circuit Breaker

* DestinationRule 설정 후 siege 를 통해 부하를 준다

![image](https://user-images.githubusercontent.com/61965188/96833489-7caa4a80-147b-11eb-93d7-c62453f16aee.jpg)
![image](https://user-images.githubusercontent.com/61965188/96833567-a1062700-147b-11eb-83b1-353aeb2ff721.jpg)


