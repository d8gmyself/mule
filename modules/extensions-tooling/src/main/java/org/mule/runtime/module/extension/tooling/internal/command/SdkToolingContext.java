/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.tooling.internal.command;

import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.MuleContext;

import java.util.Map;

public interface SdkToolingContext {

  ExtensionModel getExtensionModel();

  Map<String, Object> getParameters();

  MuleContext getMuleContext();

  ClassLoader getClassLoader();
}