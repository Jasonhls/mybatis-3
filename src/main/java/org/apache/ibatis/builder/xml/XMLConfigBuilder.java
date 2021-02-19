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

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    //创建一个XPathParser对象，构造函数参数分别是Reader，是否进行DTD校验，属性配置，XML实体节点解析器
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    //Configuration的无参构造器中会注册很多alias
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 解析入口
   * @return
   */
  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    //mybatis配置文件的主流程
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      //所有的root.evalNode底层都是调用XML DOM的evaluate()方法。根据给定的节点表达式来计算指定的XPath表达式，
      // 并且返回一个XPathResult对象，返回类型在Node.evalNode()方法中均被指定为NODE。
      //1.属性解析propertiesElement
      propertiesElement(root.evalNode("properties"));
      //2.加载settings节点settingsAsProperties
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      /**
       *3.加载自定义VFS ，VFS主要用来加载容器内的各种资源，比如jar或class文件。mybatis提供了2个实现JBoss6VFS和DefaultVFS，并提供了
       * 用户扩展点，用于自定义VFS实现，加载顺序是自定义VFS实现 > 默认VFS实现 取第一个加载成功的，默认情况下会先加载JBoss6VFS，
       * 如果classpath下找不到jboss的vfs实现才会加载默认VFS实现，启动打印日志如下：
       * org.apache.ibatis.io.VFS.getClass(VFS.java:111) Class not found: org.jboss.vfs.VFS
       * org.apache.ibatis.io.JBoss6VFS.setInvalid(JBoss6VFS.java:142) JBoss 6 VFS API is not available in this environment.
       * org.apache.ibatis.io.VFS.getClass(VFS.java:111) Class not found: org.jboss.vfs.VirtualFile
       * org.apache.ibatis.io.VFS$VFSHolder.createVFS(VFS.java:63) VFS implementation org.apache.ibatis.io.JBoss6VFS is not valid in this environment.
       * org.apache.ibatis.io.VFS$VFSHolder.createVFS(VFS.java:77) Using VFS adapter org.apache.ibatis.io.DefaultVFS
       */
      loadCustomVfs(settings);
      //4.解析类型别名typeAliasesElement
      typeAliasesElement(root.evalNode("typeAliases"));
      /**
       *5.加载插件，最常用的插件应该算是分页插件PageHelper了，再比如druid连接池提供的各种监控、拦截、预发检查功能，在使用其他连接池
       * 比如dbcp的时候，在不修改连接池源码的情况下，那可以借助mybatis的插件体系实现。
       * 插件具体实现的时候，采用的是拦截器模式，要注册为mybatis插件，比如是实现org.apache.ibatis.plugin.Interceptor接口，每个插件可以有
       * 自己的属性。interceptor属性值既可以完整的类名，也可以是别名，只要别名在typealias中存在即可，如果启动时无法解析，会抛出ClassNotFound
       * 异常。实例化插件后，将设置插件的属性赋值给插件实现类的属性字段。mybatis提供了两个内置的插件例子，AlwaysMapPlugin和ExamplePlugin
       */
      pluginElement(root.evalNode("plugins"));
      /**
       *6.加载对象工厂，Mybatis每次创建对象的新实例时，它都会使用一个对象工厂（ObjectFactory）实例来完成。
       * 默认的对象工厂DefaultObjectFactory做的仅仅是实例化目标类，要么通过默认构造方法，
       * 要么在参数映射存在的时候通过参数构造方法来实例化。
       */

      objectFactoryElement(root.evalNode("objectFactory"));
      /**
       *7.创建对象包装器工厂，要实现自定义的对象包装器工厂，只要实现ObjectWrapperFactory中的两个即可偶hasWrapperFor和getWrapperFor
       */
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //8.加载反射工厂，为了提供更好的灵活性，mybatis支持用户自定义反射工厂，不过总体来说，用的不多，要实现反射工厂，只要实现
      //ReflectorFactory接口即可。默认的反射工厂是DefaultReflectorFactory。一般来说，使用默认的反射工厂就可以了。
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      //将各个值赋值给configuration，同时在这里有重新设置了默值，所有这一点很重要，configuration中的默认值不一定是真正的默认值
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      /**
       * 9.加载环境配置，类似于spring和maven里面的profile，允许给开发、生产环境同时配置不同的environment，根据不同的环境加载不同的配置，
       * 如果在SqlSessionFactoryBuilder调用期间没有传递使用哪个环境的话，默认会使用一个名为default的环境。找到对应的environment之后，就可以
       * 加载事务管理器和数据源了。事务管理器和数据源类型这里都用到了类型别名，JDBC/POOLED都是mybatis内置提供的，在Configuration构造器
       * 执行期间注册到TypeAliasRegister。
       */
      environmentsElement(root.evalNode("environments"));
      /**
       * 10.加载数据库厂商标识，Mybatis可以根据不同的数据库厂商执行不同的语句，这种多厂商的支持是基于映射语句中的databaseId属性。
       * Mybatis会加载不带databaseId属性和带有匹配当前数据库databaseId属性的所有语句。如果同时找到并带有databaseId和不带
       * databaseId的相同语句，则后者会被舍弃。为支持多厂商特性只要像下面这样在mybatis-config.xml文件中加入databaseIdProvider即可。
       * <databaseIdProvider type="DB_VENDOR">
       *     <property name="SQL Server" valule="sqlserver/>
       *     <property name="MySQL" value="mysql"/>
       *     <property name="Oracle" value="oracle"/>
       * </databaseIdProvider>
       * 典型的实现比如VendorDatabaseIdProvider类
       */
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      /**
       *11.加载类型处理器 ，无论是Mybatis在预处理语句中设置一个参数时，还是从结果集中取出一个值时，都会用类型处理器将获取的值以合适的方式转换成Java类型。
       * mybatis提供了两种方式注册类型处理器，package自动检索方式喝显示定义方式。使用自动检索功能的时候，只能通过注解方式来指定JDBC类型。
       * mybatis在初始化TypeHandlerRegistry期间，自动注册了大部分的常用的类型处理器比如字符串，数字，日期等。对于非标准的类型，用户可以自定义类型处理器来处理。
       * 要实现一个自定义类型处理器，只要实现org.apache.ibatis.type.TypeHandler接口，或继承一个实用类org.apache.ibatis.type.BaseTypeHandler，并将它映射到一个JDBC类型即可。
       * 具体例子见test.examples包中的例子
       * 还需要再mybatis-config.xml添加如下配置：
       *  <typeHandlers>
       *      <typeHandler handler="org.mybatis.example.ExampleTypeHandler"/>
       *  </typeHandlers>
       */
      typeHandlerElement(root.evalNode("typeHandlers"));
      /**
       * 12.加载mapper文件，mybatis提供了两种配置mapper的方法，一种是使用package自动搜索的模式，这样指定package下所有接口都会被注册为mapper，比如
       * <mappers>
       *     <package name="org.mybatis.builder"/>
       * </mappers>
       * 另一种方式是明确指定mapper，这又可以通过resource、url或class进行细分。比如：
       * <mappers>
       *     <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
       *     <mapper url="file:///var/mappers/AuthorMapper.xml"/>
       *     <mapper class="org.mybatis.builder.AuthorMapper"/>
       * </mappers>
       * 如果要同时使用package自动扫描和通过mapper明确指定要加载的mapper，则必须先声明mapper，然后生命package，否则DTD校验会失败。
       * 同时一定要确保package自动扫描的范围不包含明确指定的mapper，否则在通过package扫描的interface的时候，尝试加载对应xml文件的loadXmlResource()逻辑中
       * 出现判重出错，报org.apache.ibatis.binding.BindingException异常。
       * 对于通过package加载的mapper文件，调用mapperRegistry.addMappers(packageName)进行加载，其核心逻辑在org.apache.ibatis.binding.MapperRegistry中，
       * 对于每个找到的接口或mapper文件中，最后调用XMLMapperBuilder进行具体解析。对于明确指定的mapper文件或mapper接口，则主要用XMLMapperBuilder进行具体解析。
       */
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    //检查所有从settings加载的设置，确保它们都在Configuration定义的范围内
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        //将interceptor指定的名称解析为Interceptor类型
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
       String type = context.getStringAttribute("type");
       ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
       configuration.setReflectorFactory(factory);
    }
  }

  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      //加载property节点为property
      Properties defaults = context.getChildrenAsProperties();
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      //必须至少包含resource或url属性之一
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) throws Exception {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler> typeHandler = (Class<? extends TypeHandler>)resolveClass(props.getProperty("defaultEnumTypeHandler"));
    configuration.setDefaultEnumTypeHandler(typeHandler);
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    @SuppressWarnings("unchecked")
    Class<? extends Log> logImpl = (Class<? extends Log>)resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        //查找匹配的environment
        if (isSpecifiedEnvironment(id)) {
          //事务配置并创建事务工厂
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          //数据源配置加载并实例化数据源，数据源是必备的
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          //创建EnvironmentBuilder
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * mapper加载与初始化
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        //如果要同时使用package自动扫描和通过mapper明确指定要加载的mapper，一定要确保package自动扫描的范围不包括明确指定的mapper，
        //否则通过package扫描的interface的时候，尝试加载对应xml文件的loadXmlResource()的逻辑中出现判重出错，
        // 报org.apache.ibatis.binding.BindingException异常，即使xml文件中包含的内容和mapper接口中包含的语句不重复也会出错。包括加载
        //mapper接口时自动加载的xml.mapper也一样会出错。
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          //加载mapper核心逻辑
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            //加载mapper核心逻辑
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
