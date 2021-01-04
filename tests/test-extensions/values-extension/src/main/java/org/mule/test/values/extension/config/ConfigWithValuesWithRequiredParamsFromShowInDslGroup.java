/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.values.extension.config;

import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.values.OfValues;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
import org.mule.test.values.extension.GroupWithValuesParameter;
import org.mule.test.values.extension.connection.ValuesConnectionProvider;
import org.mule.test.values.extension.resolver.WithRequiredParameterFromGroupValueProvider;

@Configuration(name = "ValuesWithRequiredParamsFromShowInDslGroup")
@ConnectionProviders(ValuesConnectionProvider.class)
public class ConfigWithValuesWithRequiredParamsFromShowInDslGroup {

  @Parameter
  @OfValues(WithRequiredParameterFromGroupValueProvider.class)
  String valueParam;

  @org.mule.sdk.api.annotation.param.ParameterGroup(name = "someGroup", showInDsl = true)
  GroupWithValuesParameter paramGroup;
}
