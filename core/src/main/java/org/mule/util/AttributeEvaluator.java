/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.util;

import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.MuleRuntimeException;
import org.mule.api.expression.ExpressionManager;
import org.mule.config.i18n.CoreMessages;

import java.util.regex.Pattern;

/**
 * This class acts as a wrapper for configuration attributes that support simple text, expression or regular
 * expressions. It can be extended to support other cases too.
 */
public class AttributeEvaluator
{

    private static final Pattern SINGLE_EXPRESSION_REGEX_PATTERN = Pattern.compile("^#\\[(?:(?!#\\[).)*\\]$");
    private static final Pattern MULTIPLE_EXPRESSION_REGEX_PATTERN = Pattern.compile("#\\[[^\\[]*\\]");


    private enum AttributeType
    {
        EXPRESSION, PARSE_EXPRESSION, STATIC_VALUE
    }

    private final String attributeValue;
    private ExpressionManager expressionManager;
    private AttributeType attributeType;

    public AttributeEvaluator(String attributeValue)
    {
        this.attributeValue = attributeValue;
    }

    public AttributeEvaluator initialize(final ExpressionManager expressionManager)
    {
        this.expressionManager = expressionManager;
        resolveAttributeType();
        return this;
    }

    private void resolveAttributeType()
    {
        if (attributeValue != null && SINGLE_EXPRESSION_REGEX_PATTERN.matcher(attributeValue).matches())
        {
            this.attributeType = AttributeType.EXPRESSION;
        }
        else if (attributeValue != null && MULTIPLE_EXPRESSION_REGEX_PATTERN.matcher(attributeValue).find())
        {
            this.attributeType = AttributeType.PARSE_EXPRESSION;
        }
        else
        {
            this.attributeType = AttributeType.STATIC_VALUE;
        }
    }

    public boolean isExpression()
    {
        return this.attributeType.equals(AttributeType.EXPRESSION);
    }

    public boolean isParseExpression()
    {
        return attributeType.equals(AttributeType.PARSE_EXPRESSION);
    }

    public Object resolveValue(MuleMessage message)
    {
        if (isExpression())
        {
            return expressionManager.evaluate(attributeValue, message);
        }
        else if (isParseExpression())
        {
            return expressionManager.parse(attributeValue, message);
        }
        else
        {
            return attributeValue;
        }
    }

    public Object resolveValue(MuleEvent event)
    {
        if (isExpression())
        {
            return expressionManager.evaluate(attributeValue, event);
        }
        else if (isParseExpression())
        {
            return expressionManager.parse(attributeValue, event);
        }
        else
        {
            return attributeValue;
        }
    }

    public Integer resolveIntegerValue(MuleEvent event)
    {
        final Object value = resolveValue(event);
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number)
        {
            return ((Number) value).intValue();
        }
        else if (value instanceof String)
        {
            return Integer.parseInt((String) value);
        }
        else
        {
            throw new MuleRuntimeException(CoreMessages.createStaticMessage(String.format("Value was required as integer but is of type: %s", value.getClass().getName())));
        }
    }

    public String resolveStringValue(MuleEvent event)
    {
        final Object value = resolveValue(event);
        if (value == null)
        {
            return null;
        }
        return value.toString();
    }

    public String getRawValue()
    {
        return attributeValue;
    }
}
