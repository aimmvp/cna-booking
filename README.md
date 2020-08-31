# 주제 - 회의실 시스템

회의실 예약, 컨펌 관리 시스템 입니다.

# 구현 Repository

총 4개
1. https://github.com/aimmvp/cna-booking
2. https://github.com/aimmvp/cna-confirm
3. https://github.com/aimmvp/cna-notification
4. https://github.com/aimmvp/cna-gateway

# 서비스 시나리오

## 기능적 요구사항

1. 사용자가 회의실을 예약한다.(bookingCreate)
2. 사용자는 회의실 예약을 취소 할 수 있다.(bookingCancel)
3. 회의실을 예약하면 관리자에게 승인요청이 간다.
4. 관리자는 승인을 할 수 있다.(confirmComplete)
5. 관리자는 승인 거절 할 수 있다.(confirmDeny)
6. 관리자가 승인 거절하면 예약취소한다.(bookingCancel)
7. 예약취소하면 예약정보는 삭제한다. 
8. 에약/승인 상태가 바뀔때마다 이메일로 알림을 준다.
9. 예약이 취소(bookingCancelled) 되면 컴펌 내역이 삭제 된다. (confirmDelete)

## 비기능적 요구사항
1. 트랜잭션
  - 승인거절(confirmDenied) 되었을 경우 예약을 취소한다.(Sync 호출)
  
2. 장애격리
  - 알림기능이 취소되더라도 예약과 승인 기능은 가능하다.
  - Circuit Breaker, fallback
  
3. 성능
  - 예약/승인 상태는 예약목록 시스템에서 확인 가능하다.(CQRS)
  - 예약/승인 상태가 변경될때 이메일로 알림을 줄 수 있다.(Event Driven)
  
# 분석 설계
* 이벤트스토밍 결과: http://www.msaez.io/#/storming/mOaNWpsERuRRDFTdm37r55hNZTm1/mine/595fb092dd58662b3447b8cc4f33f1e5/-MG1NAFxiCO7t8IK7chh

## 이벤트 도출

![이벤트 스토밍](https://user-images.githubusercontent.com/67448171/91698324-7e5b3e80-ebad-11ea-8b16-48120bf8e92a.jpg)

## 기능적 요구사항을 커버하는지 검증
![기능적_비기능적 요구사항을 커버하는지 검증](https://user-images.githubusercontent.com/67448171/91702344-903fe000-ebb3-11ea-9a46-f0b949d454e3.JPG)

<기능적 요구사항 검증>

1. 사용자가 회의실을 예약한다.(bookingCreate) ok
2. 사용자는 회의실 예약을 취소 할 수 있다.(bookingCancel) ok
3. 회의실을 예약하면 관리자에게 승인요청이 간다. ok
4. 관리자는 승인을 할 수 있다.(confirmComplete) ok
5. 관리자는 승인 거절 할 수 있다.(confirmDeny) ok
6. 관리자가 승인 거절하면 예약취소한다.(bookingCancel) ok
7. 예약취소하면 예약정보는 삭제한다.  ok
8. 에약/승인 상태가 바뀔때마다 이메일로 알림을 준다.  ok
9. 예약이 취소(bookingCancelled) 되면 컴펌 내역이 삭제 된다. (confirmDelete)  ok

## 비 기능적 요구사항을 커버하는지 검증
1. 트랜잭션
  - 승인거절(confirmDenied) 되었을 경우 예약을 취소한다.(Sync 호출)  ok
  
2. 장애격리
  - 알림기능이 취소되더라도 예약과 승인 기능은 가능하다.  ok
  - Circuit Breaker, fallback   ok
  
3. 성능
  - 예약/승인 상태는 예약목록 시스템에서 확인 가능하다.(CQRS)   ok
  - 예약/승인 상태가 변경될때 이메일로 알림을 줄 수 있다.(Event Driven)   ok
  
## 헥사고날 아키텍처 다이어그램 도출  
![핵사고날](https://user-images.githubusercontent.com/67448171/91702775-2f64d780-ebb4-11ea-9a18-5ce245db3691.jpg)

- Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
- 호출관계에서 PubSub 과 Req/Resp 를 구분함
- 서브 도메인과 바운디드 컨텍스트의 분리:  각 팀의 KPI 별로 아래와 같이 관심 구현 스토리를 나눠가짐

# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현함. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n 이다)
booking/  confirm/  gateway/  notification/  view/

```
cd booking
mvn spring-boot:run

cd confirm
mvn spring-boot:run 

cd gateway
mvn spring-boot:run  

cd notification
mvn spring-boot:run

cd view
mvn spring-boot:run
```

## DDD 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언. 이때 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용함.

```
package ohcna;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ohcna.config.kafka.KafkaProcessor;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

@Entity
@Table(name="Booking_table")
public class Booking {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long roomId;
    private String useStartDtm;
    private String useEndDtm;
    private String bookingUserId;

    @PostPersist
    public void onPostPersist(){
        BookingCreated bookingCreated = new BookingCreated();
        BeanUtils.copyProperties(this, bookingCreated);
        bookingCreated.publishAfterCommit();
    }

    @PostUpdate
    public void onPostUpdate(){
        BookingChanged bookingChanged = new BookingChanged();
        BeanUtils.copyProperties(this, bookingChanged);
        bookingChanged.publishAfterCommit();
    }

    @PreRemove
    public void onPreRemove(){
        BookingCancelled bookingCancelled = new BookingCancelled();
        BeanUtils.copyProperties(this, bookingCancelled);
        bookingCancelled.publishAfterCommit();
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }
    public String getUseStartDtm() {
        return useStartDtm;
    }

    public void setUseStartDtm(String useStartDtm) {
        this.useStartDtm = useStartDtm;
    }
    public String getUseEndDtm() {
        return useEndDtm;
    }

    public void setUseEndDtm(String useEndDtm) {
        this.useEndDtm = useEndDtm;
    }
    public String getBookingUserId() {
        return bookingUserId;
    }

    public void setBookingUserId(String bookingUserId) {
        this.bookingUserId = bookingUserId;
    }

}

```

- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다

```
package ohcna;
import org.springframework.data.repository.PagingAndSortingRepository;
public interface BookingRepository extends PagingAndSortingRepository<Booking, Long>{
}
```

- 적용 후 REST API 의 테스트
```
# booking 서비스의 회의실 예약처리
http POST http://52.231.116.117:8080/bookings roomID="111" useStartDtm="20200831183000" useEndDtm="20200901183000" userId="team5"

# booking 서비스의 회의실 예약 상태변경처리
http PUT http://52.231.116.117:8080/bookings/BookingChanged roomID="111"

# booking 서비스의 회의실 예약취소
http PUT http://52.231.116.117:8080/bookings/BookingCanceled/1

# confirm 서비스의 컨펌
http POST http://52.231.116.117:8080/confirms/1

# confirm 서비스의 컨펌 반려 처리
http POST http://52.231.116.117:8080/confirms/ConfirmDeleted/1

# 회의실 상태 확인
http://52.231.116.117:8080/bookingList

```

## 동기식 호출 과 비동기식 

분석단계에서의 조건 중 하나로 컨펌 반려(confirmDeny)->회의실 예약 취소(bookingCancel) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

```
# (app) pointSystemService.java

@FeignClient(name="booking", url="http://52.231.116.117:8080")
public interface PointSystemService {
    @RequestMapping(method= RequestMethod.POST, path="/booking", consumes = "application/json")
    public void usePoints(@RequestBody PointSystem pointSystem);
}
```

```
Res, PUB/SUB 등 개발 된거 설명하려면 
```

결과 : 회의실이 예약된 후, 예약이 완료되는 것과 회의실의 상태가 변경된 것을 bookingList에서확인 할 수 있다.

# 운영

## CI/CD 설정


각 구현체들은 각자의 source repository 에 구성되었고, 사용한 CI/CD 플랫폼은 azure를 사용하였으며, pipeline build script 는 각 프로젝트 폴더 이하에 buildspec.yml 에 포함되었다.

## pipeline 동작 결과

아래 이미지는 azure의 pipeline에 각각의 서비스들을 올려, 코드가 업데이트 될때마다 자동으로 빌드/배포 하도록 하였다.
```
Test 결과 넣어야 함
```

그 결과 kubernetes cluster에 아래와 같이 서비스가 올라가있는 것을 확인할 수 있다.

```
Test 결과 넣어야 함
```

또한, 기능들도 정상적으로 작동함을 알 수 있다.

**<이벤트 날리기>**

```
Test 결과 넣어야 함
```

**<동작 결과>**

```
Test 결과 넣어야 함
```

### 오토스케일 아웃


- 컨펌서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 10개까지 늘려준다:
- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다:

```
Test 결과 넣어야 함
```

- 워크로드를 2분 동안 걸어준 후 테스트 결과는 아래와 같다.

```
Test 결과 넣어야 함
```


## 무정지 재배포

Autoscaler설정과 Readiness 제거를 한뒤, 부하를 넣었다. 

이후 Readiness를 제거한 코드를 업데이트하여 새 버전으로 배포를 시작했다.

그 결과는 아래는 같다.

```
Test 결과 넣어야 함
```

다시 Readiness 설정을 넣고 부하를 넣었다.

그리고 새버전으로 배포한 뒤 그 결과는 아래와 같다.

```
Test 결과 넣어야 함
```
배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.
