# 동시성 제어 방식에 대한 분석 및 보고서

## 동시성 제어 방식

### 1. `synchronized` 블록 활용
`PointService`에서는 사용자별로 동시성을 제어하기 위해 `synchronized` 블록을 사용합니다. 해당 블록은 특정 사용자의 `id`를 키로 사용하여 동시성 문제를 예방합니다.

#### 코드 스니펫
```java
synchronized (getLock(id)) {
    // 충전 또는 사용 로직
}
```

### 2. `ConcurrentHashMap` 기반 동적 Lock 관리
- 동적 Lock 관리를 위해 `ConcurrentHashMap`을 사용하여 사용자 ID마다 별도의 Lock 객체를 생성 및 관리합니다.
- 기존의 정적 Lock 관리 방식보다 메모리 효율적이고, 유연한 동시성 제어가 가능합니다.

#### 코드 동작 방식
1. `getLock` 메서드를 호출하여 사용자 ID별로 Lock 객체를 가져옴.
2. `ConcurrentHashMap.computeIfAbsent`를 활용하여 Lock 객체를 동적으로 생성.

#### 코드 스니펫
```java
private final ConcurrentHashMap<Long, Object> lockMap = new ConcurrentHashMap<>();

private Object getLock(Long id) {
    return lockMap.computeIfAbsent(id, key -> new Object());
}
```

### 3. Lock 객체 정리
- 사용자가 더 이상 Lock 객체를 필요로 하지 않을 경우, `lockMap`에서 해당 Lock 객체를 제거하여 메모리 낭비를 방지합니다.

#### 코드 스니펫
```java
lockMap.remove(id);
```

---

## 동시성 제어 장점
1. **사용자별 동시성 제어**:
    - 특정 사용자 ID에 대한 요청만 동기화되므로, 다른 사용자 요청은 병렬로 처리 가능
    - 시스템의 처리량(Throughput)을 유지하며 데이터 무결성을 보장

2. **메모리 효율성**:
    - `ConcurrentHashMap`을 사용해 Lock 객체를 동적으로 생성하고 필요 시 제거함으로써 메모리 사용을 최소화

3. **유연한 확장성**:
    - 다수의 사용자 요청을 효율적으로 처리할 수 있도록 설계

---

## 테스트 및 검증
### 통합 테스트 시나리오
1. **동시 충전 요청**:
    - 동일한 사용자 ID에 대해 여러 스레드가 동시에 포인트를 충전
    - 결과: 최종 잔고가 모든 요청을 정확히 반영해야 함

2. **충전과 사용 요청 동시 발생**:
    - 충전과 사용 요청이 동시에 들어와도 잔고가 음수가 되지 않아야 함

3. **다중 사용자 요청**:
    - 여러 사용자 ID에 대해 병렬 요청을 처리했을 때, 처리 속도가 느려지지 않아야 함

#### 테스트 코드 예시
```java
@Test
void 동시_충전과_사용_테스트() throws InterruptedException {
    int threadCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
            try {
                pointService.charge(1L, 100L);
                pointService.use(1L, 50L);
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    UserPoint userPoint = pointService.point(1L);
    assertThat(userPoint.point()).isEqualTo(500L); // 예상 최종 잔고
}
```

---

## 문제점 및 개선 방안
### 문제점
1. **Lock 객체 관리 오버헤드**:
    - 동적 Lock 객체를 관리하면서 `ConcurrentHashMap`에서 Lock을 생성하거나 제거하는 데 부하가 발생할 가능성
2. **Lock 객체 누수 가능성**:
    - Lock 객체 제거 로직이 누락되거나 제대로 작동하지 않으면 메모리 누수가 발생할 가능성

### 개선 방안
1. **Lock 객체 만료 정책 추가**:
    - 일정 시간 동안 사용되지 않은 Lock 객체를 자동으로 제거하는 기능을 추가
    - 예: `ScheduledExecutorService`를 활용해 주기적으로 정리

2. **다른 동시성 제어 기술 적용**:
    - `StampedLock`이나 `ReadWriteLock`을 사용하여 읽기와 쓰기 작업의 성능을 최적화
    - 데이터베이스의 트랜잭션 락 활용으로 애플리케이션 레벨 락을 줄임.

---

### 1. 사용자별 Lock 흐름
<br> 각 사용자 ID에 대해 Lock이 생성되고 요청 처리 후 해제되는 과정 </br>
<img width="809" alt="Image" src="https://github.com/user-attachments/assets/b5cb787f-4f28-41eb-9a65-33fc86bd2eb9" />

### 2. 충전/사용 요청의 병렬 처리
<br> 사용자 A와 사용자 B의 요청이 각각 병렬로 처리 </br>
<img width="544" alt="Image" src="https://github.com/user-attachments/assets/3c3e11e8-f688-40d3-bd0d-ee815c1c8848" />


## 결론
`PointService`는 사용자별 동시성을 효과적으로 제어하기 위해 `synchronized` 블록과 `ConcurrentHashMap` 기반의 동적 Lock 관리를 활용하였습니다.
이 방식은 높은 처리량을 유지하면서도 데이터의 무결성과 일관성을 보장합니다.
추가적으로 Lock 객체의 효율적인 관리 및 테스트를 통해 운영 환경에서도 안정적으로 동작하도록 개선할 여지가 있습니다.
