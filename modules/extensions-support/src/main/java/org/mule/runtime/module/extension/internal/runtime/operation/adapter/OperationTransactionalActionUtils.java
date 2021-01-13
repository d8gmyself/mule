/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.operation.adapter;

import static java.lang.String.format;

import org.mule.runtime.extension.api.tx.OperationTransactionalAction;

/**
 * ADD JDOC
 */
public final class OperationTransactionalActionUtils {

  private OperationTransactionalActionUtils() {}

  /**
   * ADD JDOC
   * @param operationTransactionalAction
   * @return
   */
  public static OperationTransactionalAction from(Object operationTransactionalAction) {
    if (operationTransactionalAction instanceof OperationTransactionalAction) {
      return (OperationTransactionalAction) operationTransactionalAction;
    } else if (operationTransactionalAction instanceof org.mule.sdk.api.tx.OperationTransactionalAction) {
      return fromSdk((org.mule.sdk.api.tx.OperationTransactionalAction) operationTransactionalAction);
    }
    throw new IllegalArgumentException(format("operationTransactionalAction is expected to be a org.mule.sdk.api.tx.OperationTransactionalAction or org.mule.runtime.extension.api.tx.OperationTransactionalAction, but was %s",
                                              operationTransactionalAction.getClass()));
  }

  private static OperationTransactionalAction fromSdk(org.mule.sdk.api.tx.OperationTransactionalAction operationTransactionalAction) {
    switch (operationTransactionalAction) {
      case JOIN_IF_POSSIBLE:
        return OperationTransactionalAction.JOIN_IF_POSSIBLE;
      case NOT_SUPPORTED:
        return OperationTransactionalAction.NOT_SUPPORTED;
      case ALWAYS_JOIN:
        return OperationTransactionalAction.ALWAYS_JOIN;
    }
    return null;
  }
}
