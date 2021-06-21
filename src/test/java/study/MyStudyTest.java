/**
 *    Copyright 2009-2020 the original author or authors.
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
package study;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * @description:
 * @author: helisen
 * @create: 2020-07-06 09:56
 **/
public class MyStudyTest {

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeClass
    public static void before() {
        System.out.println("测试类：执行之前的动作");
    }

    @Test
    public void test1()  {
        SqlSession sqlSession = null;
        try {
            Reader resourceAsReader = Resources.getResourceAsReader("study/mybatis-config.xml");
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(resourceAsReader);
            sqlSession = sqlSessionFactory.openSession();
            /*方式一*/
//            User user = sqlSession.selectOne("study.MyStudyMapper.getUserById", 1L);
//            System.out.println(user.toString());
//            /*方式二*/
            MyStudyMapper mapper = sqlSession.getMapper(MyStudyMapper.class);
            User user2 = mapper.getUserById(2L);
            System.out.println(user2.toString());
//            MyStudyMapper mapper = sqlSession.getMapper(MyStudyMapper.class);
//            List<User> user = mapper.getUser();
//            sqlSession.commit();
//            System.out.println(user);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(sqlSession != null) {
                sqlSession.close();
            }
        }
    }

    @Test
    public void test() {
        String sql = "hello da\njia ha    o";
        System.out.println(sql);
        sql = sql.replaceAll("[\\s]+", " ");
        System.out.println(sql);
    }
}
