package com.liuapi.redis.jedis;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * Redis 通过lua脚本可以实现CAS操作
 * 如何保证：
 * 1） 单线程执行
 * 2） lua脚本是原子性
 */
public class LuaTest {
    /**
     * 分布式锁场景下：进行解锁操作
     * CAS操作: 实现先检查当前客户端是否持有锁再删除锁的操作
     */
    @Test
    void testCompareAndThenDeleteLock() {
        try (Jedis jedis = new Jedis("localhost");) {
            String lua = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then" +
                    " return redis.call(\"del\",KEYS[1])" +
                    " else" +
                    " return 0" +
                    " end";
            int value = (int) jedis.eval(lua, Lists.newArrayList("user.lock"), Lists.newArrayList("1"));
            if (value == 1) {
                System.out.println("delete success!");
            } else {
                // value = 0
                System.out.println("delete fail!");
            }
        }
    }

    /**
     * 秒杀场景下：扣减商品库存
     * 备注
     * 由于lua脚本的限制，无法是使用  redis.call('get',KEYS[1]) >= ARGV[1] 来判断两个整数的大小，因为lua脚本会认为这两个参数都是字符串
     */
    @Test
    void secondKill() {
        int buyNumber = 1;
        try (Jedis jedis = new Jedis("localhost");) {
            String lua = "if (redis.call('get',KEYS[1])-ARGV[1])>=0 then" +
                    " return redis.call('decrby',KEYS[1],ARGV[1])" +
                    " else" +
                    " return -1" +
                    " end";
            long lastNumber = 0;
            do {
                lastNumber = (long) jedis.eval(lua, Lists.newArrayList("goods1.number"), Lists.newArrayList(buyNumber + ""));
                if (lastNumber >= 0) {
                    System.out.println("秒杀成功---> 秒杀数量：" + buyNumber + " 剩余数量：" + lastNumber);
                } else {
                    System.out.println("秒杀失败（无库存）");
                }
            } while (lastNumber >= buyNumber);
        }
        System.out.println("--------end--------");
    }

    @Test
    void testLua() {
        try (Jedis jedis = new Jedis("localhost");) {
            String luaStr = "return {KEYS[1],KEYS[2],ARGV[1],ARGV[2]}";
            Object result = jedis.eval(luaStr, Lists.newArrayList("userName", "age"), Lists.newArrayList("Jack", "20"));
            System.out.println(result);

            luaStr = "return redis.call('set','foo','bag')";
            result = jedis.eval(luaStr);
            System.out.println(result);

            luaStr = "return redis.call(\"get\",'foo')";
            result = jedis.eval(luaStr);
            System.out.println(result);

            luaStr = "return redis.call('set',KEYS[1],ARGV[1])";
            result = jedis.eval(luaStr, Lists.newArrayList("foo"), Lists.newArrayList("john"));
            System.out.println(result);

            luaStr = "return redis.call(\"get\",'foo')";
            result = jedis.eval(luaStr);
            System.out.println(result);
        }
    }

    @Test
    void testSet() {
        try (Jedis jedis = new Jedis("localhost");) {
            String lua = "return redis.call(\"set\",KEYS[1],ARGV[1])";
            Object value = jedis.eval(lua, Lists.newArrayList("user.lock"), Lists.newArrayList("1"));
            System.out.println(value);
        }
    }

    @Test
    void testLuaSha1() {
        String lua = "if (redis.call('get',KEYS[1])-ARGV[1])>=0 then" +
                " return redis.call('decrby',KEYS[1],ARGV[1])" +
                " else" +
                " return -1" +
                " end";
        try (Jedis jedis = new Jedis("localhost");) {
            String sha1 = jedis.scriptLoad(lua);
            System.out.println(sha1);

            int buyNumber = 10;
            long lastNumber = 0;
            lastNumber = (long) jedis.evalsha(sha1, Lists.newArrayList("goods1.number"), Lists.newArrayList(buyNumber + ""));
            System.out.println(lastNumber);
        }
    }

    @Test
    void executeCheckAndGetAndSet() {
        String lua = "if (redis.call('scard',KEYS[1]))>0 then" +
                " local b = redis.call('smembers',KEYS[1]);" +
                " redis.call('del',KEYS[1]);" +
                " return b" +
                " else" +
                " return nil" +
                " end";
        try (Jedis jedis = new Jedis("localhost");) {
            ArrayList<String> sets = (ArrayList<String>) jedis.eval(
                    lua, Lists.newArrayList("session:loop0"), Collections.emptyList());
            System.out.println(sets);
        }
    }

    @Test
    void existsAndCheckAndSet() {
        long time = System.currentTimeMillis();
        System.out.println(time);

        String lua = "if (redis.call('exists',KEYS[1]) ==0) then " +
                "redis.call('set',KEYS[1],ARGV[1]);" +
                "return 1;" +
                "end;" +
                "if (redis.call('get',KEYS[1])-ARGV[1] <0) then " +
                " redis.call('set',KEYS[1],ARGV[1]);" +
                " return 1;" +
                " else" +
                " return 0;" +
                " end;";
        try (Jedis jedis = new Jedis("localhost");) {
            long verify = (long) jedis.eval(
                    lua, Lists.newArrayList("ide:appcode2"), Lists.newArrayList(time + ""));
            System.out.println(verify);
        }
    }
}
