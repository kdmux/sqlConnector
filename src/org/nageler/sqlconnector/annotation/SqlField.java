package org.nageler.sqlconnector.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation may be added to a <code>public</code> field, which shall be written/read from the database
 * 
 * @author Marcel Nageler &lt;coding@nageler.org&gt;
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SqlField {
	
	String column() default "";
	
	boolean primaryKey() default false;
	
}
