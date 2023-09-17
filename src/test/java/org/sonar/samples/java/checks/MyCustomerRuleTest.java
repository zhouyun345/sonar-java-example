package org.sonar.samples.java.checks;

import org.junit.jupiter.api.Test;
import org.sonar.java.checks.verifier.CheckVerifier;
import org.sonar.samples.java.checks.MyCustomerRule;

public class MyCustomerRuleTest {

  @Test
  void test() {
    CheckVerifier.newVerifier()
      .onFile("src/test/files/MyCustomRule.java")
      .withCheck(new MyCustomerRule())
      .verifyIssues();
  }
}
