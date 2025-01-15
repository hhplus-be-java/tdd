package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointService {
    private static final Logger log = LoggerFactory.getLogger(PointService.class);
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    /**
     * 특정 유저의 포인트를 충전하는 메소드
     *
     * @param id     (userId)
     * @param amount (충전하는 포인트 금액)
     */
    public UserPoint charge(long id, long amount) {

        final long MAX_BALANCE = 5_000_000;

        UserPoint userPoint = userPointTable.selectById(id);
        long newBalance = userPoint.point() + amount;

        if (newBalance > MAX_BALANCE) {
            log.error("잔고는 최대 {}을 초과할 수 없습니다. 현재 잔액은 {}입니다.", MAX_BALANCE, newBalance);

            throw new IllegalArgumentException("잔고는 최대 " + MAX_BALANCE + "을 초과할 수 없습니다.");
        }

        userPoint = userPointTable.insertOrUpdate(id, newBalance);
        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());

        log.info("{}유저가 {}포인트를 충전하였습니다.", id, amount);

        return userPoint;
    }

    /**
     * 특정 유저의 포인트를 조회하는 메소드
     *
     * @param id (userId)
     */
    public UserPoint point(long id) {
        log.info("{}유저의 포인트를 조회합니다.", id);

        return userPointTable.selectById(id);
    }

    /**
     * 특정 유저의 포인트를 사용하는 메소드
     *
     * @param id     (userId)
     * @param amount (사용하는 포인트 금액)
     */
    public UserPoint use(long id, long amount) {
        final long MIN_USE_AMOUNT = 1_000;

        UserPoint userPoint = userPointTable.selectById(id);

        if (amount < MIN_USE_AMOUNT) {
            log.error("포인트는 최소 {} 이상 사용해야 합니다.", MIN_USE_AMOUNT);

            throw new IllegalArgumentException("포인트는 최소 " + MIN_USE_AMOUNT + " 이상 사용해야 합니다.");
        }

        if (userPoint.point() < amount) {
            log.error("잔고가 부족합니다. 현재 잔고는 {} 입니다.", userPoint.point());

            throw new IllegalArgumentException("잔고가 부족합니다. 현재 잔고는 " + userPoint.point() + "입니다.");
        }

        UserPoint updatedUserPoint = userPointTable.insertOrUpdate(id, userPoint.point() - amount);
        pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());

        log.info("{}유저가 {}포인트를 사용합니다.", id, amount);

        return updatedUserPoint;
    }

    /**
     * 특정 유저의 포인트 충전/이용 내역을 조회하는 메소드
     *
     * @param id (userId)
     */
    public List<PointHistory> history(long id) {
        log.info("{}유저의 포인트 충전/이용 전체 내역을 조회합니다.", id);

        return pointHistoryTable.selectAllByUserId(id);
    }
}
