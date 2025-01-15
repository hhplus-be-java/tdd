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
            long amount = 1000L;
            UserPoint expectedUserPoint = new UserPoint(userId, amount, System.currentTimeMillis());
            when(userPointTable.insertOrUpdate(userId, amount)).thenReturn(expectedUserPoint);

            // when
            UserPoint userPoint = pointService.charge(userId, amount);

            // then
            verify(userPointTable).insertOrUpdate(userId, amount);
            verify(pointHistoryTable).insert(eq(userId), eq(amount), eq(TransactionType.CHARGE), anyLong());
            assertThat(userPoint).isEqualTo(expectedUserPoint);
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
            long amount = 1000L;
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
            long amount = 1000L;
            long existingPoint = 10000L;

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
                new PointHistory(1L, userId, 10000L, TransactionType.CHARGE, 1000000L),
                new PointHistory(2L, userId, 5000L, TransactionType.USE, 1000500L)
            );
            when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(mockHistories);

            // when
            List<PointHistory> histories = pointService.history(userId);

            // then
            assertThat(histories).hasSize(2);
            assertThat(histories.get(0).amount()).isEqualTo(10000L);
            assertThat(histories.get(1).amount()).isEqualTo(5000L);
            verify(pointHistoryTable).selectAllByUserId(userId);
        }
    }
}