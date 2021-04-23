package io.seata.sample.service;

import java.util.Map;

import javax.annotation.PostConstruct;

import io.seata.sample.feign.OrderFeignClient;
import io.seata.sample.feign.StorageFeignClient;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * @author jimin.jm@alibaba-inc.com
 * @date 2019/06/14
 */
@Service
public class BusinessService {

    @Autowired
    private StorageFeignClient storageFeignClient;
    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 减库存，下订单
     *
     * @param userId
     * @param commodityCode
     * @param orderCount
     */
    // 全局事务发起者: 账户初始金额10000, 库存200, 订单列表为空
    @GlobalTransactional
    public void purchase(String userId, String commodityCode, int orderCount) {
        // 提交测试时, 每次扣减30个库存; 异常测试时, 每次扣减99999个库存(库存不足)
        storageFeignClient.deduct(commodityCode, orderCount);

        // 每次扣减30 * 100金额, 生成30个的订单; 异常测试时, 每次扣减30 * 90000(余额不足)
        orderFeignClient.create(userId, commodityCode, orderCount);

        // 校验数据合法性: 库存、余额不能为负数
        if (!validData()) {
            throw new RuntimeException("账户或库存不足,执行回滚");
        }
    }

    @PostConstruct
    public void initData() {
        jdbcTemplate.update("delete from account_tbl");
        jdbcTemplate.update("delete from order_tbl");
        jdbcTemplate.update("delete from storage_tbl");
        jdbcTemplate.update("insert into account_tbl(user_id,money) values('U100000','10000') ");
        jdbcTemplate.update("insert into storage_tbl(commodity_code,count) values('C100000','200') ");
    }

    public boolean validData() {
        Map accountMap = jdbcTemplate.queryForMap("select * from account_tbl where user_id='U100000'");
        if (Integer.parseInt(accountMap.get("money").toString()) < 0) {
            return false;
        }
        Map storageMap = jdbcTemplate.queryForMap("select * from storage_tbl where commodity_code='C100000'");
        if (Integer.parseInt(storageMap.get("count").toString()) < 0) {
            return false;
        }
        return true;
    }
}
