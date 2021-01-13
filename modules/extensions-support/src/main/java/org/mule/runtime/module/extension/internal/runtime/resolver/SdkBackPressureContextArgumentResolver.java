/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.resolver;

import org.mule.runtime.extension.api.runtime.operation.ExecutionContext;
import org.mule.runtime.module.extension.internal.runtime.source.legacy.SdkBackPressureContextAdapter;
import org.mule.sdk.api.runtime.source.BackPressureContext;

/**
 * ADD JDOC
 *
 * @since 1.1
 */
public class SdkBackPressureContextArgumentResolver implements ArgumentResolver<BackPressureContext> {

  private final ArgumentResolver<org.mule.runtime.extension.api.runtime.source.BackPressureContext> backPressureContextArgumentResolver =
      new BackPressureContextArgumentResolver();

  @Override
  public BackPressureContext resolve(ExecutionContext executionContext) {
    org.mule.runtime.extension.api.runtime.source.BackPressureContext backPressureContext =
        backPressureContextArgumentResolver.resolve(executionContext);
    return backPressureContext == null ? null : new SdkBackPressureContextAdapter(backPressureContext);
  }
}
