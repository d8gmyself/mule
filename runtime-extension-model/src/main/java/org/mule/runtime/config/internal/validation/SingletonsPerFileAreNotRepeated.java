/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.validation;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.mule.runtime.ast.api.util.ComponentAstPredicatesFactory.currentElemement;
import static org.mule.runtime.ast.api.util.ComponentAstPredicatesFactory.equalsIdentifier;
import static org.mule.runtime.ast.api.util.ComponentAstPredicatesFactory.topLevelElement;
import static org.mule.runtime.ast.api.validation.Validation.Level.ERROR;

import org.mule.runtime.api.meta.model.EnrichableModel;
import org.mule.runtime.ast.api.ArtifactAst;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.ast.api.validation.Validation;
import org.mule.runtime.core.privileged.extension.SingletonModelProperty;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Elements declared as singletons via the 'SingletonModelProperty' with 'appliesToFile' as true are not repeated in a config
 * file.
 */
public class SingletonsPerFileAreNotRepeated implements Validation {

  @Override
  public String getName() {
    return "Singletons per file are not repeated";
  }

  @Override
  public String getDescription() {
    return "Elements declared as singletons via the 'SingletonModelProperty' with 'appliesToFile' as true are not repeated in a config file.";
  }

  @Override
  public Level getLevel() {
    return ERROR;
  }

  @Override
  public Predicate<List<ComponentAst>> applicable() {
    return topLevelElement()
        .and(currentElemement(componentModel -> componentModel.getModel(EnrichableModel.class)
            .flatMap(enrchModel -> enrchModel.getModelProperty(SingletonModelProperty.class)
                .map(SingletonModelProperty::isAppliesToFile))
            .orElse(false)));
  }

  @Override
  public Optional<String> validate(ComponentAst component, ArtifactAst artifact) {
    final List<ComponentAst> repeated = artifact.topLevelComponentsStream()
        .filter(comp -> !comp.equals(component))
        .filter(comp -> comp.getMetadata().getFileName().equals(component.getMetadata().getFileName()))
        .filter(equalsIdentifier(component.getIdentifier()))
        .collect(toList());

    if (repeated.isEmpty()) {
      return empty();
    }

    return of("The configuration element [" + component.getIdentifier() + "] can only appear once, but was present also in " +
        repeated.stream()
            .map(this::compToLoc)
            .collect(joining(", ")));
  }

  private String compToLoc(ComponentAst component) {
    return "[" + component.getMetadata().getFileName().orElse("unknown") + ":"
        + component.getMetadata().getStartLine().orElse(-1) + "]";
  }

}
