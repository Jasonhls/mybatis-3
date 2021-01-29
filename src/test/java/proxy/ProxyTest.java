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
package proxy;

import org.junit.Test;

import java.lang.reflect.Proxy;

/**
 * @description:
 * @author: helisen
 * @create: 2020-07-07 15:42
 **/
public class ProxyTest {
    @Test
    public void test1() {
        Person p = new Student();
        StudentInvocationHandler handler = new StudentInvocationHandler(p);
        Person pp = (Person) Proxy.newProxyInstance(p.getClass().getClassLoader(), p.getClass().getInterfaces(), handler);
        System.out.println(pp);
        pp.eat();
        pp.watchMovie("helisen");
    }
}
