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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 * 当Mybatis将一个Java对象作为输入/输出参数执行CRUD语句操作时，它会创建一个PreparedStatement对象，并且调用setXXX()为占位符设置相应的参数值。
 * XXX可以是Int，String，Date等Java内置类型，或者用户自定义的类型。在实现上，Mybatis通过使用类型处理器（typeHandler）来确定XXX是具体什么类型的。
 * Mybatis对于下列类型使用内建的类型处理器：所有的基本数据类型、基本类型的包裹类型、byte[]、java.util.Date、java.sql.Time、java.sql.Timestamp、java枚举类型等。
 * 对于用户自定义的类型，可以创建自定义的类型处理器。创建自定义的类型处理器，只要实现TypeHandler接口即可。
 * 虽然可以直接实现TypeHandler接口，但在实践中，一般选择继承BaseTypeHandler，BaseTypeHandler为TypeHandler提供了部分骨架代码
 * 实现了自定义的类型处理器后，需要在mybatis配置文件mybatis-config.xml中注册就可以使用了。
 * <typeHandlers>
 *
 * </typeHandlers>
 */
public interface TypeHandler<T> {

  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  T getResult(ResultSet rs, String columnName) throws SQLException;

  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
