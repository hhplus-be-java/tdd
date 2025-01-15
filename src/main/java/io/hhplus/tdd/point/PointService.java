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
        UserPoint userPoint = userPointTable.insertOrUpdate(id, amount);
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
        UserPoint userPoint = userPointTable.selectById(id);
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
