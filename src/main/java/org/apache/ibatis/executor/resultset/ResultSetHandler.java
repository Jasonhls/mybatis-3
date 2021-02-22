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
package org.apache.ibatis.executor.resultset;

import org.apache.ibatis.cursor.Cursor;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * @author Clinton Begin
 * 结果集处理器ResultSetHandler，结果处理器负责将JDBC查询结果映射到java对象。
 * 用于对查询结果集进行处理的，目标是将JDBC结果集映射为业务对象。
 * 接口中定义的三个接口分别用于处理常规查询的结果集，游标查询的结果集以及存储过程调用的出参设置。和参数处理器一样，结果处理器也只有一个默认实现DefaultResultSetHandler。
 *结果集处理器的功能包括对象的实例化、属性自动匹配计算、常规属性赋值、嵌套ResultMap的处理、嵌套查询的处理、鉴别器结果集的处理等，每个功能我们在分享SQL语句执行selectXXX
 * 的时候都详细分析过，具体参见selectXXX部分。
 */
public interface ResultSetHandler {

  <E> List<E> handleResultSets(Statement stmt) throws SQLException;

  <E> Cursor<E> handleCursorResultSets(Statement stmt) throws SQLException;

  void handleOutputParameters(CallableStatement cs) throws SQLException;

}
