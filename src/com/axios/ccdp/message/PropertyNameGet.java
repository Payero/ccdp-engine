package com.axios.ccdp.message;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotaion to flag that this getter returns a property which should be put 
 * in the message
 * 
 * value represents the property name
 * 
 * @author Oscar E. Ganteaume
 *
 */
@Retention( RetentionPolicy.RUNTIME)
@Target( ElementType.METHOD)
public @interface PropertyNameGet
{
  String value();
}
