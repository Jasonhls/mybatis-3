package examples;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @description: 自定义类型处理器，实现方法：实现org.apache.ibatis.type.TypeHandler接口，
 * 或继承一个实用类org.apache.ibatis.type.BaseTypeHandler，并将它映射到一个JDBC类型即可。
 * 还需要再mybatis-config.xml添加如下配置：
 *  <typeHandlers>
 *      <typeHandler handler="org.mybatis.example.ExampleTypeHandler"/>
 *   </typeHandlers>
 *
 * 使用这个的类型处理器将会覆盖已经存在的处理 Java 的 String 类型属性和 VARCHAR 参数及结果的类型处理器。 要注意 MyBatis 不会窥探数据库元信息来决定使用哪种类型，
 * 所以你必须在参数和结果映射中指明那是 VARCHAR 类型的字段， 以使其能够绑定到正确的类型处理器上。 这是因为：MyBatis 直到语句被执行才清楚数据类型。
 * 通过类型处理器的泛型，MyBatis 可以得知该类型处理器处理的 Java 类型，不过这种行为可以通过两种方法改变：
 * 在类型处理器的配置元素（typeHandler element）上增加一个 javaType 属性（比如：javaType=”String”）；
 * 在类型处理器的类上（TypeHandler class）增加一个 @MappedTypes 注解来指定与其关联的 Java 类型列表。 如果在 javaType 属性中也同时指定，则注解方式将被忽略。
 * 可以通过两种方式来指定被关联的 JDBC 类型：
 *      1.在类型处理器的配置元素上增加一个 jdbcType 属性（比如：jdbcType=”VARCHAR”）；
 *      2.在类型处理器的类上（TypeHandler class）增加一个 @MappedJdbcTypes 注解来指定与其关联的 JDBC 类型列表。 如果在两个位置同时指定，则注解方式将被忽略。
 * 当决定在ResultMap中使用某一TypeHandler时，此时java类型是已知的（从结果类型中获得），但是JDBC类型是未知的。
 * 因此Mybatis使用javaType=[TheJavaType], jdbcType=null的组合来选择一个TypeHandler。 这意味着使用@MappedJdbcTypes注解可以限制TypeHandler的范围，
 * 同时除非显示的设置，否则TypeHandler在ResultMap中将是无效的。 如果希望在ResultMap中使用TypeHandler，那么设置@MappedJdbcTypes注解的includeNullJdbcType=true即可。
 * 然而从Mybatis 3.4.0开始，如果只有一个注册的TypeHandler来处理Java类型，那么它将是ResultMap使用Java类型时的默认值（即使没有includeNullJdbcType=true）。
 * 还可以创建一个泛型类型处理器，它可以处理多于一个类。为达到此目的， 需要增加一个接收该类作为参数的构造器，这样在构造一个类型处理器的时候 MyBatis 就会传入一个具体的类。
 *
 * public class GenericTypeHandler<E extends MyObject> extends BaseTypeHandler<E> {
 *   private Class<E> type;
 *   public GenericTypeHandler(Class<E> type) {
 *     if (type == null) throw new IllegalArgumentException("Type argument cannot be null");
 *     this.type = type;
 *   }
 * 我们映射枚举使用的EnumTypeHandler 和 EnumOrdinalTypeHandler 都是泛型类型处理器（generic TypeHandlers）。
 * 处理枚举类型映射
 * 　　若想映射枚举类型 Enum，则需要从 EnumTypeHandler 或者 EnumOrdinalTypeHandler 中选一个来使用。
 * 　　比如说我们想存储取近似值时用到的舍入模式。默认情况下，MyBatis 会利用 EnumTypeHandler 来把 Enum 值转换成对应的名字。
 * 　　注意 EnumTypeHandler 在某种意义上来说是比较特别的，其他的处理器只针对某个特定的类，而它不同，它会处理任意继承了 Enum 的类。
 * 　　不过，我们可能不想存储名字，相反我们的 DBA 会坚持使用整形值代码。那也一样轻而易举： 在配置文件中把 EnumOrdinalTypeHandler 加到 typeHandlers 中即可，
 *        这样每个 RoundingMode 将通过他们的序数值来映射成对应的整形。
 *
 * <!-- mybatis-config.xml -->
 * <typeHandlers>
 *   <typeHandler handler="org.apache.ibatis.type.EnumOrdinalTypeHandler" javaType="java.math.RoundingMode"/>
 * </typeHandlers>
 * 　　但是怎样能将同样的 Enum 既映射成字符串又映射成整形呢？
 * 　　自动映射器（auto-mapper）会自动地选用EnumOrdinalTypeHandler来处理，所以如果我们想用普通的 EnumTypeHandler，就需要为那些SQL 语句显式地设置要用到的类型处理器。比如：
 * <result column="roundingMode" property="roundingMode" typeHandler="org.apache.ibatis.type.EnumTypeHandler"/>
 * @author: helisen
 * @create: 2021-01-31 09:10
 **/
@MappedJdbcTypes(JdbcType.VARCHAR)
public class ExampleTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
