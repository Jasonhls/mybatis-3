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
package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * @author Clinton Begin
 * 插件几乎是所有主流框架提供的一种扩展方式之一，插件可以用于记录日志，统计运行时性能，为核心功能提供额外的辅助支持。
 * 在mybatis中，插件是在内部是通过拦截器实现的。要开发自定义自定义插件，只要实现org.apache.ibatis.plugin.Interceptor接口即可
 *
 * mybatis提供了为插件配置提供了两个注解：org.apache.ibatis.plugin.Signature和org.apache.ibatis.plugin.Intercepts。
 * Intercepts注解用来指示当前类是一个拦截器，它有一个类型为Signature数组的value属性，如果没有指定，
 * 它会拦截StatementHandler、ResultSetHandler、ParameterHandler和Executor这四个核心接口对象中的所有方法。
 * 如需改变默认行为，可以通过明确设置value的值
 * 比如：
 * @Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class}),
 *         @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class})})
 */
public interface Interceptor {

  Object intercept(Invocation invocation) throws Throwable;

  Object plugin(Object target);

  void setProperties(Properties properties);

}
