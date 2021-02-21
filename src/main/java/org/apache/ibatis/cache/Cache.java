/**
 *    Copyright 2009-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * SPI for cache providers.
 * 
 * One instance of cache will be created for each namespace.
 * 
 * The cache implementation must have a constructor that receives the cache id as an String parameter.
 * 
 * MyBatis will pass the namespace as id to the constructor.
 * 
 * <pre>
 * public MyCache(final String id) {
 *  if (id == null) {
 *    throw new IllegalArgumentException("Cache instances require an ID");
 *  }
 *  this.id = id;
 *  initialize();
 * }
 * </pre>
 *
 * @author Clinton Begin
 * 只要实现org.apache.ibatis.cache.Cache接口的任何类都可以当作缓存，Cache接口很简单。
 * mybatis提供了基本实现org.apache.ibatis.cache.impl.PerpetualCache，内部采用原始HashMap实现。第二个需要知道的方面是ybatis有一级缓存和
 * 二级缓存。
 * 一级缓存是SqlSession级别的缓存，不同SqlSession之间的缓存数据区域（HashMap）是互相不影响，MyBatis默认支持一级缓存，不需要任何的配置，
 * 默认情况下（一级缓存的有效范围可通过参数localCacheScope参数修改），取值为SESSION或者STATEMENT)，在一个SqlSession的查询期间，只要没有发生commit/rollback
 * 或者调用close()方法，那么mybatis就会现根据当前执行语句的CacheKey到一级缓存中查找，如果找到了就直接返回，不到数据库中执行。其实现在代码BaseExecutor.query()中。
 * 二级缓存是mapper级别的缓存，多个SqlSession去操作同一个mapper的sql语句，多个SqlSession可以共用二级缓存，二级缓存是跨SqlSession。二级缓存默认不启用，
 * 需要通过在Mapper中明确设置cache，它的实现在CachingExecutor的query方法中。
 * 在mybatis的缓存实现中，缓存键CacheKey的格式为：cacheKey=ID + offset + limit + sql + parameterValues + environmentId。
 * 比如CacheKey为：
 * -1445574094:212285810:org.mybatis.internal.example.mapper.UserMapper.getUser:0:2147483647:select lfPartyId,partyName from LfParty where partyName = ?　AND partyName like ? and lfPartyId in ( ?, ?):p2:p2:1:2:development
 *
 * 对于一级缓存，commit/rollback都会清空一级缓存。
 * 对应二级缓存，DML操作或者显示设置语句层面的flushCache属性都会使得二级缓存失效。
 * 在二级缓存容器的具体回收策略实现上，有下列几种：
 * LRU-最近最少使用的：移除最长时间不被使用的对象，也是默认的选项，其实现类是org.apache.ibatis.cache.decorators.LruCache。
 * FIFO-先进先出：按对象进入缓存的顺序来移除它们，其实现类是org.apache.ibatis.cache.decorators.FifoCache。
 * SOFT-软引用：移除基于垃圾回收器状态和弱引用规则的对象，其实现类是org.apache.ibatis.cache.decorators.SoftCache.
 * WEEK-弱引用：更积极地移除基于垃圾收集器状态和弱引用规则的对象，其实现类是org.apache.ibatis.cache.decorators.WeakCache.
 * 在缓存的设计上，Mybatis所有的Cache算法都是基于装饰器/Composite模式对PerpetualCache扩展增加功能。
 * 对于模块化微服务系统来说，应该来说mybatis的一二级缓存对业务数据都不合适，尤其是对于OLTP系统来说，CRM/BI这些不算，如果要求数据非常精确的话，也不是特别合适。
 * 对于这些要求数据准确的系统来说，尽可能只使用mybatis的ORM特性比较靠谱。但是有一部分数据如果前期没有很少的设计缓存的话，是很有价值的，比如说对于一些配置类数据
 * 比如数据字典、系统参数、业务配置项等很少变化的数据。
 */

public interface Cache {

  /**
   * @return The identifier of this cache
   */
  String getId();

  /**
   * @param key Can be any object but usually it is a {@link CacheKey}
   * @param value The result of a select.
   */
  void putObject(Object key, Object value);

  /**
   * @param key The key
   * @return The object stored in the cache.
   */
  Object getObject(Object key);

  /**
   * As of 3.3.0 this method is only called during a rollback 
   * for any previous value that was missing in the cache.
   * This lets any blocking cache to release the lock that 
   * may have previously put on the key.
   * A blocking cache puts a lock when a value is null 
   * and releases it when the value is back again.
   * This way other threads will wait for the value to be 
   * available instead of hitting the database.
   *
   * 
   * @param key The key
   * @return Not used
   */
  Object removeObject(Object key);

  /**
   * Clears this cache instance
   */  
  void clear();

  /**
   * Optional. This method is not called by the core.
   * 
   * @return The number of elements stored in the cache (not its capacity).
   */
  int getSize();
  
  /** 
   * Optional. As of 3.2.6 this method is no longer called by the core.
   *  
   * Any locking needed by the cache must be provided internally by the cache provider.
   * 
   * @return A ReadWriteLock 
   */
  ReadWriteLock getReadWriteLock();

}