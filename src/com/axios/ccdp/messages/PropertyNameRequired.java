package com.axios.ccdp.messages;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to flag whether this method is required to have a value (i.e. not
 * null)
 * 
 * Implication is that a CcdpMessageException will be thrown
 * 
 * @author Oscar E. Ganteaume
 *
 */
@Retention( RetentionPolicy.RUNTIME)
@Target( ElementType.METHOD)
public @interface PropertyNameRequired
{
}
