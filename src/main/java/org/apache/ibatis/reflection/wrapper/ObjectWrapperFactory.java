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
package org.apache.ibatis.reflection.wrapper;

import org.apache.ibatis.reflection.MetaObject;

/**
 * @author Clinton Begin
 * ObjectWrapperFactory是一个对象包装器工厂，用于对返回的结果对象进行二次处理，它主要在org.apache.ibatis.executor.resultset.DefaultResultSetHandler.getRowValue
 * 方法中创建对象的MetaObject时作为参数设置进去，这样MetaObject中的objectWrapper属性就可以被设置为我们自定义的ObjectWrapper实现而不是mybatis内置实现。
 *
 * 典型的下划线转驼峰，我们可以使用ObjectWrapperFactory来统一处理，
 * 通过实现这个接口，可以判断当object是特定类型时，返回true，然后在下面的getWrapperFor中返回一个可以处理key为驼峰的ObjectWrapper 实现类即可
 * （当然实际中，不会这样做，而是通过设置mapUnderscoreToCamelCase来实现）
 */
public interface ObjectWrapperFactory {

  boolean hasWrapperFor(Object object);

  ObjectWrapper getWrapperFor(MetaObject metaObject, Object object);

}
