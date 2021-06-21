package study.parser;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.util.AddAliasesVisitor;
import net.sf.jsqlparser.util.SelectUtils;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.junit.Test;

import java.util.List;

public class ParserTest {
    @Test
    public void test() throws Exception{
        String sql = "select * from user";
        Statement statement = CCJSqlParserUtil.parse(sql);
        //方法2：
//        CCJSqlParser ccjSqlParser = new CCJSqlParser(sql);
//        Statement statement1 = ccjSqlParser.Statement();
    }

    @Test
    public void tests() throws Exception {
        String sqls = "select * from user;select * from account";
        //方法一：
//        Statements statements = CCJSqlParserUtil.parseStatements(sqls);
        //方法二：
        CCJSqlParser ccjSqlParser = new CCJSqlParser(sqls);
        Statements statements = ccjSqlParser.Statements();

        List<Statement> statementList = statements.getStatements();
        System.out.println(statementList);
    }

    @Test
    public void testExpression() throws Exception{
        Expression expression = CCJSqlParserUtil.parseExpression("a+b*c");
    }

    /**
     * 从sql中提取表名
     * @throws JSQLParserException
     */
    @Test
    public void getTableNameFromSql() throws JSQLParserException {
        String sql = "select * from user";
        Statement statement = CCJSqlParserUtil.parse(sql);
        TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
        List<String> tableList = tablesNamesFinder.getTableList(statement);
        System.out.println(tableList);
    }

    /**
     * 向select添加一列或表达式
     * @throws JSQLParserException
     */
    @Test
    public void addColumn() throws JSQLParserException {
        Select select = (Select) CCJSqlParserUtil.parse("select A from user");
        SelectUtils.addExpression(select, new Column("B"));
        System.out.println(select);
    }

    /**
     * 将别名应用于所有表达式
     * @throws Exception
     */
    @Test
    public void testAlias() throws Exception{
        Select select = (Select) CCJSqlParserUtil.parse("select A,B,C from user");
        SelectBody selectBody = select.getSelectBody();
        AddAliasesVisitor addAliasesVisitor = new AddAliasesVisitor();
        addAliasesVisitor.setPrefix("B");
        selectBody.accept(addAliasesVisitor);
        System.out.println(selectBody.toString());
    }
}
