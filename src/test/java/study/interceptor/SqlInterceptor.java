package study.interceptor;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Intercepts(value = {
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class SqlInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = ms.getBoundSql(parameter);
        showSqlLog(ms.getConfiguration(), ms, boundSql);
        return invocation.proceed();
    }

    private void showSqlLog(Configuration configuration, MappedStatement ms, BoundSql boundSql) {
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        Object parameterObject = boundSql.getParameterObject();
        String sql = boundSql.getSql().replaceAll("[\\s]", " ");
        if(typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            sql = sql.replaceAll("\\?", getValueByParameterMapping(parameterObject));
        }else {
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
            if(parameterMappings != null && parameterMappings.size() > 0) {
                for (ParameterMapping pm : parameterMappings) {
                    String property = pm.getProperty();
                    if(metaObject.hasGetter(property)) {
                        sql = sql.replaceAll("\\?", getValueByParameterMapping(metaObject.getValue(property)));
                    }else if(boundSql.hasAdditionalParameter(property)){
                        sql = sql.replaceAll("\\?", getValueByParameterMapping(boundSql.getAdditionalParameter(property)));
                    }
                }
            }
        }
        logSql(sql, ms.getId());
    }

    private void logSql(String sql, String id) {
        System.out.println("sql对应的id: " + id + "，打印sql: " + sql);
    }

    private String getValueByParameterMapping(Object parameterObject) {
        String value;
        if(parameterObject instanceof String) {
            value = parameterObject.toString();
        }else if(parameterObject instanceof Date) {
            SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
            value = sdf.format(parameterObject);
        }else {
            if(parameterObject != null) {
                value = parameterObject.toString();
            }else {
                value = "NULL";
            }
        }
        return value;
    }



    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {

    }
}
