# 동시성 제어 방식에 대한 분석 및 보고서

## 동시성 제어 방식

### 1. `synchronized` 블록 활용
`PointService`에서는 사용자별로 동시성을 제어하기 위해 고정된 `lock` 객체와 `synchronized` 블록을 사용합니다. 
이 방식은 단일 객체를 기준으로 동시성을 제어하여 특정 시점에서 하나의 스레드만 코드 블록에 접근할 수 있도록 보장합니다.

#### 코드 스니펫
```java
synchronized (lock) {
UserPoint userPoint = userPointTable.selectById(id);
long balance = userPoint.point() + amount;

    if (balance > MAX_BALANCE) {
        throw new IllegalArgumentException("잔고는 최대 " + MAX_BALANCE + "을 초과할 수 없습니다.");
    }

userPoint = userPointTable.insertOrUpdate(id, balance);
    pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return userPoint;
}
```

#### 2. 동시성 제어 동작 방식
- `lock` 객체를 기준으로 모든 충전 및 사용 요청이 순차적으로 처리됩니다.
- 동일한 `lock` 객체를 사용하므로, 하나의 요청이 처리되는 동안 다른 요청은 대기하게 됩니다.


---

## 동시성 제어 장점
1. **간단한 구현**:
    - 고정된 `lock` 객체를 사용하여 간단한 방식으로 동시성을 제어합니다.
    - 코드가 직관적이고 유지보수가 용이합니다.

2. **데이터 무결성 보장**:
    - `synchronized` 블록을 통해 동시에 실행되는 요청이 데이터에 손상을 입히지 않도록 보장합니다.

3. **성능의 수용 가능성**:
    - 단일 사용자에 대해 동시 요청이 적은 경우 충분히 효율적입니다.

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
1. **전체 동시성 차단**:
    - 단일 `lock` 객체를 사용하므로, 모든 사용자 요청이 하나의 동기화 블록에서 처리됩니다.
    - 여러 사용자의 요청이 들어와도 병렬로 처리되지 않고 순차적으로 처리되므로 성능 저하 가능성이 있습니다.
2. **확장성 부족**:
    - 사용자 수가 많아지거나 동시 요청이 증가할 경우 처리 속도가 느려질 수 있습니다.

### 개선 방안
1. **사용자별 Lock 적용**:
    - 사용자별 Lock을 관리하여 각 사용자에 대해 독립적으로 동기화를 적용합니다.
    - 이를 통해 다중 사용자 요청이 병렬로 처리될 수 있도록 개선할 수 있습니다.

2. **데이터베이스 트랜잭션 활용**:
    - 동시성 문제를 애플리케이션 레벨에서 제어하지 않고 데이터베이스 트랜잭션과 Lock을 활용하여 처리합니다.

---

### 1. 사용자별 Lock 흐름
<br> 각 사용자 ID에 대해 Lock이 생성되고 요청 처리 후 해제되는 과정 </br>
<img width="809" alt="Image" src="https://github.com/user-attachments/assets/b5cb787f-4f28-41eb-9a65-33fc86bd2eb9" />

### 2. 충전/사용 요청의 병렬 처리
<br> 사용자 A와 사용자 B의 요청이 각각 병렬로 처리 </br>
<img width="544" alt="Image" src="https://github.com/user-attachments/assets/3c3e11e8-f688-40d3-bd0d-ee815c1c8848" />


## 결론
`PointService`는 `synchronized` 블록을 사용하여 동시성을 간단하게 제어하면서 데이터 무결성과 일관성을 보장합니다.
그러나 단일 `lock` 객체를 사용하는 방식은 전체 처리량에 제약을 가져올 수 있습니다.
사용자별 Lock 관리와 같은 개선 방안을 도입하면 성능과 확장성을 향상시킬 수 있습니다.