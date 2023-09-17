/*
 * SonarQube Java
 * Copyright (C) 2012-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.samples.java.checks;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.SymbolMetadata;
import org.sonar.plugins.java.api.semantic.SymbolMetadata.AnnotationInstance;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ParameterizedTypeTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;
import org.sonar.plugins.java.api.tree.TypeTree;
import org.sonar.plugins.java.api.tree.VariableTree;

@Rule(key = "CUSTOM5128")
public class MyMissingBeanValidationCheck extends IssuableSubscriptionVisitor {

  private static final String JAVAX_VALIDATION_VALID = "javax.validation.Valid";
  private static final String SPRING_VALIDATION_VALIDATED = "org.springframework.validation.annotation.Validated";

  private static final String JAVAX_VALIDATION_CONSTRAINT = "javax.validation.Constraint";

  private static final String SPRING_CONTROLLER = "org.springframework.stereotype.Controller";
  private static final String SPRING_REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return Collections.singletonList(Kind.METHOD);
  }

  @Override
  public void visitNode(Tree tree) {
    MethodTree methodTree = (MethodTree) tree;
    // 只校验controller接口方法参数变量
    if (isControllerMethod(methodTree)) {
      for (VariableTree parameter : methodTree.parameters()) {
        checkField(parameter);
        // 校验方法参数变量类中的所有字段
        parameter.type().symbolType().symbol().memberSymbols().forEach(e -> {
          if (e.declaration() != null && e.isVariableSymbol()) {
            checkField((VariableTree) e.declaration());
          }
        });
      }
    }
  }

  private void checkField(VariableTree field) {
    getIssueMessage(field).ifPresent(message -> reportIssue(field.type(), message));
  }

  private static boolean isControllerMethod(MethodTree methodTree) {
    SymbolMetadata parentClassOwner = methodTree.symbol().owner().metadata();
    final List<AnnotationInstance> classAnnotations = parentClassOwner.annotations();
    final List<AnnotationInstance> methodAnnotations = methodTree.symbol().metadata().annotations();
    return classAnnotations.stream().anyMatch(MyMissingBeanValidationCheck::isControllerAnnotation)
      && methodAnnotations.stream().anyMatch(MyMissingBeanValidationCheck::isRequestMappingAnnotation);
  }

  private static Optional<String> getIssueMessage(VariableTree variable) {
    if (!validationEnabled(variable) && validationSupported(variable)) {
      return Optional.of(
        MessageFormat.format("Add missing \"@Valid\" on \"{0}\" to validate it with \"Bean Validation\".",
          variable.simpleName()));
    }
    return Optional.empty();
  }

  private static boolean validationEnabled(VariableTree variable) {
    if (variable.symbol().metadata().isAnnotatedWith(JAVAX_VALIDATION_VALID)
      || variable.symbol().metadata().isAnnotatedWith(SPRING_VALIDATION_VALIDATED)) {
      return true;
    }
    return typeArgumentAnnotations(variable).anyMatch(annotation ->
      annotation.is(JAVAX_VALIDATION_VALID)
        || annotation.is(SPRING_VALIDATION_VALIDATED));
  }

  private static Stream<Type> typeArgumentAnnotations(VariableTree variable) {
    return typeArgumentTypeTrees(variable).flatMap(type -> type.annotations().stream()).map(ExpressionTree::symbolType);
  }

  private static Stream<TypeTree> typeArgumentTypeTrees(VariableTree variable) {
    TypeTree variableType = variable.type();
    if (!variableType.is(Tree.Kind.PARAMETERIZED_TYPE)) {
      return Stream.empty();
    }
    return ((ParameterizedTypeTree) variableType).typeArguments().stream();
  }

  private static boolean validationSupported(VariableTree variable) {
    final Stream<AnnotationInstance> annotationInstanceStream = annotationInstances(variable);
    return annotationInstanceStream.anyMatch(MyMissingBeanValidationCheck::isConstraintAnnotation);
  }

  private static Stream<SymbolMetadata.AnnotationInstance> annotationInstances(VariableTree variable) {
    if (variable.type().is(Tree.Kind.PARAMETERIZED_TYPE)) {
      return typeArgumentAnnotationInstances(variable);
    }
    Symbol.TypeSymbol classSymbol = variable.symbol().type().symbol();
    return classAndFieldAnnotationInstances(classSymbol);
  }

  private static Stream<SymbolMetadata.AnnotationInstance> typeArgumentAnnotationInstances(VariableTree variable) {
    return typeArgumentTypeTrees(variable).map(TypeTree::symbolType).map(Type::symbol)
      .flatMap(MyMissingBeanValidationCheck::classAndFieldAnnotationInstances);
  }

  private static Stream<SymbolMetadata.AnnotationInstance> classAndFieldAnnotationInstances(
    Symbol.TypeSymbol classSymbol) {
    return Stream.concat(classAnnotationInstances(classSymbol), fieldAnnotationInstances(classSymbol));
  }

  private static Stream<SymbolMetadata.AnnotationInstance> classAnnotationInstances(Symbol classSymbol) {
    final List<AnnotationInstance> annotations = classSymbol.metadata().annotations();
    return annotations.stream();
  }

  private static Stream<SymbolMetadata.AnnotationInstance> fieldAnnotationInstances(Symbol.TypeSymbol classSymbol) {
    final Collection<Symbol> symbols = classSymbol.memberSymbols();
    return symbols.stream().flatMap(MyMissingBeanValidationCheck::classAnnotationInstances);
  }

  private static boolean isConstraintAnnotation(SymbolMetadata.AnnotationInstance annotationInstance) {
    return annotationInstance.symbol().metadata().isAnnotatedWith(JAVAX_VALIDATION_CONSTRAINT);
  }

  private static boolean isRequestMappingAnnotation(SymbolMetadata.AnnotationInstance annotationInstance) {
    return annotationInstance.symbol().metadata().isAnnotatedWith(SPRING_REQUEST_MAPPING);
  }

  private static boolean isControllerAnnotation(SymbolMetadata.AnnotationInstance annotationInstance) {
    return annotationInstance.symbol().metadata().isAnnotatedWith(SPRING_CONTROLLER);
  }
}
