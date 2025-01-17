package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;
    @Mock
    private PointHistoryTable pointHistoryTable;
    @InjectMocks
    private PointService pointService;

    @Nested
    @DisplayName("포인트 충전 테스트")
    class charge {

        @Test
        @DisplayName("포인트 충전 성공")
        void 포인트_충전_성공() {
            // given
            long userId = 1L;
            long amount = 1_000L;
            long balance = 0L;
            UserPoint existingUserPoint = new UserPoint(userId, balance, System.currentTimeMillis());
            UserPoint expectedUserPoint = new UserPoint(userId, amount, System.currentTimeMillis());

            when(userPointTable.selectById(userId)).thenReturn(existingUserPoint);
            when(userPointTable.insertOrUpdate(userId, amount)).thenReturn(expectedUserPoint);

            // when
            UserPoint userPoint = pointService.charge(userId, amount);

            // then
            verify(userPointTable).selectById(userId);
            verify(userPointTable).insertOrUpdate(userId, balance + amount);
            verify(pointHistoryTable).insert(eq(userId), eq(amount), eq(TransactionType.CHARGE), anyLong());
            assertThat(userPoint).isEqualTo(expectedUserPoint);
        }

        @Test
        @DisplayName("실패 - 최대 잔고 초과")
        void 최대_잔고_초과() {
            // given
            long userId = 1L;
            long balance = 4_990_000L;
            long amount = 20_000L;
            UserPoint existingUserPoint = new UserPoint(userId, balance, System.currentTimeMillis());

            when(userPointTable.selectById(userId)).thenReturn(existingUserPoint);

            // when
            IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> pointService.charge(userId, amount)
            );

            // then
            assertThat(exception.getMessage()).isEqualTo("잔고는 최대 5000000을 초과할 수 없습니다.");
            verify(userPointTable).selectById(userId);
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
            verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("포인트 조회 테스트")
    class point {

        @Test
        @DisplayName("포인트 조회 성공")
        void 포인트_조회_성공() {
            // given
            long userId = 1L;
            long amount = 1_000L;
            UserPoint expectedUserPoint = new UserPoint(userId, amount, System.currentTimeMillis());
            when(userPointTable.selectById(userId)).thenReturn(expectedUserPoint);

            // when
            UserPoint userPoint = pointService.point(userId);

            // then
            verify(userPointTable).selectById(userId);
            assertThat(userPoint).isEqualTo(expectedUserPoint);
        }
    }

    @Nested
    @DisplayName("포인트 사용 테스트")
    class use {

        @Test
        @DisplayName("포인트 사용 성공")
        void 포인트_사용_성공() {
            // given
            long userId = 1L;
            long amount = 1_000L;
            long existingPoint = 10_000L;

            // when
            when(userPointTable.selectById(userId)).thenReturn(new UserPoint(userId, existingPoint, System.currentTimeMillis()));
            when(userPointTable.insertOrUpdate(userId, existingPoint - amount))
                .thenReturn(new UserPoint(userId, existingPoint - amount, System.currentTimeMillis()));

            // then
            UserPoint result = pointService.use(userId, amount);
            Assertions.assertEquals(existingPoint - amount, result.point());
            verify(userPointTable).selectById(userId);
            verify(userPointTable).insertOrUpdate(userId, existingPoint - amount);
            verify(pointHistoryTable).insert(eq(userId), eq(amount), eq(TransactionType.USE), anyLong());
        }

        @Test
        @DisplayName("실패 - 잔고 부족")
        void 잔고_부족() {
            //given
            long userId = 1L;
            long balance = 500L;
            long amount = 1_000L;
            UserPoint existingUserPoint = new UserPoint(userId, balance, System.currentTimeMillis());

            when(userPointTable.selectById(userId)).thenReturn(existingUserPoint);

            // when
            IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> pointService.use(userId, amount)
            );

            // then
            assertThat(exception.getMessage()).isEqualTo("잔고가 부족합니다. 현재 잔고는 " + balance + "입니다.");
            verify(userPointTable).selectById(userId);
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
            verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        }

        @Test
        @DisplayName("실패 - 포인트 최소 사용 금액 미달")
        void 포인트_최소_사용_금액_미달() {
            // given
            long userId = 1L;
            long balance = 10_000L;
            long amount = 500L;
            UserPoint existingUserPoint = new UserPoint(userId, balance, System.currentTimeMillis());

            when(userPointTable.selectById(userId)).thenReturn(existingUserPoint);

            // when
            IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> pointService.use(userId, amount)
            );

            // then
            assertThat(exception.getMessage()).isEqualTo("포인트는 최소 1000 이상 사용해야 합니다.");
            verify(userPointTable).selectById(userId);
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
            verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("포인트 충전/이용 내역 조회 테스트")
    class history {

        @Test
        @DisplayName("포인트 충전/이용 내역 조회 성공")
        void 포인트_충전_이용_내역_조회_성공() {
            // given
            long userId = 1L;
            List<PointHistory> mockHistories = List.of(
                new PointHistory(1L, userId, 10_000L, TransactionType.CHARGE, 1_000_000L),
                new PointHistory(2L, userId, 5_000L, TransactionType.USE, 1_000_500L)
            );
            when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(mockHistories);

            // when
            List<PointHistory> histories = pointService.history(userId);

            // then
            assertThat(histories).hasSize(2);
            assertThat(histories.get(0).amount()).isEqualTo(10_000L);
            assertThat(histories.get(1).amount()).isEqualTo(5_000L);
            verify(pointHistoryTable).selectAllByUserId(userId);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class concurrency {

        @Test
        @DisplayName("동시 충전 테스트")
        void 동시_충전_테스트() throws InterruptedException {
            // given
            long userId = 1L;
            long balance = 0L;
            long amount = 1_000L;
            int threadCount = 100;
            UserPoint initialUserPoint = new UserPoint(userId, balance, System.currentTimeMillis());

            when(userPointTable.selectById(userId)).thenReturn(initialUserPoint);
            when(userPointTable.insertOrUpdate(eq(userId), anyLong()))
                .thenAnswer(invocation -> {
                    long newBalance = invocation.getArgument(1);
                    return new UserPoint(userId, newBalance, System.currentTimeMillis());
                });

            // 동시 요청 실행
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        pointService.charge(userId, amount);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            verify(userPointTable, times(threadCount)).insertOrUpdate(eq(userId), anyLong());
            verify(pointHistoryTable, times(threadCount)).insert(eq(userId), eq(amount), eq(TransactionType.CHARGE), anyLong());
        }

        @Test
        @DisplayName("동시 사용 테스트")
        void 동시_사용_테스트() throws InterruptedException {
            // given
            long userId = 1L;
            long balance = 100_000L;
            long amount = 1_000L;
            int threadCount = 50;
            UserPoint initialUserPoint = new UserPoint(userId, balance, System.currentTimeMillis());

            when(userPointTable.selectById(userId)).thenReturn(initialUserPoint);
            when(userPointTable.insertOrUpdate(eq(userId), anyLong()))
                .thenAnswer(invocation -> {
                    long newBalance = invocation.getArgument(1);
                    return new UserPoint(userId, newBalance, System.currentTimeMillis());
                });

            // 동시 요청 실행
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        pointService.use(userId, amount);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            verify(userPointTable, times(threadCount)).insertOrUpdate(eq(userId), anyLong());
            verify(pointHistoryTable, times(threadCount)).insert(eq(userId), eq(amount), eq(TransactionType.USE), anyLong());
        }
    }
}