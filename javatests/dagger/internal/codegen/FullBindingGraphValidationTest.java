/*
 * Copyright (C) 2018 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.TestUtils.endsWithMessage;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableMap;
import com.google.testing.compile.Compilation;
import dagger.testing.compile.CompilerTests;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FullBindingGraphValidationTest {
  private static final Source MODULE_WITH_ERRORS =
      CompilerTests.javaSource(
          "test.ModuleWithErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module",
          "interface ModuleWithErrors {",
          "  @Binds Object object1(String string);",
          "  @Binds Object object2(Long l);",
          "  @Binds Number missingDependency(Integer i);",
          "}");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern MODULE_WITH_ERRORS_MESSAGE =
      endsWithMessage(
          "\033[1;31m[Dagger/DuplicateBindings]\033[0m Object is bound multiple times:",
          "    @Binds Object ModuleWithErrors.object1(String)",
          "    @Binds Object ModuleWithErrors.object2(Long)",
          "    in component: [ModuleWithErrors]",
          "",
          "======================",
          "Full classname legend:",
          "======================",
          "ModuleWithErrors: test.ModuleWithErrors",
          "========================",
          "End of classname legend:",
          "========================");

  private static final Pattern INCLUDES_MODULE_WITH_ERRORS_MESSAGE =
      endsWithMessage(
          "\033[1;31m[Dagger/DuplicateBindings]\033[0m Object is bound multiple times:",
          "    @Binds Object ModuleWithErrors.object1(String)",
          "    @Binds Object ModuleWithErrors.object2(Long)",
          "    in component: [IncludesModuleWithErrors]",
          "",
          "======================",
          "Full classname legend:",
          "======================",
          "IncludesModuleWithErrors: test.IncludesModuleWithErrors",
          "ModuleWithErrors:         test.ModuleWithErrors",
          "========================",
          "End of classname legend:",
          "========================");

  @Test
  public void moduleWithErrors_validationTypeNone() {
    CompilerTests.daggerCompiler(MODULE_WITH_ERRORS)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void moduleWithErrors_validationTypeError() {
    CompilerTests.daggerCompiler(MODULE_WITH_ERRORS)
        .withProcessingOptions(ImmutableMap.of("dagger.fullBindingGraphValidation", "ERROR"))
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContainingMatch(MODULE_WITH_ERRORS_MESSAGE.pattern())
                  .onSource(MODULE_WITH_ERRORS)
                  .onLineContaining("interface ModuleWithErrors");
            });
  }

  // TODO(b/247113233): Convert this test to XProcessing Testing once this bug is fixed.
  @Test
  public void moduleWithErrors_validationTypeWarning() {
    Compilation compilation =
        compilerWithOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(MODULE_WITH_ERRORS.toJFO());

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(MODULE_WITH_ERRORS_MESSAGE)
        .inFile(MODULE_WITH_ERRORS.toJFO())
        .onLineContaining("interface ModuleWithErrors");

    assertThat(compilation).hadWarningCount(1);
  }

  private static final Source INCLUDES_MODULE_WITH_ERRORS =
      CompilerTests.javaSource(
          "test.IncludesModuleWithErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(includes = ModuleWithErrors.class)",
          "interface IncludesModuleWithErrors {}");

  @Test
  public void includesModuleWithErrors_validationTypeNone() {
    CompilerTests.daggerCompiler(MODULE_WITH_ERRORS, INCLUDES_MODULE_WITH_ERRORS)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void includesModuleWithErrors_validationTypeError() {
    CompilerTests.daggerCompiler(MODULE_WITH_ERRORS, INCLUDES_MODULE_WITH_ERRORS)
        .withProcessingOptions(ImmutableMap.of("dagger.fullBindingGraphValidation", "ERROR"))
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContainingMatch(MODULE_WITH_ERRORS_MESSAGE.pattern())
                  .onSource(MODULE_WITH_ERRORS)
                  .onLineContaining("interface ModuleWithErrors");
              subject.hasErrorContaining("ModuleWithErrors has errors")
                  .onSource(INCLUDES_MODULE_WITH_ERRORS)
                  .onLineContaining("ModuleWithErrors.class");
            });
  }

  // TODO(b/247113233): Convert this test to XProcessing Testing once this bug is fixed.
  @Test
  public void includesModuleWithErrors_validationTypeWarning() {
    Compilation compilation =
        compilerWithOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(MODULE_WITH_ERRORS.toJFO(), INCLUDES_MODULE_WITH_ERRORS.toJFO());

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(MODULE_WITH_ERRORS_MESSAGE)
        .inFile(MODULE_WITH_ERRORS.toJFO())
        .onLineContaining("interface ModuleWithErrors");

    // TODO(b/130284666)
    assertThat(compilation)
        .hadWarningContainingMatch(INCLUDES_MODULE_WITH_ERRORS_MESSAGE)
        .inFile(INCLUDES_MODULE_WITH_ERRORS.toJFO())
        .onLineContaining("interface IncludesModuleWithErrors");

    assertThat(compilation).hadWarningCount(2);
  }

  private static final Source A_MODULE =
      CompilerTests.javaSource(
          "test.AModule",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module",
          "interface AModule {",
          "  @Binds Object object(String string);",
          "}");

  private static final Source COMBINED_WITH_A_MODULE_HAS_ERRORS =
      CompilerTests.javaSource(
          "test.CombinedWithAModuleHasErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(includes = AModule.class)",
          "interface CombinedWithAModuleHasErrors {",
          "  @Binds Object object(Long l);",
          "}");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern COMBINED_WITH_A_MODULE_HAS_ERRORS_MESSAGE =
      endsWithMessage(
          "\033[1;31m[Dagger/DuplicateBindings]\033[0m Object is bound multiple times:",
          "    @Binds Object AModule.object(String)",
          "    @Binds Object CombinedWithAModuleHasErrors.object(Long)",
          "    in component: [CombinedWithAModuleHasErrors]",
          "",
          "======================",
          "Full classname legend:",
          "======================",
          "AModule:                      test.AModule",
          "CombinedWithAModuleHasErrors: test.CombinedWithAModuleHasErrors",
          "========================",
          "End of classname legend:",
          "========================");

  @Test
  public void moduleIncludingModuleWithCombinedErrors_validationTypeNone() {
    CompilerTests.daggerCompiler(A_MODULE, COMBINED_WITH_A_MODULE_HAS_ERRORS)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void moduleIncludingModuleWithCombinedErrors_validationTypeError() {
    CompilerTests.daggerCompiler(A_MODULE, COMBINED_WITH_A_MODULE_HAS_ERRORS)
        .withProcessingOptions(ImmutableMap.of("dagger.fullBindingGraphValidation", "ERROR"))
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContainingMatch(COMBINED_WITH_A_MODULE_HAS_ERRORS_MESSAGE.pattern())
                  .onSource(COMBINED_WITH_A_MODULE_HAS_ERRORS)
                  .onLineContaining("interface CombinedWithAModuleHasErrors");
            });
  }

  // TODO(b/247113233): Convert this test to XProcessing Testing once this bug is fixed.
  @Test
  public void moduleIncludingModuleWithCombinedErrors_validationTypeWarning() {
    Compilation compilation =
        compilerWithOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(A_MODULE.toJFO(), COMBINED_WITH_A_MODULE_HAS_ERRORS.toJFO());

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(COMBINED_WITH_A_MODULE_HAS_ERRORS_MESSAGE)
        .inFile(COMBINED_WITH_A_MODULE_HAS_ERRORS.toJFO())
        .onLineContaining("interface CombinedWithAModuleHasErrors");

    assertThat(compilation).hadWarningCount(1);
  }

  private static final Source SUBCOMPONENT_WITH_ERRORS =
      CompilerTests.javaSource(
          "test.SubcomponentWithErrors",
          "package test;",
          "",
          "import dagger.BindsInstance;",
          "import dagger.Subcomponent;",
          "",
          "@Subcomponent(modules = AModule.class)",
          "interface SubcomponentWithErrors {",
          "  @Subcomponent.Builder",
          "  interface Builder {",
          "    @BindsInstance Builder object(Object object);",
          "    SubcomponentWithErrors build();",
          "  }",
          "}");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern SUBCOMPONENT_WITH_ERRORS_MESSAGE =
      endsWithMessage(
          "\033[1;31m[Dagger/DuplicateBindings]\033[0m Object is bound multiple times:",
          "    @Binds Object AModule.object(String)",
          "    @BindsInstance SubcomponentWithErrors.Builder"
              + " SubcomponentWithErrors.Builder.object(Object)",
          "    in component: [SubcomponentWithErrors]",
          "",
          "======================",
          "Full classname legend:",
          "======================",
          "AModule:                test.AModule",
          "SubcomponentWithErrors: test.SubcomponentWithErrors",
          "========================",
          "End of classname legend:",
          "========================");

  private static final Pattern MODULE_WITH_SUBCOMPONENT_WITH_ERRORS_MESSAGE =
      endsWithMessage(
          "\033[1;31m[Dagger/DuplicateBindings]\033[0m Object is bound multiple times:",
          "    @Binds Object AModule.object(String)",
          "    @BindsInstance SubcomponentWithErrors.Builder"
              + " SubcomponentWithErrors.Builder.object(Object)",
          "    in component: [ModuleWithSubcomponentWithErrors → SubcomponentWithErrors]",
          "",
          "======================",
          "Full classname legend:",
          "======================",
          "AModule:                          test.AModule",
          "ModuleWithSubcomponentWithErrors: test.ModuleWithSubcomponentWithErrors",
          "SubcomponentWithErrors:           test.SubcomponentWithErrors",
          "========================",
          "End of classname legend:",
          "========================");

  @Test
  public void subcomponentWithErrors_validationTypeNone() {
    CompilerTests.daggerCompiler(SUBCOMPONENT_WITH_ERRORS, A_MODULE)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void subcomponentWithErrors_validationTypeError() {
    CompilerTests.daggerCompiler(SUBCOMPONENT_WITH_ERRORS, A_MODULE)
        .withProcessingOptions(ImmutableMap.of("dagger.fullBindingGraphValidation", "ERROR"))
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContainingMatch(SUBCOMPONENT_WITH_ERRORS_MESSAGE.pattern())
                  .onSource(SUBCOMPONENT_WITH_ERRORS)
                  .onLineContaining("interface SubcomponentWithErrors");
            });
  }

  // TODO(b/247113233): Convert this test to XProcessing Testing once this bug is fixed.
  @Test
  public void subcomponentWithErrors_validationTypeWarning() {
    Compilation compilation =
        compilerWithOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(SUBCOMPONENT_WITH_ERRORS.toJFO(), A_MODULE.toJFO());

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(SUBCOMPONENT_WITH_ERRORS_MESSAGE)
        .inFile(SUBCOMPONENT_WITH_ERRORS.toJFO())
        .onLineContaining("interface SubcomponentWithErrors");

    assertThat(compilation).hadWarningCount(1);
  }

  private static final Source MODULE_WITH_SUBCOMPONENT_WITH_ERRORS =
      CompilerTests.javaSource(
          "test.ModuleWithSubcomponentWithErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(subcomponents = SubcomponentWithErrors.class)",
          "interface ModuleWithSubcomponentWithErrors {}");

  @Test
  public void moduleWithSubcomponentWithErrors_validationTypeNone() {
    CompilerTests.daggerCompiler(
            MODULE_WITH_SUBCOMPONENT_WITH_ERRORS, SUBCOMPONENT_WITH_ERRORS, A_MODULE)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void moduleWithSubcomponentWithErrors_validationTypeError() {
    CompilerTests.daggerCompiler(
            MODULE_WITH_SUBCOMPONENT_WITH_ERRORS, SUBCOMPONENT_WITH_ERRORS, A_MODULE)
        .withProcessingOptions(ImmutableMap.of("dagger.fullBindingGraphValidation", "ERROR"))
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContainingMatch(
                      MODULE_WITH_SUBCOMPONENT_WITH_ERRORS_MESSAGE.pattern())
                  .onSource(MODULE_WITH_SUBCOMPONENT_WITH_ERRORS)
                  .onLineContaining("interface ModuleWithSubcomponentWithErrors");
              // TODO(b/130283677)
              subject.hasErrorContainingMatch(SUBCOMPONENT_WITH_ERRORS_MESSAGE.pattern())
                  .onSource(SUBCOMPONENT_WITH_ERRORS)
                  .onLineContaining("interface SubcomponentWithErrors");
            });
  }

  // TODO(b/247113233): Convert this test to XProcessing Testing once this bug is fixed.
  @Test
  public void moduleWithSubcomponentWithErrors_validationTypeWarning() {
    Compilation compilation =
        compilerWithOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(
                MODULE_WITH_SUBCOMPONENT_WITH_ERRORS.toJFO(),
                SUBCOMPONENT_WITH_ERRORS.toJFO(),
                A_MODULE.toJFO());

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(MODULE_WITH_SUBCOMPONENT_WITH_ERRORS_MESSAGE)
        .inFile(MODULE_WITH_SUBCOMPONENT_WITH_ERRORS.toJFO())
        .onLineContaining("interface ModuleWithSubcomponentWithErrors");

    // TODO(b/130283677)
    assertThat(compilation)
        .hadWarningContainingMatch(SUBCOMPONENT_WITH_ERRORS_MESSAGE)
        .inFile(SUBCOMPONENT_WITH_ERRORS.toJFO())
        .onLineContaining("interface SubcomponentWithErrors");

    assertThat(compilation).hadWarningCount(2);
  }

  private static final Source A_SUBCOMPONENT =
      CompilerTests.javaSource(
          "test.ASubcomponent",
          "package test;",
          "",
          "import dagger.BindsInstance;",
          "import dagger.Subcomponent;",
          "",
          "@Subcomponent(modules = AModule.class)",
          "interface ASubcomponent {",
          "  @Subcomponent.Builder",
          "  interface Builder {",
          "    ASubcomponent build();",
          "  }",
          "}");

  private static final Source COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS =
      CompilerTests.javaSource(
          "test.CombinedWithASubcomponentHasErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(subcomponents = ASubcomponent.class)",
          "interface CombinedWithASubcomponentHasErrors {",
          "  @Binds Object object(Number number);",
          "}");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS_MESSAGE =
      endsWithMessage(
          "\033[1;31m[Dagger/DuplicateBindings]\033[0m Object is bound multiple times:",
          "    @Binds Object AModule.object(String)",
          "    @Binds Object CombinedWithASubcomponentHasErrors.object(Number)",
          "    in component: [CombinedWithASubcomponentHasErrors → ASubcomponent]",
          "",
          "======================",
          "Full classname legend:",
          "======================",
          "AModule:                            test.AModule",
          "ASubcomponent:                      test.ASubcomponent",
          "CombinedWithASubcomponentHasErrors: test.CombinedWithASubcomponentHasErrors",
          "========================",
          "End of classname legend:",
          "========================");

  @Test
  public void moduleWithSubcomponentWithCombinedErrors_validationTypeNone() {
    CompilerTests.daggerCompiler(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS, A_SUBCOMPONENT, A_MODULE)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void moduleWithSubcomponentWithCombinedErrors_validationTypeError() {
    CompilerTests.daggerCompiler(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS, A_SUBCOMPONENT, A_MODULE)
        .withProcessingOptions(ImmutableMap.of("dagger.fullBindingGraphValidation", "ERROR"))
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContainingMatch(
                      COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS_MESSAGE.pattern())
                  .onSource(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS)
                  .onLineContaining("interface CombinedWithASubcomponentHasErrors");
            });
  }

  // TODO(b/247113233): Convert this test to XProcessing Testing once this bug is fixed.
  @Test
  public void moduleWithSubcomponentWithCombinedErrors_validationTypeWarning() {
    Compilation compilation =
        compilerWithOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(
                COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS.toJFO(),
                A_SUBCOMPONENT.toJFO(),
                A_MODULE.toJFO());

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS_MESSAGE)
        .inFile(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS.toJFO())
        .onLineContaining("interface CombinedWithASubcomponentHasErrors");

    assertThat(compilation).hadWarningCount(1);
  }

  @Test
  public void bothAliasesDifferentValues() {
    CompilerTests.daggerCompiler(MODULE_WITH_ERRORS)
        .withProcessingOptions(
            ImmutableMap.of(
                "dagger.moduleBindingValidation", "NONE",
                "dagger.fullBindingGraphValidation", "ERROR"))
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Only one of the equivalent options "
                      + "(-Adagger.fullBindingGraphValidation, -Adagger.moduleBindingValidation)"
                      + " should be used; prefer -Adagger.fullBindingGraphValidation");
            });
  }

  @Test
  public void bothAliasesSameValue() {
    CompilerTests.daggerCompiler(MODULE_WITH_ERRORS)
        .withProcessingOptions(
            ImmutableMap.of(
                "dagger.moduleBindingValidation", "NONE",
                "dagger.fullBindingGraphValidation", "NONE"))
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(1);
              subject.hasWarningContaining(
                  "Only one of the equivalent options "
                      + "(-Adagger.fullBindingGraphValidation, -Adagger.moduleBindingValidation)"
                      + " should be used; prefer -Adagger.fullBindingGraphValidation");
            });
  }
}
