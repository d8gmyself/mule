/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.values.extension.source;

import static org.mule.runtime.extension.api.annotation.param.MediaType.TEXT_PLAIN;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.values.OfValues;
import org.mule.test.values.extension.ValuesConnection;
import org.mule.test.values.extension.resolver.WithConnectionValueProvider;

@MediaType(TEXT_PLAIN)
public class SourceWithConnection extends AbstractSource {

  @OfValues(WithConnectionValueProvider.class)
  @Parameter
  String channel;

  @org.mule.sdk.api.annotation.param.Connection
  ConnectionProvider<ValuesConnection> connection;

}
