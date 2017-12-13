package com.axios.ccdp.messages;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to flag that this setter sets a property which should be  
 * extracted from the message
 * 
 * value represents the property name
 * 
 * @author Oscar E. Ganteaume
 *
 */
@Retention( RetentionPolicy.RUNTIME)
@Target( ElementType.METHOD)
public @interface PropertyNameSet
{
  String value();
}
