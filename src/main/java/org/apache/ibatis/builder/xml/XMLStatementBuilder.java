/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.builder.xml;

import java.util.List;
import java.util.Locale;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class XMLStatementBuilder extends BaseBuilder {

  private final MapperBuilderAssistant builderAssistant;
  private final XNode context;
  private final String requiredDatabaseId;

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context) {
    this(configuration, builderAssistant, context, null);
  }

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context, String databaseId) {
    super(configuration);
    this.builderAssistant = builderAssistant;
    this.context = context;
    this.requiredDatabaseId = databaseId;
  }

  /**
   * 解析入口
   * 从DTD可以看出，sql/crud元素里面可以包含这些元素：#PCDATA(文本节点) | include | trim | where | set | foreach | choose | if | bind，
   * 除了文本节点外，其他类型的节点都可以嵌套
   */
  public void parseStatementNode() {
    String id = context.getStringAttribute("id");
    String databaseId = context.getStringAttribute("databaseId");

    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      return;
    }

    Integer fetchSize = context.getIntAttribute("fetchSize");
    Integer timeout = context.getIntAttribute("timeout");
    String parameterMap = context.getStringAttribute("parameterMap");
    String parameterType = context.getStringAttribute("parameterType");
    //解决别名，Configuration无参构造中会默认添加许多别名到集合中，然后这里就会判断parameterType是否在这个别名集合中
    Class<?> parameterTypeClass = resolveClass(parameterType);
    String resultMap = context.getStringAttribute("resultMap");
    String resultType = context.getStringAttribute("resultType");
    String lang = context.getStringAttribute("lang");
    /**
     *Mybatis从3.2开始支持可插拔的脚本语言，因此你可以在插入一种语言的驱动（language driver）之后来写基于这种语言的动态SQL查询
     * Mybatis除了XML格式外，还提供了mybatis-velocity，允许使用velocity表达式编写SQL语句。可以通过实现LanguageDriver接口的方式来插入一种语言：
     * 一旦有了自定义的语言驱动，就可以在mybatis-config.xml文件中将它设置为默认语言：
     * <typeAliases>
     *     <typeAlias type="org.sample.MyLanguage" alias="myLanguage"/>
     * </typeAliases>
     * <settings>
     *     <setting name="defaultScriptingLanguage" value="myLanguage"/>
     * </settings>
     * 除了设置默认语言，还可以针对特殊的语句指定特定语言，通过如下的lang属性来完成
     * <select id="selectBlog" lang="myLanguage">
     *     SELECT * FROM BLOG
     * </select>
     */
    //如果配置中没有配置lang属性，就会获取默认的LanguageDriver即XMLLanguageDriver
    LanguageDriver langDriver = getLanguageDriver(lang);

    Class<?> resultTypeClass = resolveClass(resultType);
    //结果集类型，FORWARD_ONLY,SCROLL_SENSITIVE或SCROLL_INSENSITIVE中的一个，默认值为unset（依赖驱动）
    String resultSetType = context.getStringAttribute("resultSetType");
    //解析crud语句的类型，mybatis目前支持三种，prepare、硬编码、以及存储过程调用
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);

    String nodeName = context.getNode().getNodeName();
    //解析SQL命令类型，目前主要有UNKNOWN，INSERT，UPDATE，SELECT，FLUSH
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    //insert/delete/update后是否刷新缓存
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    //select是否使用缓存
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);
    //这个设置仅针对嵌套结果 select 语句适用：如果为true，就是假设包含了嵌套结果集或是分组了，这样的话当返回一个主结果行的时候，
    //就不会发生有对前面结果集的引用的情况。这就使得在获取嵌套的结果集的时候不至于导致内存不够用。默认值：false。
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);

    // Include Fragments before parsing
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    /**
     * 解析包含的sql片段的的逻辑
     */
    includeParser.applyIncludes(context.getNode());

    // Parse selectKey after includes and remove them.
    /**
     * 解析selectKey节点，selectKey节点用于支持数据库比如Oracle不支持自动生成主键，或者可能JDBC驱动不支持自动生成主键时的请你看。
     * 对于数据库自动生成主键的字段（比如MySQL和SQL Server），那么你可以设置useGeneratedKeys="true"，而且设置keyProperty到你已经做好的目标属性上
     * 就可以了，不需要使用selectKey节点。由于已经处理了SQL片段节点，当前在处理CRUD节点，所以先将包含的SQL片段展开，然后解析selectKey是正确的，
     * 因为selectKey可以包含在SQL片段中。
     */
    processSelectKeyNodes(id, parameterTypeClass, langDriver);
    
    // Parse the SQL (pre: <selectKey> and <include> were parsed and removed)
    /**
     * 默认情况下，mybatis使用org.apache.ibatis.scripting.xmltags.XMLLanguageDriver。通过调用createSqlSource方法创建SqlSource
     * 这里面包含了解析sql的核心逻辑
     */
    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
    String resultSets = context.getStringAttribute("resultSets");
    String keyProperty = context.getStringAttribute("keyProperty");
    String keyColumn = context.getStringAttribute("keyColumn");
    KeyGenerator keyGenerator;
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    if (configuration.hasKeyGenerator(keyStatementId)) {
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else {
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
          ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
    }

    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered, 
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
  }

  private void processSelectKeyNodes(String id, Class<?> parameterTypeClass, LanguageDriver langDriver) {
    List<XNode> selectKeyNodes = context.evalNodes("selectKey");
    if (configuration.getDatabaseId() != null) {
      parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, configuration.getDatabaseId());
    }
    parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, null);
    removeSelectKeyNodes(selectKeyNodes);
  }

  private void parseSelectKeyNodes(String parentId, List<XNode> list, Class<?> parameterTypeClass, LanguageDriver langDriver, String skRequiredDatabaseId) {
    for (XNode nodeToHandle : list) {
      String id = parentId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
      String databaseId = nodeToHandle.getStringAttribute("databaseId");
      if (databaseIdMatchesCurrent(id, databaseId, skRequiredDatabaseId)) {
        parseSelectKeyNode(id, nodeToHandle, parameterTypeClass, langDriver, databaseId);
      }
    }
  }

  /**
   * 解析SelectKey节点总的来说比较简单：
   * 1.加载相关属性；
   * 2.使用语言驱动器创建SqlSource。SqlSource是一个接口，它代表了从xml或者注解上读取到的sql映射语句的内容，其中参数使用占位符进行了替换，
   * 在运行时，其代表的SQL会发送给数据库
   * mybatis提供了两种类型的实现org.apache.ibatis.scripting.xmltags.DynamicSqlSource和org.apache.ibatis.scripting.defaults.RawSqlSource
   * 3.SelectKey在实现上内部和其他的CRUD一样，都被当作一个MappedStatement进行存储。
   * 4.最后为SelectKey对应的sql语句创建并维护一个KeyGenerator
   * @param id
   * @param nodeToHandle
   * @param parameterTypeClass
   * @param langDriver
   * @param databaseId
   */
  private void parseSelectKeyNode(String id, XNode nodeToHandle, Class<?> parameterTypeClass, LanguageDriver langDriver, String databaseId) {
    String resultType = nodeToHandle.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);
    StatementType statementType = StatementType.valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
    String keyColumn = nodeToHandle.getStringAttribute("keyColumn");
    boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));

    //defaults
    boolean useCache = false;
    boolean resultOrdered = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    SqlSource sqlSource = langDriver.createSqlSource(configuration, nodeToHandle, parameterTypeClass);
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, null);

    id = builderAssistant.applyCurrentNamespace(id, false);

    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
  }

  private void removeSelectKeyNodes(List<XNode> selectKeyNodes) {
    for (XNode nodeToHandle : selectKeyNodes) {
      nodeToHandle.getParent().getNode().removeChild(nodeToHandle.getNode());
    }
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      if (databaseId != null) {
        return false;
      }
      // skip this statement if there is a previous one with a not null databaseId
      id = builderAssistant.applyCurrentNamespace(id, false);
      if (this.configuration.hasStatement(id, false)) {
        MappedStatement previous = this.configuration.getMappedStatement(id, false); // issue #2
        if (previous.getDatabaseId() != null) {
          return false;
        }
      }
    }
    return true;
  }

  private LanguageDriver getLanguageDriver(String lang) {
    Class<?> langClass = null;
    if (lang != null) {
      langClass = resolveClass(lang);
    }
    return builderAssistant.getLanguageDriver(langClass);
  }

}
