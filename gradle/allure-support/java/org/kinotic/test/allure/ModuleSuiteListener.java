package org.kinotic.test.allure;

import io.qameta.allure.listener.TestLifecycleListener;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.TestResult;

import java.util.List;

/**
 * Groups every Java module's tests under a single {@code Kinotic Java} bucket in the merged Allure
 * report, mirroring the {@code Kinotic JS} tree: {@code Kinotic Java > <module> > <class>}.
 *
 * <p>The module name is supplied per module by the build via the {@code kinotic.allure.module}
 * system property. When that property is absent the listener does nothing, leaving the default
 * labels untouched.
 */
public class ModuleSuiteListener implements TestLifecycleListener {

    private static final String PARENT_SUITE = "Kinotic Java";
    private static final String MODULE_PROPERTY = "kinotic.allure.module";

    @Override
    public void beforeTestStop(TestResult result) {
        String module = System.getProperty(MODULE_PROPERTY);
        if (module == null || module.isBlank()) {
            return;
        }

        List<Label> labels = result.getLabels();

        // allure-junit5 fixes the suite label to the fully qualified class name and never sets
        // parentSuite/subSuite, so the class is the only suite-tree label present here. Capture it,
        // drop the suite-tree labels, then rebuild the three levels we want.
        String testClass = labels.stream()
                                 .filter(label -> "suite".equals(label.getName()))
                                 .map(Label::getValue)
                                 .findFirst()
                                 .orElse(null);

        labels.removeIf(label -> {
            String name = label.getName();
            return "parentSuite".equals(name) || "suite".equals(name) || "subSuite".equals(name);
        });

        labels.add(new Label().setName("parentSuite").setValue(PARENT_SUITE));
        labels.add(new Label().setName("suite").setValue(module));
        if (testClass != null) {
            labels.add(new Label().setName("subSuite").setValue(simpleClassName(testClass)));
        }
    }

    private static String simpleClassName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }
}
