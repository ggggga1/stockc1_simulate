package com.xc.utils.redis;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import redis.clients.jedis.ShardedJedis;


public class RedisShardedPoolUtils {

    private static final Logger log = LoggerFactory.getLogger(RedisShardedPoolUtils.class);

    public static String set(String key, String value) {

        ShardedJedis jedis = null;

        String result = null;

        try {

            jedis = RedisShardedPool.getJedis();

            result = jedis.set(key, value);

        } catch (Exception e) {

            log.error("redis set key: {} value: {} error", new Object[]{key, value, e});

            RedisShardedPool.returnBrokenResouce(jedis);

            return result;

        }

        RedisShardedPool.returnResouce(jedis);

        return result;

    }

    public static String get(String key) {

        ShardedJedis jedis = null;

        String result = null;


        try {

            jedis = RedisShardedPool.getJedis();

            result = jedis.get(key);

        } catch (Exception e) {

            log.error("redis get key: {} error", key, e);

            RedisShardedPool.returnBrokenResouce(jedis);

            return result;

        }

        RedisShardedPool.returnResouce(jedis);

        return result;

    }


    public static String setEx(String key, String value, int exTime) {

        ShardedJedis jedis = null;

        String result = null;

        try {

            jedis = RedisShardedPool.getJedis();

            result = jedis.setex(key, exTime, value);

        } catch (Exception e) {

            log.error("redis setEx key: {} value: {}   error...", new Object[]{key, value, e});

            RedisShardedPool.returnBrokenResouce(jedis);

            return result;

        }

        RedisShardedPool.returnResouce(jedis);

        return result;

    }


    public static Long expire(String key, int exTime) {

        ShardedJedis jedis = null;

        Long result = null;


        try {

            jedis = RedisShardedPool.getJedis();

            result = jedis.expire(key, exTime);

        } catch (Exception e) {

            log.error("redis expire key: {}  error ", key, e);

            RedisShardedPool.returnBrokenResouce(jedis);

            return result;

        }

        RedisShardedPool.returnResouce(jedis);

        return result;

    }


    public static Long del(String key) {

        ShardedJedis jedis = null;

        Long result = null;


        try {

            jedis = RedisShardedPool.getJedis();

            result = jedis.del(key);

        } catch (Exception e) {

            log.error("redis del key: {} error ", key, e);

            RedisShardedPool.returnBrokenResouce(jedis);

            return result;

        }

        RedisShardedPool.returnResouce(jedis);

        return result;

    }


    public static void main(String[] args) {
        ShardedJedis jedis = RedisShardedPool.getJedis();

        System.out.println(get("key1"));

        System.out.println(get("key2"));

        System.out.println(get("key3"));

        System.out.println("redis shaded pool utils end ...");

    }

}