/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.scripting;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;

/**
 * 第一种方式：实现了LanguageDriver之后，可以在配置文件中指定该实现类作为SQL的解析器，在XML中使用lang属性指定。
 *<typeAliases>
 *         <typeAlias type="com.cn.language.MyLanguageDriver" alias="myLanguage"/>
 *</typeAliases>
 * <select id="selectBlog" lang="myLanguage">
 *     SELECT * FROM BLOG
 * </select>
 * 第二种方式：除了可以在语句级别指定之外，也可以全家设置，如下：
 * <settings>
 *     <setting name="defaultScriptingLanguage" value="myLanguage"/>
 * </settings>
 * 第三种方式：对于mapper接口，也可以使用@Lang注解，如下：
 * @Lang(MyLanguageDriver.class)
 * @Select("SELECT * FROM users")
 * List<User> selectUser();
 *
 *
 * LanguageDriver将实际的实现根据采用的底层不同，委托给了具体的Builder，对于XML配置，委托给XMLScriptBuilder。
 * 对于使用Velocity模板的解析器，委托给SQLScriptSource解析具体的SQL。
 * 注：mybatis-velocity还提供了VelocityLanguageDriver和FreeMarkerLanguageDriver，可参见：
 * https://github.com/mybatis/velocity-scripting
 * https://github.com/mybatis/freemarker-scripting
 */

public interface LanguageDriver {

  /**
   * Creates a {@link ParameterHandler} that passes the actual parameters to the the JDBC statement.
   * 
   * @param mappedStatement The mapped statement that is being executed
   * @param parameterObject The input parameter object (can be null) 
   * @param boundSql The resulting SQL once the dynamic language has been executed.
   * @return
   * @author Frank D. Martinez [mnesarco]
   * @see DefaultParameterHandler
   * 创建一个ParameterHandler对象，用于将实际参数赋值到JDBC语句中
   */
  ParameterHandler createParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql);

  /**
   * Creates an {@link SqlSource} that will hold the statement read from a mapper xml file. 
   * It is called during startup, when the mapped statement is read from a class or an xml file.
   * 
   * @param configuration The MyBatis configuration
   * @param script XNode parsed from a XML file
   * @param parameterType input parameter type got from a mapper method or specified in the parameterType xml attribute. Can be null.
   * @return
   * 将XML中读入的语句解析并返回一个sqlSource对象
   */
  SqlSource createSqlSource(Configuration configuration, XNode script, Class<?> parameterType);

  /**
   * Creates an {@link SqlSource} that will hold the statement read from an annotation.
   * It is called during startup, when the mapped statement is read from a class or an xml file.
   * 
   * @param configuration The MyBatis configuration
   * @param script The content of the annotation
   * @param parameterType input parameter type got from a mapper method or specified in the parameterType xml attribute. Can be null.
   * @return
   * 将注解中读入的语句解析并返回一个sqlSource对象
   */
  SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType);

}
